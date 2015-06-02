package com.teamadhoc.wifigroupstream.dj;

import NanoHTTPD.NanoHTTPD;
import NanoHTTPD.SimpleWebServer;
import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
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
import com.teamadhoc.wifigroupstream.Utilities;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * A ListFragment that displays available peers on discovery and requests the
 * parent activity to handle user interaction events
 */
public class ServerDeviceListFragment extends ListFragment
        implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, Handler.Callback {

    public static final String TAG = "ServerDeviceList";
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    ProgressDialog progressDialog = null;
    private View contentView = null;
    private WifiP2pDevice device;
    private final Handler handler = new Handler(this);
    private ServerSocketHandler serverThread;
    private String httpHostIP = null;
    private Activity activity = null;
    private File wwwroot = null;
    private NanoHTTPD httpServer = null;
    public static final int HTTP_PORT = 9002;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;

        // Get the application directory
        wwwroot = activity.getApplicationContext().getFilesDir();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.connected_list, null);
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
        stopServer();
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

                ((DJFragmentListener) getActivity()).connect(config);
                break;

            case WifiP2pDevice.INVITED:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to abort",
                        "Revoking invitation to: " + device.deviceName, true, true);

                ((DJFragmentListener) getActivity()).cancelDisconnect();
                // Start another discovery
                ((DJFragmentListener) getActivity()).discoverDevices();
                break;

            case WifiP2pDevice.CONNECTED:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to abort",
                        "Disconnecting: " + device.deviceName, true, true);

                ((DJFragmentListener) getActivity()).disconnect();
                // Start another discovery
                ((DJFragmentListener) getActivity()).discoverDevices();
                break;

            // Refresh the list of devices
            case WifiP2pDevice.FAILED:
            case WifiP2pDevice.UNAVAILABLE:
            default:
                ((DJFragmentListener) getActivity()).discoverDevices();
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
                        // Show the invited dialog
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }

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
            case ServerSocketHandler.SERVER_CALLBACK:
                serverThread = (ServerSocketHandler) msg.obj;
                Log.d(TAG, "Retrieved server thread.");
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
            try {
                // WARNING:
                // depends on the timing, if we don't get a server back in time,
                // we may end up running multiple threads of the server instance!
                if (this.serverThread == null) {
                    Thread server = new ServerSocketHandler(this.handler);
                    server.start();

                    if (wwwroot != null) {
                        if (httpServer == null) {
                            httpHostIP = info.groupOwnerAddress.getHostAddress();

                            boolean quiet = false;

                            httpServer = new SimpleWebServer(httpHostIP, HTTP_PORT, wwwroot, quiet);

                            try {
                                httpServer.start();
                                Log.d("HTTP Server", "Started web server with IP address: " + httpHostIP);
                                Toast.makeText(contentView.getContext(), "DJ Server started.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            catch (IOException ioe) {
                                Log.e("HTTP Server", "Couldn't start server:\n");
                            }
                        }
                    } else {
                        Log.e("HTTP Server", "Could not retrieve a directory for the HTTP server.");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Can't start server.", e);
            }
        } else if (info.groupFormed) {
            // TODO: clear remembered groups (how?) so that DJ mode always becomes group owner
            // In DJ mode, we must be the group owner, or else we have a problem
            Log.e(TAG, "DJ Mode did not become the group owner.");
            Toast.makeText(contentView.getContext(), "Error: DJ Mode did not become the group owner.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void stopServer() {
        if (serverThread != null) {
            serverThread.disconnectServer();
            serverThread = null;
        }
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public void playMusicOnClients(File musicFile, long startTime, int startPos) {
        if (serverThread == null) {
            Log.d(TAG, "Server has not started. No music will be played remotely.");
            return;
        }

        try {
            // Copy the music file to the web server directory, then pass the URL to the client
            File webFile = new File(wwwroot, URLEncoder.encode(musicFile.getName(), "UTF-8"));

            Utilities.copyFile(musicFile, webFile);

            Uri webMusicURI = Uri.parse("http://" + httpHostIP + ":"
                    + String.valueOf(HTTP_PORT) + "/" + webFile.getName());

            serverThread.sendPlay(webMusicURI.toString(), startTime, startPos);
        } catch (IOException e1) {
            Log.e(TAG, "Can't copy file to HTTP Server.", e1);
        }
    }

    public void stopMusicOnClients() {
        if (serverThread != null) {
            serverThread.sendStop();
        }
    }

    /**
     * An interface-callback for the activity to listen to fragment interaction events.
     */
    public interface DJFragmentListener {
        void cancelDisconnect();
        void connect(WifiP2pConfig config);
        void disconnect();
        void discoverDevices();
    }
}

