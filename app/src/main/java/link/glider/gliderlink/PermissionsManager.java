package link.glider.gliderlink;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;

public class PermissionsManager
{
	// ================================================================================
	// Class Properties
	// ================================================================================

	private Context context;

	// ================================================================================
	// Singleton Methods
	// ================================================================================

	private PermissionsManager()
	{
		// Do nothing here
	}

	// ================================================================================
	// Context Methods
	// ================================================================================

	public void setContext(Context context)
	{
		this.context = context;
	}

	// ================================================================================
	// Permission Checking Methods
	// ================================================================================

	public boolean hasAllPermissions()
	{
		return hasLocationPermission() && hasContactsPermission() && hasWriteExternalStoragePermission();
	}

	private boolean hasLocationPermission()
	{
		return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
				ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private boolean hasContactsPermission()
	{
		return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
				ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
	}

	public boolean hasReadPhoneStatePermission()
	{
		return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
	}

	private boolean hasWriteExternalStoragePermission()
	{
		return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	// ================================================================================
	// Permission Requesting Methods
	// ================================================================================

	@TargetApi(Build.VERSION_CODES.M)
	public void requestLocationPermission(Activity activity, final int REQUEST_PERMISSION_CODE)
	{
		String[] permissionsToRequest = {
				Manifest.permission.ACCESS_COARSE_LOCATION,
				Manifest.permission.ACCESS_FINE_LOCATION

		};
		activity.requestPermissions(permissionsToRequest, REQUEST_PERMISSION_CODE);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public void requestContactsAndPhoneStatePermissions(Activity activity, final int REQUEST_PERMISSION_CODE)
	{
		String[] permissionsToRequest = {
				Manifest.permission.READ_CONTACTS,
				Manifest.permission.GET_ACCOUNTS,
				Manifest.permission.READ_PHONE_STATE
		};
		activity.requestPermissions(permissionsToRequest, REQUEST_PERMISSION_CODE);
	}

	@TargetApi(Build.VERSION_CODES.M)
	public void requestWriteExternalStoragePermission(Activity activity, final int REQUEST_PERMISSION_CODE)
	{
		String[] permissionsToRequest = {
				Manifest.permission.WRITE_EXTERNAL_STORAGE
		};
		activity.requestPermissions(permissionsToRequest, REQUEST_PERMISSION_CODE);
	}

	// ================================================================================
	// Helper Methods
	// ================================================================================

	public static boolean allPermissionsWereGranted(int[] grantResults)
	{
		for (int grantResult : grantResults)
		{
			if (grantResult != PackageManager.PERMISSION_GRANTED)
			{
				return false;
			}
		}

		return true;
	}

}
