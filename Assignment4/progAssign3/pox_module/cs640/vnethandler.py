from pox.core import core
import pox.openflow.libopenflow_01 as of
from pox.lib.revent import *
from pox.lib.util import dpidToStr
from pox.lib.util import str_to_bool
from pox.lib.recoco import Timer
from pox.lib.packet import ethernet
import time

import threading
import asyncore
import collections
import logging
import socket

# Required for VNS
import sys
import os
from twisted.python import threadable
from threading import Thread

from twisted.internet import reactor
from VNSProtocol import VNS_DEFAULT_PORT, create_vns_server
from VNSProtocol import VNSOpen, VNSClose, VNSPacket 
from VNSProtocol import VNSInterface, VNSSwitchInterface, VNSHardwareInfo

log = core.getLogger()

def pack_mac(macaddr):
  octets = macaddr.split(':')
  ret = ''
  for byte in octets:
    ret += chr(int(byte, 16))
  return ret

def pack_ip(ipaddr):
  octets = ipaddr.split('.')
  ret = ''
  for byte in octets:
    ret += chr(int(byte))
  return ret

class VNetDevice:
  def __init__ (self, swid, ifaces):
    self.swid = swid
    self.conn = None
    self.intfname_to_port = {}
    self.port_to_intfname = {}

    self.interfaces = []
    for intf in ifaces.keys():
      ip, mask, mac, port = ifaces[intf]
      if (ip is None or mask is None or mac is None):
        self.interfaces.append(VNSSwitchInterface(intf))
      else:
        ip = pack_ip(ip)
        mask = pack_ip(mask)
        mac = pack_mac(mac)
        self.interfaces.append(VNSInterface(intf, mac, ip, mask))
      # Mapping between of-port and intf-name
      self.intfname_to_port[intf] = port
      self.port_to_intfname[port] = intf

  def handle_packet_msg(self, vns_msg):
    out_intf = vns_msg.intf_name
    pkt = vns_msg.ethernet_frame

    try:
      out_port = self.intfname_to_port[out_intf]
    except KeyError:
      log.debug('packet-out through wrong port number %s' % out_port)
      return
    log.debug("Packet out %s.%s: %r" % (self.swid, out_intf, ethernet(pkt)))
    log.debug('VNetServerHandler raise packet out event')
    core.VNetHandler.raiseEvent(VNetPacketOut(pkt, out_port, self.swid))

  def handle_VNetPacketIn(self, event):
    try:
      intfname = self.port_to_intfname[event.port]
    except KeyError:
      log.debug("Couldn't find interface for portnumber %s" % event.port)
      return
    log.debug("Packet in %s.%s: %s" % (self.swid, intfname,
        ethernet(event.pkt)))
    if (self.conn is None):
      log.debug("VNet device %s is not connected" % (self.swid))
      return
    self.conn.send(VNSPacket(intfname, event.pkt))

class VNetServerListener(EventMixin):
  ''' TCP Server to handle connection to VNet '''
  def __init__ (self, address=('127.0.0.1', 8888)):
    port = address[1]
    self.listenTo(core.VNetOFNetHandler)
    self.devsByConn = {}
    self.devsByName = {}
    self.server = create_vns_server(port, self.recv_msg,
        self.handle_new_client, self.handle_client_disconnect)
    log.info("VNet server listening on %s:%d" % (address[0],address[1]))
    return

  def _handle_VNetPacketIn(self, event):
    dev = self.devsByName[event.swid]
    if (dev is None):
      return
    dev.handle_VNetPacketIn(event)

  def recv_msg(self, conn, vns_msg):
    # demux sr-client messages and take approriate actions
    if vns_msg is None:
      log.debug("invalid message")
      self.handle_close_msg(conn)
      return

    log.debug('Received VNS msg: %s' % vns_msg)
    if vns_msg.get_type() == VNSOpen.get_type():
      self.handle_open_msg(conn, vns_msg)
    elif vns_msg.get_type() == VNSClose.get_type():
      self.handle_close_msg(conn)
    elif vns_msg.get_type() == VNSPacket.get_type():
      self.handle_packet_msg(conn, vns_msg)
    else:
      log.debug('Unexpected VNS message received: %s' % vns_msg)

  def handle_open_msg(self, conn, vns_msg):
    dev = self.devsByName[vns_msg.vhost]
    if (dev is None):
      log.debug('interfaces for %s not populated yet' % (vns_msg.vhost))  
      return
    self.devsByConn[conn] = dev
    dev.conn = conn
    conn.send(VNSHardwareInfo(dev.interfaces))
    return

  def handle_close_msg(self, conn):
#conn.send("Goodbyte!") # spelling mistake intended...
    conn.transport.loseConnection()
    return

  def handle_packet_msg(self, conn, vns_msg):
    dev = self.devsByConn[conn]
    dev.handle_packet_msg(vns_msg)

  def handle_new_client(self, conn):
    log.debug('Accepted client at %s' % conn.transport.getPeer().host)
    return

  def handle_client_disconnect(self, conn):
    log.info("Client disconnected")
    del self.devsByConn[conn]
    conn.transport.loseConnection()
    return


class VNetPacketOut(Event):
  '''Event to raise upon receicing a packet back from VNet device'''

  def __init__(self, packet, port, swid):
    Event.__init__(self)
    self.pkt = packet
    self.port = port
    self.swid = swid


class VNetHandler(EventMixin):
  _eventMixin_events = set([VNetPacketOut])

  def __init__(self):
    EventMixin.__init__(self)
    self.listenTo(core)
    self.listenTo(core.VNetOFNetHandler)
    # self.server_thread = threading.Thread(target=asyncore.loop)
    # use twisted as VNS also used Twisted.
    # its messages are already nicely defined in VNSProtocol.py
    self.server_thread = threading.Thread(target=lambda: reactor.run(installSignalHandlers=False))
    self.server_thread.daemon = True
    self.server_thread.start()
    self.server = VNetServerListener()

  def _handle_VNetDevInfo(self, event):
    log.info("VNetHandler catch VNetDevInfo(ifaces=%s,swid=%s,dpid=%d)", 
            event.ifaces, event.swid, event.dpid)
    self.server.devsByName[event.swid] = VNetDevice(event.swid, event.ifaces)

  def _handle_GoingDownEvent (self, event):
    log.debug("Shutting down VNetServer")

def launch():
  """
  Starts the VNet handler application.
  """
  core.registerNew(VNetHandler)
