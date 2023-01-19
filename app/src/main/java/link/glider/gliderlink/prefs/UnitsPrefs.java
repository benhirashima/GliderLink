package link.glider.gliderlink.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import link.glider.gliderlink.Constants.Units;
import link.glider.gliderlink.R;
import link.glider.gliderlink.utils.NumberUtils;

public class UnitsPrefs
{
	// WARNING: these are defined in strings.xml too, but we need access to them without a context. TODO: better way?
	public static final String KEY_DISTANCE_UNITS = "distance_units";
	public static final String KEY_ALTITUDE_UNITS = "altitude_units";
	public static final String KEY_VERT_SPEED_UNITS = "vert_speed_units";
	public static final String KEY_AIRSPEED_UNITS = "airspeed_units";

	private static final Logger log = LoggerFactory.getLogger(UnitsPrefs.class);
	private Units distUnits = Units.nm;
	private Units altUnits = Units.ft;
	private Units vertUnits = Units.kts;
	private Units airspeedUnits = Units.kts;

	public UnitsPrefs() {}

	public UnitsPrefs(Context context)
	{
		setWithContext(context);
	}

	public void setWithContext(Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		setDistanceUnits(prefs.getString(UnitsPrefs.KEY_DISTANCE_UNITS, context.getResources().getStringArray(R.array.distance_units)[0]));
		setAltitudeUnits(prefs.getString(UnitsPrefs.KEY_ALTITUDE_UNITS, context.getResources().getStringArray(R.array.altitude_units)[0]));
		setVerticalSpeedUnits(prefs.getString(UnitsPrefs.KEY_VERT_SPEED_UNITS, context.getResources().getStringArray(R.array.vert_speed_units)[0]));
		setAirspeedUnits(prefs.getString(UnitsPrefs.KEY_AIRSPEED_UNITS, context.getResources().getStringArray(R.array.airspeed_units)[0]));
	}

	public Units unitsFromString(String input)
	{
		if(input.equals("nm"))
			return Units.nm;
		else if(input.equals("km"))
			return Units.km;
		else if(input.equals("sm"))
			return Units.sm;
		else if(input.equals("ft"))
			return Units.ft;
		else if(input.equals("m"))
			return Units.m;
		else if(input.equals("kts"))
			return Units.kts;
		else if(input.equals("m/s"))
			return Units.mps;
		else if(input.equals("kmh"))
			return Units.kmh;
		else if(input.equals("fpm"))
			return Units.fpm;
		else if(input.equals("mph"))
			return Units.mph;
		else
			return Units.unknown;
	}

	public Units getDistanceUnits()
	{
		return distUnits;
	}

	public Units getAltitudeUnits()
	{
		return altUnits;
	}

	public Units getVerticalSpeedUnits()
	{
		return vertUnits;
	}

	public Units getAirspeedUnits()
	{
		return airspeedUnits;
	}

	public void setDistanceUnits(final String unitsStr)
	{
		if (unitsStr == null || unitsStr.length() == 0) return;

		Units units = unitsFromString(unitsStr);

		if (units == Units.nm || units == Units.km || units == Units.sm)
		{
			distUnits = units;
		}
		else
		{
			log.error("Failed to set distance units to %s", unitsStr);
		}
	}

	public void setAltitudeUnits(final String unitsStr)
	{
		if (unitsStr == null || unitsStr.length() == 0) return;

		Units units = unitsFromString(unitsStr);

		if (units == Units.ft || units == Units.m)
		{
			altUnits = units;
		}
		else
		{
			log.error("Failed to set altitude units to %s", unitsStr);
		}
	}

	public void setVerticalSpeedUnits(final String unitsStr)
	{
		if (unitsStr == null || unitsStr.length() == 0) return;

		Units units = unitsFromString(unitsStr);

		if (units == Units.kts || units == Units.mps || units == Units.fpm)
		{
			vertUnits = units;
		}
		else
		{
			log.error("Failed to set vertical speed units to %s", unitsStr);
		}
	}

	public void setAirspeedUnits(final String unitsStr)
	{
		if (unitsStr == null || unitsStr.length() == 0) return;

		Units units = unitsFromString(unitsStr);

		if (units == Units.kts || units == Units.kmh || units == Units.mph)
		{
			airspeedUnits = units;
		}
		else
		{
			log.error("Failed to set airspeed units to %s", unitsStr);
		}
	}

	public double convertDistanceMeters(double meters)
	{
		switch(getDistanceUnits())
		{
			case nm:
				return meters * NumberUtils.METERS_TO_NM;
			case km:
				return meters / 1000;
			case sm:
				return meters * NumberUtils.METERS_TO_SM;
			default:
			{
				log.error("Failed to convert distance in meters. Unsupported units.");
				return meters;
			}
		}
	}

	public double convertAltitudeMeters(double meters)
	{
		switch(getAltitudeUnits())
		{
			case ft:
				return meters * NumberUtils.METERS_TO_FEET;
			case m:
				return meters;
			default:
			{
				log.error("Failed to convert distance in meters. Unsupported units.");
				return meters;
			}
		}
	}

	public double convertVertSpeedMps(double mps)
	{
		switch(getVerticalSpeedUnits())
		{
			case kts:
				return mps * NumberUtils.MPS_TO_KTS;
			case fpm:
				return mps * NumberUtils.MPS_TO_FPM;
			case mps:
				return mps;
			default:
			{
				log.error("Failed to convert vertical speed in mps. Unsupported units.");
				return mps;
			}
		}
	}

	public double convertAirspeedMps(double mps)
	{
		switch(getAirspeedUnits())
		{
			case kts:
				return mps * NumberUtils.MPS_TO_KTS;
			case mph:
				return mps * NumberUtils.MPS_TO_MPH;
			case kmh:
				return mps * NumberUtils.MPS_TO_KMH;
			default:
			{
				log.error("Failed to convert vertical speed in mps. Unsupported units.");
				return mps;
			}
		}
	}
}
