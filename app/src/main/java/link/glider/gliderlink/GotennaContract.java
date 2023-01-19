package link.glider.gliderlink;

import link.glider.gliderlink.messaging.IAircraftMessage;

public interface GotennaContract
{
    interface Gotenna
    {
        void search();
        void stop();
        boolean transmit(final byte[] data);
        void addListener(final GotennaManagerListener listener);
        void removeListener(final GotennaManagerListener listener);
    }

    interface GotennaManagerListener
    {
        void onGotennaStateChange (final link.glider.gliderlink.GotennaManager.State state);
        void onGotennaMessageReceived (final IAircraftMessage msg);
    }
}
