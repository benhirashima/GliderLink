package link.glider.gliderlink.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by bhirashi on 10/21/16.
 */

public class BitmapUtils
{
	public static Bitmap getBitmap(final Context context, final int drawableId) {
		final Drawable drawable = ContextCompat.getDrawable(context, drawableId);
		if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable) drawable).getBitmap();
		} else if (drawable instanceof VectorDrawableCompat) {
			//noinspection ConstantConditions
			return getBitmap((VectorDrawableCompat) drawable);
		} else {
			throw new IllegalArgumentException("unsupported drawable type");
		}
	}

	public static Bitmap getBitmap(final VectorDrawableCompat vectorDrawable) {
		final Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(), vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(bitmap);
		vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		vectorDrawable.draw(canvas);
		return bitmap;
	}

	public static Bitmap rotateBitmap (@NonNull Bitmap source, float angle)
	{
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
	}
}
