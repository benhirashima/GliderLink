package link.glider.gliderlink;

import android.location.Location;
import android.support.annotation.Nullable;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

import link.glider.gliderlink.messaging.IAircraftMessage;

class Aircraft
{
	private static final int MESSAGE_HISTORY_SIZE = 3;
	private static final Logger log = LoggerFactory.getLogger(Aircraft.class);

	private final long aircraftID;
	private final ConcurrentHashMap<NotificationType, Long> pastNotifications;
	private final CircularFifoQueue<IAircraftMessage> messages;

	public Aircraft(IAircraftMessage msg)
	{
		aircraftID = msg.getSenderGID();
		pastNotifications = new ConcurrentHashMap<>();
		messages = new CircularFifoQueue<>(MESSAGE_HISTORY_SIZE);
		messages.offer(msg);
	}

	public long getAircraftID()
	{
		return aircraftID;
	}

	public IAircraftMessage getLastMsg()
	{
		synchronized(messages)
		{
			return messages.get(messages.size() - 1);
		}
	}

	public void setLastMsg(IAircraftMessage newMsg)
	{
		if(newMsg.getSenderGID() != aircraftID)
		{
			log.error("Message has wrong sender ID.");
			return;
		}
		synchronized(messages)
		{
			messages.offer(newMsg);
		}
	}

	@Nullable
	public Long getLastNotificationTime(NotificationType type)
	{
		return pastNotifications.get(type);
	}

	public void setLastNotificationTime(NotificationType type, long time)
	{
		pastNotifications.put(type, time);
	}

	public boolean hasBeenShownLately(NotificationType type)
	{
		if (!pastNotifications.containsKey(type)) return false;

		Long lastNoteTime = getLastNotificationTime(type);
		if (lastNoteTime == null)
		{
			return false;
		}
		else
		{
			long now = System.currentTimeMillis();
			return now - lastNoteTime <= type.getMinRepeatPeriodMs();
		}
	}

	public boolean hasJustStartedClimbing()
	{
		synchronized(messages)
		{
			IAircraftMessage latest = messages.get(messages.size() - 1);
			boolean isClimbing = latest.hasVertSpeedAvg() && latest.getVertSpeedAvg() > 0.5f; //TODO: pref
			if (messages.size() == 1) return isClimbing;
			IAircraftMessage secondLatest = messages.get(messages.size() - 2);
			if (isClimbing && secondLatest.hasVertSpeedAvg())
			{
				return secondLatest.getVertSpeedAvg() <= 0;
			}
			return isClimbing;
		}
	}

	public boolean hasJustComeClose(Location myLocation)
	{
		synchronized(messages)
		{
//            if (messages.size() == 0) return false; // impossible
			IAircraftMessage latest = messages.get(messages.size() - 1);
			if (messages.size() == 1) return isClose(myLocation, latest.getLocation());
			IAircraftMessage secondLatest = messages.get(messages.size() - 2);
			if (isClose(myLocation, latest.getLocation()) && !isClose(myLocation, secondLatest.getLocation()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isClose(Location loc1, Location loc2)
	{
		float dist = loc1.distanceTo(loc2);
//		log.trace("dist: " + dist);
		if (dist <= 1852) // 1852m = 1nm TODO: pref?
		{
			if (loc1.hasAltitude() && loc2.hasAltitude())
			{
				return Math.abs(loc1.getAltitude() - loc2.getAltitude()) < 500; // TODO: pref?
			}
			else
			{
				return true; // altitudes unknown. assume close.
			}
		}
		else
		{
			return false;
		}
	}

	public double distanceFrom(Location myLocation)
	{
		synchronized(messages)
		{
			IAircraftMessage latest = messages.get(messages.size() - 1);
			Location location = latest.getLocation();
			return myLocation.distanceTo(location);
		}
	}
}
