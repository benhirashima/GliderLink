package link.glider.gliderlink;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import link.glider.gliderlink.prefs.UnitsPrefs;

public class StatsActivity extends UnlockedActivity
{
	private static final Logger log = LoggerFactory.getLogger(StatsActivity.class);
	private TextView statsText;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stats);
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

		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				sendEmail();
			}
		});

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		UnitsPrefs unitsPrefs = new UnitsPrefs(getApplicationContext());
		SharedPreferences stats = getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);

		float maxDist = stats.getFloat(getString(R.string.pref_key_max_distance), 0);
		String maxDistStr = String.format("%.1f" + prefs.getString(UnitsPrefs.KEY_DISTANCE_UNITS, "nm"), unitsPrefs.convertDistanceMeters(maxDist));
		String receivedAt = "";
		String receivedFrom = "";
		if (maxDist > 0)
		{
			receivedAt = formatFix(stats.getFloat(getString(R.string.pref_key_max_dist_my_latitude_float), 0), stats.getFloat(getString(R.string.pref_key_max_dist_my_longitude_float), 0), stats.getFloat(getString(R.string.pref_key_max_dist_my_altitude_float), 0), unitsPrefs, prefs);
			receivedFrom = formatFix(stats.getFloat(getString(R.string.pref_key_max_dist_their_latitude_float), 0), stats.getFloat(getString(R.string.pref_key_max_dist_their_longitude_float), 0), stats.getFloat(getString(R.string.pref_key_max_dist_their_latitude_float), 0), unitsPrefs, prefs);
		}
		long distTimestamp = stats.getLong(getString(R.string.pref_key_max_dist_timestamp), 0);
		String dateTimeDistStr = "";
		if (distTimestamp > 0)
		{
			Date dateTimeDist = new Date(distTimestamp);
			dateTimeDistStr = Constants.DATE_TIME_FORMAT.format(dateTimeDist);
		}

		int maxContacts = stats.getInt(getString(R.string.pref_key_max_contacts), 0);
		String seenAt = "";
		if (maxContacts > 0)
		{
			seenAt = formatFix(stats.getFloat(getString(R.string.pref_key_max_contacts_my_latitude_float), 0), stats.getFloat(getString(R.string.pref_key_max_contacts_my_latitude_float), 0), stats.getFloat(getString(R.string.pref_key_max_contacts_my_altitude_float), 0), unitsPrefs, prefs);
		}
		long contactsTimestamp = stats.getLong(getString(R.string.pref_key_max_contacts_timestamp), 0);
		String dateTimeContactsStr = "";
		if (contactsTimestamp > 0)
		{
			Date dateTimeContacts = new Date(contactsTimestamp);
			dateTimeContactsStr = Constants.DATE_TIME_FORMAT.format(dateTimeContacts);
		}

		statsText = findViewById(R.id.statsText);
		statsText.setText(Html.fromHtml(getString(R.string.stats_text, maxDistStr, receivedFrom, receivedAt, dateTimeDistStr, maxContacts, seenAt, dateTimeContactsStr)));
	}

	String formatFix(float lat, float lng, float alt, UnitsPrefs unitsPrefs, SharedPreferences prefs)
	{
		String myCoords = String.format("%.7f, %.7f", lat, lng);
		String myAlt = String.format("%.1f", unitsPrefs.convertAltitudeMeters(alt));
		return myCoords + " @ " + myAlt + prefs.getString(getString(R.string.pref_key_altitude_units), "ft");
	}

	private void sendEmail()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		UnitsPrefs unitsPrefs = new UnitsPrefs(getApplicationContext());
		SharedPreferences stats = getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);

		StringBuilder builder = new StringBuilder();
		builder.append(getString(R.string.records_email_body));
		builder.append("\n\n");
		builder.append(statsText.getText());

		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { Constants.SUPPORT_EMAIL });
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " " + getString(R.string.records_email_subject));
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, builder.toString());
		emailIntent.putExtra(Intent.EXTRA_HTML_TEXT, builder.toString());

		startActivity(Intent.createChooser(emailIntent, getString(R.string.choose_email_program)));
	}
}
