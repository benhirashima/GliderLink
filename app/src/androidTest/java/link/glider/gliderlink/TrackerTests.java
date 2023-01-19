package link.glider.gliderlink;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.test.ApplicationTestCase;

import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;

import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static android.content.Context.MODE_PRIVATE;
import static org.mockito.Mockito.mock;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
@SuppressWarnings("UnusedAssignment")
@RunWith(MockitoJUnitRunner.class)
public class TrackerTests extends ApplicationTestCase<Application>
{
	private FakeTrackerDelegate delegate;
	private LocationManager locationMan;
	private GotennaManager gtMan;
	private SharedPreferences prefs;
	private SharedPreferences stats;
	private NoteCenter noteCenter;

	public TrackerTests ()
	{
		super(Application.class);
	}

	@Override
	public void setUp () throws Exception
	{
		super.setUp();
		createApplication();
		delegate = new FakeTrackerDelegate();
		locationMan = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
		gtMan = new GotennaManager(getContext(), "7", 123245, GTConnectionManager.getInstance(), GTCommandCenter.getInstance());
		prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		stats = getContext().getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);
		noteCenter = mock(NoteCenter.class);
	}

	public void testConstructor ()
	{
		Tracker tracker;
		try
		{
			tracker = new Tracker(getContext(), delegate, locationMan, gtMan, prefs, stats, noteCenter);
		}
		catch (IllegalArgumentException e)
		{
			assertNull(e);
			return;
		}
		assertNotNull(tracker);
	}
}