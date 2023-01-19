package link.glider.gliderlink;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.Toolbar;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import link.glider.gliderlink.utils.BitmapUtils;

public class MapActivity extends UnlockedActivity implements View.OnClickListener, LocationListener
{
	private static final Logger log = LoggerFactory.getLogger(MapActivity.class);
	private static final String KEY_DIALOG_DISMISSED = "KEY_DIALOG_DISMISSED";
	private MapView mapView;
    private FloatingActionButton fab;
    private boolean dialogDismissed = false;
	private LocationManager locationMan;
	private MyLocationNewOverlay myLocationOverlay;
	private Location lastLocation = null;

	@Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

		if (savedInstanceState != null)
		{
			dialogDismissed = savedInstanceState.getBoolean(KEY_DIALOG_DISMISSED);
		}

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

		locationMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(this);

		Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        if (!dialogDismissed)
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.map_hint).setTitle(R.string.map_hint_title);
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which) { dialogDismissed = true; }
			});
			builder.create().show();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(KEY_DIALOG_DISMISSED, dialogDismissed);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onResume ()
	{
		super.onResume();
		log.debug("onResume()");
		try
		{
			Toast.makeText(this, R.string.detecting_location, Toast.LENGTH_LONG).show();
			locationMan.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
			if(lastLocation == null) lastLocation = locationMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			configureMap();
			centerOnLocation(true);
		}
		catch (SecurityException e)
		{
			Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
		myLocationOverlay.enableMyLocation();
	}

	@Override
	public void onPause ()
	{
		super.onPause();
		log.debug("onPause");
		try
		{
			locationMan.removeUpdates(this);
		}
		catch (SecurityException e)
		{
			log.error(e.getLocalizedMessage());
		}
		myLocationOverlay.disableMyLocation();
	}

    @Override
    public void onClick (View v)
    {
        if (v == fab)
        {
            downloadMap();
        }
    }

	private void configureMap()
	{
		mapView = findViewById(R.id.mapView);
		mapView.setBuiltInZoomControls(true);
		mapView.setMultiTouchControls(true);
		mapView.setTileSource(TileSourceFactory.USGS_TOPO);
		mapView.setUseDataConnection(true);
		mapView.setMinZoomLevel(8.);
		mapView.setMaxZoomLevel(13.);

		ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(mapView);
		myScaleBarOverlay.setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.nautical);
		myScaleBarOverlay.setAlignBottom(true);
		mapView.getOverlays().add(myScaleBarOverlay);

		final IMapController mapController = mapView.getController();
		mapController.setZoom(9.5);

		final ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.GliderOwnship);
		final VectorDrawableCompat vect = VectorDrawableCompat.create(getResources(), R.drawable.ic_cockpit_circle_32dp, wrapper.getTheme());
		final Bitmap gliderIcon = BitmapUtils.getBitmap(vect);
		myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(getApplicationContext()), mapView);
		myLocationOverlay.enableFollowLocation();
		myLocationOverlay.setDirectionArrow(gliderIcon, gliderIcon);
		myLocationOverlay.setPersonHotspot(gliderIcon.getWidth() / 2, gliderIcon.getHeight() / 2);
		myLocationOverlay.setDrawAccuracyEnabled(true);
		mapView.getOverlays().add(myLocationOverlay);
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

    private void downloadMap()
	{
		lockOrientation();
		CacheManager cacheMan = new CacheManager(mapView);
		cacheMan.downloadAreaAsync(this, mapView.getBoundingBox(), 0, 13, new CacheManager.CacheManagerCallback()
		{
			@Override
			public void onTaskComplete ()
			{
				Toast.makeText(getApplicationContext(), "Download complete!", Toast.LENGTH_LONG).show();
				startMainActivity();
			}

			@Override
			public void onTaskFailed (int errors)
			{
				showTryAgain();
			}

			@Override
			public void updateProgress (int progress, int currentZoomLevel, int zoomMin, int zoomMax)
			{
				//NOOP since we are using the build in UI
			}

			@Override
			public void downloadStarted ()
			{
				//NOOP since we are using the build in UI
			}

			@Override
			public void setPossibleTilesInArea (int total)
			{
				//NOOP since we are using the build in UI
			}
		});
	}

	private void startMainActivity()
	{
		Intent i = new Intent(this, MainActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
	}

	private void showTryAgain()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.map_download_failed)
				.setTitle(R.string.map_hint_title);
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which) { }
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which) { startMainActivity(); }
		});
		builder.create().show();
	}

	private void lockOrientation()
	{
		int rotation = getWindowManager().getDefaultDisplay().getRotation();

		switch(rotation) {
			case Surface.ROTATION_180:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
				break;
			case Surface.ROTATION_270:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
				break;
			case  Surface.ROTATION_0:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			case Surface.ROTATION_90:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
		}
	}

	@Override
	public void onLocationChanged(Location location)
	{

	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{

	}

	@Override
	public void onProviderEnabled(String provider)
	{

	}

	@Override
	public void onProviderDisabled(String provider)
	{

	}
}
