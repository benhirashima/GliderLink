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
import link.glider.gliderlink.utils.NumberUtils;
import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static android.content.Context.MODE_PRIVATE;
import static org.mockito.Mockito.mock;

/**
 * Created by Ben on 1/8/2017.
 */

@RunWith(MockitoJUnitRunner.class)
public class VertSpeedTests extends ApplicationTestCase<Application>
{
	private FakeTrackerDelegate delegate;
	private LocationManager locationMan;
	private SharedPreferences prefs;
	private SharedPreferences stats;
	private Tracker tracker;
	private NoteCenter noteCenter;
	private GotennaManager gtMan;

	public VertSpeedTests()
	{
		super(Application.class);
	}

	@Override
	public void setUp() throws Exception
	{
		super.setUp();
		createApplication();
		delegate = new FakeTrackerDelegate();
		locationMan = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		stats = getContext().getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);
		noteCenter = mock(NoteCenter.class);
		gtMan = new GotennaManager(getContext(), "7", 123245, GTConnectionManager.getInstance(), GTCommandCenter.getInstance());

		try
		{
			tracker = new Tracker(getContext(), delegate, locationMan, gtMan, prefs, stats, noteCenter);
		}
		catch(IllegalArgumentException e)
		{
			assertNull(e);
		}
	}

	private void sendLocation(double alt, long time)
	{
		Location loc = new Location(LocationManager.GPS_PROVIDER);
		loc.setAltitude(alt);
		loc.setAccuracy(1);
		loc.setElapsedRealtimeNanos(time);
		tracker.onLocationChanged(loc);
	}

	@Test
	public void testPositive()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long oldestNanos = nowNanos - Tracker.VERT_SPD_AVG_PERIOD_MS * NumberUtils.NANOS_PER_MILLI;

		sendLocation(0, oldestNanos - NumberUtils.NANOS_PER_SEC);
		sendLocation(0, oldestNanos); // exactly VERT_SPD_AVG_PERIOD_MS old. should be selected over nearby ones.
		sendLocation(5, oldestNanos + NumberUtils.NANOS_PER_SEC);
		sendLocation(30, nowNanos);

		assertEquals(1.0, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testNegative()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long oldestNanos = nowNanos - Tracker.VERT_SPD_AVG_PERIOD_MS * NumberUtils.NANOS_PER_MILLI;

		sendLocation(35, oldestNanos - NumberUtils.NANOS_PER_SEC);
		sendLocation(30, oldestNanos); // exactly VERT_SPD_AVG_PERIOD_MS old. should be selected over nearby ones.
		sendLocation(25, oldestNanos + NumberUtils.NANOS_PER_SEC);
		sendLocation(0, nowNanos);

		assertEquals(-1.0, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testLatestAgeLimit()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos() - Tracker.MIN_LOC_TIME_INTV_MS;
		long latestNanos = nowNanos - Tracker.MIN_LOC_TIME_INTV_MS;
		long oldestNanos = latestNanos - Tracker.VERT_SPD_AVG_PERIOD_MS * NumberUtils.NANOS_PER_MILLI;

		sendLocation(30, oldestNanos);
		sendLocation(0, latestNanos);

		assertEquals(-1.0, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testLatestAgeOverLimit()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long latestNanos = nowNanos - Tracker.MIN_LOC_TIME_INTV_MS * NumberUtils.NANOS_PER_MILLI; // too late
		long oldestNanos = latestNanos - Tracker.VERT_SPD_AVG_PERIOD_MS * NumberUtils.NANOS_PER_MILLI;

		sendLocation(30, oldestNanos);
		sendLocation(0, latestNanos);

		assertEquals(LocationMessage.VSPEED_NONE, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testMaxPeriod()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long oldestNanos = nowNanos - (Tracker.VERT_SPD_AVG_PERIOD_MS + Tracker.MIN_LOC_TIME_INTV_MS) * NumberUtils.NANOS_PER_MILLI;

		sendLocation(0, oldestNanos);
		sendLocation(35, nowNanos);

		assertEquals(1.0, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testMinPeriod()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long oldestNanos = nowNanos - (Tracker.VERT_SPD_AVG_PERIOD_MS - Tracker.MIN_LOC_TIME_INTV_MS) * NumberUtils.NANOS_PER_MILLI;

		sendLocation(0, oldestNanos);
		sendLocation(25, nowNanos);

		assertEquals(1.0, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testMaxPeriodExceeded()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long oldestNanos = nowNanos - (Tracker.VERT_SPD_AVG_PERIOD_MS + Tracker.MIN_LOC_TIME_INTV_MS + 1000) * NumberUtils.NANOS_PER_MILLI;

		sendLocation(0, oldestNanos);
		sendLocation(30, nowNanos);

		assertEquals(LocationMessage.VSPEED_NONE, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}

	@Test
	public void testMinPeriodExceeded()
	{
		long nowNanos = SystemClock.elapsedRealtimeNanos();
		long oldestNanos = nowNanos - (Tracker.VERT_SPD_AVG_PERIOD_MS - Tracker.MIN_LOC_TIME_INTV_MS - 1000) * NumberUtils.NANOS_PER_MILLI;

		sendLocation(0, oldestNanos);
		sendLocation(30, nowNanos);

		assertEquals(LocationMessage.VSPEED_NONE, tracker.calcVertSpeedAvgMetersPerSec(nowNanos), 0.001f);
	}
}
