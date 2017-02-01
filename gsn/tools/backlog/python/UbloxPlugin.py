# -*- coding: UTF-8 -*-
__author__      = "Tonio Gsell <tgsell@tik.ee.ethz.ch>"
__copyright__   = "Copyright 2010, ETH Zurich, Switzerland, Tonio Gsell"
__license__     = "GPL"
__version__     = "$Revision$"
__date__        = "$Date$"
__id__          = "$Id$"

import time
import os
import errno
import logging
import StringIO
import collections
import asyncore
import socket
import struct
import Queue
import serial
from threading import Thread, Lock, Event, Timer

import BackLogMessage
import pynmea2 
import ublox
import RTCM3
from RTCM3_Decls import *
from AbstractPlugin import AbstractPluginClass

DEFAULT_BACKLOG = True

MAX_MESSAGE_LENGTH = 2048
PRIMARY_READ_BYTES = 2048
RELAY_READ_BYTES = 2048
SEND_QUEUE_SIZE = 50

PREFIX = 'REF'
NMEA_FILE_SUFFIX = '.nmea'
UBX_FILE_SUFFIX = '.ubx'
RTCM3_FILE_SUFFIX = '.rtcm3'

MSG_TYPE_UBX   = 1
MSG_TYPE_NMEA  = 2
MSG_TYPE_RTCM3 = 3

NMEA_PREAMBLE = b"$"


def messageReady(logger, b):
    for _ in xrange(len(b)):
        if ord(b[0]) == ublox.PREAMBLE1:
            if len(b) >= 2 and ord(b[1]) != ublox.PREAMBLE2:
                return (None, None, b[1:])
            if len(b) >= 6:
                l = struct.unpack('<H', b[4:6])[0]+8
                if len(b) >= l:
                    return (MSG_TYPE_UBX, b[:l], b[l:])
                else:
                    return (None, None, b)
            else:
                return (None, None, b)
        elif b[0] == NMEA_PREAMBLE:
            s = b.split('\r\n', 1)
            if len(s) > 1:
                return (MSG_TYPE_NMEA,s[0]+bytes('\r\n'),s[1])
            else:
                return (None,None,b)
        elif ord(b[0]) == RTCM3.RTCM3_Preamble:
            if len(b) >= 3:
                l = (struct.unpack('>H',b[1:3])[0]&0x3FF)+6
                if l > 1023:
                    logger.error('RTCM3 message too long')
                    return (None, None, b[1:])
                elif l <= len(b):
                    return (MSG_TYPE_RTCM3, b[:l], b[l:])
                else:
                    return (None, None, b)
            else:
                return (None, None, b)
        else:
            b = b[1:]
    return (None, None, bytes())


class UbloxPluginClass(AbstractPluginClass):
    '''
    This plugin forwards incoming packets from a ublox chip to GSN.
    
    data/instance attributes:
    _plugstop
    '''
    
    def __init__(self, parent, config):
        AbstractPluginClass.__init__(self, parent, config, DEFAULT_BACKLOG)
        
        dev_primary = self.getOptionValue('dev_primary')
        if not dev_primary:
            raise TypeError('no dev_primary specified')
        else:
            self.info('using primary input device %s' % (dev_primary,))
            
        dev_primary_baudrate = self.getOptionValue('dev_primary_baudrate')
            
        self._ubloxCommPrimary = UbloxComm(self, dev_primary, dev_primary_baudrate, False)
            
        self._fileFolder = self.getOptionValue('primary_folder')
        if self._fileFolder:
            self.info('use %s to write message files to' % (self._fileFolder,))
            if not os.path.isdir(self._fileFolder):
                self.warning('message file folder >%s< is not a directory -> creating it' % (self._fileFolder,))
                os.makedirs(self._fileFolder)
            filename_time = time.time()
            self._nmeaFile = open('%s/%s%s%s' % (os.path.dirname(self._fileFolder), PREFIX, time.strftime('%Y%m%d%H%M%S', time.gmtime(filename_time)), NMEA_FILE_SUFFIX), 'w')
            self._ubxFile = open('%s/%s%s%s' % (os.path.dirname(self._fileFolder), PREFIX, time.strftime('%Y%m%d%H%M%S', time.gmtime(filename_time)), UBX_FILE_SUFFIX), 'w')
            self._rtcm3File = open('%s/%s%s%s' % (os.path.dirname(self._fileFolder), PREFIX, time.strftime('%Y%m%d%H%M%S', time.gmtime(filename_time)), RTCM3_FILE_SUFFIX), 'w')
        
        dev_dispatch = self.getOptionValue('dev_dispatch')
        self._ubloxDispatcher = None
        if dev_dispatch:
            self.info('dispatch data on port %s' % (dev_dispatch,))
            self._ubloxDispatcher = UbloxDispatcher(self, ('', int(dev_dispatch)))
            self._asyncoreThread =  Thread(target=asyncore.loop,kwargs = {'timeout':1} )
        
        self._dispatchOnlyRtcm3 = False
        dispatchOnlyRtcm3 = self.getOptionValue('dispatch_only_rtcm3')
        if dispatchOnlyRtcm3:
            self._dispatchOnlyRtcm3 = bool(dispatchOnlyRtcm3)
            if self._dispatchOnlyRtcm3:
                self.info('dispatch only RTCM3 messages')
        
        relayOnlyRtcm3 = self.getOptionValue('relay_only_rtcm3')
        if relayOnlyRtcm3:
            relayOnlyRtcm3 = bool(relayOnlyRtcm3)
            if relayOnlyRtcm3:
                self.info('relay only RTCM3 messages')
        else:
            relayOnlyRtcm3 = False
        
        dev_relay = self.getOptionValue('dev_relay')
        self._ubloxRelay = None
        if dev_relay:
            self.info('forwarding data from %s to primary device' % (dev_relay,))
            dev_relay_baudrate = self.getOptionValue('dev_relay_baudrate')
            self._ubloxRelay = UbloxRelay(self, UbloxComm(self, dev_relay, dev_relay_baudrate, False), self._ubloxCommPrimary, relayOnlyRtcm3)
        
        self._plugstop = False
        
        
    def run(self):
        self.name = 'UbloxPlugin-Thread'
        self.info('started')
        
        if self._ubloxRelay:
            self._ubloxRelay.start()
        if self._ubloxDispatcher:
            self._asyncoreThread.start()
            
        self._ubloxCommPrimary.start()
        
        b = bytes()
        while not self._plugstop:
            time.sleep(0.01)
            try:
                try:
                    a = self._ubloxCommPrimary.read(PRIMARY_READ_BYTES)
                except socket.error, e:
                    pass
            except Exception, e:
                self.exception(e)
                
            if not a:
                continue
                
            if self._ubloxDispatcher and not self._dispatchOnlyRtcm3:
                self._ubloxDispatcher.broadcast(a)
            
            b = b + a
            type = True
            
            while type and not self._plugstop:
                try:
                    (type, msg, b) = messageReady(self, b)
                    
                    if type == MSG_TYPE_UBX:
#                         ubloxMsg = ublox.UBloxMessage()
#                         ubloxMsg.add(msg)
#                         if ubloxMsg.valid():
#                             self.info('UBX: %s' % (ubloxMsg,))
                        self._ubxFile.write('%s\r\n' % (msg,))
                        self._ubxFile.flush()
                    elif type == MSG_TYPE_NMEA:
#                        self.info('NMEA: %s' % (msg,))
                        self._nmeaFile.write(msg)
                        self._nmeaFile.flush()
                    elif type == MSG_TYPE_RTCM3:
#                        self.info('RTCM3: %s' % (msg,))
                        if self._ubloxDispatcher and self._dispatchOnlyRtcm3:
                            self._ubloxDispatcher.broadcast(msg)
                        self._rtcm3File.write('%s\r\n' % (msg,))
                        self._rtcm3File.flush()
                except Exception, e:
                    self.exception(e)
            
        if self._fileFolder:
            self._nmeaFile.close()
            self._ubxFile.close()
            self._rtcm3File.close()
        
        if self._ubloxDispatcher:
            self._asyncoreThread.join()
        if self._ubloxRelay:
            self._ubloxRelay.join()
        
        self._logger.info('died')
        
        
    def incoming(self, b):
        if not self._ubloxCommPrimary.write(b):
            self.error('could not write message')
        
    
    def stop(self):
        self._plugstop = True
        if self._ubloxDispatcher:
            self._ubloxDispatcher.close()
        if self._ubloxRelay:
            self._ubloxRelay.stop()
        self._ubloxCommPrimary.stop()
        self.info('stopped')
    
    
    def msgReceived(self, data):
        return
    
    
    def isBusy(self):
        return False
    
    
    def needsWLAN(self):
        return False
    

class UbloxComm(Thread):

    '''
    data/instance attributes:
    _logger
    _sendqueue
    _ubloxCommStop
    _ubloxCommLock
    _dev
    _read_only
    '''
    
    SERIAL = 1
    TCP = 2
    FILE = 3
    
    def __init__(self, parent, serial_device, baudrate=None, blocking=True):
        Thread.__init__(self, name='%s-Thread' % (self.__class__.__name__,))
        self._logger = logging.getLogger(self.__class__.__name__)
        self._sendqueue = Queue.Queue(SEND_QUEUE_SIZE)
        self._ubloxPlugin = parent
        self._ubloxCommStop = False
        
        self._dev = None
        self._deviceConnected = False
        self._ubloxCommLock = Lock()
        self._read_only = False
        self._blocking = blocking
        if self._blocking:
            self._timeout = 60
        else:
            self._timeout = 0

        if serial_device.startswith("tcp:"):
            self._devType = self.TCP
            a = serial_device.split(':')
            self._tcpDestinationAddr = (a[1], int(a[2]))
            self._testTCPDevice()
        elif os.path.isfile(serial_device):
            self._devType = self.FILE
            self._read_only = True
            self._dev = open(serial_device, mode='rb')
            self._deviceConnected = True
        else:
            self._devType = self.SERIAL
            
            if baudrate is None:
                raise TypeError('no baudrate specified')
            else:
                baudrate = int(baudrate)
                self._logger.info('using baudrate %s' % (baudrate,))
            
            self._device = serial_device
            self._baudrate = baudrate
            self._testSerialDevice()


    def run(self):
        self._logger.info('started')
        
        # speed optimizations
        acquire = self._ubloxCommLock.acquire
        release = self._ubloxCommLock.release
        get = self._sendqueue.get
        
        while not self._ubloxCommStop:
            ubloxCommMsg = get()
            if self._ubloxCommStop:
                break
            
            try:
                l = self._write(ubloxCommMsg)
                if l != len(ubloxCommMsg) and self._deviceConnected:
                    self._ubloxPlugin.exception('not all bytes have been written in UbloxComm')
            except Exception, e:
                if not self._ubloxCommStop:
                    self._ubloxPlugin.exception('error in UbloxComm write functionality: %s' % (e,))
            finally:
                self._sendqueue.task_done()
 
        self._logger.info('died')


    def write(self, packet):
        if not self._ubloxCommStop:
            try:
                self._sendqueue.put_nowait(packet)
            except Queue.Full:
                self._logger.debug('UbloxComm send queue is full')
                return False
        return True
    

    def read(self, n):
        '''read some bytes'''
        ret = bytes()
        self._ubloxCommLock.acquire()  
        try:
            if self._devType == self.SERIAL:
                ret = self._serialAccess(n, 'r')
            elif self._devType == self.TCP:
                ret = self._tcpAccess(n, 'r')
            elif self._devType == self.FILE:
                try:
                    ret = self._dev.read(n)
                except Exception, e:
                    self._logger.error(e)
        except Exception, e:
            self._logger.error(e)
        finally:
            self._ubloxCommLock.release()
        return ret
    

    def _write(self, buf):
        '''write some bytes'''
        ret = False
        
        self._ubloxCommLock.acquire()
        if not self._read_only:
            if self._devType == self.SERIAL:
                ret = self._serialAccess(buf, 'w')
            elif self._devType == self.TCP:
                ret = self._tcpAccess(buf, 'w')
            elif self._devType == self.FILE:
                try:
                    ret = self._dev.write(buf)
                except Exception, e:
                    self._logger.error(e)
            self._ubloxCommLock.release()
            return ret
        
        
    def _tcpAccess(self, data, mode):
        if (mode == 'w'):
            if self._dev is None:
                return -1
            try:
                if self._deviceConnected:
                    l = self._dev.send(data)
                    if l > 0:
                        return l
                    else:
                        self._logger.warning("TCP connection lost -> retry to connect in 2 seconds")
                        self._deviceConnected = False
                        Timer(2.0, self._testTCPDevice).start()
                        return -1
                else:
                    return -1
            except socket.error, e:
                err = e.args[0]
                if err == errno.EAGAIN or err == errno.EWOULDBLOCK:
                    return 0
                else:
                        self._logger.warning("TCP connection lost -> retry to connect in 2 seconds")
                        self._deviceConnected = False
                        Timer(2.0, self._testTCPDevice).start()
                        return -1
        elif (mode == 'r'):
            if self._dev is None:
                return bytes()
            try:
                if self._deviceConnected:
                    ret = self._dev.recv(data)
                    if len(ret) > 0:
                        return ret
                    else:
                        self._logger.warning("TCP connection lost -> retry to connect in 2 seconds")
                        self._deviceConnected = False
                        Timer(2.0, self._testTCPDevice).start()
                        return bytes()
                else:
                    return bytes()
            except socket.error, e:
                err = e.args[0]
                if err == errno.EAGAIN or err == errno.EWOULDBLOCK:
                    return bytes()
                else:
                    self._logger.warning("TCP connection lost -> retry to connect in 2 seconds")
                    self._deviceConnected = False
                    Timer(2.0, self._testTCPDevice).start()
                    return bytes()
        else:
            self._logger.error("tcpAccess: Wrong mode specified")
            return False
        
        
    def _serialAccess(self, data, mode):
        if (mode == 'w'):
            if self._dev is None:
                return -1
            try:
                if self._deviceConnected:
                    return self._dev.write(data)
                else:
                    return -1
            except Exception, e:
                self._logger.warning("Serial connection lost -> retry to connect in 2 seconds")
                self._deviceConnected = False
                Timer(2.0, self._testSerialDevice).start()
                return -1
        elif (mode == 'r'):
            if self._dev is None:
                return bytes()
            try:
                if self._deviceConnected:
                    d = self._dev.read(data)
    #                 if (len(d) != data):
    #                     return ''
                    return d
                else:
                    return bytes()
            except Exception, e:
                self._logger.warning("Serial connection lost -> retry to connect in 2 seconds")
                self._deviceConnected = False
                Timer(2.0, self._testSerialDevice).start()
                return bytes()
        else:
            self._logger.error("serialAccess: Wrong mode specified")
            return False
        
        
    def _testSerialDevice(self):
        if not self._ubloxCommStop:
            try:
                self._dev = serial.Serial(self._device, baudrate=self._baudrate,
                                         dsrdtr=False, rtscts=False, xonxoff=False, timeout=self._timeout)
                self._deviceConnected = True
                self._logger.info("Successfully (re)opened serial connection")
            except Exception, e:
                if self._dev and self._dev.isOpen():
                    self._dev.close()
                self._logger.warning('Could not open serial connection -> retry in 2 seconds')
                Timer(2.0, self._testSerialDevice).start()
                
                
    def _testTCPDevice(self):
        if not self._ubloxCommStop:
            try:
                self._dev = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self._dev.connect(self._tcpDestinationAddr)
                if self._blocking:
                    self._dev.setblocking(1)
                else:
                    self._dev.setblocking(0)
                self._dev.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                self._dev.setsockopt(socket.SOL_TCP, socket.TCP_NODELAY, 1)
                self._deviceConnected = True
                self._logger.info("Successfully (re)opened TCP connection")
            except Exception, e:
                self._logger.warning('Could not open TCP connection -> retry in 2 seconds')
                Timer(2.0, self._testTCPDevice).start()


    def stop(self):
        self._ubloxCommStop = True
        try:
            self._sendqueue.put_nowait('end')
        except Queue.Full:
            pass
        except Exception, e:
            self._logger.exception(e)
        self._logger.info('stopped')


class UbloxRelay(Thread):
    
    def __init__(self, parent, deviceFrom, deviceTo, relayOnlyRtcm3=False):
        Thread.__init__(self, name='%s-Thread' % (self.__class__.__name__,))
        self._logger = logging.getLogger(self.__class__.__name__)
        self._deviceFrom = deviceFrom
        self._deviceTo = deviceTo
        self._ubloxPlugin = parent
        self._ubloxRelayStop = False
        self._relayOnlyRtcm3 = relayOnlyRtcm3
        self.error = self._logger.error
        self.warning = self._logger.warning
        
        self._rtcm3 = RTCM3.RTCM3(2)
        
        
    def run(self):
        self._deviceFrom.start()
        
        b = bytes()
        while not self._ubloxRelayStop:
            time.sleep(0.01)
            try:
                try:
                    a = self._deviceFrom.read(RELAY_READ_BYTES)
                except socket.error, e:
                    continue
                if not a:
                    continue
                b = b + a
                type = True
                      
                if not self._relayOnlyRtcm3:
                    self._deviceTo.write(b)
                    b = bytes()
                else:
                    while type and not self._ubloxRelayStop:
                        (type, msg, b) = messageReady(self._logger, b)
                        if type is MSG_TYPE_RTCM3:
                            self._deviceTo.write(msg)
            except Exception, e:
                self._ubloxPlugin.exception(e)
 
        self._logger.info('died')
                
                
    def stop(self):
        self._ubloxRelayStop = True
        self._deviceFrom.stop()
        self._logger.info('stopped')


class UbloxDispatcher(asyncore.dispatcher):

    def __init__(self, parent, address=('localhost', 0)):
        self._logger = logging.getLogger(self.__class__.__name__)
        self._ubloxPlugin = parent
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.set_reuse_addr()
        self.bind(address)
        self.listen(1)
        self.clients = []

    def handle_accept(self):
        socket, addr = self.accept() # For the remote client.
        self._logger.info('Accepted client at %s', addr)
        self.clients.append(RemoteClient(self, socket, addr))

    def handle_read(self):
        self._logger.info('Received message: %s', self.read())

    def broadcast(self, message):
#         self._logger.info('Broadcasting message: %s', message)
        for remote_client in self.clients:
            remote_client.say(message)
            
    def incoming(self, message):
        self._ubloxPlugin.incoming(message)


class RemoteClient(asyncore.dispatcher):

    """Wraps a remote client socket."""

    def __init__(self, host, socket, address):
        asyncore.dispatcher.__init__(self, socket)
        self._logger = logging.getLogger(self.__class__.__name__)
        self.host = host
        self.outbox = collections.deque()
        self.buffer = None

    def say(self, message):
        self.outbox.append(message)

    def handle_read(self):
        client_message = self.recv(MAX_MESSAGE_LENGTH)
        self.host.incoming(client_message)

    def handle_write(self):
        try:
            if not self.outbox:
                return
            if not self.buffer:
                self.buffer = self.outbox.popleft()
            sent = self.send(self.buffer)
            self.buffer = self.buffer[sent:]
        except Exception, e:
            self._logger.error(e)
            
    def writable(self):
        ''' It has point to call handle_write only when there's something in outbox
            Having this method always returning true will cause 100% CPU usage
        '''
        return bool(self.outbox)

    def handle_close(self):
        self.host.clients.remove(self)
        self._logger.info("Client removed from list")
        self.close()

    def handle_error(self):
        self._logger.error("Socket error")