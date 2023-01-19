package link.glider.gliderlink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.widget.Toast;

import com.gotenna.sdk.GoTenna;
import com.gotenna.sdk.exceptions.GTInvalidAppTokenException;

import org.osmdroid.config.Configuration;

import java.util.Random;

/**
 * Created by bhirashi on 10/16/16.
 */
public class MyApp extends MultiDexApplication
{
	private static final String LOG_TAG = MyApp.class.getSimpleName();
	private static final String GOTENNA_APP_TOKEN = "REDACTED";
	private static Context applicationContext;
	private static boolean tokenIsValid = true;

	@Override
	public void onCreate()
	{
		super.onCreate();

//        OpenStreetMapTileProviderConstants.DEBUG_TILE_PROVIDERS=true;
//        OpenStreetMapTileProviderConstants.DEBUGMODE=true;

		try
		{
			MyApp.applicationContext = getApplicationContext();
			GoTenna.setApplicationToken(getApplicationContext(), GOTENNA_APP_TOKEN);
		}
		catch(GTInvalidAppTokenException e)
		{
			// Normally, this will never happen
			Log.w(LOG_TAG, e);
			tokenIsValid = false;
			Toast.makeText(getApplicationContext(), "Your goTenna App Token was Invalid.", Toast.LENGTH_LONG).show();
		}

		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

		Configuration.getInstance().setOsmdroidBasePath(this.getExternalFilesDir("osmdroid"));

		verifyPermissions();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String gidKey = getString(R.string.pref_key_gotenna_gid);
		if (!prefs.contains(gidKey))
		{
			prefs.edit().putLong(gidKey, new Random().nextLong()).apply();
			Log.i(LOG_TAG, "Generated goTenna GID");
		}
	}

	public static Context getAppContext()
	{
		return applicationContext;
	}

	@VisibleForTesting(otherwise = VisibleForTesting.NONE)
	public static void setAppContext(Context context)
	{
		applicationContext = context;
	}

	public static boolean tokenIsValid()
	{
		return tokenIsValid;
	}

	@Override
	protected void attachBaseContext(Context base)
	{
		super.attachBaseContext(base);
		MultiDex.install(this);
	}

	private void verifyPermissions()
	{
		boolean allGranted = true;
		for (String key : Constants.PERMISSIONS)
		{
			int permission = ActivityCompat.checkSelfPermission(this, key);
			if (permission != PackageManager.PERMISSION_GRANTED)
			{
				allGranted = false;
				startIntroActivity();
				break;
			}
		}
		if (allGranted) startMainActivity();
	}

	private void startIntroActivity()
	{
		Intent i = new Intent(this, IntroActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
	}

	private void startMainActivity()
	{
		Intent i = new Intent(this, MainActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
	}
}