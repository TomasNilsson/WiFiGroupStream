package com.teamadhoc.wifigroupstream.speaker;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.dj.ServerSocketHandler;

/**
 * The implementation of a ClientSocketHandler used by the Wi-Fi P2P clients.
 * Inspired by https://github.com/bryan-y88/Musics_Around
 */

public class ClientSocketHandler extends Thread {
    private static final String TAG = "ClientSocketHandler";
    private Handler handler;
    private InetAddress address;
    private Socket socket;

    // For syncing time with the server
    private Timer timer = null;

    // A time out for connecting to a server, unit is in milliseconds, 0 for never timing out
    private static final int CONN_TIMEOUT = 0;
    private static final int BUFFER_SIZE = 256;
    public static final int EVENT_RECEIVE_MSG = 100;
    public static final int CLIENT_CALLBACK = 101;

    public ClientSocketHandler(Handler handler, InetAddress groupOwnerAddress, Timer timer) {
        this.handler = handler;
        this.address = groupOwnerAddress;
        this.timer = timer;
    }

    @Override
    public void run() {
        // Let the UI thread control the server
        handler.obtainMessage(CLIENT_CALLBACK, this).sendToTarget();

        // Connect the socket first
        connect();

        // Thread will stop when disconnect is called, at that point the socket
        // should be closed and nullified
        while (socket != null) {
            try {
                InputStream iStream = socket.getInputStream();
                OutputStream oStream = socket.getOutputStream();

                // Clear the buffer before reading
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytes;

                // Read from the InputStream
                bytes = iStream.read(buffer);
                if (bytes == -1) {
                    continue;
                }

                String recMsg = new String(buffer);

                String[] cmdString = recMsg.split(ServerSocketHandler.CMD_DELIMITER);

                Log.d(TAG, "Command received: " + recMsg);

                // *** Time Sync here ***
                // Receiving messages should be as fast as possible to ensure
                // the successfulness of time synchronization
                if (cmdString[0].equals(ServerSocketHandler.SYNC_CMD) && cmdString.length > 1) {
                    // Check if we have received a timer parameter,
                    // if so, set the time, then send back an Acknowledgment
                    timer.setCurrTime(Long.parseLong(cmdString[1]));

                    // Just send the same message back to the server
                    oStream.write(recMsg.getBytes());
                    Log.d(TAG, "Command sent: " + recMsg);
                }

                handler.obtainMessage(EVENT_RECEIVE_MSG, buffer).sendToTarget();
            }
            // This is an ok exception, because someone could have wanted this
            // connection to be closed in the middle of socket read
            catch (SocketException e) {
                Log.d(TAG, "Socket connection has ended.", e);
                disconnect();
            }
            catch (IOException e) {
                Log.e(TAG, "Unexpectedly disconnected during socket read.", e);
                disconnect();
            }
            catch (NumberFormatException e) {
                Log.e(TAG, "Cannot parse time received from server", e);
                disconnect();
            }
        }
    }

    public void connect() {
        if (socket == null || socket.isClosed()) {
            socket = new Socket();
        }

        try {
            socket.bind(null);
            socket.connect(new InetSocketAddress(address.getHostAddress(),
                    ServerSocketHandler.SERVER_PORT), CONN_TIMEOUT);
            Log.d(TAG, "Connected to server");
            socket.setSoTimeout(CONN_TIMEOUT);
        }catch (IOException e) {
            Log.e(TAG, "Can't connect to server.", e);
            disconnect();
        }
    }

    public void disconnect() {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
            socket = null;
        } catch (IOException e) {
            Log.e(TAG, "Could not close socket upon disconnect.", e);
            socket = null;
        }
    }
}

