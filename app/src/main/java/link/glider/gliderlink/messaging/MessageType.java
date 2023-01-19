package link.glider.gliderlink.messaging;

public enum MessageType
{
	SINGLE_LOCATION((byte)1),
	MULTI_LOCATION((byte)2);

	private final byte val;

	MessageType (byte index) {
		this.val = index;
	}

	public byte getValue() {
		return this.val;
	}
}