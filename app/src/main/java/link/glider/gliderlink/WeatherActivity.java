package link.glider.gliderlink;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.TimeZone;

public class WeatherActivity extends UnlockedActivity
{
	private static final Logger log = LoggerFactory.getLogger(WeatherActivity.class);
	private FloatingActionButton fab;
	private ProgressDialog progressDialog;
	private AsyncTask<String, Integer, Void> downloadTask;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_weather);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		toolbar.setNavigationOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick (View v)
			{
				onBackPressed();
			}
		});

		fab = findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				downloadWeather();
			}
		});

		progressDialog = new ProgressDialog(WeatherActivity.this);
		progressDialog.setMessage(getString(R.string.downloading_weather));
		progressDialog.setIndeterminate(false);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setCancelable(true);
		progressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialogInterface) {
				// Cancel the AsyncTask
				downloadTask.cancel(false);
			}
		});
	}

	private void downloadWeather()
	{
		String sdcardState = Environment.getExternalStorageState();
		if (sdcardState.equals(Environment.MEDIA_MOUNTED))
		{
			downloadTask = new DownloadFilesTask(this);
			downloadTask.execute(
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1100lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1200lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1300lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1400lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1500lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1600lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1700lst.d2.body.png",
					"https://rasp.nfshost.com/hollister/OUT+0/FCST/wblmaxmin.curr.1800lst.d2.body.png"
			);
		}
		else
		{
			Toast.makeText(this, R.string.download_error_sdcard, Toast.LENGTH_LONG).show();
		}
	}

	private static class DownloadFilesTask extends AsyncTask<String, Integer, Void>
	{
		private WeakReference<WeatherActivity> activityWeakReference;
		int failed = 0;

		DownloadFilesTask(WeatherActivity activity)
		{
			activityWeakReference = new WeakReference<>(activity);
		}

		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			WeatherActivity activity = activityWeakReference.get();
			activity.progressDialog.show();
			activity.progressDialog.setProgress(0);
		}

		@Override
		protected Void doInBackground(String... urls)
		{
			WeatherActivity activity = activityWeakReference.get();

			int count;
			int downloaded = 0;
			activity.progressDialog.setMax(urls.length);

			File raspDir = activity.getExternalFilesDir("rasp");

			for (int i = 0; i < urls.length; i++)
			{
				if (isCancelled()) return null;
				try
				{
					URL url = new URL(urls[i]);

					URLConnection connection = url.openConnection();
					connection.connect();
					// input stream to read file - with 8k buffer
					InputStream input = new BufferedInputStream(url.openStream(), 8192);
					String writePath = raspDir.getAbsolutePath() + "/" + url.getFile().substring(url.getFile().lastIndexOf('/') + 1);
					log.debug("Writing to " + writePath);
					// Output stream to write file
					OutputStream output = new FileOutputStream(writePath);
					byte data[] = new byte[1024];
					long total = 0;
					while((count = input.read(data)) != -1)
					{
						total += count;
						output.write(data, 0, count);
					}
					output.flush();
					output.close();
					input.close();

					downloaded++;
					publishProgress(downloaded);
				}
				catch(Exception e)
				{
					log.error(e.getLocalizedMessage());
					failed++;
				}
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... progress)
		{
			WeatherActivity activity = activityWeakReference.get();
			activity.progressDialog.setProgress(progress[0]);
		}

		@Override
		protected void onPostExecute(Void result)
		{
			WeatherActivity activity = activityWeakReference.get();
			SharedPreferences stats = activity.getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);
			stats.edit().putLong(activity.getString(R.string.pref_key_weather_timestamp), Calendar.getInstance(TimeZone.getDefault()).getTimeInMillis()).apply();
			// TODO: show number failed
			activity.progressDialog.dismiss();
			Toast.makeText(activity, activity.getString(R.string.finished_downloading_weather), Toast.LENGTH_LONG).show();
		}
	}
}
