package link.glider.gliderlink.messaging;

import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.Nullable;

import com.gotenna.sdk.TLVSection;
import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.messages.GTMessageData;
import com.gotenna.sdk.user.User;
import com.gotenna.sdk.user.UserDataStore;
import com.gotenna.sdk.utils.EndianUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;

import link.glider.gliderlink.utils.NumberUtils;

/**
 * Created by bhirashi on 11/20/16.
 */

public class LocationMessage extends BaseMessage implements IAircraftMessage
{
	private static final Logger log = LoggerFactory.getLogger(LocationMessage.class);
	private double latitude;
	private double longitude;
	private int altitude = ALT_NONE;
	private short bearing = BEARING_NONE;
	private short gSpeed = GSPEED_NONE;
	private float vSpeedAvg = VSPEED_NONE;
	private short accuracy = ACCURACY_NONE;

	private Location location;

	// for parsing received messages
	public LocationMessage(final ArrayList<TLVSection> tlvSections, final GTMessageData messageData) throws GTDataMissingException
	{
		this(tlvSections, messageData.getSenderGID(), messageData.getMessageSentDate().getTime());
	}

	public LocationMessage(final ArrayList<TLVSection> tlvSections, final long senderGID, final long senderTimeUtcMs) throws GTDataMissingException
	{
		super(tlvSections, senderGID, senderTimeUtcMs);

		if(tlvSections.size() < SECTION_COUNT)
		{
			throw new GTDataMissingException(String.format("Param tlvSections should have a size of %d or greater", SECTION_COUNT));
		}

		final Iterator iter = tlvSections.iterator();

		while(iter.hasNext()) {
			final TLVSection section = (TLVSection)iter.next();

			try {
				if(section.getType() == SectionType.LATITUDE.getValue()) {
					this.latitude = EndianUtils.bytesToDouble(section.getValue());
				} else if(section.getType() == SectionType.LONGITUDE.getValue()) {
					this.longitude = EndianUtils.bytesToDouble(section.getValue());
				} else if(section.getType() == SectionType.ALTITUDE.getValue()) {
					this.altitude = EndianUtils.bytesToInteger(section.getValue());
				} else if(section.getType() == SectionType.BEARING.getValue()) {
					this.bearing = EndianUtils.bytesToShort(section.getValue());
				} else if(section.getType() == SectionType.GSPEED.getValue()) {
					this.gSpeed = EndianUtils.bytesToShort(section.getValue());
				} else if(section.getType() == SectionType.VSPEED.getValue()) {
					this.vSpeedAvg = Float.intBitsToFloat(EndianUtils.bytesToInteger(section.getValue())); // convert int to float
				} else if(section.getType() == SectionType.ACCURACY.getValue()) {
					this.accuracy = EndianUtils.bytesToShort(section.getValue());
				}
			} catch (Exception e) {
				log.error(e.getLocalizedMessage());
			}
		}
	}

	// for creating messages to send
	public LocationMessage(final double latitude, final double longitude, final double altitude, final float bearing, final float gSpeed, final float vSpeedAvg, final float accuracy, final long senderTime) throws GTDataMissingException
	{
		this(UserDataStore.getInstance().getCurrentUser(), latitude, longitude, altitude, bearing, gSpeed, vSpeedAvg, accuracy, senderTime);
	}

	public LocationMessage(final User user, final double latitude, final double longitude, final double altitude, final float bearing, final float gSpeed, final float vSpeedAvg, final float accuracy, final long senderTime) throws GTDataMissingException
	{
		super(user);
		if(latitude == 0.0D || longitude == 0.0D) {
			throw new GTDataMissingException("Missing valid latitude and/or longitude");
		}
		this.messageType = MessageType.SINGLE_LOCATION.getValue();
		this.altitude = NumberUtils.doubleRoundToInt(altitude);
		this.latitude = latitude;
		this.longitude = longitude;
		this.bearing = NumberUtils.doubleRoundToShort(bearing);
		this.gSpeed = NumberUtils.doubleRoundToShort(gSpeed);
		this.vSpeedAvg = vSpeedAvg;
		this.accuracy = NumberUtils.doubleRoundToShort(accuracy);
		this.setGpsFixTimeMs(senderTime);
	}

	@Override
	public byte getMessageType()
	{
		return MessageType.SINGLE_LOCATION.getValue();
	}

	@Override
	public double getLatitude() {
		return this.latitude;
	}

	@Override
	public double getLongitude() {
		return this.longitude;
	}

	@Override
	public boolean hasAltitude() { return this.altitude != LocationMessage.ALT_NONE; }

	@Override
	public int getAltitude () {
		return this.altitude;
	}

	@Override
	public boolean hasBearing() { return this.bearing < LocationMessage.BEARING_NONE && this.bearing >= 0; }

	@Override
	public short getBearing ()
	{
		return this.bearing;
	}

	@Override
	public boolean hasSpeed() { return this.gSpeed > LocationMessage.GSPEED_NONE; }

	@Override
	public short getSpeed ()
	{
		return this.gSpeed;
	}

	@Override
	public boolean hasVertSpeedAvg() { return this.vSpeedAvg != LocationMessage.VSPEED_NONE; }

	@Override
	public float getVertSpeedAvg ()
	{
		return this.vSpeedAvg;
	}

	@Override
	public short getAccuracy ()
	{
		return this.accuracy;
	}

	@Override
	public ArrayList<TLVSection> getTLVSections() {
		final ArrayList<TLVSection> list = super.getTLVSections();
//		list.add(new TLVSection(SectionType.MESSAGE_TYPE.getValue(), getMessageType())); // not needed since default message type is LOCATION. saves payload space.
		try
		{
			list.add(new TLVSection(SectionType.LATITUDE.getValue(), EndianUtils.doubleToBigEndianBytes(this.latitude)));
			list.add(new TLVSection(SectionType.LONGITUDE.getValue(), EndianUtils.doubleToBigEndianBytes(this.longitude)));
			list.add(new TLVSection(SectionType.ALTITUDE.getValue(), EndianUtils.integerToBigEndianBytes(this.altitude)));
			list.add(new TLVSection(SectionType.BEARING.getValue(), EndianUtils.shortToBigEndianBytes(this.bearing)));
			list.add(new TLVSection(SectionType.GSPEED.getValue(), EndianUtils.shortToBigEndianBytes(this.gSpeed)));
			list.add(new TLVSection(SectionType.VSPEED.getValue(), EndianUtils.integerToBigEndianBytes(Float.floatToIntBits(this.vSpeedAvg))));  // convert float to int
//			list.add(new TLVSection(SectionType.ACCURACY.getValue(), EndianUtils.shortToBigEndianBytes(this.accuracy)));
		}
		catch(Exception e)
		{
			log.error(e.getLocalizedMessage());
		}
		return list;
	}

	@Override
	public byte[] serializeToBytes() {
		return TLVSection.tlvSectionsToData(this.getTLVSections());
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
		location.setTime(getTimestamp());
		return location;
	}

	@Nullable
	@Override
	public String toJson()
	{
		return AircraftMessage.toJson(this);
	}
}