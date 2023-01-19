package link.glider.gliderlink;

import android.os.Handler;

import com.gotenna.sdk.TLVSection;
import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommand;
import com.gotenna.sdk.commands.GTCommandCenter;
import com.gotenna.sdk.commands.GTError;
import com.gotenna.sdk.commands.Place;
import com.gotenna.sdk.exceptions.GTDataMissingException;
import com.gotenna.sdk.interfaces.GTErrorListener;
import com.gotenna.sdk.messages.GTBaseMessageData;
import com.gotenna.sdk.messages.GTMessageData;
import com.gotenna.sdk.responses.GTResponse;
import com.gotenna.sdk.types.GTDataTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import link.glider.gliderlink.messaging.BundledLocationMsg;
import link.glider.gliderlink.messaging.IAircraftMessage;
import link.glider.gliderlink.messaging.MessageType;
import link.glider.gliderlink.messaging.MultiLocationMsg;
import link.glider.gliderlink.messaging.SectionType;

public class GotennaManager implements GotennaContract.Gotenna, GTCommandCenter.GTMessageListener, GTConnectionManager.GTConnectionListener
{
    private static GotennaManager sharedInstance = null;
    private static final Logger log = LoggerFactory.getLogger(GotennaManager.class);
    private static final int MAX_SCAN_RETRIES = 10;
    private static final int SCAN_TIMEOUT = 25000; // 25 seconds

    private int scanRetries = 0;
    private long timeGotennaConnectedMs = 0;
    private String username;
    private long gid;

    private final GTConnectionManager gtConnMan;
    private final GTCommandCenter gtCommandCenter;
    private final List<GotennaContract.GotennaManagerListener> listeners = Collections.synchronizedList(new ArrayList<GotennaContract.GotennaManagerListener>());


    private final Handler handler =  new Handler();

    private final Runnable scanTimeoutRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            log.info("scan timeout.");
            handler.postDelayed(retryScanningRunnable, SCAN_TIMEOUT);
        }
    };

    private final Runnable retryScanningRunnable = new Runnable() {
        @Override
        public void run()
        {
            retryScanning();
        }
    };

    private volatile State state = State.STOPPED;

    public enum State
    {
        STOPPED,
        SEARCHING,
        CONNECTED,
        ;

        State(){}
    }

    // lax singleton
    public static GotennaManager getSharedInstance()
    {
        if (sharedInstance == null)
        {
            sharedInstance = new GotennaManager(GTConnectionManager.getInstance(), GTCommandCenter.getInstance());
        }
        return sharedInstance;
    }

    public GotennaManager(final GTConnectionManager gtConnMan, final GTCommandCenter gtCommandCenter) throws IllegalArgumentException
    {
        if (gtConnMan == null || gtCommandCenter == null) throw new IllegalArgumentException("Arguments cannot be null.");
        this.gtConnMan = gtConnMan;
        this.gtCommandCenter = gtCommandCenter;
    }

    public State getState()
    {
        return state;
    }

    public void search()
    {
        changeState(State.SEARCHING);
    }

    public void stop()
    {
        changeState(State.STOPPED);
    }

    // returns true if connected, not if message sent
    public boolean transmit(final byte[] data)
    {
        if (!state.equals(State.CONNECTED)) return false;

        gtCommandCenter.sendBroadcastMessage(data, null, new GTErrorListener()
        {
            @Override
            public void onError (GTError error)
            {
                if (error.getCode() == GTError.DATA_RATE_LIMIT_EXCEEDED)
                {
                    log.error("Data rate limit was exceeded. Skipping transmission.");
                }
                else
                {
                    log.error(error.toString());
                }
                //TODO: handle error?
            }
        });
        return true;
    }

    public void addListener(final GotennaContract.GotennaManagerListener listener)
    {
        if (listener != null)
        {
            listeners.add(listener);
        }
        else
        {
            log.error("Attempted to add null listener.");
        }
    }

    public void removeListener(final GotennaContract.GotennaManagerListener listener)
    {
        if (listener != null)
        {
            listeners.remove(listener);
        }
        else
        {
            log.error("Attempted to remove null listener.");
        }
    }

    private synchronized void changeState(final State toState)
    {
        if (toState == state)
        {
            log.debug("already in state " + toState);
            return;
        }
        log.debug("change state from " + state + " to " + toState);

        // first, run exit routine for the state we're leaving
        switch (state)
        {
            case SEARCHING:
                exitSearchTxState();
                break;
            case CONNECTED:
                exitConnectedState();
                break;
            // STOPPED state omitted
        }

        // next, run enter routine for the state we're changing to
        switch (toState)
        {
            case SEARCHING:
                enterSearchTxState();
                break;
            case CONNECTED:
                enterConnectedState();
                break;
            default:
                enterStoppedState();
        }

        for (GotennaContract.GotennaManagerListener listener : listeners)
        {
            listener.onGotennaStateChange(state);
        }
    }

    private void enterSearchTxState()
    {
        log.trace("enterSearchTxState()");
        state = State.SEARCHING;
        startScanning();
        gtCommandCenter.setMessageListener(this);
    }

    private void exitSearchTxState()
    {
        log.trace("exitSearchTxState()");
        stopScanning();
    }

    private void enterConnectedState()
    {
        log.trace("enterConnectedState()");
        state = State.CONNECTED;
        timeGotennaConnectedMs = System.currentTimeMillis();
        scanRetries = 0;
        setGotennaRegion();
    }

    private void exitConnectedState()
    {
        log.trace("exitConnectedState()");
    }

    private void enterStoppedState()
    {
        log.trace("enterStoppedState()");
        state = State.STOPPED;
        gtCommandCenter.setMessageListener(null);
        gtConnMan.removeGtConnectionListener(this);
        try
        {
            if (gtConnMan.isConnected()) gtConnMan.disconnect();
        }
        catch (Exception e)
        {
            log.error(e.getLocalizedMessage());
        }
    }

    private void startScanning ()
    {
        gtConnMan.addGtConnectionListener(this);
        gtConnMan.scanAndConnect(GTConnectionManager.GTDeviceType.MESH);
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);
    }

    private void stopScanning()
    {
        handler.removeCallbacks(scanTimeoutRunnable);
        handler.removeCallbacks(retryScanningRunnable);
        gtConnMan.stopScan();
    }

    private void retryScanning()
    {
        if (scanRetries >= MAX_SCAN_RETRIES)
        {
            log.info("reached max scan retries. Giving up. :(");
            changeState(State.STOPPED);
            return;
        }
        scanRetries++;
        log.debug("scan retries: " + scanRetries);
        startScanning();
    }

    public void setGotennaGID(final long gid, final String username)
    {
        if (username == null || username.length() == 0 || gid == 0) throw new IllegalArgumentException("Arguments cannot be null or zero.");

        this.gid = gid;
        this.username = username;

        if (state == State.CONNECTED)
        {
            // The UserDataStore automatically saves the user's basic info after setGoTennaGID is called
            gtCommandCenter.setGoTennaGID(gid, username, new GTCommand.GTCommandResponseListener()
            {
                @Override
                public void onResponse (GTResponse response)
                {
                    if (response.getResponseCode() != GTDataTypes.GTCommandResponseCode.POSITIVE)
                    {
                        log.error("Failed to set goTenna GID.");
                        changeState(State.STOPPED);
                    }
                }
            }, new GTErrorListener()
            {
                @Override
                public void onError (GTError error)
                {
                    log.error("Failed to set goTenna GID.");
                    changeState(State.STOPPED);
                }
            });
        }
    }

    private void setGotennaRegion()
    {
        gtCommandCenter.sendSetGeoRegion(Place.NORTH_AMERICA, // TODO: change
                new GTCommand.GTCommandResponseListener()
                {
                    @Override
                    public void onResponse(GTResponse gtResponse)
                    {
                        log.info("sendSetGeoRegion() response: " + gtResponse.toString());
                    }
                },
                new GTErrorListener() {
                    @Override
                    public void onError(GTError gtError)
                    {
                        log.error("sendSetGeoRegion() response: " + gtError.toString());
                        changeState(State.STOPPED);
                    }
                }
        );
    }

    @Override
    public void onConnectionStateUpdated (GTConnectionManager.GTConnectionState gtConnectionState)
    {
        log.debug("onConnectionStateUpdated(" + gtConnectionState.toString() + ")");
        switch (gtConnectionState)
        {
            case CONNECTED:
            {
                if (state == State.SEARCHING)
                {
                    changeState(State.CONNECTED);
                }
                else
                {
                    log.warn("XCVR connected while not searching.");
                }
                break;
            }
            case DISCONNECTED:
            {
                changeState(State.STOPPED);
                break;
            }
        }
    }

    @Override
    public void onIncomingMessage (GTBaseMessageData gtBaseMessageData)
    {
        // for goTenna message types
    }

    public void onIncomingMessage(GTMessageData msgData)
    {
//        if (timeGotennaConnectedMs == 0 || System.currentTimeMillis() - timeGotennaConnectedMs < 5000) return; //ignore stored messages that come in right after connection

        ArrayList<TLVSection> tlvSections = TLVSection.tlvSectionsFromData(msgData.getDataToProcess());
        byte messageType = getMessageType(tlvSections);
//		long timeOffsetMs = System.currentTimeMillis() - msgData.getMessageSentDate().getTime(); // difference between our time and sender's time, assuming we received message instantaneously TODO:what if sender time is way out of sync?

        if (messageType == MessageType.SINGLE_LOCATION.getValue() || messageType == MessageType.MULTI_LOCATION.getValue())
        {
            MultiLocationMsg msg;
            try
            {
                msg = new MultiLocationMsg(tlvSections, msgData);
                notifyListeners(msg);
            }
            catch (GTDataMissingException e)
            {
                log.error(e.getLocalizedMessage());
                return;
            }

            log.debug(String.format("Received multi-location message with %d locations.", msg.getBundledLocationMsgs().size()));

            for(BundledLocationMsg locMsg : msg.getBundledLocationMsgs())
            {
                notifyListeners(msg);
            }
        }
    }

    private byte getMessageType(ArrayList<TLVSection> tlvSections)
    {
        for(TLVSection section : tlvSections)
        {
            if (section.getType() == SectionType.MESSAGE_TYPE.getValue()) return section.getValue()[0];
        }
        return MessageType.SINGLE_LOCATION.getValue();
    }

    private void notifyListeners(IAircraftMessage msg)
    {
        for (GotennaContract.GotennaManagerListener listener : listeners)
        {
            if (listener != null) listener.onGotennaMessageReceived(msg);
        }
    }
}
