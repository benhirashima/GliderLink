package link.glider.gliderlink.messaging;

import com.gotenna.sdk.TLVSection;
import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.messages.GTMessageData;
import com.gotenna.sdk.messages.GTMessageDataProtocol;
import com.gotenna.sdk.user.User;
import com.gotenna.sdk.user.UserDataStore;
import com.gotenna.sdk.utils.EndianUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Created by bhirashi on 11/20/16.
 */

public abstract class BaseMessage implements GTMessageDataProtocol
{
	public static final byte SECTION_COUNT = 3;
	private static final Logger log = LoggerFactory.getLogger(BaseMessage.class);
	private String senderName;
	private long senderGID;
	private long gpsFixTimeMs;
	private long timestamp;
	protected byte messageType = MessageType.SINGLE_LOCATION.getValue(); //default, for backward compat with versions that didn't have message types;

	BaseMessage() throws GTDataMissingException
	{
		this(UserDataStore.getInstance().getCurrentUser());
	}

	BaseMessage(final User user) throws GTDataMissingException
	{
		if(user == null) {
			throw new GTDataMissingException("UserDataStore.getCurrentUser returned null");
		} else {
			this.senderGID = user.getGID();
			this.senderName = user.getName();
//			this.gpsFixTimeMs = System.currentTimeMillis();
			this.timestamp = System.currentTimeMillis();
		}
	}

	BaseMessage(final ArrayList<TLVSection> tlvSections, final GTMessageData messageData) throws GTDataMissingException
	{
		this(tlvSections, messageData.getSenderGID(), messageData.getMessageSentDate().getTime());
	}

	BaseMessage(final ArrayList<TLVSection> tlvSections, final long senderGID, final long gpsFixTimeMs) throws GTDataMissingException
	{
		if(tlvSections.size() < SECTION_COUNT) {
			throw new GTDataMissingException(String.format("Param tlvSections should have a size of %d or greater", SECTION_COUNT));
		} else {
			this.senderGID = senderGID;
//			this.gpsFixTimeMs = gpsFixTimeMs;
			this.timestamp = System.currentTimeMillis();

			for(TLVSection section : tlvSections)
			{
				if (section.getType() == SectionType.MESSAGE_TYPE.getValue()) this.messageType = section.getValue()[0];
				if (section.getType() == SectionType.SENDER_NAME.getValue()) this.senderName = new String(section.getValue());
				if (section.getType() == SectionType.GPS_FIX_TIME.getValue())
					this.gpsFixTimeMs = EndianUtils.bytesToLong(section.getValue());
			}
		}
	}

	public byte getMessageType()
	{
		return this.messageType;
	}

	public String getSenderName () {
		return this.senderName;
	}

	public long getSenderGID() {
		return this.senderGID;
	}

	public long getGpsFixTimeMs ()
	{
		return this.gpsFixTimeMs;
	}

	public void setGpsFixTimeMs (long time)
	{
		this.gpsFixTimeMs = time;
	}

	/**
	 * Gets time in milliseconds when this object was instantiated, which is probably when the message was received.
	 */
	public long getTimestamp() {
		return this.timestamp;
	}

	private ArrayList<TLVSection> buildTLVSections() {
		final ArrayList<TLVSection> list = new ArrayList<>();
		try
		{
			list.add(new TLVSection(SectionType.SENDER_NAME.getValue(), this.senderName));
			list.add(new TLVSection(SectionType.GPS_FIX_TIME.getValue(), this.gpsFixTimeMs));
		}
		catch(Exception e)
		{
			log.error(e.getLocalizedMessage());
		}
		return list;
	}

	public ArrayList<TLVSection> getTLVSections() {
		return this.buildTLVSections();
	}

	public byte[] serializeToBytes() {
		return TLVSection.tlvSectionsToData(this.getTLVSections());
	}
}
