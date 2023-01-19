package link.glider.gliderlink;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class IntroActivity extends UnlockedActivity
{
	private static final int REQUEST_CODE = 1;
	private TextView introText;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intro);

		introText = findViewById(R.id.intro_text);
		introText.setMovementMethod(LinkMovementMethod.getInstance());
		introText.setText(Html.fromHtml(getString(R.string.intro_text) + getString(R.string.permissions_explanation)));
	}

	public void onNextButton(@NonNull View view)
	{
		verifyPermissions();
	}

	private void verifyPermissions()
	{
		boolean allGranted = true;
		for (String key : Constants.PERMISSIONS)
		{
			int permission = ActivityCompat.checkSelfPermission(this, key);
			if (permission != PackageManager.PERMISSION_GRANTED)
			{
				allGranted = false;
				ActivityCompat.requestPermissions(this, Constants.PERMISSIONS, REQUEST_CODE);
				break;
			}
		}
		if (allGranted) startMapActivity();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissionArray, @NonNull int[] grantResults)
	{
		boolean allGranted = true;
		if (requestCode == REQUEST_CODE)
		{
			for (int result : grantResults)
			{
				if (result != 0)
				{
					allGranted = false;
					break;
				}
			}
			if (allGranted)
			{
				startMapActivity();
			}
			else
			{
				Toast.makeText(this, "Permissions not granted. Cannot continue.", Toast.LENGTH_LONG).show();
			}
		}
	}

	private void startMapActivity()
	{
		startActivity(new Intent(this, MapActivity.class));
	}
}
