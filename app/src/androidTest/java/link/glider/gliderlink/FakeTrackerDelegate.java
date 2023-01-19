package link.glider.gliderlink;

import link.glider.gliderlink.messaging.IAircraftMessage;

public class FakeTrackerDelegate implements TrackerContract.TrackerDelegate
{
	@Override
	public void onTrackerStateChange(Tracker.State state)
	{

	}

	@Override
	public void onMessageReceived (IAircraftMessage msg)
	{

	}
}
