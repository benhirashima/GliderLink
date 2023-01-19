package link.glider.gliderlink;

import link.glider.gliderlink.messaging.IAircraftMessage;

public interface IAircraftMessageListener
{
	void onAircraftMessage(IAircraftMessage msg);
}
