package link.glider.gliderlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.user.UserDataStore;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import link.glider.gliderlink.Constants.Units;
import link.glider.gliderlink.messaging.LocationMessage;
import link.glider.gliderlink.messaging.IAircraftMessage;
import link.glider.gliderlink.messaging.MultiLocationMsg;
import link.glider.gliderlink.prefs.UnitsPrefs;
import link.glider.gliderlink.utils.NumberUtils;

public class Tracker implements TrackerContract.Tracker, LocationListener, GotennaContract.GotennaManagerListener, IAircraftMessageListener
{
	public static final int TX_FREQ_MS = 20000;
	public static final int LOC_EXPIRED_MS = TX_FREQ_MS * 3;

	public static final short MIN_LOC_TIME_INTV_MS = 5000; // milliseconds. careful changing this; it affects vert spd calculation.
	public static final int VERT_SPD_AVG_PERIOD_MS = 60000; // milliseconds
	public static final String CALLSIGN_UNKNOWN = "???";
	private static final int MIN_ACCURACY_FOR_VERT_SPD = 10; // meters
	private static final float MIN_SPEED_AIRBORNE = 37; // kmh
	private static final float MAX_ALT_LANDED = 3050; // meters ~ 10,000ft
	public static final long MIN_TIME_STATIONARY_NANOS = TX_FREQ_MS * NumberUtils.NANOS_PER_MILLI;
	public static final String ACTION_MSG_SENT = Tracker.class.getName() + ".MSG_SENT";
	public static final String ACTION_MSG_RECEIVED = Tracker.class.getName() + ".MSG_RECEIVED";
	private static final int CULL_CHECK_MS = 20000;
	private static final int CULL_AFTER_MS = 300000;
	private static final int MAX_LOC_AGE_SECS = TX_FREQ_MS * NumberUtils.MILLIS_PER_SEC;

	private static final Logger log = LoggerFactory.getLogger(Tracker.class);
	private static final ConcurrentHashMap<Long, Aircraft> aircrafts = new ConcurrentHashMap<>();
	private static final AtomicBoolean isAirborne = new AtomicBoolean(false);
	private static final AtomicLong timeFellBelowMinSpeed = new AtomicLong(0); // in nanos since we first fell below min airborne speed

	private State state = State.STOPPED;
	private TrackerContract.TrackerDelegate delegate;
	private final Context context;
	private Location lastLocation;
	private final LocationManager locationMan;
	private final GotennaManager gtMan;
	private final CircularFifoQueue<Location> locations = new CircularFifoQueue<>(VERT_SPD_AVG_PERIOD_MS / MIN_LOC_TIME_INTV_MS + 1);
	private DroneManager droneMan;
	private final SharedPreferences prefs;
	private final SharedPreferences stats;
	private final NoteCenter noteCenter;
	private final UnitsPrefs unitsPrefs;
	private float maxDist;
	private int maxContacts;
	private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
	private AtomicLong gpsTimeOffsetMs = new AtomicLong(0);

	private final Handler handler =  new Handler();

	private final Runnable txRunnable = new Runnable() {
		@Override
		public void run ()
		{
			sendLocationShout();
			handler.postDelayed(this, getEccentricTxPeriodMs());
		}
	};

	private final Runnable landingRunnable = new Runnable() {
		@Override
		public void run()
		{
			Tracker.checkIfStationaryForLong();
			if (Tracker.getIsAirBorne())
				handler.postDelayed(this, MIN_LOC_TIME_INTV_MS);
			else
				Tracker.this.changeState(State.GROUND);
		}
	};

	private final Runnable cullRunnable = new Runnable() {
		@Override
		public void run ()
		{
			cullOldGliders();
			handler.postDelayed(this, CULL_CHECK_MS);
		}
	};

	public enum State
	{
		//                  airborne	gotenna		gps on		tx'ing
		STOPPED				(false,		false,		false,		false),
		SEARCH_XCVR			(false,		true,		true,		false),	// searching for transceiver device (gotenna)
		GROUND				(false,		true,		true,		false),	// we always go from SEARCH_XCVR to GROUND. if airborne, we will immediately switch to AIRBORNE.
		AIRBORNE			(true,		true,		true,		true),
		AIRBORNE_NO_GPS		(true,		true,		true,		false),
		BASE				(false,		true,		true,		true), 	// for when we're operating as a base station
		DEMO				(true,		false,		false,		false)
		;

		private final boolean isAirborne;
		private final boolean isTxOnline;
		private final boolean isGpsOnline;
		private final boolean isTransmitting;

		State(boolean isTxOnline, boolean isAirborne, boolean isGpsOnline, boolean isTransmitting)
		{
			this.isTxOnline = isTxOnline;
			this.isAirborne = isAirborne;
			this.isGpsOnline = isGpsOnline;
			this.isTransmitting = isTransmitting;
		}
	}

	public Tracker (Context context, TrackerContract.TrackerDelegate delegate, LocationManager locationMan, GotennaManager gtMan, SharedPreferences prefs, SharedPreferences stats, NoteCenter noteCenter) throws IllegalArgumentException
	{
		if (context == null || delegate == null || locationMan == null || gtMan == null || prefs == null || noteCenter == null) throw new IllegalArgumentException("Arguments cannot be null.");

		this.delegate = delegate;
		this.context = context;
		this.locationMan = locationMan;
		this.gtMan = gtMan;
		this.gtMan.addListener(this);
		this.prefs = prefs;
		this.stats = stats;
		this.noteCenter = noteCenter;
		unitsPrefs = new UnitsPrefs(context);
		maxDist = prefs.getFloat(context.getString(R.string.pref_key_max_distance), 0);
		maxContacts = stats.getInt(context.getString(R.string.pref_key_max_contacts), 0);
		prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
			public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
				log.info("Settings key changed: " + key);
				if(key.equals(Tracker.this.context.getString(R.string.pref_key_callsign)))
				{
					UserDataStore.getInstance().getCurrentUser().setName(prefs.getString(key, CALLSIGN_UNKNOWN));
					log.info("Callsign updated in goTenna.");
				}
			}
		};
		this.prefs.registerOnSharedPreferenceChangeListener(prefsListener);
	}

	// ================================================================================
	// region - TrackerContract.Tracker
	// ================================================================================

	@Override
	public void start()
	{
		log.debug("start()");
		changeState(State.SEARCH_XCVR);
		handler.post(cullRunnable);
	}

	@Override
	public void startDemo()
	{
		log.debug("startDemo()");
		changeState(State.DEMO);
		handler.post(cullRunnable);
	}

	@Override
	public void stop()
	{
		log.debug("stop()");
		changeState(State.STOPPED);
		handler.removeCallbacks(cullRunnable);
	}
	//endregion

	// ================================================================================
	// region - State changes
	// ================================================================================

	private synchronized void changeState(State toState)
	{
		if (toState == state)
		{
			log.debug("already in state " + toState);
			return;
		}
		log.debug("change state from " + state + " to " + toState);

		// first, run exit routine for the state we're leaving
		switch (state)
		{
			case SEARCH_XCVR:
				exitSearchTxState();
				break;
			case GROUND:
				exitGroundState();
				break;
			case AIRBORNE:
				exitAirborneState();
				break;
			case AIRBORNE_NO_GPS:
				exitAirborneNoGpsState();
				break;
			case BASE:
				exitBaseState();
				break;
			case DEMO:
				exitDemoState();
				break;
			// STOPPED state omitted
		}

		// next, run enter routine for the state we're changing to
		switch (toState)
		{
			case SEARCH_XCVR:
				enterSearchTxState();
				break;
			case GROUND:
				enterGroundState();
				break;
			case AIRBORNE:
				enterAirborneState();
				break;
			case AIRBORNE_NO_GPS:
				enterAirborneNoGpsState();
				break;
			case BASE:
				enterBaseState();
				break;
			case DEMO:
				enterDemoState();
				break;
			default:
				enterStoppedState();
		}
	}

	private void enterSearchTxState()
	{
		log.trace("enterSearchTxState()");
		state = State.SEARCH_XCVR;
		startGps();
		if(gtMan.getState() == GotennaManager.State.CONNECTED)
		{
			changeState(State.GROUND);
		}
		else
		{
			gtMan.search();
		}
	}

	private void exitSearchTxState()
	{
		log.trace("exitSearchTxState()");
	}

	private void enterGroundState()
	{
		log.trace("enterGroundState()");
		state = State.GROUND;
		changeState(State.AIRBORNE);
	}

	private void exitGroundState()
	{
		log.trace("exitGroundState()");
	}

	private void enterAirborneState()
	{
		log.trace("enterAirborneState()");
		state = State.AIRBORNE;
		startTransmitting();
//		if (!BuildConfig.DEBUG) handler.post(landingRunnable); // don't check airborne if debugging
	}

	private void exitAirborneState()
	{
		log.trace("exitAirborneState()");
		stopTransmitting();
		handler.removeCallbacks(landingRunnable);
	}

	private void enterAirborneNoGpsState()
	{
		log.trace("enterAirborneNoGpsState()");
		state = State.AIRBORNE_NO_GPS;
		stopTransmitting();
	}

	private void exitAirborneNoGpsState()
	{
		log.trace("exitAirborneNoGpsState()");
	}

	private void enterBaseState()
	{
		log.trace("enterBaseState()");
		state = State.BASE;
	}

	private void exitBaseState()
	{
		log.trace("exitBaseState()");
	}

	private void enterDemoState()
	{
		log.trace("enterDemoState()");
		state = State.DEMO;
		startGps();
		droneMan = new DroneManager(this);
		droneMan.launchDrones(lastLocation);
	}

	private void exitDemoState()
	{
		log.trace("exitDemoState()");
		droneMan.stopDrones();
		stopGps();
	}

	private void enterStoppedState()
	{
		log.trace("enterStoppedState()");
		state = State.STOPPED;
		stopTransmitting();
		stopGps();
		gtMan.stop();
		aircrafts.clear();
		delegate.onTrackerStateChange(state);
	}
	//endregion


	// ================================================================================
	// region - GPS stuff
	// ================================================================================

	private boolean startGps()
	{
		try
		{
			locationMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_LOC_TIME_INTV_MS, 0, this);
			lastLocation = locationMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (lastLocation == null) lastLocation = locationMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		}
		catch (SecurityException e)
		{
			Toast.makeText(context, "Location security exception", Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

	private void stopGps()
	{
		try
		{
			locationMan.removeUpdates(this);
		}
		catch (SecurityException e)
		{
			log.error(e.getLocalizedMessage());
		}
	}

	//endregion

	// ================================================================================
	// region - LocationListener
	// ================================================================================

	@Override
	public void onLocationChanged(Location location)
	{
		gpsTimeOffsetMs.set(location.getTime() - System.currentTimeMillis());
		lastLocation = location;
		locations.offer(location);
		updateAirborneStatus();
//        log.trace(location.toString());
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
		if (status == LocationProvider.OUT_OF_SERVICE)
			log.warn("LocationListener.onStatusChanged(OUT_OF_SERVICE)");
		else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE)
			log.warn("LocationListener.onStatusChanged(TEMPORARILY_UNAVAILABLE)");
	}

	@Override
	public void onProviderEnabled(String provider)
	{
		log.debug("LocationListener.onProviderEnabled(" + provider + ")");
	}

	@Override
	public void onProviderDisabled(String provider)
	{
		log.debug("LocationListener.onProviderDisabled(" + provider + ")");
	}
	//endregion

	// ================================================================================
	// region - Static Methods
	// ================================================================================

	public static ConcurrentHashMap<Long, Aircraft> getAircraft()
	{
		return Tracker.aircrafts;
	}

	private static String getLocationDetailsStr(final Location loc, final String distNm)
	{
		return String.format("%.8f", loc.getLatitude()) + ", " + String.format("%.8f", loc.getLongitude()) + " Dist: " + distNm + "nm Alt: " + String.format("%.0f", loc.getAltitude() * NumberUtils.METERS_TO_FEET) + "ft Hdg: " + String.format("%.0f", loc.getBearing()) + " Spd: " + String.format("%.0f", loc.getSpeed() * NumberUtils.MPS_TO_KTS) + "kts Acc: " + String.format("%.0fm", loc.getAccuracy());
	}

	public static boolean getIsAirBorne()
	{
		return isAirborne.get();
	}

	public static void checkIfStationaryForLong()
	{
		if (timeFellBelowMinSpeed.get() == 0) return;
		if (SystemClock.elapsedRealtimeNanos() - timeFellBelowMinSpeed.get() > MIN_TIME_STATIONARY_NANOS)
		{
			isAirborne.set(false);
			timeFellBelowMinSpeed.set(0);
		}
	}
	//endregion

	// ================================================================================
	// region - Class Instance Methods
	// ================================================================================

	private void updateAirborneStatus()
	{
		if (lastLocation.hasSpeed())
		{
			if (lastLocation.getSpeed() >= MIN_SPEED_AIRBORNE)
			{
				timeFellBelowMinSpeed.set(0);
				if (!Tracker.getIsAirBorne())
				{
					isAirborne.set(true);
					startTransmitting();
					handler.post(landingRunnable);
				}
			}
			else if (Tracker.getIsAirBorne()) // airborne, but slow
			{
				// only detect landing if under ~10,000ft. otherwise more likely to be stationary in wave.
				if (lastLocation.hasAltitude() && lastLocation.getAltitude() <= MAX_ALT_LANDED)
				{
					if (timeFellBelowMinSpeed.get() == 0)
					{
						timeFellBelowMinSpeed.set(lastLocation.getElapsedRealtimeNanos()); // using time from location allows us to fake the time in tests
					}
				}
			}
		}
	}

	@Override
	public void onAircraftMessage(IAircraftMessage msg)
	{
		if (msg == null)
		{
			log.error("onAircraftMessage received a null object.");
			return;
		}

		if (state != State.DEMO && msg.getSenderGID() == UserDataStore.getInstance().getCurrentUser().getGID()) return; // ignore re-broadcasted own position

		if (System.currentTimeMillis() + gpsTimeOffsetMs.get() - msg.getGpsFixTimeMs() > CULL_AFTER_MS)
		{
			log.warn("Ignoring message with old location.");
			return;
		}

		LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_MSG_RECEIVED));

		if (putMessage(msg)) // add aircraft and return whether it's a new contact
		{
			sendNewContactNotification(msg);
			if (aircrafts.size() > maxContacts && state != State.DEMO)
			{
				maxContacts = aircrafts.size();
				saveMaxContactsRecord(maxContacts, lastLocation);
			}
		}

		Aircraft aircraft = aircrafts.get(msg.getSenderGID());
		if (aircraft.hasJustStartedClimbing())
		{
			if (!aircraft.hasBeenShownLately(NotificationType.CLIMBING))
				sendClimbingNotification(msg);
		}

		Location loc = msg.getLocation();

		String distStr = "?";
		if (lastLocation != null) // TODO: check age of lastLocation?
		{
			if (!aircraft.hasBeenShownLately(NotificationType.AIRCRAFT_CLOSE) && aircraft.hasJustComeClose(lastLocation))
			{
				sendCloseNotification(msg);
			}

			float distMeters = loc.distanceTo(lastLocation);
			if (distMeters > maxDist && state != State.DEMO)
			{
				maxDist = distMeters;
				saveMaxDistRecord(maxDist, lastLocation, loc);
			}
			distStr = String.format("%.2f", distMeters * NumberUtils.METERS_TO_NM);
		}

		delegate.onMessageReceived(msg);

		log.trace("Received from " + msg.getSenderName() + ": " + getLocationDetailsStr(loc, distStr));
	}

	private void saveMaxDistRecord(float maxDist, Location myLocation, Location theirLocation)
	{
		log.info("Distance record: " + String.format("%.1fkm", maxDist / 1000));
		SharedPreferences.Editor editor = stats.edit();
		editor.putFloat(context.getString(R.string.pref_key_max_distance), maxDist);
		editor.putLong(context.getString(R.string.pref_key_max_dist_timestamp), Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
		editor.putFloat(context.getString(R.string.pref_key_max_dist_my_longitude_float), (float)myLocation.getLongitude());
		editor.putFloat(context.getString(R.string.pref_key_max_dist_my_latitude_float), (float)myLocation.getLatitude());
		editor.putFloat(context.getString(R.string.pref_key_max_dist_my_altitude_float), (float)myLocation.getAltitude());
		editor.putFloat(context.getString(R.string.pref_key_max_dist_their_longitude_float), (float)theirLocation.getLongitude());
		editor.putFloat(context.getString(R.string.pref_key_max_dist_their_latitude_float), (float)theirLocation.getLatitude());
		editor.putFloat(context.getString(R.string.pref_key_max_dist_their_altitude_float), (float)theirLocation.getAltitude());
		editor.commit();
	}

	private void saveMaxContactsRecord(int maxContacts, Location myLocation)
	{
		log.info("Max contacts record: " + maxContacts);
		SharedPreferences.Editor editor = stats.edit();
		editor.putInt(context.getString(R.string.pref_key_max_contacts), maxContacts);
		editor.putLong(context.getString(R.string.pref_key_max_contacts_timestamp), Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
		editor.putFloat(context.getString(R.string.pref_key_max_contacts_my_longitude_float), (float)myLocation.getLongitude());
		editor.putFloat(context.getString(R.string.pref_key_max_contacts_my_latitude_float), (float)myLocation.getLatitude());
		editor.putFloat(context.getString(R.string.pref_key_max_contacts_my_altitude_float), (float)myLocation.getAltitude());
		editor.commit();
	}

	/*
	@returns True if this is the first message received from the sender.
	 */
	private boolean putMessage(final IAircraftMessage msg)
	{
		Aircraft aircraft = Tracker.aircrafts.get(msg.getSenderGID());
		if (aircraft == null) // new message
		{
			aircraft = new Aircraft(msg);
			Tracker.aircrafts.put(aircraft.getAircraftID(), aircraft);
			return true;
		}
		else
		{
			if (msg.getGpsFixTimeMs() > aircraft.getLastMsg().getGpsFixTimeMs())
			{
				aircraft.setLastMsg(msg);
			}
			else
			{
				log.debug("Ignoring out of date message.");
			}
			return false;
		}
	}

	private void updateLastNotificationTime(IAircraftMessage msg, NotificationType type)
	{
		Aircraft aircraft = aircrafts.get(msg.getSenderGID());
		aircraft.setLastNotificationTime(type, msg.getTimestamp());
	}

	private void sendNewContactNotification(IAircraftMessage msg)
	{
		if (prefs.getBoolean(context.getString(R.string.pref_key_show_notifications), true) && prefs.getBoolean(context.getString(R.string.pref_key_notification_new_contact), true))
		{
			updateLastNotificationTime(msg, NotificationType.NEW_CONTACT);
			noteCenter.showNotification("New contact " + msg.getSenderName() + getDistAndBearingString(lastLocation, msg.getLocation()));
		}
	}

	private void sendClimbingNotification(IAircraftMessage msg)
	{
		if (prefs.getBoolean(context.getString(R.string.pref_key_show_notifications), true) && prefs.getBoolean(context.getString(R.string.pref_key_notification_climbing), true))
		{
			updateLastNotificationTime(msg, NotificationType.CLIMBING);
			final double vertSpdConverted = Math.abs(unitsPrefs.convertVertSpeedMps(msg.getVertSpeedAvg()));
			String fmtStr = unitsPrefs.getVerticalSpeedUnits() == Units.fpm ? "%.0f" : "%.1f";
			fmtStr += prefs.getString(UnitsPrefs.KEY_VERT_SPEED_UNITS, context.getResources().getStringArray(R.array.vert_speed_units)[0]);
			noteCenter.showNotification(msg.getSenderName() + " is climbing at " + String.format(fmtStr, vertSpdConverted));
		}
	}

	private void sendCloseNotification(IAircraftMessage msg)
	{
		if (prefs.getBoolean(context.getString(R.string.pref_key_show_notifications), true) && prefs.getBoolean(context.getString(R.string.pref_key_notification_close), true))
		{
			updateLastNotificationTime(msg, NotificationType.AIRCRAFT_CLOSE);
			noteCenter.showNotification("Caution: " + msg.getSenderName() + " " + getDistAndBearingString(lastLocation, msg.getLocation()), android.R.drawable.ic_dialog_alert);
		}
	}

	private String getDistAndBearingString(Location myLocation, Location theirLocation)
	{
		double dist = 0;
		int bearing360 = 0;

		if (myLocation != null && theirLocation != null)
		{
			dist = myLocation.distanceTo(theirLocation);
			dist = unitsPrefs.convertDistanceMeters(dist);
			bearing360 = get360BearingTo(myLocation, theirLocation);
		}
		final String fmtStr = ", %.1f" + prefs.getString(UnitsPrefs.KEY_DISTANCE_UNITS, context.getResources().getStringArray(R.array.distance_units)[0]) + ", ";
		return String.format(fmtStr, dist) + bearing360 + "°";
	}

	// TODO: write test. edge cases.
	private int get360BearingTo(Location myLocation, Location theirLocation)
	{
		int initialBearing = Math.round(myLocation.bearingTo(theirLocation));
		return initialBearing < 0 ? 360 + initialBearing : initialBearing;
	}

	private void startTransmitting()
	{
		sendLocationShout();
		handler.postDelayed(txRunnable, getEccentricTxPeriodMs());
	}

	private void stopTransmitting()
	{
		handler.removeCallbacks(txRunnable);
	}

	/**
	 * Calculates vertical speed if it can be calculated with reasonable accuracy. Latest fix must be less than MIN_LOC_TIME_INTV_MS old.
	 * May use a time period between VERT_SPD_AVG_PERIOD_MS - MIN_LOC_TIME_INTV_MS and VERT_SPD_AVG_PERIOD_MS + MIN_LOC_TIME_INTV_MS (30 +/- 5 seconds).
	 * @return Average vertical speed or IAircraftMessage.VSPEED_NONE to indicate failure.
	 */
	public float calcVertSpeedAvgMetersPerSec(final long nowNanos)
	{
		float vertSpeedAvgMeters;
		Location bestLoc = null;

		synchronized (locations)
		{
			if (locations.size() == 0) return IAircraftMessage.VSPEED_NONE;

			Location latestLocation = locations.get(locations.size() - 1);
			if (latestLocation == null ||
					latestLocation.getAccuracy() > MIN_ACCURACY_FOR_VERT_SPD ||
					nowNanos - latestLocation.getElapsedRealtimeNanos() >= MIN_LOC_TIME_INTV_MS * NumberUtils.NANOS_PER_MILLI)
			{
				//            log.debug("unable to calculate vert spd");
				return IAircraftMessage.VSPEED_NONE;
			}

			// find a location in the circular buffer that is around VERT_SPD_AVG_PERIOD_MS old, plus or minus MIN_LOC_TIME_INTV_MS
			Iterator<Location> iterator = locations.iterator();
			while (iterator.hasNext())
			{
				final Location loc = iterator.next();
				final long timeDiffNanos = latestLocation.getElapsedRealtimeNanos() - loc.getElapsedRealtimeNanos();
				final long locAgeMs = timeDiffNanos / NumberUtils.NANOS_PER_MILLI;
				if (locAgeMs <= VERT_SPD_AVG_PERIOD_MS + MIN_LOC_TIME_INTV_MS &&
						locAgeMs >= VERT_SPD_AVG_PERIOD_MS - MIN_LOC_TIME_INTV_MS &&
						loc.getAccuracy() < MIN_ACCURACY_FOR_VERT_SPD) // plus or minus one location update period
				{
					if (bestLoc == null)
					{
						bestLoc = loc;
					}
					else
					{
						final long bestTimeDiff = calcTimeDiffFromPeriod(latestLocation.getElapsedRealtimeNanos(), bestLoc.getElapsedRealtimeNanos());
						final long thisTimeDiff = calcTimeDiffFromPeriod(latestLocation.getElapsedRealtimeNanos(), loc.getElapsedRealtimeNanos());
						if (thisTimeDiff < bestTimeDiff) bestLoc = loc;
					}
				}
			}

			if (bestLoc == null)
			{
//            log.debug("failed to find location of the right age");
				return IAircraftMessage.VSPEED_NONE;
			}

			final long locAgeNanos = latestLocation.getElapsedRealtimeNanos() - bestLoc.getElapsedRealtimeNanos();
			final long locAgeMs = locAgeNanos / NumberUtils.NANOS_PER_MILLI;
			final long timePeriodSecs = locAgeMs / NumberUtils.MILLIS_PER_SEC;
			final float altDiff = (float)(latestLocation.getAltitude() - bestLoc.getAltitude());
			vertSpeedAvgMeters = altDiff / timePeriodSecs; // meters per second
		}

//        log.trace("vario: " + String.format("%.1fm/s", vertSpeedAvgMeters) + " period: " + timePeriodSecs + "s alt_diff: " + String.format("%.0fm", altDiff));
		return vertSpeedAvgMeters;
	}

	/**
	 * @return The difference between the period spanned between the two arguments,
	 * and the VERT_SPD_AVG_PERIOD_MS.
	 */
	private long calcTimeDiffFromPeriod(long firstNanos, long secondNanos)
	{
		final long timeDiffNanos = firstNanos - secondNanos;
		final float timeDiffMillis = timeDiffNanos / NumberUtils.NANOS_PER_MILLI;
		return Math.abs(Math.round(VERT_SPD_AVG_PERIOD_MS - timeDiffMillis));
	}

	private int getEccentricTxPeriodMs()
	{
		// add some eccentricity to the transmission period to reduce the chance of repeatedly colliding with another on the same rhythm
		int additionalRandomDelay = new Random().nextInt(50) * 10; // random number between 0-500 in steps of 10. this gives 50 different delays possible.
		int result = TX_FREQ_MS + additionalRandomDelay;
//		log.trace("getEccentricTxPeriodMs = " + result + ", " + additionalRandomDelay);
		return result;
	}

	private void cullOldGliders()
	{
		ArrayList<Long> toCull = new ArrayList<>();
		for (Aircraft aircraft : aircrafts.values())
		{
			long ageMs = System.currentTimeMillis() + gpsTimeOffsetMs.get() - aircraft.getLastMsg().getGpsFixTimeMs();
			if (ageMs >= CULL_AFTER_MS) toCull.add(aircraft.getAircraftID());
		}
		if (toCull.size() > 0)
		{
			for (Long id : toCull)
			{
				aircrafts.remove(id);
				log.info("Culled " + String.valueOf(id));
			}
		}
	}
	//endregion

	// ================================================================================
	// region - Gotenna stuff
	// ================================================================================

	private boolean sendLocationShout()
	{
		//TODO: what about other gliders?
		if (lastLocation == null)
		{
			log.debug("Current location unknown. Can't send broadcast message.");
			return false;
		}
		//TODO: send meshed messages without own location
//		long elapsedNanos = SystemClock.elapsedRealtimeNanos() - lastLocation.getElapsedRealtimeNanos();
//		if (elapsedNanos >=  MAX_LOC_AGE_SECS * 1e6)
//		{
//			log.debug("Location too old. Not sending message.");
//			return false;
//		}

		if (gtMan.getState() == GotennaManager.State.CONNECTED)
		{
//			log.trace("Send: " + getLocationDetailsStr(lastLocation, "0") + " Vario: " + locMsg.getVertSpeedAvg());

			MultiLocationMsg multiMsg = buildMultiMsg(lastLocation); // if no other aircraft, multi looks same as single to recipient
			if(multiMsg == null) return false;
//			log.debug("MultiLocationMsg size " + multiMsg.serializeToBytes().length);
			gtMan.transmit(multiMsg.serializeToBytes());

			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_MSG_SENT));

			return true;
		}
		else
		{
			Toast.makeText(context, R.string.gotenna_disconnected, Toast.LENGTH_LONG).show();
			log.debug("Failed to send broadcast message. XCVR is disconnected.");
			return false;
		}
	}

	@Nullable
	private MultiLocationMsg buildMultiMsg(Location location)
	{
		MultiLocationMsg multiMsg = null;

		try
		{
			multiMsg = new MultiLocationMsg(
					location.getLatitude(),
					location.getLongitude(),
					location.hasAltitude() ? location.getAltitude() : LocationMessage.ALT_NONE,
					location.hasBearing() ? location.getBearing() : LocationMessage.BEARING_NONE,
					location.hasSpeed() ? location.getSpeed() : LocationMessage.GSPEED_NONE,
					calcVertSpeedAvgMetersPerSec(SystemClock.elapsedRealtimeNanos()),
					location.hasAccuracy()? location.getAccuracy() : LocationMessage.ACCURACY_NONE,
					location.getTime()
			);

			ArrayList<IAircraftMessage> closestAircraft = getClosestAircraft(3);
			for(IAircraftMessage msg : closestAircraft)
			{
				if(msg instanceof LocationMessage)
				{
					multiMsg.addLocationMsg((LocationMessage) msg);
				}
				else
				{
					log.warn("Last message not an instance of LocationMessage.");
				}
			}
		}
		catch(GTDataMissingException e)
		{
			log.error(e.getLocalizedMessage());
		}
		return multiMsg;
	}

	private ArrayList<IAircraftMessage> getClosestAircraft(final int max)
	{
		// make a copy of lastLocation so it doesn't get updated while we're working
		Location myLocation = new Location(lastLocation);

		// sort aircraft by distance from us
		TreeMap<Float, IAircraftMessage> sorted = new TreeMap<>();
		for(Aircraft aircraft : getAircraft().values())
		{
			sorted.put(myLocation.distanceTo(aircraft.getLastMsg().getLocation()), aircraft.getLastMsg());
		}

		// build a list of the three closest that have non-expired locations
		ArrayList<IAircraftMessage> result = new ArrayList<>(max);
		Iterator<Map.Entry<Float, IAircraftMessage>> iterator = sorted.entrySet().iterator();
		int remaining = max;
		long now = System.currentTimeMillis();
		while (remaining > 0 && iterator.hasNext())
		{
			Map.Entry<Float, IAircraftMessage> entry = iterator.next();
			long age = now - entry.getValue().getGpsFixTimeMs();
			if (age < LOC_EXPIRED_MS)
			{
				result.add(entry.getValue());
				remaining--;
			}
		}

		// if there is any room left, add closest expired locations
		iterator = sorted.entrySet().iterator();
		while (remaining > 0 && iterator.hasNext())
		{
			Map.Entry<Float, IAircraftMessage> entry = iterator.next();
			long age = now - entry.getValue().getGpsFixTimeMs();
			if (age >= LOC_EXPIRED_MS)
			{
				result.add(entry.getValue());
				remaining--;
			}
		}
		if (result.size() > max) log.error("More than max aircraft returned.");
		return result;
	}

	// ================================================================================
	// region - GotennaContract.Delegate Implementation
	// ================================================================================

	@Override
	public void onGotennaStateChange (GotennaManager.State state)
	{
		if (state == GotennaManager.State.CONNECTED)
		{
			changeState(State.GROUND);
		}
		else if (state == GotennaManager.State.STOPPED)
		{
			if (!this.state.equals(Tracker.State.STOPPED))
			{
				changeState(State.SEARCH_XCVR);
			}
		}
	}

	@Override
	public void onGotennaMessageReceived (IAircraftMessage msg)
	{
		onAircraftMessage(msg);
	}

	//endregion

	//endregion goTenna Stuff
}
