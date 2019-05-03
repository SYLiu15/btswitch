package com.syl.btswitch;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.LocalBroadcastManager;
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

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_READY = 10;
    //public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;

    private int mState = UART_PROFILE_DISCONNECTED;

    private String mDeviceName;
    private String mDeviceAddress;
    private TextView mDeviceTitle;
    private TextView mConnectionState;
    private TextView mDataField;
    //private BluetoothLeService mBluetoothLeService;
    private UartService mService = null;
    private boolean mDeviceFound = false;
    private boolean mConnected = false;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;

    private String mData1 = null;
    private String mFeatherData = null;
    private String mControlData = "1193046";

    // Code to manage Service lifecycle.
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        //@Override
        public void onServiceConnected(ComponentName componentName, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            //mBluetoothLeService.connect(mDeviceAddress);
            //mService.connect(mDeviceAddress);
        }

        //@Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    /*private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
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
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };*/



    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        /*String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
                        btnConnectDisconnect.setText("Disconnect");
                        edtMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                        listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);*/
                        //updateConnectionState(R.string.connected);
                        mState = UART_PROFILE_CONNECTED;
                        updateDeviceText();
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        /*String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
                        btnConnectDisconnect.setText("Connect");
                        edtMessage.setEnabled(false);
                        btnSend.setEnabled(false);
                        ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                        listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());*/
                        mState = UART_PROFILE_DISCONNECTED;
                        updateDeviceText();
                        mService.close();
                        //setUiState();

                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                final StringBuilder newString = new StringBuilder();
                for (byte aByte: txValue) {
                    String bytehex = String.format("%02X", (byte) aByte);
                    newString.append(bytehex)/*.append(" ")*/;
                }
                displayData(newString.toString().trim());
                /*runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            //String text = new String(txValue, "UTF-8");
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            listAdapter.add("["+currentDateTimeString+"] RX: "+newString);
                            messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });*/
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                //showMessage("Device doesn't support UART. Disconnecting");

                //Add something here
                mService.disconnect();
            }


        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onStart() {
        super.onStart();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDeviceTitle = (TextView) findViewById(R.id.deviceLabelText);
        mConnectionState = (TextView) findViewById(R.id.deviceStatusText);
        mDataField = (TextView) findViewById(R.id.test_textview);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        service_init();
    }

    /*@Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }*/

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        /*super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;*/
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
        if (mDevice != null) {
            /*unbindService(mServiceConnection);
            mService = null;*/
            mService.disconnect();
            mDeviceName = null;
            mConnected = false;
        }
        Intent myIntent = new Intent(this, DeviceScanActivity.class);
        startActivityForResult(myIntent, REQUEST_SELECT_DEVICE);
        updateDeviceText();
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
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && returnIntent != null) {
                    mDeviceFound = true;
                    mDeviceName = returnIntent.getStringExtra("DEVICE_NAME");
                    mDeviceAddress = returnIntent.getStringExtra("DEVICE_ADDRESS");
                    //Toast.makeText(this, mDeviceAddress, Toast.LENGTH_SHORT).show();


                    //String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mDeviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    //((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mService.connect(mDeviceAddress);

                    updateDeviceText();
                } else {
                    mDeviceFound = false;
                    updateDeviceText();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
        /*super.onActivityResult(requestCode, resultCode, returnIntent);
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                mDeviceFound = true;
                mDeviceName = returnIntent.getStringExtra("DEVICE_NAME");
                mDeviceAddress = returnIntent.getStringExtra("DEVICE_ADDRESS");


                //Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                //bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                mService.connect(mDeviceAddress);
                updateDeviceText();
            } else if (resultCode == Activity.RESULT_CANCELED){
                mDeviceFound = false;
                updateDeviceText();
            }
        }*/
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
            mDataField.setText(mFeatherData);
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

        if (mState == UART_PROFILE_CONNECTED) {
            mConnectionState.setText(R.string.connected);
        } else {
            mConnectionState.setText(R.string.disconnected);
        }
    }

    public void sendTestData(View view) {
        /*mWriteCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(SampleGattAttributes.TX_UART_SERVICE),
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        mWriteCharacteristic.setValue(value);*/

        if (mDevice != null) {
            byte[] value = mControlData.getBytes();
            mService.writeRXCharacteristic(value);
        }
    }

    /*private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }*/
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }


}
