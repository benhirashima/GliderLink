package link.glider.gliderlink.messaging;

import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import link.glider.gliderlink.utils.NumberUtils;

public class AircraftMessage implements IAircraftMessage
{
	private static final Logger log = LoggerFactory.getLogger(AircraftMessage.class);
	private final long senderGid;
	private final String senderName;
	private final double latitude;
	private final double longitude;
	private final int altitude;
	private final short bearing;
	private final short speed;
	private final float vertSpeedAvg;
	private final short accuracy;
	private final long senderTime;
	private final long timestamp;

	private Location location;

	public AircraftMessage(IAircraftMessage copyFrom)
	{
		this(copyFrom.getSenderGID(), copyFrom.getSenderName(), copyFrom.getLatitude(), copyFrom.getLongitude(), copyFrom.getAltitude(), copyFrom.getBearing(), copyFrom.getSpeed(), copyFrom.getVertSpeedAvg(), copyFrom.getAccuracy(), copyFrom.getGpsFixTimeMs(), copyFrom.getTimestamp());
	}

	public AircraftMessage(final long senderGid, final String senderName, final double latitude, final double longitude, final double altitude, final float bearing, final float speed, final float vertSpeedAvg, final float accuracy, final long senderTime, final long timestamp)
	{
		this.senderGid = senderGid;
		this.senderName = senderName;
		this.altitude = NumberUtils.doubleRoundToInt(altitude);
		this.latitude = latitude;
		this.longitude = longitude;
		this.bearing = NumberUtils.doubleRoundToShort(bearing);
		this.speed = NumberUtils.doubleRoundToShort(speed);
		this.vertSpeedAvg = vertSpeedAvg;
		this.accuracy = NumberUtils.doubleRoundToShort(accuracy);
		this.senderTime = senderTime;
		this.timestamp = timestamp;
	}

	@Override
	public long getSenderGID()
	{
		return this.senderGid;
	}

	@Override
	public String getSenderName()
	{
		return this.senderName;
	}

	@Override
	public double getLatitude()
	{
		return this.latitude;
	}

	@Override
	public double getLongitude()
	{
		return this.longitude;
	}

	@Override
	public boolean hasAltitude()
	{
		return this.altitude != ALT_NONE;
	}

	@Override
	public int getAltitude()
	{
		return this.altitude;
	}

	@Override
	public boolean hasBearing()
	{
		return this.bearing < BEARING_NONE && this.bearing >= 0;
	}

	@Override
	public short getBearing()
	{
		return this.bearing;
	}

	@Override
	public boolean hasSpeed()
	{
		return this.speed > GSPEED_NONE;
	}

	@Override
	public short getSpeed()
	{
		return this.speed;
	}

	@Override
	public boolean hasVertSpeedAvg()
	{
		return this.vertSpeedAvg != VSPEED_NONE;
	}

	@Override
	public float getVertSpeedAvg()
	{
		return this.vertSpeedAvg;
	}

	@Override
	public short getAccuracy()
	{
		return this.accuracy;
	}

	@Override
	public long getGpsFixTimeMs ()
	{
		return this.senderTime;
	}

	@Override
	public long getTimestamp()
	{
		return this.timestamp;
	}

	@Override
	public Location getLocation()
	{
		if (location != null) return location;

		location = new Location(LocationManager.GPS_PROVIDER);
		location.setLatitude(getLatitude());
		location.setLongitude(getLongitude());
		if (hasAltitude()) location.setAltitude(getAltitude());
		if (hasBearing()) location.setBearing(getBearing());
		if (hasSpeed()) location.setSpeed(getSpeed());
		location.setAccuracy(getAccuracy());
		location.setTime(this.senderTime);
		return location;
	}

	@Nullable
	@Override
	public String toJson()
	{
		return AircraftMessage.toJson(this);
	}

	@Nullable
	public static String toJson(IAircraftMessage msg)
	{
		try
		{
			JSONObject loc = new JSONObject();
			loc.put("gid", msg.getSenderGID());
			loc.put("callsign", msg.getSenderName());
			loc.put("senderTime", msg.getGpsFixTimeMs());
			loc.put("receivedTime", msg.getTimestamp());
			loc.put("latitude", msg.getLatitude());
			loc.put("longitude", msg.getLongitude());
			loc.put("altitude", msg.getAltitude());
			loc.put("bearing", msg.getBearing());
			loc.put("gspeed", msg.getSpeed());
			loc.put("vspeed", msg.getVertSpeedAvg());
			loc.put("accuracy", msg.getAccuracy());

			JSONObject root = new JSONObject();
			root.put("position", loc);
			return root.toString();
		}
		catch(JSONException e)
		{
			log.error(e.getLocalizedMessage());
		}
		return null;
	}
}
