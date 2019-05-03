package com.syl.btswitch;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private String mDeviceName;
    private String mDeviceAddress;
    private TextView mDeviceTitle;
    private TextView mConnectionState;
    private TextView mDataField;
    private BluetoothLeService mBluetoothLeService;
    private boolean mDeviceFound = false;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private String mData1 = null;
    private String mFeatherData = null;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                //updateConnectionState(R.string.connected);
                updateDeviceText();
                //invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                //updateConnectionState(R.string.disconnected);
                updateDeviceText();
                //invalidateOptionsMenu();
                //clearUI();
            } /*else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } */else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDeviceTitle = (TextView) findViewById(R.id.deviceLabelText);
        mConnectionState = (TextView) findViewById(R.id.deviceStatusText);
        mDataField = (TextView) findViewById(R.id.test_textview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    public void testButton(View view) {
        Toast myToast = Toast.makeText(this, String.valueOf(mConnected), Toast.LENGTH_SHORT);
        myToast.setGravity(Gravity.CENTER,0,0);
        myToast.show();
    }

    public void showBtMenu(View view) {/*
        //PopupWindow myPopup;
        LayoutInflater myLayout = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View myView = myLayout.inflate(R.layout.bt_popup, null);
        final PopupWindow myPopup = new PopupWindow(myView,ConstraintLayout.LayoutParams.MATCH_PARENT,ConstraintLayout.LayoutParams.MATCH_PARENT,true);
        myPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        myPopup.showAtLocation(myView, Gravity.CENTER,0,0);*/

        //run activity here
        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
            mDeviceName = null;
            mConnected = false;
        }
        updateDeviceText();
        Intent myIntent = new Intent(this, DeviceScanActivity.class);
        startActivityForResult(myIntent, 1);
/*
        Button menuCancel = (Button) myView.findViewById(R.id.btcancel);
        menuCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myPopup.dismiss();
            }
        });*/
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        super.onActivityResult(requestCode, resultCode, returnIntent);
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                mDeviceFound = true;
                mDeviceName = returnIntent.getStringExtra("DEVICE_NAME");
                mDeviceAddress = returnIntent.getStringExtra("DEVICE_ADDRESS");


                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                updateDeviceText();
            } else if (resultCode == Activity.RESULT_CANCELED){
                mDeviceFound = false;
                updateDeviceText();
            }
        }
    }
/*
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }*/

    private void displayData(String data) {
        if (mData1 == null) {
            mData1 = data;
        } else {
            mFeatherData = mData1;
            mFeatherData += data;
            mData1 = null;
            if (mFeatherData != null) {
                mDataField.setText(mFeatherData);
            }
        }
        /*if (data != null) {
            mDataField.setText(data);
        } else {
            mDataField.setText(R.string.disconnected);
        }*/
    }

    private void updateDeviceText() {
        if (mDeviceName != null) {
            mDeviceTitle.setText(mDeviceName);
        } else {
            mDeviceTitle.setText(R.string.disconnected);
        }

        if (mConnected) {
            mConnectionState.setText(R.string.connected);
        } else {
            mConnectionState.setText(R.string.disconnected);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


}
