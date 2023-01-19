package link.glider.gliderlink;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import link.glider.gliderlink.messaging.LocationMessage;
import link.glider.gliderlink.utils.ui.AircraftOverlayItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
@RunWith(MockitoJUnitRunner.class)
public class GliderOverlayItemTests
{
	@Mock
	private Context context;
	@Mock
	private Resources resources;
	@Mock
	private DisplayMetrics metrics;
	@Mock
	private LocationMessage msg;

	@Before
	public void setUp()
	{
		when(context.getResources()).thenReturn(resources);
		when(resources.getDisplayMetrics()).thenReturn(metrics);
		metrics.density = 300f;
		MyApp.setAppContext(context);
	}

	@Test
	public void getAltStringPositive() throws Exception {
		when(msg.getAltitude()).thenReturn(33); // meters
		when(msg.hasAltitude()).thenReturn(true);
		AircraftOverlayItem overlay = new AircraftOverlayItem(msg);
		assertEquals("+1", overlay.getAltString(0));
	}

	@Test
	public void getAltStringZero() throws Exception {
		when(msg.getAltitude()).thenReturn(101); // meters
		when(msg.hasAltitude()).thenReturn(true);
		AircraftOverlayItem overlay = new AircraftOverlayItem(msg);
		assertEquals("0", overlay.getAltString(101));
	}

	@Test
	public void getAltStringNegative() throws Exception {
		when(msg.getAltitude()).thenReturn(0); // meters
		when(msg.hasAltitude()).thenReturn(true);
		AircraftOverlayItem overlay = new AircraftOverlayItem(msg);
		assertEquals("-1", overlay.getAltString(33));
	}

	@Test
	public void getVertSpdStringPositive() throws Exception
	{
		when(msg.getVertSpeedAvg()).thenReturn(1f);
		when(msg.hasVertSpeedAvg()).thenReturn(true);
		AircraftOverlayItem overlay = new AircraftOverlayItem(msg);
		assertEquals("+1.9", overlay.getVertSpdString());
	}

	@Test
	public void getVertSpdStringZero() throws Exception
	{
		when(msg.getVertSpeedAvg()).thenReturn(0f);
		when(msg.hasVertSpeedAvg()).thenReturn(true);
		AircraftOverlayItem overlay = new AircraftOverlayItem(msg);
		assertEquals("0", overlay.getVertSpdString());
	}

	@Test
	public void getVertSpdStringNegative() throws Exception
	{
		when(msg.getVertSpeedAvg()).thenReturn(-1f);
		when(msg.hasVertSpeedAvg()).thenReturn(true);
		AircraftOverlayItem overlay = new AircraftOverlayItem(msg);
		assertEquals("-1.9", overlay.getVertSpdString());
	}
}