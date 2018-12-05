# Copyright 2011 James McCauley
#
# This file is part of POX.
#
# POX is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# POX is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with POX.  If not, see <http://www.gnu.org/licenses/>.

from pox.core import core
import pox.openflow.libopenflow_01 as of
from pox.lib.revent import *
#from pox.lib.util import dpidToStr
#from pox.lib.util import str_to_bool
#from pox.lib.addresses import IPAddr, EthAddr


#import time
#import code
import os
#import struct
import sys

log = core.getLogger()
IPCONFIG_FILE = './ip_config'
IP_SETTING={}

class VNetDevInfo(Event):
  '''Event to raise when the info about an OF switch is ready'''

  def __init__(self, ifaces, swid, dpid):
    Event.__init__(self)
    self.ifaces = ifaces
    self.swid = swid
    self.dpid = dpid


class VNetOFDevHandler (EventMixin):
  def __init__ (self, connection):
    self.connection = connection
    self.dpid = connection.dpid % 1000
    log.debug("dpid=%s", self.dpid)
    swifaces = {}
    self.connection.send(of.ofp_switch_config(miss_send_len = 65535))
    for port in connection.features.ports:
        intf_name = port.name.split('-')
        if(len(intf_name) < 2):
          continue
        else:
          self.swid = intf_name[0]
          intf_name = intf_name[1]
        if port.name in IP_SETTING.keys():
          swifaces[intf_name] = (IP_SETTING[port.name][0], 
              IP_SETTING[port.name][1], port.hw_addr.toStr(), port.port_no)
        else:
          swifaces[intf_name] = (None, None, None, port.port_no)

    # We want to hear OF PacketIn messages, so we listen
    self.listenTo(connection)

    self.listenTo(core.VNetHandler)
    core.VNetOFNetHandler.raiseEvent(
        VNetDevInfo(swifaces, self.swid, self.dpid))

  def _handle_PacketIn (self, event):
    '''Handles packet in messages from the OF device'''
    pkt = event.parse()
    raw_packet = pkt.raw
    core.VNetOFNetHandler.raiseEvent(
        VNetPacketIn(raw_packet, event.port, self.swid))
    msg = of.ofp_packet_out()
    msg.buffer_id = event.ofp.buffer_id
    msg.in_port = event.port
    self.connection.send(msg)

  def _handle_VNetPacketOut(self, event):
    if (event.swid != self.swid):
        return
    msg = of.ofp_packet_out()
    new_packet = event.pkt
    msg.actions.append(of.ofp_action_output(port=event.port))
    msg.buffer_id = -1
    msg.in_port = of.OFPP_NONE
    msg.data = new_packet
    self.connection.send(msg)

class VNetPacketIn(Event):
  '''Event to raise upon receiving a packet_in from openflow'''

  def __init__(self, packet, port, swid):
    Event.__init__(self)
    self.pkt = packet
    self.port = port
    self.swid = swid

class VNetOFNetHandler (EventMixin):
  '''Waits for OF switches to connect and makes them simple routers'''
  _eventMixin_events = set([VNetPacketIn, VNetDevInfo])

  def __init__ (self):
    EventMixin.__init__(self)
    self.listenTo(core.openflow)

  def _handle_ConnectionUp (self, event):
    log.debug("Connection %s" % (event.connection,))
    VNetOFDevHandler(event.connection)

def get_ip_setting():
  if (not os.path.isfile(IPCONFIG_FILE)):
    return -1
  f = open(IPCONFIG_FILE, 'r')
  for line in f:
    if(len(line.split()) == 0):
      break
    name, ip, mask = line.split()
    IP_SETTING[name] = [ip, mask]

  return 0

def launch():
  '''Starts a virtual network topology'''    
  core.registerNew(VNetOFNetHandler)
  
  r = get_ip_setting()
  if r == -1:
    log.error("Failed to load VNet config file %s" % IPCONFIG_FILE)
    sys.exit(2)
  else:
    log.info('Successfully loaded VNet config file\n %s\n' % IP_SETTING)
