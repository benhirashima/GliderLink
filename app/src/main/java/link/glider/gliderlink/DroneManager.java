package link.glider.gliderlink;

import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;

import link.glider.gliderlink.messaging.AircraftMessage;
import link.glider.gliderlink.messaging.IAircraftMessage;
import link.glider.gliderlink.utils.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

class DroneManager
{
	private static final Logger log = LoggerFactory.getLogger(DroneManager.class);
	private static final int UPDATE_INTERVAL = 5000; // milliseconds

	private final IAircraftMessageListener messageListener;
	private final Handler handler;
	private final Runnable droneRunnable;
	private final HashMap<String, IAircraftMessage> drones;
	private Location myLocation;

	private int ticks = 0;
	private static final String[] droneCallsigns = { StringUtils.randomAlphaNumeric(2), StringUtils.randomAlphaNumeric(2), StringUtils.randomAlphaNumeric(2) };

	public DroneManager(IAircraftMessageListener messageListener)
	{
		this.messageListener = messageListener;
		drones = new HashMap<>(3);
		handler = new Handler();
		droneRunnable = new Runnable() {
			@Override
			public void run()
			{
				doMods();
				sendMessages();
				handler.postDelayed(this, UPDATE_INTERVAL);
			}
		};
	}

	public void launchDrones(Location currLocation)
	{
		log.debug("launchDrones()");
		if (currLocation == null)
		{
			myLocation = new Location(LocationManager.GPS_PROVIDER);
			myLocation.setLatitude(Constants.CVH.getLatitude());
			myLocation.setLongitude(Constants.CVH.getLongitude());
			myLocation.setAltitude(Constants.CVH.getAltitude());
		}
		else
		{
			myLocation = currLocation;
		}
		makeDrones(myLocation);
		handler.postDelayed(droneRunnable, UPDATE_INTERVAL);
	}

	public void stopDrones()
	{
		log.debug("stopDrones()");
		handler.removeCallbacks(droneRunnable);
	}

	private void makeDrones(Location location)
	{
		if (location == null) return;

		long now = System.currentTimeMillis();

		AircraftMessage msg = new AircraftMessage(1111, droneCallsigns[0], location.getLatitude(), location.getLongitude() - 0.015, 0, 180f, 30f, -1f, 0f, now, now);
		drones.put(msg.getSenderName(), msg);

		msg = new AircraftMessage(2222, droneCallsigns[1], location.getLatitude() - 0.045, location.getLongitude() + 0.015, 0, 0f, 30f, -0.5f, 0f, now, now);
		drones.put(msg.getSenderName(), msg);

		msg = new AircraftMessage(3333, droneCallsigns[2], location.getLatitude() + 0.015, location.getLongitude(), 0, 270f, 30f, -3f, 0f, now, now);
		drones.put(msg.getSenderName(), msg);
	}

	private void sendMessages()
	{
		if (drones.size() == 0) return;
		long msNow = System.currentTimeMillis();
		ArrayList<IAircraftMessage> updatedDrones = new ArrayList<>(drones.size());

		for(IAircraftMessage msg : drones.values())
		{
			double lat, lng, alt;
			lat = msg.getLatitude();
			lng = msg.getLongitude();
			alt = msg.getAltitude();

			if (alt == 0) continue;

			if (msg.getBearing() == 0)
				lat += 0.0001;
			else if (msg.getBearing() == 180)
				lat -= 0.0001;
			else if (msg.getBearing() == 90)
				lng += 0.0001;
			else if (msg.getBearing() == 270)
				lng -= 0.0001;
			alt += msg.getVertSpeedAvg();

			AircraftMessage newMsg = new AircraftMessage(msg.getSenderGID(), msg.getSenderName(), lat, lng, alt, msg.getBearing(), msg.getSpeed(), msg.getVertSpeedAvg(), msg.getAccuracy(), msNow, msNow);
			messageListener.onAircraftMessage(newMsg);
			updatedDrones.add(newMsg);
		}

		for(IAircraftMessage msg : updatedDrones)
		{
			drones.put(msg.getSenderName(), msg);
		}
	}

	private void doMods()
	{
		if (ticks == 0)
		{
			IAircraftMessage msg = drones.get(droneCallsigns[0]);
			AircraftMessage newMsg = new AircraftMessage(msg.getSenderGID(), msg.getSenderName(), msg.getLatitude(), msg.getLongitude(), 500, msg.getBearing(), msg.getSpeed(), msg.getVertSpeedAvg(), msg.getAccuracy(), msg.getGpsFixTimeMs(), msg.getTimestamp());
			drones.put(droneCallsigns[0], newMsg);
		}
		else if (ticks == 1)
		{
			IAircraftMessage msg = drones.get(droneCallsigns[1]);
			AircraftMessage newMsg = new AircraftMessage(msg.getSenderGID(), msg.getSenderName(), msg.getLatitude(), msg.getLongitude(), 1000, msg.getBearing(), msg.getSpeed(), msg.getVertSpeedAvg(), msg.getAccuracy(), msg.getGpsFixTimeMs(), msg.getTimestamp());
			drones.put(droneCallsigns[1], newMsg);
		}
		else if (ticks == 2)
		{
			IAircraftMessage msg = drones.get(droneCallsigns[2]);
			AircraftMessage newMsg = new AircraftMessage(msg.getSenderGID(), msg.getSenderName(), msg.getLatitude(), msg.getLongitude(), 1500, msg.getBearing(), msg.getSpeed(), msg.getVertSpeedAvg(), msg.getAccuracy(), msg.getGpsFixTimeMs(), msg.getTimestamp());
			drones.put(droneCallsigns[2], newMsg);
		}
		else if (ticks == 3)
		{
			IAircraftMessage msg = drones.get(droneCallsigns[2]);
			AircraftMessage newMsg = new AircraftMessage(msg.getSenderGID(), msg.getSenderName(), msg.getLatitude(), msg.getLongitude(), msg.getAltitude(), msg.getBearing(), msg.getSpeed(), -msg.getVertSpeedAvg(), msg.getAccuracy(), msg.getGpsFixTimeMs(), msg.getTimestamp());
			drones.put(droneCallsigns[2], newMsg);
		}
		else if (ticks == 5)
		{
			drones.remove(droneCallsigns[1]);
		}

		ticks++;
	}
}
