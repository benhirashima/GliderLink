package link.glider.gliderlink;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gotenna.sdk.bluetooth.BluetoothAdapterManager;
import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;
import com.gotenna.sdk.commands.GTError;
import com.gotenna.sdk.interfaces.GTErrorListener;
import com.gotenna.sdk.responses.SystemInfoResponseData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import link.glider.gliderlink.messaging.IAircraftMessage;

public class GotennaActivity extends UnlockedActivity implements GTConnectionManager.GTConnectionListener, GotennaContract.GotennaManagerListener
{
	private static final Logger log = LoggerFactory.getLogger(GotennaActivity.class);
	private static final int BLUETOOTH_REQUEST_CODE = 7;

	private GTConnectionManager gtConnMan;
	private GTCommandCenter gtCommandCenter;
	private final Handler handler =  new Handler();
	private final Runnable scanTimeoutRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run ()
				{
					onScanTimeOut();
				}
			});
		}
	};
	private TextView messageText;
	private BluetoothAdapterManager btMan;
	private Spanned htmlSpan;
	private GotennaManager gtMan;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gotenna);
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
				startScanning();
			}
		});

		gtConnMan = GTConnectionManager.getInstance();
		gtConnMan.addGtConnectionListener(this);
		gtCommandCenter = GTCommandCenter.getInstance();
		btMan = BluetoothAdapterManager.getInstance();

		htmlSpan = Html.fromHtml(getString(R.string.gotenna_pair) + getString(R.string.gotenna_description));

		messageText = findViewById(R.id.pairing_instructions);
		messageText.setMovementMethod(LinkMovementMethod.getInstance());

		gtMan = GotennaManager.getSharedInstance();
	}

	@Override
	protected void onResume ()
	{
		super.onResume();
		gtMan.addListener(this);
		updateMsgText();
	}

	@Override
	protected void onPause ()
	{
		gtMan.removeListener(this);
		super.onPause();
	}

	private void updateMsgTextOnUiThread()
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run ()
			{
				updateMsgText();
			}
		});
	}

	private void updateMsgText ()
	{
		if (gtMan.getState() == GotennaManager.State.CONNECTED)
		{
			gtCommandCenter.sendGetSystemInfo(
				new GTCommandCenter.GTSystemInfoResponseListener()
				{
					@Override
					public void onResponse (SystemInfoResponseData systemInfoResponseData)
					{
						final Spanned str = Html.fromHtml(getString(R.string.gotenna_info, gtConnMan.getConnectedGotennaAddress(), systemInfoResponseData.getBatteryLevelAsPercentage()));
						runOnUiThread(new Runnable() {
							@Override
							public void run()
							{
								messageText.setText(str);
							}
						});
					}
				},
				new GTErrorListener()
				{
					@Override
					public void onError (GTError gtError)
					{
						Toast.makeText(getApplicationContext(), R.string.gotenna_error, Toast.LENGTH_LONG).show();
					}
				}
			);
		}
		else if (gtMan.getState() == GotennaManager.State.SEARCHING)
		{
			messageText.setText(R.string.gotenna_searching);
		}
		else if (gtConnMan.getConnectedGotennaAddress() != null)
		{
			messageText.setText(Html.fromHtml(getString(R.string.gotenna_address, gtConnMan.getConnectedGotennaAddress())));
		}
		else
		{
			messageText.setText(htmlSpan);
		}
	}

	private void startScanning ()
	{
		log.debug("startScanning()");
		if (!checkBluetooth()) return;

		gtConnMan.addGtConnectionListener(this);
		if (gtConnMan.getConnectedGotennaAddress() != null)
		{
			gtConnMan.clearConnectedGotennaAddress();
		}
		gtMan.search();
		updateMsgText();
	}

	private void stopScanning()
	{
		log.debug("startScanning()");
		gtMan.stop();
		updateMsgText();
	}

	private void onScanTimeOut()
	{
		log.debug("goTenna scan timeout.");
		stopScanning();
		handler.removeCallbacks(scanTimeoutRunnable);
		messageText.setText(R.string.gotenna_timeout);
	}

	@Override
	public void onConnectionStateUpdated(GTConnectionManager.GTConnectionState gtConnectionState)
	{
		log.debug("onConnectionStateUpdated(" + gtConnectionState.toString() + ")");
		switch (gtConnectionState)
		{
			case CONNECTED:
			{
				updateMsgTextOnUiThread();
				break;
			}
			case DISCONNECTED:
			{
				Toast.makeText(getApplicationContext(), getString(R.string.gotenna_disconnected), Toast.LENGTH_SHORT).show();
				updateMsgTextOnUiThread();
				break;
			}
		}
	}

	private boolean checkBluetooth ()
	{
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
		else
		{
			messageText.setText(R.string.no_bluetooth_support_message);
		}
		return false;
	}

	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == BLUETOOTH_REQUEST_CODE && resultCode < 0)
		{
			startScanning();
		}
	}

	@Override
	public void onGotennaStateChange (GotennaManager.State state)
	{
		switch (state)
		{
			case CONNECTED:
			{
				updateMsgTextOnUiThread();
				break;
			}
			case STOPPED:
			{
				Toast.makeText(getApplicationContext(), getString(R.string.gotenna_disconnected), Toast.LENGTH_SHORT).show();
				updateMsgTextOnUiThread();
				break;
			}
		}
	}

	@Override
	public void onGotennaMessageReceived (IAircraftMessage msg)
	{

	}
}
