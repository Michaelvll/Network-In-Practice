package edu.wisc.cs.sdn.vnet.vns;

import java.nio.ByteBuffer;

public abstract class Command 
{
	public static final int VNS_OPEN = 1;
	public static final int VNS_CLOSE = 2;
	public static final int VNS_PACKET = 4;
	public static final int VNS_BANNER = 8;
	public static final int VNS_HW_INFO = 16;
	public static final int VNS_RTABLE = 32;
	public static final int VNS_OPEN_TEMPLATE = 64;
	public static final int VNS_AUTH_REQUEST = 128;
	public static final int VNS_AUTH_REPLY = 256;
	public static final int VNS_AUTH_STATUS = 512;
	
	public static final int ID_SIZE = 32;
	
	protected int mLen;
	protected int mType;
	
	public Command(int mType)
	{
		this.mLen = this.getSize();
		this.mType = mType;
	}
	
	protected Command deserialize(ByteBuffer buf)
	{
		this.mLen = buf.getInt();
		this.mType = buf.getInt();
		return this;
	}
	
	protected byte[] serialize()
	{
		byte[] data = new byte[8];
        ByteBuffer bb = ByteBuffer.wrap(data);
        
        bb.putInt(this.mLen);
        bb.putInt(this.mType);
        
        return data;
	}
	
	protected int getSize()
	{ return 4 + 4; }
	
	
}
