package link.glider.gliderlink.utils.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.view.ContextThemeWrapper;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.OverlayItem;

import link.glider.gliderlink.Constants;
import link.glider.gliderlink.MyApp;
import link.glider.gliderlink.R;
import link.glider.gliderlink.Tracker;
import link.glider.gliderlink.messaging.IAircraftMessage;
import link.glider.gliderlink.prefs.UnitsPrefs;
import link.glider.gliderlink.utils.BitmapUtils;
import link.glider.gliderlink.utils.NumberUtils;

/**
 * Created by bhirashi on 10/22/16.
 */

public class AircraftOverlayItem extends OverlayItem
{
	private static final float textSize = 18 * MyApp.getAppContext().getResources().getDisplayMetrics().density;
	private static final float textSizeRight = 18 * MyApp.getAppContext().getResources().getDisplayMetrics().density;

	private final IAircraftMessage latestMessage;
	private final UnitsPrefs unitsPrefs;

	public AircraftOverlayItem(IAircraftMessage message, UnitsPrefs unitsPrefs) {
		super(null, message.getSenderName(), null, new GeoPoint(message.getLatitude(), message.getLongitude(), message.getAltitude()));
		this.latestMessage = message;
		this.unitsPrefs = unitsPrefs;
	}

	public IAircraftMessage getLatestMessage ()
	{
		return latestMessage;
	}

	public void updateMarker(double myAltitude)
	{
		long msSinceLastUpdate = System.currentTimeMillis() - latestMessage.getTimestamp();
		int style;
		if (msSinceLastUpdate > (Tracker.LOC_EXPIRED_MS) + 2000)
		{
			style = R.style.GliderExpired;
		}
		else if (msSinceLastUpdate > Tracker.TX_FREQ_MS + 2000) // add extra padding for transmission time
		{
			style = R.style.GliderStale;
		}
		else
		{
			style = R.style.GliderFresh;
		}

		Bitmap bitmap = AircraftOverlayItem.makeRotatedGliderBitmap(latestMessage.getBearing(), style);
		BitmapDrawable marker = AircraftOverlayItem.writeTextOnBitmap(bitmap, latestMessage.getSenderName(), getAltString(myAltitude), getVertSpdString());

		setMarker(marker);
		setMarkerHotspot(HotspotPlace.CENTER);
	}

	@Nullable
	public String getAltString(double myAltitudeMeters)
	{
		String altStr = null;
		if (latestMessage.hasAltitude())
		{
			double altDiff = latestMessage.getAltitude() - myAltitudeMeters;
			if (unitsPrefs.getAltitudeUnits() == Constants.Units.ft)
			{
				altDiff = NumberUtils.roundToHundreds(altDiff * NumberUtils.METERS_TO_FEET);
			}
			else if (unitsPrefs.getAltitudeUnits() == Constants.Units.m)
			{
				altDiff = NumberUtils.roundToTens(altDiff);
			}

			altStr = String.format("%.0f", altDiff);

			if (altDiff > 0) altStr = "+" + altStr; // minus sign will already be there if negative
		}
		return altStr;
	}

	@Nullable
	public String getVertSpdString()
	{
		String vertStr = null;
		if (latestMessage.hasVertSpeedAvg())
		{
			float vertSpd = latestMessage.getVertSpeedAvg();
//			if (vertSpd < 2. && vertSpd > -2.) return "0"; //TODO: pref
			double vertSpdConverted = Math.abs(unitsPrefs.convertVertSpeedMps(vertSpd));

			String fmtStr = unitsPrefs.getVerticalSpeedUnits() == Constants.Units.fpm ? "%.0f" : "%.1f";
			vertStr = String.format(fmtStr, vertSpdConverted);
			if (vertSpd > 0)
				vertStr = vertStr + '\u2191';
			else if (vertSpd < 0)
				vertStr = vertStr + '\u2193';
		}
		return vertStr;
	}

	private static Bitmap makeRotatedGliderBitmap (float heading, @StyleRes int style)
	{
		final ContextThemeWrapper wrapper = new ContextThemeWrapper(MyApp.getAppContext(), style);
		final VectorDrawableCompat vect = VectorDrawableCompat.create(MyApp.getAppContext().getResources(), R.drawable.ic_cockpit_circle_32dp, wrapper.getTheme());
		final Bitmap gliderIcon = BitmapUtils.getBitmap(vect);
		return BitmapUtils.rotateBitmap(gliderIcon, heading);
	}

	private static BitmapDrawable writeTextOnBitmap(Bitmap bmInput, String name, String alt, String vertSpd)
	{
		Bitmap bmOutput = Bitmap.createBitmap(bmInput.getWidth() * 4, bmInput.getHeight(), Bitmap.Config.ARGB_8888);

		Paint paintRight = new Paint();
		paintRight.setStyle(Paint.Style.FILL);
		paintRight.setColor(Color.BLACK);
		paintRight.setTextSize(textSize);
		paintRight.setTextAlign(Paint.Align.LEFT);
		paintRight.setAntiAlias(true);
		paintRight.setTypeface(Typeface.DEFAULT_BOLD);

		Paint paintLeft = new Paint();
		paintLeft.setStyle(Paint.Style.FILL);
		paintLeft.setColor(Color.BLACK);
		paintLeft.setTextSize(textSizeRight);
		paintLeft.setTextAlign(Paint.Align.RIGHT);
		paintLeft.setAntiAlias(true);
		paintLeft.setTypeface(Typeface.DEFAULT_BOLD);

		final float midX = bmOutput.getWidth()/2;
		final float iconXLeft = midX - bmInput.getWidth()/2;
		final float iconXRight = midX + bmInput.getWidth()/2;
		Canvas canvas = new Canvas(bmOutput);
		canvas.drawBitmap(bmInput, iconXLeft, 0, null);
		if (vertSpd != null && !vertSpd.equals("0") && !vertSpd.equals("0.0"))
			canvas.drawText(vertSpd, iconXRight, textSize*1.25f, paintRight);
		if (name != null)
			canvas.drawText(name, iconXLeft, textSizeRight*0.8f, paintLeft);
		if (alt != null)
			canvas.drawText(alt, iconXLeft, textSizeRight*2 - textSizeRight*.25f, paintLeft);

		return new BitmapDrawable(MyApp.getAppContext().getResources(), bmOutput);
	}
}
