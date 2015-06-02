package com.teamadhoc.wifigroupstream.dj;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.teamadhoc.wifigroupstream.R;

/**
 * A BroadcastReceiver that notifies of important Wi-Fi P2P events.
 */
public class ServerWiFiDirectBroadcastReceiver extends BroadcastReceiver{
    public static final String TAG = "WiFiDirectBroadcastRec";

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private DJActivity activity;

    public ServerWiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                             DJActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wi-Fi P2P is enabled
                activity.setIsWifiP2pEnabled(true);
            } else {
                // Wi-Fi P2P is not enabled
                activity.setIsWifiP2pEnabled(false);
                activity.resetDeviceList();
            }
            Log.d(TAG, "P2P state changed - " + state);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // The peer list has changed.
            // Request available peers from the Wi-Fi P2P manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null) {
                manager.requestPeers(channel, (WifiP2pManager.PeerListListener) activity.getFragmentManager()
                        .findFragmentById(R.id.frag_dj_devices));
            }
            Log.d(TAG, "P2P peers changed");
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                // We are connected with the other device.
                // Request connection info to find group owner IP
                ServerDeviceListFragment fragment = (ServerDeviceListFragment) activity
                        .getFragmentManager().findFragmentById(R.id.frag_dj_devices);
                manager.requestConnectionInfo(channel, fragment);
            } else {
                // It's a disconnect
                activity.resetDeviceList();
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's Wi-Fi state changing
            ServerDeviceListFragment fragment = (ServerDeviceListFragment) activity.getFragmentManager()
                    .findFragmentById(R.id.frag_dj_devices);
            fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
        }
    }
}
