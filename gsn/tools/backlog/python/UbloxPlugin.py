# -*- coding: UTF-8 -*-
__author__      = "Tonio Gsell <tgsell@tik.ee.ethz.ch>"
__copyright__   = "Copyright 2010, ETH Zurich, Switzerland, Tonio Gsell"
__license__     = "GPL"
__version__     = "$Revision$"
__date__        = "$Date$"
__id__          = "$Id$"

import time
import os
import logging
import StringIO
import collections
import asyncore
import socket
import Queue
from threading import Thread, Lock, Event

import BackLogMessage
from ublox import UBloxMessage
from AbstractPlugin import AbstractPluginClass

DEFAULT_BACKLOG = True

MAX_MESSAGE_LENGTH = 1024000
SEND_QUEUE_SIZE = 50

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
        
        dev_dispatch = self.getOptionValue('dev_dispatch')
        self._ubloxDispatcher = None
        if dev_dispatch:
            self.info('dispatch data on port %s' % (dev_dispatch,))
            self._ubloxDispatcher = UbloxDispatcher(self, ('localhost', int(dev_dispatch)))
            self._asyncoreThread =  Thread(target=asyncore.loop,kwargs = {'timeout':1} )
            self._asyncoreThread.start()
        
        dev_relay = self.getOptionValue('dev_relay')
        self._ubloxCommRelay = None
        if dev_relay:
            self.info('forwarding data from %s to primary device' % (dev_relay,))
            dev_relay_baudrate = self.getOptionValue('dev_relay_baudrate')
            self._ubloxCommRelay = UbloxComm(self, dev_relay, dev_relay_baudrate, False)
        
        self._plugstop = False
        
        
    def run(self):
        self.name = 'UbloxPlugin-Thread'
        self.info('started')
        
        while not self._plugstop:
            msg = UBloxMessage()
            while not self._plugstop:
                try:
                    n = msg.needed_bytes()
                    b = None
                    try:
                        b = self._ubloxCommPrimary.read(n)
                    except socket.error, e:
                        break
                    if not b:
                        break
                    if self._ubloxDispatcher:
                        self._ubloxDispatcher.broadcast(b)
                    msg.add(b)
                    if msg.valid():
                        self.info(msg)
                        break
                except Exception, e:
                    self.exception(e)
            
            if self._ubloxCommRelay:
                while not self._plugstop:
                    try:
                        try:
                            b = self._ubloxCommRelay.read(4096)
                        except socket.error, e:
                            break
                        if not b:
                            break
                        self._ubloxCommPrimary.write(b)
                    except Exception, e:
                        self.exception(e)
            
            time.sleep(0.001)
        
    
    def stop(self):
        self._plugstop = True
        if self._ubloxDispatcher:
            self._ubloxDispatcher.close()
            self._asyncoreThread.join()
        if self._ubloxCommRelay:
            self._ubloxCommRelay.stop()
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
    _use_sendrecv
    _read_only
    '''
    
    def __init__(self, parent, serial_device, baudrate=None, blocking=True):
        Thread.__init__(self, name='%s-Thread' % (self.__class__.__name__,))
        self._logger = logging.getLogger(self.__class__.__name__)
        self._sendqueue = Queue.Queue(SEND_QUEUE_SIZE)
        self._ubloxPlugin = parent
        self._ubloxCommStop = False
        
        self._ubloxCommLock = Lock()
        self._use_sendrecv = False
        self._read_only = False
        if blocking:
            timeout = 60
        else:
            timeout = 0

        if serial_device.startswith("tcp:"):
            a = serial_device.split(':')
            destination_addr = (a[1], int(a[2]))
            self._dev = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._dev.connect(destination_addr)
            if blocking:
                self._dev.setblocking(1)
            else:
                self._dev.setblocking(0)
            self._dev.setsockopt(socket.SOL_TCP, socket.TCP_NODELAY, 1)            
            self._use_sendrecv = True
        elif os.path.isfile(serial_device):
            self._read_only = True
            self._dev = open(serial_device, mode='rb')
        else:
            import serial
            
            if baudrate is None:
                raise TypeError('no baudrate specified')
            else:
                baudrate = int(baudrate)
                self._logger.info('using baudrate %s' % (baudrate,))
                
            self._dev = serial.Serial(serial_device, baudrate=baudrate,
                                     dsrdtr=False, rtscts=False, xonxoff=False, timeout=timeout)


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
                len = self._write(ubloxCommMsg)
                if len != len(str(ubloxCommMsg)):
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
        ret = ''
        self._ubloxCommLock.acquire()
        try:
            if self._use_sendrecv:
                ret = self._dev.recv(n)
            else:
                ret = self._dev.read(n)
        except Exception, e:
            self._ubloxCommLock.release()
            raise e
        self._ubloxCommLock.release()
        return ret

    def _write(self, buf):
        '''write some bytes'''
        if not self._read_only:
            if self._use_sendrecv:
                return self._dev.send(buf)
            return self._dev.write(buf)


    def stop(self):
        self._ubloxCommStop = True
        try:
            self._sendqueue.put_nowait('end')
        except Queue.Full:
            pass
        except Exception, e:
            self._logger.exception(e)
        self._logger.info('stopped')


class UbloxDispatcher(asyncore.dispatcher):

    def __init__(self, parent, address=('localhost', 0)):
        self._logger = logging.getLogger(self.__class__.__name__)
        self._ubloxPlugin = parent
        asyncore.dispatcher.__init__(self)
        self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
        self.bind(address)
        self.listen(1)
        self.remote_clients = []

    def handle_accept(self):
        socket, addr = self.accept() # For the remote client.
        self._logger.info('Accepted client at %s', addr)
        self.remote_clients.append(RemoteClient(self, socket, addr))

    def handle_read(self):
        self._logger.info('Received message: %s', self.read())

    def broadcast(self, message):
#         self._logger.info('Broadcasting message: %s', message)
        for remote_client in self.remote_clients:
            remote_client.say(message)


class RemoteClient(asyncore.dispatcher):

    """Wraps a remote client socket."""

    def __init__(self, host, socket, address):
        asyncore.dispatcher.__init__(self, socket)
        self.host = host
        self.outbox = collections.deque()

    def say(self, message):
        self.outbox.append(message)

    def handle_read(self):
        client_message = self.recv(MAX_MESSAGE_LENGTH)
        self.host.broadcast(client_message)

    def handle_write(self):
        if not self.outbox:
            return
        message = self.outbox.popleft()
        if len(message) > MAX_MESSAGE_LENGTH:
            raise ValueError('Message too long')
        self.send(message)