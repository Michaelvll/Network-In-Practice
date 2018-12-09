package edu.wisc.cs.sdn.vnet.vns;

import java.nio.ByteBuffer;

import net.floodlightcontroller.packet.Ethernet;

public class CommandPacket extends Command
{
	protected String mInterfaceName;
	protected Ethernet etherPacket;
	
	public CommandPacket()
	{ super(Command.VNS_PACKET); }
	
	protected CommandPacket deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
				
		byte[] tmpBytes = new byte[16];
		buf.get(tmpBytes);
		this.mInterfaceName = new String(tmpBytes).trim();
		
        this.etherPacket = new Ethernet();
		this.etherPacket.deserialize(buf.array(), buf.position(),
				buf.capacity() - buf.position());
		
		return this;
	}
	
	protected int getSize()
	{ return super.getSize() + 16; }
	
	protected byte[] serialize()
	{
		byte[] packet = this.etherPacket.serialize();
		int size = this.getSize() + packet.length;
		this.mLen = size;
		
		byte[] data = new byte[size];
        ByteBuffer bb = ByteBuffer.wrap(data);
        
        byte[] parentData = super.serialize();
        
        bb.put(parentData);
        byte[] tmp = new byte[16];
        System.arraycopy(this.mInterfaceName.getBytes(), 0, tmp, 0, 
                this.mInterfaceName.length());
        bb.put(tmp);
        bb.put(packet);
        
        return data;
	}
}
