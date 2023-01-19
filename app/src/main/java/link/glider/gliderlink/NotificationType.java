package link.glider.gliderlink;

import link.glider.gliderlink.utils.NumberUtils;

public enum NotificationType
{
	NEW_CONTACT(1, 0),
	AIRCRAFT_CLOSE(2, 5 * 60 * NumberUtils.MILLIS_PER_SEC),
	CLIMBING(3, 5 * 60 * NumberUtils.MILLIS_PER_SEC),
	LANDED(4, 0),
	XCVR_DISCONNECTED(5, 0);

	private final int val;
	private final long period;

	NotificationType (int index, long minRepeatPeriod)
	{
		this.val = index;
		this.period = minRepeatPeriod;
	}

	public int getValue() {
		return this.val;
	}

	public long getMinRepeatPeriodMs()
	{
		return this.period;
	}
}