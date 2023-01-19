package link.glider.gliderlink.messaging;

import com.gotenna.sdk.TLVSection;
import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.utils.EndianUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class BundledLocationMsg extends LocationMessage
{
	private static final Logger log = LoggerFactory.getLogger(BundledLocationMsg.class);

	public static BundledLocationMsg parse(final byte[] data) throws GTDataMissingException
	{
		ArrayList<TLVSection> tlvSections = TLVSection.tlvSectionsFromData(data);
		Long senderGid = null;
		Long senderTime = null;

		for (TLVSection section : tlvSections)
		{
			if (section.getType() == SectionType.SENDER_GID.getValue())
			{
				senderGid = EndianUtils.bytesToLong(section.getValue());
			}
			else if (section.getType() == SectionType.GPS_FIX_TIME.getValue())
			{
				senderTime = EndianUtils.bytesToLong(section.getValue());
			}
		}

		if (senderGid == null || senderTime == null) throw new GTDataMissingException("Sender GID or sender time missing.");

		return new BundledLocationMsg(tlvSections, senderGid, senderTime);
	}

	public BundledLocationMsg(final LocationMessage msg) throws GTDataMissingException
	{
		super(msg.getTLVSections(), msg.getSenderGID(), msg.getGpsFixTimeMs());
	}

	private BundledLocationMsg(final ArrayList<TLVSection> tlvSections, final long senderGid, final long senderTime) throws GTDataMissingException
	{
		super(tlvSections, senderGid, senderTime);
	}

	@Override
	public ArrayList<TLVSection> getTLVSections()
	{
		final ArrayList<TLVSection> list = super.getTLVSections();
		final ArrayList<TLVSection> newList = new ArrayList<>(list.size());

		// filter out non-essential sections to reduce payload size
		for(TLVSection section : list)
		{
			if (section.getType() != SectionType.MESSAGE_TYPE.getValue() && section.getType() != SectionType.GSPEED.getValue() && section.getType() != SectionType.ACCURACY.getValue()) newList.add(section);
		}

		try
		{
			newList.add(new TLVSection(SectionType.SENDER_GID.getValue(), EndianUtils.longToBigEndianBytes(this.getSenderGID())));
			newList.add(new TLVSection(SectionType.GPS_FIX_TIME.getValue(), EndianUtils.longToBigEndianBytes(this.getGpsFixTimeMs())));
		}
		catch(Exception e)
		{
			log.error(e.getLocalizedMessage());
		}
		return newList;
	}

	@Override
	public byte[] serializeToBytes() {
		return TLVSection.tlvSectionsToData(this.getTLVSections());
	}
}
