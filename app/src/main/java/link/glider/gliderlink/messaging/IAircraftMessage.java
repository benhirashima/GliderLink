package link.glider.gliderlink.messaging;

import android.location.Location;

public interface IAircraftMessage
{
	int ALT_NONE = -10000;
	short BEARING_NONE = 361;
	short GSPEED_NONE = -1;
	float VSPEED_NONE = -8675309;
	short ACCURACY_NONE = -1;

	long getSenderGID();

	String getSenderName();

	double getLatitude();

	double getLongitude();

	boolean hasAltitude();

	int getAltitude ();

	boolean hasBearing();

	short getBearing ();

	boolean hasSpeed();

	short getSpeed();

	boolean hasVertSpeedAvg();

	float getVertSpeedAvg();

	short getAccuracy();

	long getGpsFixTimeMs();

	/**
	 * Get time we received the message.
	 * @return Time in milliseconds in the default locale/timezone.
	 */
	long getTimestamp();

	Location getLocation();

	String toJson();
}
