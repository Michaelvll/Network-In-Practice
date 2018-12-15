"""Defines the VNS protocol and some associated helper functions."""

import re
from socket import inet_aton, inet_ntoa
import struct

from ltprotocol.ltprotocol import LTMessage, LTProtocol, LTTwistedServer

VNS_DEFAULT_PORT = 3250
VNS_MESSAGES = []
IDSIZE = 32

__clean_re = re.compile(r'\x00*')
def strip_null_chars(s):
    """Remove null characters from a string."""
    return __clean_re.sub('', s)

class VNSOpen(LTMessage):
    @staticmethod
    def get_type():
        return 1

    def __init__(self, vhost):
        LTMessage.__init__(self)
        self.vhost = str(vhost)

    def length(self):
        return VNSOpen.SIZE

    FORMAT = '> %us' % (IDSIZE)
    SIZE = struct.calcsize(FORMAT)

    def pack(self):
        return struct.pack(VNSOpen.FORMAT, self.vhost)

    @staticmethod
    def unpack(body):
        t = struct.unpack(VNSOpen.FORMAT, body)
        vhost = strip_null_chars(t[0])
        return VNSOpen(vhost)

    def __str__(self):
        return 'OPEN: host=%s' % (self.vhost)
VNS_MESSAGES.append(VNSOpen)

class VNSClose(LTMessage):
    @staticmethod
    def get_type():
        return 2

    @staticmethod
    def get_banners_and_close(msg):
        """Split msg up into the minimum number of VNSBanner messages and VNSClose it will fit in."""
        msgs = []
        n = len(msg)/255 + 1
        for i in range(n):
            if i+1 < n:
                msgs.append(VNSBanner(msg[i*255:(i+1)*255]))
            else:
                msgs.append(VNSClose(msg[i*255:(i+1)*255]))
        return msgs

    def __init__(self, msg):
        LTMessage.__init__(self)
        self.msg = str(msg)

    def length(self):
        return VNSClose.SIZE

    FORMAT = '> 256s'
    SIZE = struct.calcsize(FORMAT)

    def pack(self):
        return struct.pack(VNSClose.FORMAT, self.msg)

    @staticmethod
    def unpack(body):
        t = struct.unpack(VNSClose.FORMAT, body)
        return VNSClose(strip_null_chars(t[0]))

    def __str__(self):
        return 'CLOSE: %s' % self.msg
VNS_MESSAGES.append(VNSClose)

class VNSPacket(LTMessage):
    @staticmethod
    def get_type():
        return 4

    def __init__(self, intf_name, ethernet_frame):
        LTMessage.__init__(self)
        self.intf_name = str(intf_name)
        self.ethernet_frame = str(ethernet_frame)

    def length(self):
        return VNSPacket.HEADER_SIZE + len(self.ethernet_frame)

    HEADER_FORMAT = '> 16s'
    HEADER_SIZE = struct.calcsize(HEADER_FORMAT)

    def pack(self):
        return struct.pack(VNSPacket.HEADER_FORMAT, self.intf_name) + self.ethernet_frame

    @staticmethod
    def unpack(body):
        t = struct.unpack(VNSPacket.HEADER_FORMAT, body[:VNSPacket.HEADER_SIZE])
        intf_name = strip_null_chars(t[0])
        return VNSPacket(intf_name, body[VNSPacket.HEADER_SIZE:])

    def __str__(self):
        return 'PACKET: %uB on %s' % (len(self.ethernet_frame), self.intf_name)
VNS_MESSAGES.append(VNSPacket)

class VNSProtocolException(Exception):
    def __init__(self, msg):
        self.msg = msg

    def __str__(self):
        return self.msg

class VNSInterface:
    def __init__(self, name, mac, ip, mask):
        self.name = str(name)
        self.mac = str(mac)
        self.ip = str(ip)
        self.mask = str(mask)

        if len(mac) != 6:
            raise VNSProtocolException('MAC address must be 6B')

        if len(ip) != 4:
            raise VNSProtocolException('IP address must be 4B')

        if len(mask) != 4:
            raise VNSProtocolException('IP address mask must be 4B')

    HWINTERFACE = 1  # string
    HWETHER = 2     # string
    HWETHIP = 4     # uint32
    HWMASK = 8     # uint32

    FORMAT = '> I32s I32s I4s28s I4s28s'
    SIZE = struct.calcsize(FORMAT)

    def length(self):
        return SIZE

    def pack(self):
        return struct.pack(VNSInterface.FORMAT,
                           VNSInterface.HWINTERFACE, self.name,
                           VNSInterface.HWETHER, self.mac,
                           VNSInterface.HWETHIP, self.ip, '',
                           VNSInterface.HWMASK, self.mask, '')

    def __str__(self):
        fmt = '%s: mac=%s ip=%s mask=%s'
        return fmt % (self.name, self.mac, inet_ntoa(self.ip), inet_ntoa(self.mask))

class VNSSwitchInterface:
    def __init__(self, name):
        self.name = str(name)

    FORMAT = '> I32s'
    SIZE = struct.calcsize(FORMAT)

    def length(self):
        return SIZE

    def pack(self):
        return struct.pack(VNSSwitchInterface.FORMAT,
                           VNSInterface.HWINTERFACE, self.name)

    def __str__(self):
        return self.name


class VNSHardwareInfo(LTMessage):
    @staticmethod
    def get_type():
        return 16

    def __init__(self, interfaces):
        LTMessage.__init__(self)
        self.interfaces = interfaces

    def length(self):
        len = 0
        for iface in self.interfaces:
            len += iface.length()
        return len

    def pack(self):
        return ''.join([intf.pack() for intf in self.interfaces])

    def __str__(self):
        return 'Hardware Info: %s' % ' || '.join([str(intf) for intf in self.interfaces])
VNS_MESSAGES.append(VNSHardwareInfo)

VNS_PROTOCOL = LTProtocol(VNS_MESSAGES, 'I', 'I')

def create_vns_server(port, recv_callback, new_conn_callback, lost_conn_callback, verbose=True):
    """Starts a server which listens for VNS clients on the specified port.

    @param port  the port to listen on
    @param recv_callback  the function to call with received message content
                         (takes two arguments: transport, msg)
    @param new_conn_callback   called with one argument (a LTProtocol) when a connection is started
    @param lost_conn_callback  called with one argument (a LTProtocol) when a connection is lost
    @param verbose        whether to print messages when they are sent

    @return returns the new LTTwistedServer
    """
    server = LTTwistedServer(VNS_PROTOCOL, recv_callback, new_conn_callback, lost_conn_callback, verbose)
    server.listen(port)
    return server
