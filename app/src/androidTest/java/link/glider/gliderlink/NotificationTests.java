package link.glider.gliderlink;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.test.ApplicationTestCase;

import link.glider.gliderlink.messaging.LocationMessage;
import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;
import com.gotenna.sdk.encryption.EncryptionInfoHeader;
import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.messages.GTMessageData;
import com.gotenna.sdk.responses.GTResponse;
import com.gotenna.sdk.user.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osmdroid.util.GeoPoint;

import java.util.Calendar;

import static android.content.Context.MODE_PRIVATE;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTests extends ApplicationTestCase<Application>
{
	private static final GeoPoint CVH = new GeoPoint(36.8916666666667, -121.408333333333);
	private static final GeoPoint HOME = new GeoPoint(37.74760673, -122.43260524);
	private static final long BROADCAST_GID = 1111111111L;

	private FakeTrackerDelegate delegate;
	private LocationManager locationMan;
	private SharedPreferences prefs;
	private SharedPreferences stats;
	private Tracker tracker;
	private GotennaManager gtMan;
	private NoteCenter noteCenter;

	public NotificationTests()
	{
		super(Application.class);
	}

	@Override
	public void setUp() throws Exception
	{
		super.setUp();
		createApplication();
		delegate = spy(FakeTrackerDelegate.class);
		locationMan = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		stats = getContext().getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);
		gtMan = new GotennaManager(getContext(), "7", 123245, GTConnectionManager.getInstance(), GTCommandCenter.getInstance());
		noteCenter = mock(NoteCenter.class);
		try
		{
			tracker = new Tracker(getContext(), delegate, locationMan, gtMan, prefs, stats, noteCenter);
			Tracker.getAircraft().clear();
		}
		catch(IllegalArgumentException e)
		{
			assertNull(e);
		}
	}

	private void sendMessage(String callsign, long gid, double lat, double lon, double alt, float vertSpd)
	{
		User user = User.createUser(callsign, gid);
		try
		{
			LocationMessage msg = new LocationMessage(user, lat, lon, alt, LocationMessage.BEARING_NONE, LocationMessage.GSPEED_NONE, vertSpd, 0, System.currentTimeMillis());
			GTMessageData msgData = new GTMessageData(msg.serializeToBytes(), BROADCAST_GID, new EncryptionInfoHeader(false, msg.getSenderGID(), Short.MAX_VALUE), new GTResponse(msg.serializeToBytes(), null));
			gtMan.onIncomingMessage(msgData);
		}
		catch(GTDataMissingException e)
		{
			assertNull(e);
		}
	}

	private void sendMessage(String callsign, long gid, double alt, float vertSpd)
	{
		sendMessage(callsign, gid, CVH.getLatitude(), CVH.getLongitude(), alt, vertSpd);
	}

	private void sendLocation(double lat, double lon, double alt)
	{
		sendLocation(lat, lon, alt, 100, SystemClock.elapsedRealtimeNanos());
	}

	private void sendLocation(double lat, double lon, double alt, float speed, long timeNanos)
	{
		Location loc = new Location(LocationManager.GPS_PROVIDER);
		loc.setLatitude(lat);
		loc.setLongitude(lon);
		loc.setAltitude(alt);
		loc.setSpeed(speed);
		loc.setAccuracy(1);
		loc.setElapsedRealtimeNanos(timeNanos);
		tracker.onLocationChanged(loc);
	}

	@Test
	public void testNewContact()
	{
		Long gid = 111L;
		long now = System.currentTimeMillis();
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000);
		sendMessage("TST", gid, 0, 0);
		Mockito.verify(noteCenter).showNotification(isA(String.class));
		sendMessage("TST", gid, 0, 0);
		Aircraft aircraft = Tracker.getAircraft().get(gid);
		assertEquals(now, aircraft.getLastNotificationTime(NotificationType.NEW_CONTACT), 100);
	}

	@Test
	public void testClimbing()
	{
		Long gid = 111L;
		long now = System.currentTimeMillis();
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000);
		sendMessage("TST", gid, 0, -1);
		sendMessage("TST", gid, 100, 1);
		Mockito.verify(noteCenter, times(2)).showNotification(isA(String.class)); // new contact and climbing
		Aircraft aircraft = Tracker.getAircraft().get(gid);
		assertEquals(now, aircraft.getLastNotificationTime(NotificationType.CLIMBING), 100);
		sendMessage("TST", gid, 100, 1);
	}

	@Test
	public void testClimbingOnFirstContact()
	{
		Long gid = 111L;
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000);
		sendMessage("TST", gid, 100, 1);
		Mockito.verify(noteCenter, times(2)).showNotification(isA(String.class));
	}

	@Test
	public void testAircraftClose()
	{
		Long gid1 = 111L;
		long now = System.currentTimeMillis();
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000);
		sendMessage("TS1", gid1, HOME.getLatitude(), HOME.getLongitude(), 1000, -1); // far away
		sendMessage("TS1", gid1, 1000, -1); // close
		Mockito.verify(noteCenter).showNotification(isA(String.class)); // new contact
		Mockito.verify(noteCenter).showNotification(isA(String.class), anyInt()); // close note
		Aircraft aircraft = Tracker.getAircraft().get(gid1);
		assertEquals(now, aircraft.getLastNotificationTime(NotificationType.AIRCRAFT_CLOSE), 100);
		sendMessage("TS1", gid1, HOME.getLatitude(), HOME.getLongitude(), 1000, -1); // far away
		sendMessage("TS1", gid1, 1000, -1); // close again
	}

	@Test
	public void testAircraftCloseOnFirstContact()
	{
		Long gid1 = 111L;
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000);
		sendMessage("TS1", gid1, 1000, -1); // close
		Mockito.verify(noteCenter).showNotification(isA(String.class)); // new contact
		Mockito.verify(noteCenter).showNotification(isA(String.class), anyInt()); // close note
	}

	@Test
	public void testTakeOffAndLanding()
	{
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000, 100, SystemClock.elapsedRealtimeNanos() - Tracker.MIN_TIME_STATIONARY_NANOS * 2);
		assertTrue(Tracker.getIsAirBorne());
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000, 0, SystemClock.elapsedRealtimeNanos() - Tracker.MIN_TIME_STATIONARY_NANOS);
		Tracker.checkIfStationaryForLong();
		assertFalse(Tracker.getIsAirBorne());
	}

	@Test
	public void testNoLandingHighAlt()
	{
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 3100, 100, SystemClock.elapsedRealtimeNanos() - Tracker.MIN_TIME_STATIONARY_NANOS * 2);
		assertTrue(Tracker.getIsAirBorne());
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 3100, 0, SystemClock.elapsedRealtimeNanos() - Tracker.MIN_TIME_STATIONARY_NANOS);
		Tracker.checkIfStationaryForLong();
		assertTrue(Tracker.getIsAirBorne());
	}

	@Test
	public void testNotLandedYet()
	{
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000, 100, SystemClock.elapsedRealtimeNanos() - Tracker.MIN_TIME_STATIONARY_NANOS * 2);
		assertTrue(Tracker.getIsAirBorne());
		sendLocation(CVH.getLatitude(), CVH.getLongitude(), 1000, 0, SystemClock.elapsedRealtimeNanos());
		Tracker.checkIfStationaryForLong();
		assertTrue(Tracker.getIsAirBorne());
	}

	//TODO write test for goTenna disconnected
	//TODO write test for failed to find goTenna
}
