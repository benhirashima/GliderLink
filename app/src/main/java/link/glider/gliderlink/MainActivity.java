package link.glider.gliderlink;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.gotenna.sdk.bluetooth.BluetoothAdapterManager;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay2;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import link.glider.gliderlink.messaging.AircraftMessage;
import link.glider.gliderlink.messaging.IAircraftMessage;
import link.glider.gliderlink.prefs.SettingsActivity;
import link.glider.gliderlink.prefs.UnitsPrefs;
import link.glider.gliderlink.utils.ui.AircraftOverlayItem;
import link.glider.gliderlink.utils.BitmapUtils;
import link.glider.gliderlink.utils.NumberUtils;

public class MainActivity extends UnlockedActivity implements LocationListener, NavigationView.OnNavigationItemSelectedListener, SharedPreferences.OnSharedPreferenceChangeListener, GotennaContract.GotennaManagerListener
{
	private static final Logger log = LoggerFactory.getLogger(MainActivity.class);
	private static final double MAX_ZOOM_LEVEL = 15;
	private static final double INITIAL_ZOOM_LEVEL = 11;

	private static final String KEY_ZOOM_LEVEL = "KEY_ZOOM_LEVEL";
	private static final String KEY_LAST_LOCATION = "KEY_LAST_LOCATION";

	private Intent svcIntent;
	private Switch svcSwitch;
	private TextView gliderCount;
	private TextView closestGlider;
	private MapView mapView;
	private GpsMyLocationProvider mapLocationProvider;
	private LocationManager locationMan;
	private Location lastLocation;
	private MyLocationNewOverlay myLocationOverlay;
	private ItemizedIconOverlay<OverlayItem> aircraftOverlay;
	private final List<OverlayItem> overlayItems = Collections.synchronizedList(new ArrayList<OverlayItem>());
	private Handler handler;
	private Runnable runnable;
	private Runnable ognRunnable;
	private BluetoothAdapterManager btMan;
	private DrawerLayout drawerLayout;
	private final GroundOverlay2 raspOverlay = new GroundOverlay2();
	private Snackbar snackbarDemo;
	private UnitsPrefs unitsPrefs;
	private TextView lblClosestUnits;
	private SharedPreferences prefs;
	private Switch weatherSwitch;
	private DownloadOgnFlightsTask downloadOgnFlightsTask;
	private final List<AircraftMessage> ognFlights = Collections.synchronizedList(new ArrayList<AircraftMessage>());
	private ScaleBarOverlay myScaleBarOverlay;
	private GotennaManager gtMan;
	private TextView lblGotennaBtm;
	private Switch gotennaSwitch;

	private long msSinceLastUpdate = 0;
	private final AtomicBoolean isSvcRunning = new AtomicBoolean(false);
	private int secsSinceMsgRx = 0;
	private int secsUntilTx = Tracker.TX_FREQ_MS / NumberUtils.MILLIS_PER_SEC;
	private double zoomLevel = INITIAL_ZOOM_LEVEL;
	private int currEffectiveHour = -1;
	private AtomicLong gpsTimeOffsetMs = new AtomicLong(0);

	// ================================================================================
	// region - Overridden Activity Methods
	// ================================================================================

	@Override
	protected void onCreate (Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		verifyPermissions();

		if (savedInstanceState != null) {
			// must happen before configureMapView()
			zoomLevel = savedInstanceState.getDouble(KEY_ZOOM_LEVEL);
			GeoPoint geoPoint = (GeoPoint)savedInstanceState.getSerializable(KEY_LAST_LOCATION);
			if (geoPoint != null)
			{
				lastLocation = new Location(LocationManager.GPS_PROVIDER);
				lastLocation.setLatitude(geoPoint.getLatitude());
				lastLocation.setLongitude(geoPoint.getLongitude());
				lastLocation.setAltitude(geoPoint.getAltitude());
			}
		}

		setContentView(R.layout.activity_main);

		svcIntent = new Intent(this, MainService.class);
		locationMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		btMan = BluetoothAdapterManager.getInstance();

		NavigationView navigationView = findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		drawerLayout = findViewById(R.id.drawer_layout);
		svcSwitch = findViewById(R.id.svcButton);
		svcSwitch.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onSwitchToggled(v);
			}
		});
		ImageButton btnMenu = findViewById(R.id.btnMenu);
		btnMenu.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				drawerLayout.openDrawer(GravityCompat.START);
			}
		});
		gliderCount = findViewById(R.id.gliderCount);
		closestGlider = findViewById(R.id.closestGlider);
		FloatingActionButton btnCenter = findViewById(R.id.btnCenterLocation);
		btnCenter.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick (View v)
			{
				centerOnLocation();
			}
		});
		lblClosestUnits = findViewById(R.id.lblClosestUnits);
		lblGotennaBtm = findViewById(R.id.lblGotennaBottom);
		weatherSwitch = findViewById(R.id.weatherSwitch);
		weatherSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
			{
				runOnUiThread(new Runnable() {
					@Override
					public void run()
					{
						handleWeatherSwitch(buttonView, isChecked);
					}
				});
			}
		});
		gotennaSwitch = findViewById(R.id.gotennaSwitch);
		gotennaSwitch.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick (View v)
			{
				onGotennaToggled(v);
			}
		});

		handler = new Handler();
		runnable = new Runnable()
		{
			@Override
			public void run ()
			{
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						refreshGliderData();
						if (weatherSwitch.isChecked()) updateWxOverlay();
					}
				});
				handler.postDelayed(this, 1000);
			}
		};

		ognRunnable = new Runnable()
		{
			@Override
			public void run ()
			{
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						downloadOgnFlights();
					}
				});
			}
		};

		snackbarDemo = Snackbar.make(findViewById(R.id.coordinator), R.string.demo_mode, Snackbar.LENGTH_INDEFINITE);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		lblClosestUnits.setText(prefs.getString(UnitsPrefs.KEY_DISTANCE_UNITS, "nm"));
		unitsPrefs = new UnitsPrefs(this.getApplicationContext());
		prefs.registerOnSharedPreferenceChangeListener(this);

		gtMan = GotennaManager.getSharedInstance();

		configureMapView();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putDouble(KEY_ZOOM_LEVEL, mapView.getZoomLevelDouble());
		if (lastLocation != null)
		{
			outState.putSerializable(KEY_LAST_LOCATION, new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getAltitude()));
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onResume ()
	{
		super.onResume();
		log.debug("onResume");

		gtMan.addListener(this);
		updateGotennaStatus();

		// delay the ping in case service is starting due to resuming after bluetooth dialog
		handler.postDelayed(new Runnable()
		{
			@Override
			public void run ()
			{
				pingRadioService();
			}
		}, 500);

		runnable.run();

		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
		{
			try
			{
				locationMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
				if(lastLocation == null) lastLocation = locationMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				myLocationOverlay.onLocationChanged(lastLocation, mapLocationProvider); //shows user location quicker
			}
			catch(SecurityException e)
			{
				Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			}
			myLocationOverlay.enableMyLocation();
		}

		downloadOgnFlights();
	}

	@Override
	public void onPause ()
	{
		super.onPause();
		log.debug("onPause");

		gtMan.removeListener(this);

		handler.removeCallbacks(runnable);
		handler.removeCallbacks(ognRunnable);
		try
		{
			locationMan.removeUpdates(this);
		}
		catch (SecurityException e)
		{
			log.error(e.getLocalizedMessage());
		}
		myLocationOverlay.disableMyLocation();
		if(downloadOgnFlightsTask != null) downloadOgnFlightsTask.cancel(true);
	}

	@Override
	protected void onStart ()
	{
		final IntentFilter filter = new IntentFilter(Tracker.ACTION_MSG_RECEIVED);
		filter.addAction(MainService.ACTION_PONG);
		filter.addAction(MainService.ACTION_SELF_STOP);
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);

		super.onStart();
	}

	@Override
	protected void onStop ()
	{
		LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
		super.onStop();
	}

	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == BLUETOOTH_REQUEST_CODE && resultCode < 0)
		{
			startMainService();
		}
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem menuItem)
	{
		drawerLayout.closeDrawers();

		switch(menuItem.getItemId())
		{
			case R.id.nav_gotenna:
				startGotennaActivity();
				return true;

			case R.id.nav_maps:
				startMapDownloadActivity();
				return true;

			case R.id.nav_demo:
				startDemo();
				return true;

			case R.id.nav_stats:
				startStatsActivity();
				return true;

			case R.id.nav_weather:
				startWeatherActivity();
				return true;

			case R.id.nav_settings:
				startSettingsActivity();
				return true;

			case R.id.nav_info:
				startAboutActivity();
				return true;
		}
		return false;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
	{
		log.info("Settings key changed: " + key);
		if(key.equals(UnitsPrefs.KEY_DISTANCE_UNITS) || key.equals(UnitsPrefs.KEY_ALTITUDE_UNITS) || key.equals(UnitsPrefs.KEY_VERT_SPEED_UNITS) || key.equals(UnitsPrefs.KEY_AIRSPEED_UNITS))
		{
			lblClosestUnits.setText(prefs.getString(UnitsPrefs.KEY_DISTANCE_UNITS, "nm"));
			unitsPrefs.setWithContext(MainActivity.this.getApplicationContext());
			updateOverlay();
		}
		if(key.equals(UnitsPrefs.KEY_DISTANCE_UNITS))
		{
			myScaleBarOverlay.setUnitsOfMeasure(getScaleUnits());
		}
		if (key.equals(getString(R.string.pref_key_gotenna_gid)) || key.equals(getString(R.string.pref_key_callsign)))
		{
			gtMan.setGotennaGID(prefs.getLong(getString(R.string.pref_key_gotenna_gid), 0), prefs.getString(getString(R.string.pref_key_callsign), ""));
		}
	}
	//endregion

	// ================================================================================
	// region - LocationListener
	// ================================================================================

	@Override
	public void onLocationChanged (Location location)
	{
//        log.debug(location.toString());
		lastLocation = location;
		gpsTimeOffsetMs.set(location.getTime() - System.currentTimeMillis());
	}

	@Override
	public void onStatusChanged (String provider, int status, Bundle extras)
	{
//        log.debug("onStatusChanged(" + status + ")");
	}

	@Override
	public void onProviderEnabled (String provider)
	{
//        log.debug("onProviderEnabled(" + provider + ")");
	}

	@Override
	public void onProviderDisabled (String provider)
	{
//        log.debug("onProviderDisabled(" + provider + ")");
	}
	//endregion


	// ================================================================================
	// region - Class Instance Methods
	// ================================================================================

	private void configureMapView ()
	{
		mapView = findViewById(R.id.mapView);
		mapView.setBuiltInZoomControls(true);
		mapView.setMultiTouchControls(true);
		mapView.setTileSource(TileSourceFactory.USGS_TOPO);
		mapView.setUseDataConnection(true); //TODO: pref
		mapView.setMaxZoomLevel(MAX_ZOOM_LEVEL);
		mapView.setKeepScreenOn(true);

		myScaleBarOverlay = new ScaleBarOverlay(mapView);
		myScaleBarOverlay.setUnitsOfMeasure(getScaleUnits());
		myScaleBarOverlay.setAlignBottom(true);
		mapView.getOverlays().add(myScaleBarOverlay);

		final IMapController mapController = mapView.getController();
		mapController.setZoom(zoomLevel);

		mapLocationProvider = new GpsMyLocationProvider(getApplicationContext());

		final ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.GliderOwnship);
		final VectorDrawableCompat vect = VectorDrawableCompat.create(getResources(), R.drawable.ic_cockpit_circle_32dp, wrapper.getTheme());
		final Bitmap gliderIcon = BitmapUtils.getBitmap(vect);
		myLocationOverlay = new MyLocationNewOverlay(mapLocationProvider, mapView);
		myLocationOverlay.enableFollowLocation();
		myLocationOverlay.setDirectionArrow(gliderIcon, gliderIcon);
		myLocationOverlay.setPersonHotspot(gliderIcon.getWidth() / 2, gliderIcon.getHeight() / 2);
		myLocationOverlay.setDrawAccuracyEnabled(true);
		mapView.getOverlays().add(myLocationOverlay);

		aircraftOverlay = new ItemizedIconOverlay<>(getApplicationContext(), overlayItems,
				new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>()
				{
					@Override
					public boolean onItemSingleTapUp (final int index, final OverlayItem item)
					{
						AircraftOverlayItem gliderItem = (AircraftOverlayItem) item;
						Toast.makeText(MainActivity.this, getGliderDetails(gliderItem.getLatestMessage()), Toast.LENGTH_LONG).show();
						return true; // We 'handled' this event.
					}

					@Override
					public boolean onItemLongPress (final int index, final OverlayItem item)
					{
						return false;
					}
				}
		);
		mapView.getOverlays().add(aircraftOverlay);

		raspOverlay.setPosition(new GeoPoint(37.4182892, -122.3207550), new GeoPoint(35.9410553, -120.5683594)); // Hollister RASP
		raspOverlay.setTransparency(0.8f);
	}

	private ScaleBarOverlay.UnitsOfMeasure getScaleUnits()
	{
		ScaleBarOverlay.UnitsOfMeasure scaleUnits;
		switch (unitsPrefs.getDistanceUnits())
		{
			case km:
				scaleUnits = ScaleBarOverlay.UnitsOfMeasure.metric;
				break;
			case nm:
				scaleUnits = ScaleBarOverlay.UnitsOfMeasure.nautical;
				break;
			default:
				scaleUnits = ScaleBarOverlay.UnitsOfMeasure.imperial;
				break;
		}
		return scaleUnits;
	}

	private String getGliderDetails(IAircraftMessage msg)
	{
		String alt = String.format("%.0f" + prefs.getString(UnitsPrefs.KEY_ALTITUDE_UNITS, getResources().getStringArray(R.array.altitude_units)[0]), unitsPrefs.convertAltitudeMeters(msg.getAltitude()));
		String dist = String.format("%.1f" + prefs.getString(UnitsPrefs.KEY_DISTANCE_UNITS, getResources().getStringArray(R.array.distance_units)[0]), unitsPrefs.convertDistanceMeters(msg.getLocation().distanceTo(lastLocation)));
		String speed = String.format("%.0f" + prefs.getString(UnitsPrefs.KEY_AIRSPEED_UNITS, getResources().getStringArray(R.array.airspeed_units)[0]), unitsPrefs.convertAirspeedMps(msg.getSpeed()));
		long timeDiff = System.currentTimeMillis() - msg.getGpsFixTimeMs();
		String timeStr = String.format("%ds " + getString(R.string.seconds_ago), timeDiff / 1000);
		StringBuilder sb = new StringBuilder(7);
		sb.append(alt);
		if (msg.hasSpeed()) sb.append(", ").append(speed);
		sb.append(", ").append(dist).append(", ").append(timeStr);
		return sb.toString();
	}

	private void updateWxOverlay()
	{
		log.debug("updateWxOverlay");
		if (currEffectiveHour != getEffectiveHour()) loadWxOverlay();
	}

	private void loadWxOverlay()
	{
		currEffectiveHour = getEffectiveHour();
		String sdcardState = Environment.getExternalStorageState();
		if (sdcardState.equals(Environment.MEDIA_MOUNTED) || sdcardState.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
		{
			String imageFilePath = getExternalFilesDir("rasp") + String.format("/wblmaxmin.curr.%d00lst.d2.body.png", currEffectiveHour);
			File imageFile = new File(imageFilePath);
			if (imageFile.exists())
			{
				Bitmap raspImg = BitmapFactory.decodeFile(imageFilePath);
				if (raspImg != null)
				{
					raspOverlay.setImage(raspImg);
					log.debug("loaded rasp image " + imageFilePath);
				}
			}
			else
			{
				Toast.makeText(this, R.string.weather_missing, Toast.LENGTH_LONG).show();
			}
		}
		else
		{
			log.error("failed to load rasp image because sdcard was unavailable");
		}
	}

	private int getEffectiveHour()
	{
		Calendar cal = Calendar.getInstance();
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int min = cal.get(Calendar.MINUTE);
		int effectiveHour = hour;
		if (hour < 11) effectiveHour = 11;
		else if (hour >= 18) effectiveHour = 18;
		else if (min >= 30) effectiveHour = hour + 1;
		return effectiveHour;
	}

	private void pingRadioService ()
	{
		isSvcRunning.set(false);
		updateSwitchState();
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(MainService.ACTION_PING));
	}

	private void refreshGliderData ()
	{
		updateOverlay();
		secsSinceMsgRx++;
		secsUntilTx--;
	}

	private void centerOnLocation ()
	{
		centerOnLocation(true);
	}

	private void centerOnLocation (boolean animate)
	{
		if (lastLocation == null) return;
		GeoPoint myLocation = new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude());
		if (animate)
		{
			mapView.getController().animateTo(myLocation);
		}
		else
		{
			mapView.getController().setCenter(myLocation);
		}
		myLocationOverlay.enableFollowLocation();
	}

	private void onSwitchToggled(@NonNull View v)
	{
//		if (svcSwitch.isChecked() && BuildConfig.DEBUG)
//		{
//			startDemo();
//		}
//		else
//		{
			if(svcSwitch.isChecked())
			{
				if (PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.pref_key_callsign), "").length() == 0)
				{
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setMessage(R.string.missing_callsign).setTitle(R.string.pref_title_callsign);
					builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							svcSwitch.setChecked(false);
						}
					});
					builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which) { startSettingsActivity(); }
					});
					builder.create().show();
					return;
				}
			}
			setServiceState(svcSwitch.isChecked());
//		}
	}

	private void onGotennaToggled(@NonNull View v)
	{
		if(gotennaSwitch.isChecked())
		{
			if (!checkBluetoothPermission())
			{
				gotennaSwitch.setChecked(false);
				return;
			}
			gtMan.search();
		}
		else
		{
			gtMan.stop();
		}
	}

	private void handleWeatherSwitch(CompoundButton buttonView, boolean isChecked)
	{
		if (isChecked)
		{
//			SharedPreferences stats = getSharedPreferences(Constants.STATS_PREFS_FILE_NAME, MODE_PRIVATE);
//			long weatherTimestamp = stats.getLong(getString(R.string.pref_key_weather_timestamp), 0);
//			Date now = new Date();
//			Date downloaded = new Date(weatherTimestamp);
			loadWxOverlay();
			mapView.getOverlayManager().add(raspOverlay);
			mapView.invalidate();
		}
		else
		{
			mapView.getOverlayManager().remove(raspOverlay);
			mapView.invalidate();
		}
	}

	private void startGotennaActivity ()
	{
		Intent intent = new Intent(this, GotennaActivity.class);
		startActivity(intent);
	}

	private void startSettingsActivity ()
	{
		Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
	}

	private void startMapDownloadActivity()
	{
		Intent intent = new Intent(this, MapActivity.class);
		startActivity(intent);
	}

	private void startAboutActivity()
	{
		Intent intent = new Intent(this, AboutActivity.class);
		startActivity(intent);
	}

	private void startStatsActivity()
	{
		Intent intent = new Intent(this, StatsActivity.class);
		startActivity(intent);
	}

	private void startWeatherActivity()
	{
		Intent intent = new Intent(this, WeatherActivity.class);
		startActivity(intent);
	}

	public void onMenuButtonClicked(@NonNull View v)
	{
		log.debug("nav menu " + v.getId());
	}

	private void setServiceState (boolean isChecked)
	{
		if (isChecked)
		{
			svcIntent.putExtra(MainService.EXTRA_DEMO_MODE, false);
			startMainService();
		}
		else
		{
			stopMainService();
			if (snackbarDemo.isShownOrQueued()) snackbarDemo.dismiss();
		}
	}

	private void updateSwitchState ()
	{
		svcSwitch.setChecked(isSvcRunning.get());
	}

	@TargetApi(27)
	private void startMainService ()
	{
		startService(svcIntent); // isSvcRunning gets set when ACTION_STARTED broadcast received
		isSvcRunning.set(true);
		updateSwitchState();
		refreshGliderData();
	}

	@TargetApi(27)
	private void stopMainService ()
	{
		stopService(svcIntent);
		isSvcRunning.set(false);
		updateSwitchState();
	}

	private void startDemo()
	{
		centerOnLocation(false);
		mapView.getController().zoomTo(13, null);
		svcIntent.putExtra(MainService.EXTRA_DEMO_MODE, true);
		startMainService();
		snackbarDemo.setAction(android.R.string.cancel, new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				stopMainService();
			}
		});
		snackbarDemo.show();
	}

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive (Context context, Intent intent)
		{
			if (intent.getAction() == null) return;
			log.debug("onReceive(" + intent.getAction() + ")");
			if (intent.getAction().equals(MainService.ACTION_PONG))
			{
				isSvcRunning.set(true);
				updateSwitchState();
				refreshGliderData();
			}
			else if (intent.getAction().equals(MainService.ACTION_SELF_STOP))
			{
				stopMainService(); // redundantly calls stopService(), but shouldn't be a problem
			}
			else if (intent.getAction().equals(Tracker.ACTION_MSG_RECEIVED))
			{
				secsSinceMsgRx = 0;
			}
			else if (intent.getAction().equals(Tracker.ACTION_MSG_SENT))
			{
				secsUntilTx = Tracker.TX_FREQ_MS / NumberUtils.MILLIS_PER_SEC;
			}
		}
	};

	private void updateOverlay ()
	{
		boolean hasUpdates = false;
		double currAlt = lastLocation != null ? lastLocation.getAltitude() : 0;
		double closest = -1f;

		aircraftOverlay.removeAllItems();
		for (Aircraft aircraft : Tracker.getAircraft().values())
		{
			IAircraftMessage msg = aircraft.getLastMsg();
			AircraftOverlayItem aircraftItem = new AircraftOverlayItem(msg, unitsPrefs);
			aircraftItem.updateMarker(currAlt);
			aircraftOverlay.addItem(aircraftItem);
			hasUpdates = true;

			if (lastLocation != null)
			{
				double dist = aircraft.distanceFrom(lastLocation);
				if(dist < closest || closest < 0) closest = dist;
			}
		}

		synchronized (ognFlights)
		{
			for (AircraftMessage msg : ognFlights)
			{
				AircraftOverlayItem aircraftItem = new AircraftOverlayItem(msg, unitsPrefs);
				aircraftItem.updateMarker(currAlt);
				aircraftOverlay.addItem(aircraftItem);
				hasUpdates = true;
			}
		}

		if (hasUpdates) mapView.postInvalidate();

		gliderCount.setText(String.valueOf(Tracker.getAircraft().size()));
		if (closest < 0)
		{
			closestGlider.setText("");
		}
		else
		{
			closest = unitsPrefs.convertDistanceMeters(closest);
			String distStr;
			if(closest < 10.0f)
			{
				distStr = String.format("%.1f", closest);
			}
			else
			{
				distStr = String.format("%.0f", closest);
			}
			closestGlider.setText(distStr);
		}
	}

	private boolean isNetworkAvailable()
	{
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	private void downloadOgnFlights()
	{
		handler.removeCallbacks(ognRunnable); // cancel next scheduled run
		if (isNetworkAvailable())
		{
			ognFlights.clear();
			double latNorth = mapView.getBoundingBox().getLatNorth();
			double latSouth = mapView.getBoundingBox().getLatSouth();
			double lonWest = mapView.getBoundingBox().getLonWest();
			double lonEast = mapView.getBoundingBox().getLonEast();
			String url = "http://live.glidernet.org/lxml.php?a=0&b=" + latNorth + "&c=" + latSouth + "&d=" + lonEast + "&e=" + lonWest + "&z=2";
			downloadOgnFlightsTask = new DownloadOgnFlightsTask(this);
			downloadOgnFlightsTask.execute(url);
//		log.debug(url);
		}
		handler.postDelayed(ognRunnable, 5000);
	}

	private static class DownloadOgnFlightsTask extends AsyncTask<String, Integer, Void>
	{
		private WeakReference<MainActivity> activityWeakReference;
		int failed = 0;

		DownloadOgnFlightsTask(MainActivity activity)
		{
			activityWeakReference = new WeakReference<>(activity);
		}

		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			MainActivity activity = activityWeakReference.get();
		}

		@Override
		protected Void doInBackground(String... urls)
		{
			MainActivity activity = activityWeakReference.get();

			if (isCancelled()) return null;
			try
			{
				URL url = new URL(urls[0]);

				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc = db.parse(new InputSource(url.openStream()));
				doc.getDocumentElement().normalize();

				NodeList nodeList1 = doc.getElementsByTagName("m");
				for (int i = 0; i < nodeList1.getLength(); i++)
				{
					Node node = nodeList1.item(i);
					String data = node.getAttributes().getNamedItem("a").getNodeValue();
					log.debug(data);
					activity.ognFlights.add(activity.parseOgnFlight(data));
				}

			}
			catch(Exception e)
			{
				log.error(e.getLocalizedMessage());
				failed++;
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... progress)
		{
			MainActivity activity = activityWeakReference.get();
		}

		@Override
		protected void onPostExecute(Void result)
		{
			MainActivity activity = activityWeakReference.get();
			log.debug(activity.ognFlights.size() + " OGN flights parsed.");
			activity.refreshGliderData();
		}
	}

	private AircraftMessage parseOgnFlight(String a)
	{
		String[] tokens = a.split(",", 14);
		double lat = Double.parseDouble(tokens[0]);
		double lon = Double.parseDouble(tokens[1]);
		String callsign = tokens[2];
		int alt = Integer.parseInt(tokens[4]);
		long gpsFixTime = 0;
		try
		{
			gpsFixTime = Constants.TIME_FORMAT.parse(tokens[5]).getTime();
		}
		catch (ParseException e)
		{
			log.error(e.getLocalizedMessage());
		}
		int track = Integer.parseInt(tokens[7]);
		float gspd = Float.parseFloat(tokens[8]);
		float vspd = Float.parseFloat(tokens[9]);
		return new AircraftMessage(0, callsign, lat, lon, alt, track, gspd, vspd, 0, gpsFixTime, System.currentTimeMillis());
	}

	private void updateGotennaStatus ()
	{
		switch (gtMan.getState())
		{
			case CONNECTED:
				lblGotennaBtm.setText(R.string.on);
				if (!gotennaSwitch.isChecked()) gotennaSwitch.setChecked(true);
				break;
			case SEARCHING:
				lblGotennaBtm.setText(R.string.searching);
				if (!gotennaSwitch.isChecked()) gotennaSwitch.setChecked(true);
				break;
			default:
				lblGotennaBtm.setText(R.string.off);
				if (gotennaSwitch.isChecked()) gotennaSwitch.setChecked(false);
				break;
		}
	}
	//endregion

	// ================================================================================
	// region - Check permissions
	// ================================================================================

	private static final int BLUETOOTH_REQUEST_CODE = 22222;
	private static final String[] PERMISSIONS = {
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.ACCESS_FINE_LOCATION
	};

	private void verifyPermissions ()
	{
		for (String s : PERMISSIONS)
		{
			int permission = ActivityCompat.checkSelfPermission(this, s);
			if (permission != PackageManager.PERMISSION_GRANTED)
			{
				startActivity(new Intent(this, IntroActivity.class));
				break;
			}
		}
	}

	private boolean checkBluetoothPermission()
	{
		if (BuildConfig.DEBUG) return true;

		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			BluetoothAdapterManager.BluetoothStatus status = btMan.getBluetoothStatus();

			switch(status)
			{
				case SUPPORTED_AND_ENABLED:
				{
					return true;
				}

				case SUPPORTED_NOT_ENABLED:
				{
					BluetoothAdapterManager.showRequestBluetoothPermissionDialog(this, BLUETOOTH_REQUEST_CODE);
					return false;
				}
			}
		}

		Toast.makeText(this, R.string.no_bluetooth_support_message, Toast.LENGTH_LONG).show();
		return false;
	}
	//endregion

	// ================================================================================
	// region - GotennaManagerListener
	// ================================================================================

	@Override
	public void onGotennaStateChange (GotennaManager.State state)
	{
		updateGotennaStatus();
	}

	@Override
	public void onGotennaMessageReceived (IAircraftMessage msg)
	{

	}
	//endregion
}
