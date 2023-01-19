package link.glider.gliderlink.messaging;

import com.gotenna.sdk.TLVSection;
import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.messages.GTMessageData;
import com.gotenna.sdk.user.UserDataStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class MultiLocationMsg extends LocationMessage
{
	private static final Logger log = LoggerFactory.getLogger(MultiLocationMsg.class);
	private final ArrayList<BundledLocationMsg> locMsgs = new ArrayList<>();

	public MultiLocationMsg(final double latitude, final double longitude, final double altitude, final float bearing, final float gSpeed, final float vSpeedAvg, final float accuracy, final long senderTime) throws GTDataMissingException
	{
		super(UserDataStore.getInstance().getCurrentUser(), latitude, longitude, altitude, bearing, gSpeed, vSpeedAvg, accuracy, senderTime);
	}

	public MultiLocationMsg(final ArrayList<TLVSection> tlvSections, final GTMessageData messageData) throws GTDataMissingException
	{
		super(tlvSections, messageData);

		for(TLVSection section : tlvSections)
		{
			try
			{
				if(section.getType() == SectionType.LOCATION.getValue())
				{
					locMsgs.add(BundledLocationMsg.parse(section.getValue()));
				}
			}
			catch(Exception e)
			{
				log.error(e.getLocalizedMessage());
			}
		}
	}

	@Override
	public byte getMessageType()
	{
		return MessageType.MULTI_LOCATION.getValue();
	}

	public ArrayList<BundledLocationMsg> getBundledLocationMsgs()
	{
		return this.locMsgs;
	}

	public boolean addBundledLocationMsg(BundledLocationMsg msg)
	{
		return locMsgs.add(msg);
	}

	public boolean addLocationMsg(LocationMessage msg)
	{
		try
		{
			return addBundledLocationMsg(new BundledLocationMsg(msg));
		}
		catch(GTDataMissingException e)
		{
			log.error(e.getLocalizedMessage());
			return false;
		}
	}

	@Override
	public ArrayList<TLVSection> getTLVSections()
	{
		ArrayList<TLVSection> tlvSections = super.getTLVSections();
//		tlvSections.add(new TLVSection(SectionType.MESSAGE_TYPE.getValue(), getMessageType())); // omitting to save payload space. assuming anything without message type is multi-location.
		for(BundledLocationMsg bMsg : locMsgs)
		{
			tlvSections.add(new TLVSection(SectionType.LOCATION.getValue(), bMsg.serializeToBytes()));
		}
		return tlvSections;
	}

	@Override
	public byte[] serializeToBytes() {
		return TLVSection.tlvSectionsToData(this.getTLVSections());
	}
}
