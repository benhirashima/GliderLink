package link.glider.gliderlink.messaging;

/**
 * Created by bhirashi on 12/29/16.
 */

public enum SectionType
{
	MESSAGE_TYPE(1),
	SENDER_NAME(2),
	LATITUDE(8),
	LONGITUDE(9),
	ALTITUDE(10),
	BEARING(11),
	GSPEED(12),
	VSPEED(13),
	ACCURACY(14),
	TIMESTAMP(19),
	LOCATION(20),
	SENDER_GID(21),
	GPS_FIX_TIME(22);

	private final int val;

	SectionType (int index) {
		this.val = index;
	}

	public int getValue() {
		return this.val;
	}
}
