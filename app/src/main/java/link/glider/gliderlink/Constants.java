package link.glider.gliderlink;

import android.Manifest;

import org.osmdroid.util.GeoPoint;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

final public class Constants
{
	public static final GeoPoint CVH = new GeoPoint(36.8916666666667, -121.408333333333, 1000);
	public static final String[] PERMISSIONS = {
			Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION,
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.ACCESS_WIFI_STATE,
			Manifest.permission.ACCESS_NETWORK_STATE,
			Manifest.permission.BLUETOOTH,
			Manifest.permission.BLUETOOTH_ADMIN,
			Manifest.permission.INTERNET
	};
	public enum Units
	{
		unknown, nm, km, sm, ft, m, kmh, kts, mph, fpm, mps
	}
	public static final String STATS_PREFS_FILE_NAME = BuildConfig.APPLICATION_ID + ".stats";
	public static final DateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	public static final DateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
	public static final String SUPPORT_EMAIL = "info@glider.link";
	public static final String ACTION_TARGET_POSITION = BuildConfig.APPLICATION_ID + ".target_position";
	public static final String[] BCAST_PACKAGES = { "org.xcsoar", "org.tophat", "org.xcsoar.testing", "org.tophat.testing" };
}
