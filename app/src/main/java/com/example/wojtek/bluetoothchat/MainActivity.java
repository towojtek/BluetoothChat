package com.example.wojtek.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;



// moje
import com.example.wojtek.bluetoothchat.SerwisCzatu.StateBT;
//import pl.test.BluetoothChatService.StateBT;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private BluetoothAdapter mBluetoothAdapter;
    private SerwisCzatu mChatService;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private boolean Stop = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "wyglada na to, ze na tym urzedeniu nie ma bluetootha", Toast.LENGTH_LONG).show();
        }

        setContentView(R.layout.activity_main);
        if(savedInstanceState != null)
            setUpChat();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect: {
                Intent intent = new Intent(this, AktywnoscListyUrzadzen.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bluetooth_chat, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == RESULT_OK) {
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    setUpChat();
                } else {
                    Toast.makeText(this, "skoro nie chcesz wlaczyc bluetootha, konczymy", Toast.LENGTH_LONG).show();
                    finish();
                }
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return mChatService;
    }


    private void setUpChat() {
        mConversationArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        ((ListView) findViewById(R.id.in)).setAdapter(mConversationArrayAdapter);
        mChatService = (SerwisCzatu) getLastCustomNonConfigurationInstance();
        if (mChatService == null) {
            mChatService = new SerwisCzatu(mHandler);
        } else {
            mChatService.setHandler(mHandler);
        }
        ((Button) findViewById(R.id.button_send)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView editTextOut = (TextView) findViewById(R.id.edit_text_out);
                String message = editTextOut.getText().toString();
                if (mChatService.getState() == StateBT.CONNECTED) {
                    if (message.length() > 0) {
                        mChatService.write(message);
                        editTextOut.setText("");
                    }
                } else {
                    Toast.makeText(MainActivity.this, "nie jestesmy polaczeni", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void connectDevice(Intent data) {
        String address = data.getStringExtra(AktywnoscListyUrzadzen.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mChatService.connect(device);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        } else {
            if (mChatService == null) {
                setUpChat();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mChatService != null) {
            if (mChatService.getState() == StateBT.NONE) {
                mChatService.start();
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SerwisCzatu.MESSAGE_READ:
                    mConversationArrayAdapter.add("on: " + (String) msg.obj);
                    break;
                case SerwisCzatu.MESSAGE_WRITE:
                    mConversationArrayAdapter.add("ja: " + (String) msg.obj);
                    break;
                case SerwisCzatu.MESSAGE_ACCEPTED:
                    mConversationArrayAdapter.add("podlaczylo sie do nas urzadzenie: " + ((BluetoothDevice) msg.obj).getName());
                    break;
                case SerwisCzatu.MESSAGE_CONNECTED:
                    mConversationArrayAdapter.add("podlaczylismy sie do urzadzenia: " + ((BluetoothDevice) msg.obj).getName());
                    break;
                case SerwisCzatu.MESSAGE_ACCEPTING:
                    mConversationArrayAdapter.add("otwieramy sie na polaczenia przychodzace");
                    break;
                case SerwisCzatu.MESSAGE_CONNECTION_FAILED:
                    mConversationArrayAdapter.add("nie udalo sie polaczyc");
                    break;
                case SerwisCzatu.MESSAGE_CONNECTION_LOST:
                    mConversationArrayAdapter.add("poloczenie zostalo zerwane");
                    break;
            }
        };
    };



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mChatService != null && Stop == true) {
            mChatService.stop();
        }
    }
}
