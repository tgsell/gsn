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
import threading

import BackLogMessage
from ublox import UBloxMessage
from AbstractPlugin import AbstractPluginClass

DEFAULT_BACKLOG = True

MAX_MESSAGE_LENGTH = 1024000

class UbloxPluginClass(AbstractPluginClass):
    '''
    This plugin forwards incoming packets from a ublox chip to GSN.
    
    data/instance attributes:
    _plugstop
    '''
    
    def __init__(self, parent, config):
        AbstractPluginClass.__init__(self, parent, config, DEFAULT_BACKLOG)
            
        serial_device = self.getOptionValue('device_name')
        if serial_device is None:
            raise TypeError('no device_name specified')
        else:
            self.info('using device %s' % (serial_device,))
        
        self.use_sendrecv = False
        self.read_only = False
        timeout = 60

        if serial_device.startswith("tcp:"):
            import socket
            a = serial_device.split(':')
            destination_addr = (a[1], int(a[2]))
            self.dev = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.dev.connect(destination_addr)
            self.dev.setblocking(1)
            self.dev.setsockopt(socket.SOL_TCP, socket.TCP_NODELAY, 1)            
            self.use_sendrecv = True
        elif os.path.isfile(serial_device):
            self.read_only = True
            self.dev = open(serial_device, mode='rb')
        else:
            import serial
            
            value = self.getOptionValue('baudrate')
            if value is None:
                raise TypeError('no baudrate specified')
            else:
                baudrate = int(value)
                self.info('using baudrate %s' % (baudrate,))
                
            self.dev = serial.Serial(serial_device, baudrate=baudrate,
                                     dsrdtr=False, rtscts=False, xonxoff=False, timeout=timeout)
        
        address=('localhost', 55555)
        self._ubloxDispatcher = UbloxDispatcher(self, address)
        self._asyncoreThread =  threading.Thread(target=asyncore.loop,kwargs = {'timeout':1} )
        self._asyncoreThread.start()
        
        self._plugstop = False
        
        
    def run(self):
        self.name = 'UbloxPlugin-Thread'
        self.info('started')
        
        buff = StringIO.StringIO(2048)
        ignore_eof = True
        
        while not self._plugstop:
            msg = UBloxMessage()
            while not self._plugstop:
                n = msg.needed_bytes()
                b = self._read(n)
                if not b:
                    if ignore_eof:
                        time.sleep(0.01)
                        continue
                    break
                msg.add(b)
                if msg.valid():
#                     self.info(msg)
                    self._ubloxDispatcher.broadcast(str(msg))
                    break
                
#             while True:
#                 data = self._read(16)
#                 buff.write(data)
#                 if '\n' in data:
#                     break
    
#             self.info(buff.getvalue().splitlines()[0])
        
    
    def stop(self):
        self._plugstop = True
        self._ubloxDispatcher.close()
        self._asyncoreThread.join()
        self.info('stopped')
    
    
    def msgReceived(self, data):
        return
    
    
    def isBusy(self):
        return False
    
    
    def needsWLAN(self):
        return False

    def _write(self, buf):
        '''write some bytes'''
        if not self.read_only:
            if self.use_sendrecv:
                return self.dev.send(buf)
            return self.dev.write(buf)

    def _read(self, n):
        '''read some bytes'''
        if self.use_sendrecv:
            import socket
            try:
                return self.dev.recv(n)
            except socket.error as e:
                return ''
        return self.dev.read(n)


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