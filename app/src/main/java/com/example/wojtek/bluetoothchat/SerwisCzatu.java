package com.example.wojtek.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;


public class SerwisCzatu {
    public enum StateBT {
        NONE, // nie robimy nic
        LISTEN, // sluchamy polaczen przychodzacych
        CONNECTING, // nawiazujemy poaczenie wychodzace
        CONNECTED // jestesmy poaczeni z innym urzadzeniem
    }
    public static final int MESSAGE_READ = 0;
    public static final int MESSAGE_WRITE = 1;
    public static final int MESSAGE_ACCEPTED = 2;
    public static final int MESSAGE_CONNECTED = 3;
    public static final int MESSAGE_ACCEPTING = 4;
    public static final int MESSAGE_CONNECTION_FAILED = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;

    private BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final UUID MY_UUID = UUID.fromString("8f9576f5-e839-474f-9f48-ddb1db2237b4");
    private StateBT mStateBT = StateBT.NONE;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private Handler mHandler;


    private class AcceptThread extends Thread {
        private BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            try {
                mmServerSocket = mAdapter.listenUsingRfcommWithServiceRecord(
                        "BluetoothChat", MY_UUID);
            } catch (IOException e) {
                Log.e("@@@", "nie udalo sie sluchac", e);
            }
        }

        @Override
        public void run() {
            boolean loop = true;
            while (loop) {
                synchronized (SerwisCzatu.this) {
                    loop = (mStateBT != StateBT.CONNECTED);
                }
                setName("accepting thread");
                Log.i("@@@", "zaraz zaczne sluchac");
                try {
                    mHandler.obtainMessage(MESSAGE_ACCEPTING).sendToTarget();
                    BluetoothSocket socket = mmServerSocket.accept();
                    Log.i("@@@", "przyszlo polaczenie");
                    mHandler.obtainMessage(MESSAGE_ACCEPTED, socket.getRemoteDevice()).sendToTarget();
                    connected(socket);
                } catch (IOException e) {
                    Log.i("@@@", "wystapil blad podczas sluchania", e);
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e("@@@", "wystapil blad przy zamykaniu soketa", e);
            }
        }
    }

    public class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e("@@@", "nie udalo sie stworzyc soketa");
            }
            mmSocket = tmp;
        }
        @Override
        public void run() {
            setName("connect thread");
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
                mHandler.obtainMessage(MESSAGE_CONNECTED, mmSocket.getRemoteDevice()).sendToTarget();
            } catch (IOException e) {
                mHandler.obtainMessage(MESSAGE_CONNECTION_FAILED).sendToTarget();
                Log.e("@@@", "blad przy nawiazywaniu polaczenia", e);
                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    Log.e("@@@", "blad przy probie zamkni�cia soketa po nieudanej probie polaczenia", e1);
                }
                return;
            }
            synchronized (SerwisCzatu.this) {
                mConnectThread = null;
            }
            connected(mmSocket);
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("@@@", "wystapil blad przy zamykaniu soketa", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.i("@@@", "po nawiazaniu polaczenia nie udalo sie pobrac strumieni", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    byte[] message = new byte[bytes];
                    System.arraycopy(buffer, 0, message, 0, bytes);
                    Log.i("@@@", "dostalismy wiadomosc: " + new String(message));
                    mHandler.obtainMessage(SerwisCzatu.MESSAGE_READ, new String(message)).sendToTarget();
                } catch (IOException e) {
                    Log.e("@@@", "rozlaczylo nas!", e);
                    mHandler.obtainMessage(SerwisCzatu.MESSAGE_CONNECTION_LOST).sendToTarget();
                    SerwisCzatu.this.start();
                    break;
                }
            }
        }
        public void write(byte[] buffer) {
            Log.i("@@@", "serwis wysyla komunikat " + new String(buffer));
            try {
                mmOutStream.write(buffer);
                mHandler.obtainMessage(SerwisCzatu.MESSAGE_WRITE, new String(buffer)).sendToTarget();
            } catch (IOException e) {
                Log.e("@@@", "wystapil blad podczas proby wysylania komunikatu " + buffer, e);
            }
        }
        public void write(String message) {
            write(message.getBytes());
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("@@@", "wystapil blad przy zamykaniu soketa", e);
            }
        }
    }

    public SerwisCzatu(Handler handler) {
        mHandler = handler;
    }

    public StateBT getState() {
        return mStateBT;
    }

    public synchronized void  connected(BluetoothSocket socket) {
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(StateBT.CONNECTED);
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    public synchronized void  setState(StateBT mStateBT) {
        this.mStateBT = mStateBT;
    }

    public synchronized void  start() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(StateBT.LISTEN);
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    public synchronized void  connect(BluetoothDevice device) {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(StateBT.CONNECTING);
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    public void write(byte[] buffer) {
        ConnectedThread t;
        synchronized (this) {
            if (mStateBT != StateBT.CONNECTED) return;
            t = mConnectedThread;
        }
        t.write(buffer);
    }

    public void write(String message) {
        ConnectedThread t;
        synchronized (this) {
            if (mStateBT != StateBT.CONNECTED) return;
            t = mConnectedThread;
        }
        t.write(message);
    }

    public void setHandler(Handler handler) {
        this.mHandler = handler;
    }

    public synchronized void  stop() {
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(StateBT.NONE);
    }
}
