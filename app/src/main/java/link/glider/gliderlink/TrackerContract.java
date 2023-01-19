package link.glider.gliderlink;

import link.glider.gliderlink.messaging.IAircraftMessage;

/**
 * Created by Ben on 1/7/2017.
 */

interface TrackerContract
{
	interface Tracker
	{
		void start();
		void startDemo();
		void stop();
	}

	interface TrackerDelegate
	{
		void onTrackerStateChange(link.glider.gliderlink.Tracker.State state);
		void onMessageReceived(IAircraftMessage msg);
	}
}
