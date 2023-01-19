package link.glider.gliderlink.utils;

/**
 * Created by bhirashi on 11/20/16.
 */

public class NumberUtils
{
	public static final double METERS_TO_NM = 0.000539957;
	public static final double METERS_TO_SM = 0.000621371;
	public static final double METERS_TO_FEET = 3.28084;
	public static final double MPS_TO_KTS = 1.94384;
	public static final double MPS_TO_MPH = 2.23694;
	public static final double MPS_TO_FPM = 196.85;
	public static final double MPS_TO_KMH = 3.6;
	public static final long NANOS_PER_MILLI = 1000000;
	public static final int MILLIS_PER_SEC = 1000;
	public static final long NANOS_PER_SEC = MILLIS_PER_SEC * NANOS_PER_MILLI;

	public static short doubleRoundToShort(final double f)
	{
		if (f < Short.MIN_VALUE) return Short.MIN_VALUE;
		if (f > Short.MAX_VALUE) return Short.MAX_VALUE;
		return (short) Math.round(f);
	}

	public static int doubleRoundToInt(final double f)
	{
		return (int) Math.round(f);
	}

	public static long roundToHundreds(double input)
	{
		double divided = input / 100;
		return Math.round(divided);
	}

	public static long roundToTens(double input)
	{
		double divided = input / 10;
		return Math.round(divided);
	}
}
