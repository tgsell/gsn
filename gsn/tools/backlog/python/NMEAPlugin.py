# -*- coding: UTF-8 -*-
__author__      = "Tonio Gsell <tgsell@tik.ee.ethz.ch>"
__copyright__   = "Copyright 2010, ETH Zurich, Switzerland, Tonio Gsell"
__license__     = "GPL"
__version__     = "$Revision$"
__date__        = "$Date$"
__id__          = "$Id$"

import time
import os
import pynmea2 

import BackLogMessage
from AbstractPlugin import AbstractPluginClass

DEFAULT_BACKLOG = True

class NMEAPluginClass(AbstractPluginClass):
    '''
    This plugin forwards incoming NMEA packets to GSN.
    
    data/instance attributes:
    _plugstop
    '''
    
    def __init__(self, parent, config):
        AbstractPluginClass.__init__(self, parent, config, DEFAULT_BACKLOG)
        
        self._nmeaStreamReader = pynmea2.NMEAStreamReader()
            
        self.serial_device = self.getOptionValue('device_name')
        if self.serial_device is None:
            raise TypeError('no device_name specified')
        else:
            self.info('using device %s' % (self.serial_device,))
        
        self.baudrate = 115200
        self.use_sendrecv = False
        self.read_only = False
        timeout = 60

        if self.serial_device.startswith("tcp:"):
            import socket
            a = self.serial_device.split(':')
            destination_addr = (a[1], int(a[2]))
            self.dev = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.dev.connect(destination_addr)
            self.dev.setblocking(1)
            self.dev.setsockopt(socket.SOL_TCP, socket.TCP_NODELAY, 1)            
            self.use_sendrecv = True
        elif os.path.isfile(self.serial_device):
            self.read_only = True
            self.dev = open(self.serial_device, mode='rb')
        else:
            import serial
            self.dev = serial.Serial(self.serial_device, baudrate=self.baudrate,
                                     dsrdtr=False, rtscts=False, xonxoff=False, timeout=timeout)
        
        self._plugstop = False
        
        
    def run(self):
        self.name = 'NMEAPlugin-Thread'
        self.info('started')
        
        while not self._plugstop:
            data = self._read(16)
            for msg in self._nmeaStreamReader.next(data):
                self.info(msg)
        
    
    def stop(self):
        self._plugstop = True
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
    