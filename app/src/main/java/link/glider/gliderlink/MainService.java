package link.glider.gliderlink;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import link.glider.gliderlink.messaging.IAircraftMessage;
import link.glider.gliderlink.utils.PlatformUtils;

public class MainService extends Service implements TrackerContract.TrackerDelegate
{
	public static final String ACTION_PING = MainService.class.getName() + ".PING";
	public static final String ACTION_PONG = MainService.class.getName() + ".PONG";
	public static final String ACTION_SEND_LOCATION = MainService.class.getName() + ".ACTION_SEND_LOCATION";
	public static final String ACTION_SELF_STOP = MainService.class.getName() + ".SELF_STOP";
	public static final String EXTRA_DEMO_MODE = MainService.class.getName() + ".DEMO_MODE";

	private static final int FOREGROUND_ID = 96790;
	private static final Logger log = LoggerFactory.getLogger(MainService.class);
	private final HashMap<String, Boolean> bcastPackages = new HashMap<>();

	private Tracker tracker;
	private NoteCenter noteCenter;
	private GotennaManager gtMan;

	public MainService ()
	{
	}

	@Override
	public IBinder onBind (Intent intent)
	{
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onCreate ()
	{
		super.onCreate();
		LocationManager locationMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences stats = getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);
		noteCenter = new NoteCenter(getApplicationContext());
		gtMan = GotennaManager.getSharedInstance();
		gtMan.setGotennaGID(prefs.getLong(getString(R.string.pref_key_gotenna_gid), 0), prefs.getString(getString(R.string.pref_key_callsign), ""));

		try
		{
			tracker = new Tracker(getApplicationContext(), this, locationMan, gtMan, prefs, stats, noteCenter);
		}
		catch(IllegalArgumentException e)
		{
			log.error(e.getLocalizedMessage());
		}
	}

	@Override
	public int onStartCommand (Intent intent, int flags, int startId)
	{
		log.debug("onStartCommand()");

		if (tracker == null) stopSelf();

		startForeground(FOREGROUND_ID, noteCenter.buildForegroundNotification("Running..."));

		IntentFilter filter = new IntentFilter(ACTION_PING);
		filter.addAction(ACTION_SEND_LOCATION);
		LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);

		if (intent != null && intent.getBooleanExtra(EXTRA_DEMO_MODE, false))
		{
			tracker.startDemo();
		}
		else
		{
			tracker.start();
		}

		checkInstalledPackages(Constants.BCAST_PACKAGES);

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy ()
	{
		log.debug("onDestroy()");
		tracker.stop();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
		super.onDestroy();
	}

	@Override
	public void onLowMemory()
	{
		log.debug("onLowMemory()");
		super.onLowMemory();
	}

	@Override
	public void onTrimMemory(int level)
	{
		log.debug("onTrimMemory()");
		super.onTrimMemory(level);
	}

	// ================================================================================
	// region - TrackerContract.TrackerDelegate
	// ================================================================================

	@Override
	public void onTrackerStateChange(Tracker.State state)
	{
		if (state == Tracker.State.STOPPED)
		{
			LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(ACTION_SELF_STOP));
			NotificationManager noteMan = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			try
			{
				if (noteMan != null) noteMan.cancelAll();
			}
			catch(Exception e)
			{
				log.error(e.getLocalizedMessage());
			}
			stopSelf();
		}
	}

	@Override
	public void onMessageReceived(IAircraftMessage msg)
	{
		for (Map.Entry<String, Boolean> pkg : bcastPackages.entrySet())
		{
			if (pkg.getValue())
			{
				Intent intent = new Intent(Constants.ACTION_TARGET_POSITION);
				intent.setPackage(pkg.getKey());
				intent.putExtra("json", msg.toJson());
				sendBroadcast(intent);
			}
		}
	}


	private void checkInstalledPackages(String[] packageNames)
	{
		for (String pkg : Constants.BCAST_PACKAGES)
		{
			bcastPackages.put(pkg, PlatformUtils.isPackageInstalled(pkg, getPackageManager()));
		}
	}

	// ================================================================================
	// region - BroadcastReceiver
	// ================================================================================

	private final BroadcastReceiver mReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive (Context context, Intent intent)
		{
			if (intent.getAction() == null) return;
			if (intent.getAction().equals(ACTION_PING))
			{
				LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(ACTION_PONG));
			}
		}
	};
	//endregion
}
