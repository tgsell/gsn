'''
Created on Jul 15, 2009

@author: Tonio Gsell
@author: Mustafa Yuecel
'''

import struct
import os
import logging
import zlib
from collections import deque
from threading import Event, Thread
from pyinotify import WatchManager, ThreadedNotifier, EventsCodes, ProcessEvent

import BackLogMessage
from AbstractPlugin import AbstractPluginClass

CHUNK_SIZE_BYTES = 1000

CHUNK_ACK_CHECK_SEC = 5

INIT_PACKET = 0
RESEND_PACKET = 1
CHUNK_PACKET = 2
CRC_PACKET = 3

class BigBinaryPluginClass(AbstractPluginClass):
    '''
    Reads a file from disk and sends it to GSN.
    
    The following values have to specified in the plugin configuration file:
        file:        the location of the file
    '''

    '''
    data/instance attributes:
    _notifier
    _stopped
    _filedeque
    _lasttimestamp
    _work
    _crcAccepted
    
    TODO: remove CRC functionality after long time testing. It is not necessary over TCP.
    '''
    
    def __init__(self, parent, options):
        AbstractPluginClass.__init__(self, parent, options)
        
        paths = self.getOptionValues('path')

        if paths is None:
            raise TypeError('no paths specified to watch')

        wm = WatchManager()
        self._notifier = ThreadedNotifier(wm, BinaryChangedProcessing(self))
        
        self._work = Event()
        self._filedeque = deque()
        self._msgdeque = deque()
        
        for pathname in paths:
            if os.path.isdir(pathname):
                # tell the watch manager which folders to watch for newly written files
                wm.add_watch(pathname, EventsCodes.FLAG_COLLECTIONS['OP_FLAGS']['IN_CLOSE_WRITE'])
                self.info('enable close-after-write notification for path ' + pathname)
                
                # check the paths for existing files which have to be sent
                filetime = []
                for root, dirs, files in os.walk(pathname):
                    for filename in files:
                        f = os.path.join(root, filename)
                        time = os.stat(f).st_mtime
                        filetime.append((time, f))
            else:
                self.warning(pathname + ' is not a path')
        
        # sort the existing files by time
        filetime.sort()
        # and put it into the fifo
        for file in filetime:
            self._filedeque.appendleft(file[1])
            self.debug('putting existing file into FIFO: ' + file[1])

        self._lasttimestamp = -1
        self._stopped = False


    def getMsgType(self):
        return BackLogMessage.BIG_BINARY_MESSAGE_TYPE
    
    
    def ackReceived(self, timestamp):
       if timestamp == self._lasttimestamp:
           self._work.set()
    
    
    def msgReceived(self, msg):
        self.debug('message received')
        self._msgdeque.appendleft(msg)
        self._work.set()
           
        
    def run(self):
        self.info('started')
        
        # start pyinotify to watch the specified folders
        try:
            self._notifier.start()
        except Exception, e:
            if not self._stopped:
                self.exception(e)
                
        filedescriptor = None
                
        while not self._stopped:
            # wait for the next file event to happen
            self._work.wait()
            if self._stopped:
                break
                
            try:
                message = self._msgdeque.pop()
                # the first byte of the message defines its type
                type = struct.unpack('B', message[0])[0]
                self.debug('type: ' + str(type))
                
                # if the type is INIT_PACKET we have to send a new file
                if type == INIT_PACKET:
                    if filedescriptor and not filedescriptor.closed:
                        filename = filedescriptor.name
                        self.warning('new file request, but actual file (' + filename + ') not yet closed -> remove it!')
                        os.chmod(filename, 0744)
                        filedescriptor.close()
                        os.remove(filename)
                # if the type is RESEND_PACKET we have to resend a part of a file...
                elif type == RESEND_PACKET:
                    # how much of the file has already been downloaded
                    downloaded = int(struct.unpack('<I', message[1:5])[0])
                    # what is the name of the file to resend
                    filename = struct.unpack(str(len(message)-5) + 's', message[5:len(message)])[0]
                    self.debug('downloaded size: ' + str(downloaded))
                    self.debug('path: ' + filename)
                    self.crcAccepted = False
                    
                    try:
                        # open the specified file
                        filedescriptor = open(filename, 'rb')
                        os.chmod(filename, 0444)
                        
                        if downloaded > 0:
                            # recalculate the crc
                            sizecalculated = 0
                            crc = None
                            while sizecalculated != downloaded:
                                if downloaded - sizecalculated > 4096:
                                    part = filedescriptor.read(4096)
                                else:
                                    part = filedescriptor.read(downloaded - sizecalculated)
                                    
                                if crc:
                                    crc = zlib.crc32(part, crc)
                                else:
                                    crc = zlib.crc32(part)
                                sizecalculated += len(part)
        
                            # calculate chunk number
                            chunkNumber = downloaded / CHUNK_SIZE_BYTES
                            self.debug('calculated chunk number: ' + str(chunkNumber))
                            self.debug('recalculated crc: ' + str(crc))
                        else:
                            crc = None
                            chunkNumber = 0
                    except IOError, e:
                        self.warning(e)
            except  IndexError:
                pass
        
            try:
                if (not filedescriptor or filedescriptor.closed):
                    # get the next file to send out of the fifo
                    filename = self._filedeque.pop()
                    
                    if os.path.isfile(filename):
                        # open the file
                        filedescriptor = open(filename, 'rb')
                        os.chmod(filename, 0444)
                    else:
                        # if the file does not exist we are continuing in the fifo
                        continue
                    
                    # get the size of the file
                    filelen = os.path.getsize(filename)
                    
                    if filelen == 0:
                        # if the file is empty we ignore it and continue in the fifo
                        self.debug('ignore empty file: ' + filename)
                        os.chmod(filename, 0744)
                        filedescriptor.close()
                        os.remove(filename)
                        continue
                    
                    filenamelen = len(filename)
                    if filenamelen > 255:
                        filenamelen = 255
                    
                    chunkNumber = 0
                    packet = struct.pack('<BqI', INIT_PACKET, os.stat(filename).st_mtime, filelen) 
                    packet += struct.pack(str(filenamelen) + 'sx', filename)
                    
                    self.debug('sending initial binary packet for ' + filedescriptor.name)
                
                    crc = None
                # or are we already sending chunks of a file?
                else:
                    # read the next chunk out of the opened file
                    chunk = filedescriptor.read(CHUNK_SIZE_BYTES)
                    
                    if crc:
                        crc = zlib.crc32(chunk, crc)
                    else:
                        crc = zlib.crc32(chunk)
                    
                    if chunk == "":
                        # so we have reached the end of the file...
                        self.debug('binary completely sent')
                        
                        # create the crc packet [type, crc]
                        packet = struct.pack('<Bi', CRC_PACKET, crc)
                        packet += chunk
                        self._work.clear()
                        
                        filename = filedescriptor.name
                        os.chmod(filename, 0744)
                        filedescriptor.close()
                        
                        # send it
                        self.crcAccepted = True
                        self.debug('sending crc: ' + str(crc))
                        timestamp = self.getTimeStamp()
                        self._lasttimestamp = timestamp
                        first = True
                        while not self._work.isSet() and self.isGSNConnected() and not self._msgdeque:
                            if not first:
                                self.info('resend crc message')
                            self.processMsg(timestamp, packet, self._backlog)
                            self.debug('wait for crc acknowledge')
                            # and resend it if no ack has been received
                            self._work.wait(CHUNK_ACK_CHECK_SEC)
                            first = False
                        if self.crcAccepted and self.isGSNConnected():
                            # crc has been accepted by GSN
                            self.debug('crc has been accepted for ' + filename)
                            # remove it from disk
                            os.remove(filename)
                        continue
                    
                    # increase the chunk number
                    chunkNumber = chunkNumber + 1
                    # create the packet [type, chunk number (4bytes)]
                    packet = struct.pack('<BI', CHUNK_PACKET, chunkNumber)
                    packet += chunk
                    
                    self.debug('sending binary chunk number ' + str(chunkNumber) + ' for ' + filedescriptor.name)
                
                # tell PSBackLogMain to send the chunk to GSN
                timestamp = self.getTimeStamp()
                self._lasttimestamp = timestamp
                # send the chunk
                self._work.clear()
                first = True
                while not self._work.isSet() and self.isGSNConnected() and not self._msgdeque:
                    if not first:
                        self.info('resend message')
                    self.processMsg(timestamp, packet, self._backlog)
                    # and resend it if no ack has been received
                    self._work.wait(CHUNK_ACK_CHECK_SEC)
                    first = False
            except IndexError:
                # fifo is empty
                self.debug('file FIFO is empty waiting for next file to arrive')
                self._work.clear()
            except Exception, e:
                self.error(e.__str__())
            

        self.info('died')
    
    
    def stop(self):
        self._stopped = True
        self._notifier.stop()
        self._work.set()
        self._filedeque.clear()
        self.info('stopped')




class BinaryChangedProcessing(ProcessEvent):
    
    '''
    data/instance attributes:
    _logger
    _parent
    '''

    def __init__(self, parent):
        self._logger = logging.getLogger(self.__class__.__name__)
        self._parent = parent

    def process_default(self, event):
        self._logger.debug(event.pathname + ' changed')
        
        self._parent._filedeque.appendleft(event.pathname)
        if self._parent.isGSNConnected():
            self._parent._work.set()