package com.teamadhoc.wifigroupstream.speaker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.speaker.ClientDeviceListFragment.SpeakerFragmentListener;

public class SpeakerActivity extends Activity implements ChannelListener,
        SpeakerFragmentListener, NfcAdapter.CreateNdefMessageCallback, NfcAdapter.OnNdefPushCompleteCallback {
    public final static int SPEAKER_MODE = 0; // Indicates the least inclination to be a group owner
    public static final String TAG = "SpeakerActivity";
    private static final int NFC_MESSAGE_SENT = 1;
    private WifiP2pManager manager;
    private NfcAdapter nfcAdapter;
    private boolean isWifiP2pEnabled = false;
    private boolean channelRetried = false;
    private String myMac;
    private Timer timer;
    private CountDownTimer keepAliveTimer;
    private static final int KEEPALIVE_INTERVAL = 5000; // Keep the Wi-Fi alive every 5 seconds
    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speaker);

        // Intent filters to catch the Wi-Fi Direct events
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        // Start a timer with 25 ms precision
        this.timer = new Timer(Timer.DEFAULT_TIMER_PRECISION);
        // Asynchronous call to start a timer
        this.timer.startTimer();

        keepAliveTimer = new CountDownTimer(KEEPALIVE_INTERVAL, KEEPALIVE_INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                enableWifi();
                keepAliveTimer.start();
            }
        };

        // Check for available NFC Adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show();
        } else {
            // Register callback to set NDEF message
            nfcAdapter.setNdefPushMessageCallback(this, this);
            // Register callback to listen for message-sent success
            nfcAdapter.setOnNdefPushCompleteCallback(this, this);
        }
    }

    // Enable Wi-Fi if it has been disabled
    public void enableWifi() {
        WifiManager wifiManager = (WifiManager) this.getSystemService(this.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new ClientWiFiDirectBroadcastReceiver(manager, channel, this);
        // Register the BroadcastReceiver with the intent values to be matched
        registerReceiver(receiver, intentFilter);

        // Start discovering right away!
        discoverDevices();
        keepAliveTimer.start();
    }

    public void discoverDevices() {
        // Turn on the Wi-Fi P2P
        enableWifi();
        channelRetried = false;

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(SpeakerActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Discovery Initiated.");
            }

            // If we failed, then stop the discovery and start again
            @Override
            public void onFailure(int reasonCode) {
                Log.e(TAG, "Discovery Failed. Error Code is: " + reasonCode);
                manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "Stopping Discovery Failed. Error Code is: " + reason);
                    }

                    @Override
                    public void onSuccess() {
                        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(SpeakerActivity.this, "Discoverying DJs",
                                        Toast.LENGTH_SHORT).show();

                                Log.d(TAG, "Discovery Initiated.");
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                Log.e(TAG, "Discovery Failed. Error Code is: " + reasonCode);
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * Implementation for the CreateNdefMessageCallback interface
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        // Include the MAC address in the NdefMessage
        if (myMac == null) {
            Log.e(TAG, "MAC address not received");
            return null;
        }
        NdefMessage msg = new NdefMessage(NdefRecord.createMime(
                "application/com.teamadhoc.wifigroupstream", myMac.getBytes())
        );
        return msg;
    }

    /**
     * Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        // A handler is needed to send messages to the activity when this
        // callback occurs, because it happens from a binder thread
        handler.obtainMessage(NFC_MESSAGE_SENT).sendToTarget();
    }

    /** This handler receives a message from onNdefPushComplete */
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NFC_MESSAGE_SENT:
                    Toast.makeText(SpeakerActivity.this, "Connecting using NFC...", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        keepAliveTimer.cancel();
    }

    @Override
    public void onDestroy() {
        // Also need to disconnect the client, which is handled by the disconnect method
        disconnect();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_speaker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                if (manager != null && channel != null) {
                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // the Broadcast Receiver instead.
                    Intent intent = new Intent();
                    // Jump to Wi-Fi Direct settings
                    intent.setClassName("com.android.settings",
                            "com.android.settings.Settings$WifiP2pSettingsActivity");
                    startActivity(intent);
                } else {
                    Log.e(TAG, "channel or manager is null");
                }
                return true;

            case R.id.atn_direct_discover:
                discoverDevices();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event. This is merely an UI update.
     */
    public void resetDeviceList() {
        ClientDeviceListFragment fragmentList = (ClientDeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_speaker_devices);

        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
    }

    /*
     * Cancel an ongoing connection in progress.
     */
    @Override
    public void cancelDisconnect() {
        if (manager != null) {
            Log.d(TAG, "Someone requested a cancel connect!");

            final ClientDeviceListFragment fragment = (ClientDeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_dj_devices);

            if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                // disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {
                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Log.e(TAG, "Could not abort connection, the reason is: " + reasonCode);
                    }
                });
            }
        }
    }

    /*
     * This is the main method to connect to a device through Wi-Fi Direct!
     */
    @Override
    public void connect(WifiP2pConfig config) {
        if (manager == null) {
            return;
        }

        // In Speaker mode, we don't want to become the group owner
        WifiP2pConfig newConfig = config;
        newConfig.groupOwnerIntent = SPEAKER_MODE; // Indicates the least inclination to be a group owner.

        manager.connect(channel, newConfig, new ActionListener() {
            @Override
            public void onSuccess() {
                // The Broadcast Receiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(SpeakerActivity.this,
                        "Connection failed. Retrying...", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Wi-Fi Direct connection failed. The error code is: " + reason);
            }
        });

    }

    @Override
    public void disconnect() {
        if (manager == null) {
            return;
        }

        // removeGroup removes the group from the speaker perspective, i.e. other speakers connected
        // to the DJ will continue belonging the the DJ group.
        manager.removeGroup(channel, new ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Log.e(TAG, "Disconnect failed. Reason is: " + reasonCode);
            }

            @Override
            public void onSuccess() {
                Toast.makeText(SpeakerActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Disconnected from a device.");
            }
        });

        // Also need to disconnect the client
        ClientDeviceListFragment fragmentList = (ClientDeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_speaker_devices);

        if (fragmentList != null) {
            fragmentList.stopClient();
        }

        // Also need to stop the music
        stopMusic();
    }

    @Override
    public void onChannelDisconnected() {
        // We will try once more
        if (manager != null && !channelRetried) {
            Toast.makeText(this, "Wi-Fi Direct Channel lost. Trying again...",
                    Toast.LENGTH_LONG).show();
            resetDeviceList();

            channelRetried = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Wi-Fi Direct Channel is still lost. Try disabling / re-enabling Wi-Fi Direct in the P2P Settings.",
                    Toast.LENGTH_LONG).show();
        }

        // Also need to disconnect the client
        ClientDeviceListFragment fragmentList = (ClientDeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_speaker_devices);

        if (fragmentList != null) {
            fragmentList.stopClient();
        }
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void playMusic(String url, long startTime, int startPos) {
        SpeakerMusicFragment fragMusic = (SpeakerMusicFragment) getFragmentManager()
                .findFragmentById(R.id.fragment_speaker_music);

        if (fragMusic != null) {
            fragMusic.playSong(url, startTime, startPos);
        }
    }

    @Override
    public void stopMusic() {
        SpeakerMusicFragment fragMusic = (SpeakerMusicFragment) getFragmentManager()
                .findFragmentById(R.id.fragment_speaker_music);

        if (fragMusic != null) {
            fragMusic.stopMusic();
        }
    }

    @Override
    public Timer retrieveTimer()
    {
        return timer;
    }

    public void updateMyMac(String macAddress) {
        myMac = macAddress;
    }
}

