package com.teamadhoc.wifigroupstream.speaker;

import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.dj.ServerSocketHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * A ListFragment that displays available peers on discovery and requests the
 * parent activity to handle user interaction events
 */
public class ClientDeviceListFragment extends ListFragment
        implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, Handler.Callback {

    public static final String TAG = "ClientDeviceList";
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    ProgressDialog progressDialog = null;
    private View contentView = null;
    private WifiP2pDevice device;
    private final Handler handler = new Handler(this);
    private ClientSocketHandler clientThread;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.device_list, null);
        return contentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.setListAdapter(new WiFiPeerListAdapter(getActivity(), R.layout.row_devices, peers));
    }

    // Disconnect the server when this fragment is no longer visible.
    @Override
    public void onDestroyView() {
        stopClient();
        super.onDestroyView();
    }

    public WifiP2pDevice getDevice() {
        return device;
    }

    private static String getDeviceStatus(int deviceStatus) {
        Log.d(TAG, "Peer status :" + deviceStatus);
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";
        }
    }

    /**
     * Perform an action with a peer, depending on its state
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        WifiP2pDevice device = (WifiP2pDevice) getListAdapter().getItem(position);
        switch (device.status) {
            case WifiP2pDevice.AVAILABLE:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to: " + device.deviceName, true, true);

                ((SpeakerFragmentListener) getActivity()).connect(config);
                break;

            case WifiP2pDevice.INVITED:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to abort",
                        "Revoking invitation to: " + device.deviceName, true, true);

                ((SpeakerFragmentListener) getActivity()).cancelDisconnect();
                // Start another discovery
                ((SpeakerFragmentListener) getActivity()).discoverDevices();
                break;

            case WifiP2pDevice.CONNECTED:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to abort",
                        "Disconnecting: " + device.deviceName, true, true);

                ((SpeakerFragmentListener) getActivity()).disconnect();
                // Start another discovery
                ((SpeakerFragmentListener) getActivity()).discoverDevices();
                break;

            // Refresh the list of devices
            case WifiP2pDevice.FAILED:
            case WifiP2pDevice.UNAVAILABLE:
            default:
                ((SpeakerFragmentListener) getActivity()).discoverDevices();
                break;
        }
    }

    /**
     * Array adapter for ListFragment that maintains WifiP2pDevice list.
     */
    private class WiFiPeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

        private List<WifiP2pDevice> items;

        public WiFiPeerListAdapter(Context context, int textViewResourceId,
                                   List<WifiP2pDevice> objects) {
            super(context, textViewResourceId, objects);
            items = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.row_devices, null);
            }
            WifiP2pDevice device = items.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    if (device.status == WifiP2pDevice.INVITED) {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        // Show the invited dialog
                        progressDialog = ProgressDialog.show(getActivity(), "Inviting peer",
                                "Sent invitation to: " + device.deviceName +
                                        "\n\nTap on peer to revoke invitation.", true, true);
                    }
                    bottom.setText(getDeviceStatus(device.status));
                }
            }
            return v;
        }
    }

    /**
     * Update UI for this device.
     */
    public void updateThisDevice(WifiP2pDevice device) {
        this.device = device;
        TextView view = (TextView) contentView.findViewById(R.id.my_name);
        view.setText(device.deviceName);
        view = (TextView) contentView.findViewById(R.id.my_status);
        view.setText(getDeviceStatus(device.status));
        ((SpeakerActivity) getActivity()).updateMyMac(device.deviceAddress);
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
        if (peers.size() == 0) {
            Log.d(TAG, "No devices found");
            return;
        }
    }

    public void clearPeers() {
        peers.clear();
        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case ClientSocketHandler.EVENT_RECEIVE_MSG:
                byte[] readBuf = (byte[]) msg.obj;
                // Construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf);

                // Interpret the command
                String[] cmdString = readMessage.split(ServerSocketHandler.CMD_DELIMITER);

                if (cmdString[0].equals(ServerSocketHandler.PLAY_CMD) && cmdString.length > 3) {
                    try {
                        ((SpeakerFragmentListener) getActivity()).playMusic(cmdString[1],
                                Long.parseLong(cmdString[2]), Integer.parseInt(cmdString[3]));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not convert to a proper time for these two strings: "
                                        + cmdString[2] + " and " + cmdString[3], e);
                    }
                } else if (cmdString[0].equals(ServerSocketHandler.STOP_CMD)
                        && cmdString.length > 0) {
                    ((SpeakerFragmentListener) getActivity()).stopMusic();
                }

                Log.d(TAG, readMessage);
                break;

            case ClientSocketHandler.CLIENT_CALLBACK:
                clientThread = (ClientSocketHandler) msg.obj;
                Log.d(TAG, "Retrieved client thread.");
                break;

            default:
                Log.d(TAG, "Message type: " + msg.what);
                break;
        }
        return true;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        // The group owner IP is now known.
        if (info.groupFormed && info.isGroupOwner) {
            // TODO: clear remembered groups (how?) so that DJ mode always becomes group owner
            // In Speaker mode, we must not be the group owner, or else we have a problem
            Log.e(TAG, "Speaker Mode became the group owner.");

            Toast.makeText(contentView.getContext(), "Error: Speaker Mode became the group owner.",
                    Toast.LENGTH_SHORT).show();

        } else if (info.groupFormed) {
            // WARNING:
            // depends on the timing, if we don't get a server back in time,
            // we may end up running multiple threads of the client instance!
            if (this.clientThread == null) {
                Thread client = new ClientSocketHandler(this.handler, info.groupOwnerAddress,
                        ((SpeakerFragmentListener) getActivity()).retrieveTimer());
                client.start();
            }

            Toast.makeText(contentView.getContext(), "Speaker client started.", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopClient() {
        if (clientThread != null) {
            clientThread.disconnect();
            clientThread = null;
        }
    }

    /**
     * An interface-callback for the activity to listen to fragment interaction events.
     */
    public interface SpeakerFragmentListener {
        void cancelDisconnect();
        void connect(WifiP2pConfig config);
        void disconnect();
        void discoverDevices();
        void playMusic(String url, long startTime, int startPos);
        void stopMusic();
        Timer retrieveTimer();
    }
}

