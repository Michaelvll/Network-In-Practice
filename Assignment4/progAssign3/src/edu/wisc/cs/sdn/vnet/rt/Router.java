package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
    /**
     * Routing table for the router
     */
    private RouteTable routeTable;

    /**
     * ARP cache for the router
     */
    private AtomicReference<ArpCache> arpCache;

    private class PackIface {
        BasePacket packet;
        Iface inIface;
        Iface outIface;

        PackIface(BasePacket packet, Iface inIface, Iface outIface) {
            this.packet = packet;
            this.inIface = inIface;
            this.outIface = outIface;
        }
    }

    private AtomicReference<Map<Integer, Queue<PackIface>>> packetQueues;

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile) {
        super(host, logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new AtomicReference<>(new ArpCache());
        this.packetQueues = new AtomicReference<>(new HashMap<>());
    }

    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable() {
        return this.routeTable;
    }

    /**
     * Load a new routing table from a file.
     *
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile) {
        if (!routeTable.load(routeTableFile, this)) {
            System.err.println("Error setting up routing table from file "
                    + routeTableFile);
            System.exit(1);
        }

        System.out.println("Loaded static route table");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
    }

    /**
     * Load a new ARP cache from a file.
     *
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile) {
        if (!arpCache.get().load(arpCacheFile)) {
            System.err.println("Error setting up ARP cache from file "
                    + arpCacheFile);
            System.exit(1);
        }

        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
//		System.out.println("*** -> Received packet: " +
//                etherPacket.toString().replace("\n", "\n\t"));

        /********************************************************************/
        /* TODO: Handle packets                                             */
        // Check packet type
        switch (etherPacket.getEtherType()) {
            case Ethernet.TYPE_IPv4:
                handleIPPacket(etherPacket, inIface);
                break;
            case Ethernet.TYPE_ARP:
                handleARPPacket(etherPacket, inIface);
                break;
            default:
                System.out.println("Unknown packet (not IPv4 or ARP) dropped");
                break;
        }
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }

        /********************************************************************/
    }

    private void handleARPPacket(Ethernet etherPacket, Iface inIface) {
        ARP arpPacket = (ARP) etherPacket.getPayload();

        if (arpPacket.getOpCode() != ARP.OP_REQUEST) {
            if (arpPacket.getOpCode() == ARP.OP_REPLY) {
                int senderIP = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
                MACAddress senderMAC = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
                arpCache.get().insert(senderMAC, senderIP);

                Queue<PackIface> queue = packetQueues.get().get(senderIP);
                packetQueues.get().remove(senderIP);
                System.out.println("Get ARP reply for IP: " + IPv4.fromIPv4Address(senderIP) + "! Send " +
                        queue.size() + " packets in the queue");
                for (PackIface packIface : queue) {
                    Ethernet ethernet = (Ethernet) packIface.packet;
                    ethernet.setDestinationMACAddress(senderMAC.toBytes());
                    sendPacket(ethernet, inIface);
                    System.out.println("Send etherPacket in queue: " + ethernet.toString().replace("\n", "\n\t"));
                }
            }
            return;
        }

        int targetIP = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();
        int srcIP = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
        if (targetIP != inIface.getIpAddress()) {
            System.out.println("ARP target IP not match interface IP! Drop packet");
            return;
        }

        sendARP(etherPacket, inIface, srcIP);
    }


    private void handleIPPacket(Ethernet etherPacket, Iface inIface) {
        // Get IPv4 header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        // Check checksum
        short check = getChecksum(ipPacket);
        if ((check & 0xffff) != 0x0000) {
            System.out.println("Checksum is incorrect! Not handle. \nSum: " + String.valueOf((check & 0xffff)));
            return;
        }
        // Check TTL
        byte ttl = ipPacket.getTtl();
        --ttl;
        if (ttl == 0) {
            System.out.println("TTL is zero! Send back ICMP (Type 11, Code 0)");
            sendICMP(inIface, ipPacket, 11, 0);
            return;
        }

        // Check destination IP
        int dstIP = ipPacket.getDestinationAddress();
        for (Map.Entry<String, Iface> entry : super.interfaces.entrySet()) {
            Iface iface = entry.getValue();
            int ifaceIP = iface.getIpAddress();
            if (dstIP == ifaceIP) {
                switch (ipPacket.getProtocol()) {
                    case IPv4.PROTOCOL_TCP: case IPv4.PROTOCOL_UDP:
                        System.out.println(IPv4.protocolClassMap.get(ipPacket.getProtocol())
                                + ", match interface IP! Send back ICMP (Type 3, Code 3)");
                        sendICMP(inIface, ipPacket, 3, 3);
                        return;
                    case IPv4.PROTOCOL_ICMP:
                        if (((ICMP) ipPacket.getPayload()).getIcmpType() != 8) {
                            System.out.println("ICMP non-echo, match interface IP! Drop packet");
                            return;
                        }
                        System.out.println("ICMP echo, match interface IP! " +
                                "Send back ICMP echo reply (Type 3, Code 3)");
                        sendICMP(inIface, ipPacket, 0, 0, true);
                }
                return;
            }
        }
        // Look up route entry
        RouteEntry routeEntry = routeTable.lookup(dstIP);
        if (routeEntry == null) {
            System.out.println("No matched route entry for dstIP: " + IPv4.fromIPv4Address(dstIP)
                    + "! Send back ICMP (Type 3, Code 0)");
            sendICMP(inIface, ipPacket, 3, 0);

            return;
        }
        // Update IPv4 packet
        ipPacket.setTtl(ttl);
        ipPacket.resetChecksum();
        short newChecksum = getChecksum(ipPacket);
        ipPacket.setChecksum(newChecksum);

        // Update ethernet packet
        Iface outIface = routeEntry.getInterface();
        MACAddress sourceMAC = outIface.getMacAddress();
        etherPacket.setSourceMACAddress(sourceMAC.toBytes());

        // Look up arp entry
        arpAndSendEther(etherPacket, inIface, dstIP, routeEntry, outIface);
    }

    private void arpAndSendEther(Ethernet etherPacket, Iface inIface, int dstIP, RouteEntry routeEntry, Iface outIface) {
        int nextHopIP = routeEntry.getGatewayAddress();
        if (nextHopIP == 0) nextHopIP = dstIP;
        ArpEntry arpEntry = arpCache.get().lookup(nextHopIP);
        if (arpEntry == null) {
            System.out.println("No matched ARP entry for the next hop IP: " + IPv4.fromIPv4Address(nextHopIP) + "!");
            Thread waitARPReply = null;
            if (!packetQueues.get().containsKey(nextHopIP)) {
                packetQueues.get().put(nextHopIP, new LinkedList<>());
                WaitARP waitARP = new WaitARP(etherPacket, outIface, nextHopIP, packetQueues);
                waitARPReply = new Thread(waitARP);
            }
            packetQueues.get().get(nextHopIP).add(new PackIface(etherPacket, inIface, outIface));
            if (waitARPReply != null) waitARPReply.start();
            return;
        }
        MACAddress nextHopMAC = arpEntry.getMac();
        // Update the destination MAC address
        etherPacket.setDestinationMACAddress(nextHopMAC.toBytes());
        // Send ethernet packet
        sendPacket(etherPacket, outIface);
//		System.out.println("Send etherPacket: " + etherPacket.toString().replace("\n", "\n\t"));

    }

    private class WaitARP implements Runnable {
        Ethernet etherPacket;
        Iface outIface;
        int targetIP;
        AtomicReference<Map<Integer, Queue<PackIface>>> packetQueues;

        WaitARP(Ethernet etherPacket, Iface outIface, int targetIP, AtomicReference<Map<Integer, Queue<PackIface>>> packetQueues) {
            this.etherPacket = etherPacket;
            this.outIface = outIface;
            this.targetIP = targetIP;
            this.packetQueues = packetQueues;
        }

        @Override
        public void run() {
            for (int i = 0; i < 3; ++i) {
                System.out.println("Send ARPPacket " + String.valueOf(i) + "th: ");
                sendARP(etherPacket, outIface, targetIP, true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                ArpEntry arpEntry = arpCache.get().lookup(targetIP);
                if (arpEntry != null) return;
            }
            Queue<PackIface> queue = packetQueues.get().get(targetIP);
            packetQueues.get().remove(targetIP);
            if (queue == null) return;
            System.out.println("Do not get ARP reply for IP: " + IPv4.fromIPv4Address(targetIP) + ", after trying 3 times! Drop " +
                    queue.size() + " packets in the queue");
            for (PackIface packIface : queue) {
                Iface inIface = packIface.inIface;
                Ethernet ether = (Ethernet) packIface.packet;
                // Send destination host unreachable message ICMP
                sendICMP(inIface, (IPv4) ether.getPayload(), 3, 1);
            }
        }
    }

    private void sendARP(Ethernet etherPacket, Iface outIface, int targetIP) {
        sendARP(etherPacket, outIface, targetIP, false);
    }

    private void sendARP(Ethernet etherPacket, Iface outIface, int targetIP, boolean request) {
        Ethernet ether = new Ethernet();
        ARP arp = new ARP();
        ether.setPayload(arp);

        // Set ether header
        ether.setEtherType(Ethernet.TYPE_ARP);
        ether.setSourceMACAddress(outIface.getMacAddress().toBytes());
        ether.setDestinationMACAddress((request ? MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toBytes() :
                etherPacket.getSourceMACAddress()));

        // Set ARP header
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        arp.setProtocolType(ARP.PROTO_TYPE_IP);
        arp.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
        arp.setProtocolAddressLength((byte) 4);
        arp.setOpCode(request ? ARP.OP_REQUEST : ARP.OP_REPLY);
        arp.setSenderHardwareAddress(outIface.getMacAddress().toBytes());
        arp.setSenderProtocolAddress(outIface.getIpAddress());
        arp.setTargetHardwareAddress(request ? MACAddress.valueOf(0).toBytes() : etherPacket.getSourceMACAddress());
        arp.setTargetProtocolAddress(targetIP);

//        ether.serialize();
        sendPacket(ether, outIface);
        System.out.println("Send ARP packet: " + (request ? "request" : "reply") + ether.toString().replace("\n", "\n\t"));
    }

    private void sendICMP(Iface inIface, IPv4 ipPacket, int type, int code) {
        sendICMP(inIface, ipPacket, type, code, false);
    }

    private void sendICMP(Iface inIface, IPv4 ipPacket, int type, int code, boolean echo) {
        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();
        ether.setPayload(ip);
        ip.setPayload(icmp);
        icmp.setPayload(data);

        int dstIP = ipPacket.getSourceAddress();
        // Set header of ether packet
        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
//			System.out.println("SourceIP: " + IPv4.fromIPv4Address(dstIP));
        RouteEntry dstRouteEntry = routeTable.lookup(dstIP);
        assert dstRouteEntry != null;
        // Set header of IPv4
        Iface outIface = dstRouteEntry.getInterface();
        ip.setTtl((byte) 64);
        ip.setProtocol(IPv4.PROTOCOL_ICMP);
        ip.setSourceAddress(echo ? ipPacket.getDestinationAddress() : inIface.getIpAddress());
        ip.setDestinationAddress(dstIP);
        // Set header of ICMP
        icmp.setIcmpType((byte) type);
        icmp.setIcmpCode((byte) code);
        // Set Data payload
        if (!echo) {
            int headerLength = ipPacket.getHeaderLength() * 4;
            int packetLength = 4 + headerLength + 8;
            byte[] dataBytes = new byte[packetLength];
            ByteBuffer bb = ByteBuffer.wrap(dataBytes);
            bb.putInt(0);
            byte[] ipBytes = ipPacket.serialize();
            bb.put(Arrays.copyOfRange(ipBytes, 0, headerLength));
            bb.put(Arrays.copyOfRange(ipBytes, headerLength, headerLength + 8));
            bb.rewind();
            data.setData(dataBytes);
//		    System.out.println(Arrays.toString(dataBytes));
//		    System.out.println(Arrays.toString(ipBytes));
        } else data.setData(ipPacket.getPayload().getPayload().serialize());

        arpAndSendEther(ether, inIface, dstIP, dstRouteEntry, outIface);
//		System.out.println("Send etherPacket (ICMP): " + ether.toString().replace("\n", "\n\t"));
    }


//    private MACAddress getNextHopMAC(RouteEntry routeEntry, int dstIP) {
//        int nextHopIP = routeEntry.getGatewayAddress();
//        if (nextHopIP == 0) nextHopIP = dstIP;
//        ArpEntry arpEntry = arpCache.get().lookup(nextHopIP);
//        MACAddress dstMAC = null;
//        if (arpEntry == null)
//            System.out.println("No matched ARP entry for the next hop IP: " + IPv4.fromIPv4Address(nextHopIP) + "!");
//        else dstMAC = arpEntry.getMac();
//        return dstMAC;
//    }

    private short getChecksum(IPv4 header) {
        int headerLength = header.getHeaderLength();
        byte[] data = new byte[headerLength * 4];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.put((byte) (((header.getVersion() & 0xf) << 4) | (header.getHeaderLength() & 0xf)));
        bb.put(header.getDiffServ());
        bb.putShort(header.getTotalLength());
        bb.putShort(header.getIdentification());
        bb.putShort((short) (((header.getFlags() & 0x7) << 13) | (header.getFragmentOffset() & 0x1fff)));
        bb.put(header.getTtl());
        bb.put(header.getProtocol());
        bb.putShort(header.getChecksum());
        bb.putInt(header.getSourceAddress());
        bb.putInt(header.getDestinationAddress());
        if (header.getOptions() != null)
            bb.put(header.getOptions());

        bb.rewind();
        int accumulation = 0;
        for (int i = 0; i < headerLength * 2; ++i) {
            accumulation += 0xffff & bb.getShort();
        }
        accumulation = ((accumulation >> 16) & 0xffff)
                + (accumulation & 0xffff);
        return (short) (~accumulation & 0xffff);
    }
}
