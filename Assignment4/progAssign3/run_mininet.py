#!/usr/bin/python

"""
Start up a virtual network topology for CS640
"""

from mininet.net import Mininet
from mininet.node import Controller, RemoteController
from mininet.log import setLogLevel, info
from mininet.cli import CLI
from mininet.topo import Topo
from mininet.util import quietRun, ipParse, ipStr

import sys
import string

IPCONFIG_FILE = "./ip_config"
ARPCACHE_FILE = "./arp_cache"

class VNetHost:
    def __init__(self, name, addr, gw):
        self.name = name
        self.ip = addr.split("/")[0]
        self.prefix = int(addr.split("/")[1])
        self.mask = prefixToMask(self.prefix)
        self.gw = gw
        self.host = None

    def startsshd(self):
        "Start sshd on host"
        stopsshd()
        info( '*** Starting sshd\n' )
        intf = self.host.defaultIntf()
        banner = '/tmp/%s.banner' % self.name
        self.host.cmd( 'echo "Welcome to %s at %s" >  %s' % ( self.name, self.ip, self.banner ) )
        self.host.cmd( '/usr/sbin/sshd -o "Banner %s"' % banner, '-o "UseDNS no"' )
        info( '***', self.name, 'is running sshd on', intf, 'at', self.ip, '\n' )

    def starthttp(self):
        "Start simple Python web server on hosts"
        info( '*** Starting SimpleHTTPServer on host', self.host, '\n' )
        self.host.cmd('cd ./http_server/; nohup python2.7 ./webserver.py &')
#self.host.cmd( 'cd ./http_%s/; nohup python2.7 ./webserver.py &' % (self.host.name) )

    def configureRoute(self, host):
        info( '*** Configuring routing for %s\n' % host)
        self.host = host
        for intf in host.intfList():
#            info('\t%s IP: %s/%d\n' % (intf, self.ip, self.prefix))
            intf.setIP('%s/%d' % (self.ip, self.prefix))
        if (self.gw != None):
            intf = host.defaultIntf()
#           info('\tDefault route: gw %s dev %s\n' % (self.gw, intf))
            host.cmd('route add default gw %s dev %s' % (self.gw, intf))

    def configureArp(self, arpcache):
        info( '*** Configuring ARP for %s\n' % self.host)
        for ip in arpcache.keys():
            self.host.cmd('arp -s %s %s' % (ip, arpcache[ip]))

class VNetSwitch:
    def __init__(self, name):
        self.name = name

class VNetRouter:
    def __init__(self, name, addrs):
        self.name = name
        self.ips = []
        self.prefixes = []
        self.masks = []
        self.subnets = []
        self.ifaces = 0
        self.switch = None
        for addr in addrs:
            ip = addr.split("/")[0]
            prefix = int(addr.split("/")[1])
            mask = prefixToMask(prefix)
            subnet = ipStr(ipParse(ip) & ipParse(mask))
            self.ips.append(ip)
            self.prefixes.append(prefix)
            self.masks.append(mask)
            self.subnets.append(subnet)

class VNetLink:
    def __init__(self, nameA, nameB):
        self.nameA = nameA
        self.nameB = nameB

class VNetTopo(Topo):
    "Virtual Network Topology"

    def __init__( self, topofile, *args, **kwargs ):
        Topo.__init__( self, *args, **kwargs )
        self.vhosts = []
        self.vswitches = []
        self.vrouters = {}
        self.vlinks = []
        self.graph = Graph()
        self.loadtopo(topofile)
        self.write_ipfile()
        for router in self.vrouters.values():
            self.write_rtablefile(router)

    def loadtopo(self, topofile):
        info( '*** Loading topology file %s\n' % topofile)
        try:
            with open(topofile, 'r') as f:
                for line in f:
                    parts = line.split()
                    if (parts[0] == "host"):
                        if (len(parts) != 4 or parts[2].find("/") < 0):
                            sys.exit("Error in topology configuration line: %s" % line)
                        name = parts[1]
                        addr = parts[2]
                        gw = parts[3]
                        if (gw == "-"):
                            gw = None
                        vhost = VNetHost(name, addr, gw)
                        self.vhosts.append(vhost)
                        self.addHost(vhost.name)
                        self.graph.add_node(vhost.name)
                    elif (parts[0] == "switch"):
                        name = parts[1]
                        vswitch = VNetSwitch(name)
                        self.vswitches.append(vswitch)
                        self.addSwitch(vswitch.name)
                        self.graph.add_node(vswitch.name)
                    elif (parts[0] == "router"):
                        if (len(parts) < 3):
                            sys.exit("Error in topology configuration line: %s" % line)
                        name = parts[1]
                        vrouter = VNetRouter(name, parts[2:])
                        self.vrouters[vrouter.name] = vrouter
                        self.addSwitch(vrouter.name)
                        self.graph.add_node(vrouter.name)
                    elif (parts[0] == "link"):
                        vlink = VNetLink(parts[1],parts[2])
                        self.vlinks.append(vlink)
                        self.addLink(vlink.nameA, vlink.nameB)
                        nameA = vlink.nameA
                        if (nameA in self.vrouters):
                            vrouterA = self.vrouters[nameA]
                            vrouterA.ifaces += 1
                            nameA += ".%d" % (vrouterA.ifaces)
                            self.graph.add_node(nameA)
                        nameB = vlink.nameB
                        if (nameB in self.vrouters):
                            vrouterB = self.vrouters[nameB]
                            vrouterB.ifaces += 1
                            nameB += ".%d" % (vrouterB.ifaces)
                            self.graph.add_node(nameB)
                        self.graph.add_edge(nameA, nameB, 1)
                    else:
                        sys.exit("Error in topology configuration line: %s" 
                                % line)
                f.close()

                for router in self.vrouters.values():
                    for i in range(1, router.ifaces+1):
                        ifacename = "%s.%d" % (router.name, i)
                        self.graph.add_edge(ifacename, router.name, 1)
        except EnvironmentError:
            sys.exit("Couldn't load topology file, check whether %s exists" % topofile)

    def write_ipfile(self):
        info( '*** Writing IP file %s\n' % IPCONFIG_FILE)
        try:
            with open(IPCONFIG_FILE, 'w') as f:
                for host in self.vhosts:
                    iface = '%s-eth0' % (host.name)
                    f.write('%s %s %s\n' % (iface, host.ip, host.mask))
                for router in self.vrouters.values():
                    count = 1
                    for ip in router.ips:
                        iface = '%s-eth%d' % (router.name, count)
                        f.write('%s %s %s\n' % (iface, ip, '255.255.255.0'))
                        count = count + 1
                f.close()
        except EnvironmentError:
            sys.exit("Couldn't write IP file" % IPCONFIG_FILE)

    def write_arpcachefile(self):
        info( '*** Writing ARP cache file %s\n' % ARPCACHE_FILE)
        arpcache = {}
        try:
            with open(ARPCACHE_FILE, 'w') as f:
                for vhost in self.vhosts:
                    iface = vhost.host.defaultIntf()
                    f.write('%s %s\n' % (iface.ip, iface.mac))
                    arpcache[iface.ip] = iface.mac
                for vrouter in self.vrouters.values():
                    ifaces = vrouter.switch.intfs.values()
                    for i in range(1,len(ifaces)):
                        mac = ifaces[i].mac
                        ip = vrouter.ips[i-1]
                        f.write('%s %s\n' % (ip, mac))
                        arpcache[ip] = mac
                f.close()
        except EnvironmentError:
            sys.exit("Couldn't write ARP cache file" % ARPCACHE_FILE)
        return arpcache

    def write_rtablefile(self, router):
        rtablefile = "rtable.%s" % router.name
        info( '*** Writing rtable file %s\n' % rtablefile)
        visited, path = dijkstra(self.graph, router.name)
        try:
            with open(rtablefile, 'w') as f:
                for i in range(0, len(router.ips)):
                    iface = '%s-eth%d' % (router.name, i+1)
                    ip = router.ips[i]
                    mask = router.masks[i]
                    subnet = ipStr(ipParse(ip) & ipParse(mask))
                    f.write('%s 0.0.0.0 %s eth%d\n' % (subnet, mask, i+1))
                for rt in self.vrouters.values():
                    if (rt.name == router.name):
                        continue
                    local = None
                    remote = None
                    reached = path[rt.name]
                    while (reached != router.name):
                        if (reached.split('.')[0] in self.vrouters):
                            remote = local
                            local = reached
                        reached = path[reached]

                    localrt = self.vrouters[local.split('.')[0]]
                    localport = int(local.split('.')[1])
                    remotert = self.vrouters[remote.split('.')[0]]
                    remoteport = int(remote.split('.')[1])
                    info("Reach %s from %s via %s.%d and %s.%d\n" % (
                            rt.name, router.name, localrt.name, 
                            localport, remotert.name, remoteport))
                    gw = remotert.ips[remoteport-1]
                    for i in range(0, len(rt.ips)):
                        iface = '%s-eth%d' % (rt.name, i+1)
                        ip = rt.ips[i]
                        mask = rt.masks[i]
                        subnet = ipStr(ipParse(ip) & ipParse(mask))
                        if (subnet in router.subnets):
                            continue
                        f.write('%s %s %s eth%d\n' % (subnet, gw, mask, 
                                    localport))
                        router.subnets.append(subnet)


                f.close()
        except EnvironmentError:
            sys.exit("Couldn't write IP file" % ipfile)
 
def stopallsshd():
    "Stop *all* sshd processes with a custom banner"
    info( '*** Shutting down stale sshd/Banner processes ',
        quietRun( "pkill -9 -f Banner" ), '\n' )

def stopallhttp():
    "Stop simple Python web servers"
    info( '*** Shutting down stale SimpleHTTPServers', 
        quietRun( "pkill -9 -f SimpleHTTPServer" ), '\n' )    
    info( '*** Shutting down stale webservers', 
        quietRun( "pkill -7 -f webserver.py" ), '\n' )    
  
def prefixToMask(prefix):
    shift = 32 - prefix
    return ipStr((0xffffffff >> shift) << shift)

#############################################################################
# Implementation of Dijkstra's Algorithm from Lynn Root
# https://gist.github.com/econchick/4666413
class Graph:
  def __init__(self):
    self.nodes = set()
    self.edges = {}
    self.distances = {}

  def add_node(self, value):
    self.nodes.add(value)
    self.edges[value] = []

  def add_edge(self, from_node, to_node, distance):
    self.edges[from_node].append(to_node)
    self.edges[to_node].append(from_node)
    self.distances[(from_node, to_node)] = distance
    self.distances[(to_node, from_node)] = distance


def dijkstra(graph, initial):
  visited = {initial: 0}
  path = {}

  nodes = set(graph.nodes)

  while nodes: 
    min_node = None
    for node in nodes:
      if node in visited:
        if min_node is None:
          min_node = node
        elif visited[node] < visited[min_node]:
          min_node = node

    if min_node is None:
      break

    nodes.remove(min_node)
    current_weight = visited[min_node]

    for edge in graph.edges[min_node]:
      weight = current_weight + graph.distances[(min_node, edge)]
      if edge not in visited or weight < visited[edge]:
        visited[edge] = weight
        path[edge] = min_node

  return visited, path
#############################################################################

if __name__ == '__main__':
    setLogLevel( 'info' )
    if (len(sys.argv) < 2):
        print sys.argv
        sys.exit("%s <topofile> [-a]" % (sys.argv[0]))
    topofile = sys.argv[1]
    staticarp = False
    if (len(sys.argv) >= 3):
        if (sys.argv[2] == "-a"):
            staticarp = True

    topo = VNetTopo(topofile)

    net = Mininet( topo=topo, controller=RemoteController, autoSetMacs=True)
    net.start()

    for vhost in topo.vhosts:
        node = net.get(vhost.name)
        vhost.configureRoute(node)
#        vhost.starthttp()
    for vrouter in topo.vrouters.values():
        node = net.get(vrouter.name)
        vrouter.switch = node
    arpcache = topo.write_arpcachefile()
    if (staticarp):
        for vhost in topo.vhosts:
            vhost.configureArp(arpcache)

    CLI( net )
    stopallhttp()
    net.stop()
