package link.glider.gliderlink.utils;

import android.content.pm.PackageManager;

public final class PlatformUtils
{
	public static boolean isPackageInstalled(String packageName, PackageManager packageManager)
	{
		try
		{
			return packageManager.getApplicationInfo(packageName, 0).enabled; // checks if enabled too
		}
		catch(PackageManager.NameNotFoundException e)
		{
			return false;
		}
	}
}
