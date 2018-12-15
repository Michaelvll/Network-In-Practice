package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;

/**
  * @author Anubhavnidhi Abhashkumar and Aaron Gember-Jacobson
  */
public class RIPv2Entry 
{
    public static final short ADDRESS_FAMILY_IPv4 = 2;

    protected short addressFamily;
    protected short routeTag;
	protected int address;
	protected int subnetMask;
	protected int nextHopAddress;
	protected int metric;

    public RIPv2Entry()
    { }

    public RIPv2Entry(int address, int subnetMask, int metric)
    {
        this.addressFamily = ADDRESS_FAMILY_IPv4;
        this.address = address;
        this.subnetMask = subnetMask;
        this.metric = metric;
    }

	public String toString()
	{
        return String.format("RIPv2Entry : {addressFamily=%d, routeTag=%d, address=%s, subnetMask=%s, nextHopAddress=%s, metric=%d}", 
                this.addressFamily, this.routeTag, 
                IPv4.fromIPv4Address(this.address), 
                IPv4.fromIPv4Address(this.subnetMask),
                IPv4.fromIPv4Address(this.nextHopAddress), this.metric);
	}

    public short getAddressFamily()
    { return this.addressFamily; }

    public void setAddressFamily(short addressFamily)
    { this.addressFamily = addressFamily; }

    public short getRouteTag()
    { return this.routeTag; }

    public void setRouteTag(short routeTag)
    { this.routeTag = routeTag; }

	public int getAddress()
	{ return this.address; }

	public void setAddress(int address)
	{ this.address = address; }

	public int getSubnetMask()
	{ return this.subnetMask; }

	public void setSubnetMask(int subnetMask)
	{ this.subnetMask = subnetMask; }

	public int getNextHopAddress()
	{ return this.nextHopAddress; }

	public void setNextHopAddress(int nextHopAddress)
	{ this.nextHopAddress = nextHopAddress; }

    public int getMetric()
    { return this.metric; }

    public void setMetric(int metric)
    { this.metric = metric; }

	public byte[] serialize() 
    {
		int length = 2*2 + 4*4;
		byte[] data = new byte[length];
		ByteBuffer bb = ByteBuffer.wrap(data);

		bb.putShort(this.addressFamily);
		bb.putShort(this.routeTag);
        bb.putInt(this.address);
        bb.putInt(this.subnetMask);
        bb.putInt(this.nextHopAddress);
        bb.putInt(this.metric);
		return data;
	}

	public RIPv2Entry deserialize(byte[] data, int offset, int length) 
	{
		ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

		this.addressFamily = bb.getShort();
		this.routeTag = bb.getShort();
        this.address = bb.getInt();
        this.subnetMask = bb.getInt();
        this.nextHopAddress = bb.getInt();
        this.metric = bb.getInt();
		return this;
	}

    public boolean equals(Object obj)
    {
        if (this == obj)
        { return true; }
        if (null == obj)
        { return false; }
        if (!(obj instanceof RIPv2Entry))
        { return false; }
        RIPv2Entry other = (RIPv2Entry)obj;
        if (this.addressFamily != other.addressFamily)
        { return false; }
        if (this.routeTag != other.routeTag)
        { return false; }
        if (this.address != other.address)
        { return false; }
        if (this.subnetMask != other.subnetMask)
        { return false; }
        if (this.nextHopAddress != other.nextHopAddress)
        { return false; }
        if (this.metric != other.metric)
        { return false; }
        return true; 
    }
}
