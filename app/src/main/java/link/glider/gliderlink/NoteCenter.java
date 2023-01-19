package link.glider.gliderlink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.support.v4.app.NotificationCompat.DEFAULT_ALL;
import static android.support.v4.app.NotificationCompat.PRIORITY_HIGH;

public class NoteCenter
{
	private static final int NOTE_LANDING = 938238;
	private static final String NOTIFICATION_CHANNEL = "inflight_channel";
	private static final String DEFAULT_PRIORITY_CHANNEL = "defaul_priority_channel";

	private final Context context;

	public NoteCenter(Context context)
	{
		this.context = context;
		NotificationManager noteMan = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			noteMan.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH));
		}
	}

	public void showNotification(final String msg)
	{
		showNotification(msg, android.R.drawable.ic_dialog_info);
	}

	public void showNotification(final String msg, final int iconID)
	{
		NotificationManager noteMan = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		noteMan.notify(0, buildNotification(msg, iconID));
	}

	public void showLandingNotification()
	{
		NotificationManager noteMan = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		noteMan.notify(NOTE_LANDING, buildLandingNotification());
	}

	public Notification buildForegroundNotification(final String text)
	{
		final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			NotificationManager noteMan = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
			NotificationChannel mChannel = new NotificationChannel(
					DEFAULT_PRIORITY_CHANNEL, DEFAULT_PRIORITY_CHANNEL, NotificationManager.IMPORTANCE_DEFAULT);
			noteMan.createNotificationChannel(mChannel);
		}
		final NotificationCompat.Builder b = new NotificationCompat.Builder(context, DEFAULT_PRIORITY_CHANNEL);
		b.setOngoing(true);
		b.setContentTitle(context.getString(R.string.app_name));
		b.setContentText(text);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) // TODO make png for older API levels
		{
			b.setSmallIcon(R.drawable.ic_cockpit_circle_white_24dp);
		}
		b.setContentIntent(pendingIntent);

		return b.build();
	}

	private Notification buildNotification(final String text, final int iconID)
	{
		final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);
		final NotificationCompat.Builder b = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL);
		b.setDefaults(DEFAULT_ALL);
		b.setContentTitle(context.getString(R.string.app_name));
		b.setContentText(text);
		b.setSmallIcon(iconID);
		b.setPriority(PRIORITY_HIGH);
		b.setContentIntent(pendingIntent);
		b.setAutoCancel(true);
		return b.build();
	}

	private Notification buildLandingNotification()
	{
		final PendingIntent pendIntRetrieve = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);
		final PendingIntent pendIntCancel = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);

		final NotificationCompat.Builder b = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL);
		b.setDefaults(DEFAULT_ALL);
		b.setContentTitle(context.getString(R.string.app_name));
		b.setContentText(context.getString(R.string.note_landing_text));
		b.setSmallIcon(android.R.drawable.ic_menu_help);
		b.setPriority(PRIORITY_HIGH);
		b.setAutoCancel(true);
		b.addAction(android.R.drawable.ic_delete, context.getString(R.string.cancel), pendIntRetrieve);
		b.addAction(android.R.drawable.ic_dialog_map, context.getString(R.string.note_landing_retrieve), pendIntCancel);
		return b.build();
	}
}
