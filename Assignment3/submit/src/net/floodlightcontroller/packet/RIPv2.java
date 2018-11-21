package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.LinkedList;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RIPv2 extends BasePacket 
{
    public static final byte VERSION = 2;
    public static final byte COMMAND_REQUEST = 1;
    public static final byte COMMAND_RESPONSE = 2;

	protected byte command;
	protected byte version;
	protected List<RIPv2Entry> entries;

	public RIPv2()
	{ 
        super(); 
        this.version = VERSION;
        this.entries = new LinkedList<RIPv2Entry>();
    }

	public void setEntries(List<RIPv2Entry> entries)
	{ this.entries = entries; }

	public List<RIPv2Entry> getEntries()
	{ return this.entries; }

    public void addEntry(RIPv2Entry entry)
    { this.entries.add(entry); }
	
	public void setCommand(byte command)
	{ this.command = command; }

	public byte getCommand()
	{ return this.command; }

	@Override
	public byte[] serialize() 
    {
		int length = 1 + 1 + 2 + this.entries.size() * (5*4);
		byte[] data = new byte[length];
		ByteBuffer bb = ByteBuffer.wrap(data);

		bb.put(this.command);
		bb.put(this.version);
		bb.putShort((short)0); // Put padding
		for (RIPv2Entry entry : this.entries)
		{ bb.put(entry.serialize()); }

		return data;
	}

	@Override
	public IPacket deserialize(byte[] data, int offset, int length) 
	{
		ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

		this.command = bb.get();
		this.version = bb.get();
        bb.getShort(); // Consume padding
		this.entries = new LinkedList<RIPv2Entry>();
        while (bb.position() < bb.limit())
        {
            RIPv2Entry entry = new RIPv2Entry();
            entry.deserialize(data, bb.position(), bb.limit()-bb.position());
            bb.position(bb.position() +  5*4);
            this.entries.add(entry);
        }
		return this;
	}

    public boolean equals(Object obj)
    {
        if (this == obj)
        { return true; }
        if (null == obj)
        { return false; }
        if (!(obj instanceof RIPv2))
        { return false; }
        RIPv2 other = (RIPv2)obj;
        if (this.command != other.command)
        { return false; }
        if (this.version != other.version)
        { return false; }
        if (this.entries.size() != other.entries.size())
        { return false; }
        for (int i = 0; i < this.entries.size(); i++)
        {
            if (!this.entries.get(i).equals(other.entries.get(i)))
            { return false; }
        }
        return true; 
    }

	public String toString()
	{
		String x = String.format("RIP : {command=%d, version=%d, entries={",
                this.command, this.version);
		for (RIPv2Entry entry : this.entries)
		{ x = x + entry.toString() + ","; }
        x = x + "}}";
		return x;
	}
}
