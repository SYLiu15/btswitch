/*
 * File Name: MainActivity.java
 * Editor: Suyang Liu
 * Notes: Certain functions sourced from Nordic Semiconductor example code.
 * Date: May 2019
 */

/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.syl.btswitch;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    //private static final int UART_PROFILE_READY = 10;
    //public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    //private static final int STATE_OFF = 10;

    private int mState = UART_PROFILE_DISCONNECTED;

    private String mDeviceName;
    private String mDeviceAddress;
    private TextView mDeviceTitle;
    private TextView mConnectionState;
    private TextView mDataField;
    private UartService mService = null;
    //private boolean mDeviceFound = false;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter;

    private String mData1 = null;
    private String mData2 = null;
    private String mFeatherData = null;
    private String mControlData = "1193046";

    private boolean mOutlet1State = false;
    private boolean mOutlet2State = false;
    private Button mOutlet1;
    private Button mOutlet2;
    private ImageView mStatusIndicator;

    // Code to manage Service lifecycle.
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName componentName, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        mState = UART_PROFILE_CONNECTED;
                        updateDeviceText();
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        updateDeviceText();
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
                /*final StringBuilder newString = new StringBuilder();
                for (byte aByte: txValue) {
                    String bytehex = String.format("%02X", (byte) aByte);
                    newString.append(bytehex);
                }*/
                //Charset cs = Charset.defaultCharset();
                String newString = bytesToHex(txValue);
                updateData(newString/*.toString().trim()*/);
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                Toast myToast = Toast.makeText(getApplicationContext(), ("Device doesn't support UART. Disconnecting"), Toast.LENGTH_SHORT);
                myToast.setGravity(Gravity.CENTER,0,0);
                myToast.show();

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

        mDeviceTitle = findViewById(R.id.deviceLabelText);
        mConnectionState = findViewById(R.id.deviceStatusText);
        //mDataField = findViewById(R.id.test_textview);
        mOutlet1 = findViewById(R.id.outlet1);
        mOutlet2 = findViewById(R.id.outlet2);
        mStatusIndicator = findViewById((R.id.statusIndicator));


        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        service_init();
    }

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
    }

    public void testButton(View view) {
        Toast myToast = Toast.makeText(this, String.valueOf(R.string.device_name), Toast.LENGTH_SHORT);
        myToast.setGravity(Gravity.CENTER,0,0);
        myToast.show();
    }

    public void showBtMenu(View view) {
        if (mDevice != null) {
            mService.disconnect();
            mDeviceName = null;
            mState = UART_PROFILE_DISCONNECTED;
        }
        Intent myIntent = new Intent(this, DeviceScanActivity.class);
        startActivityForResult(myIntent, REQUEST_SELECT_DEVICE);
        updateDeviceText();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && returnIntent != null) {
                    mDeviceName = returnIntent.getStringExtra("DEVICE_NAME");
                    mDeviceAddress = returnIntent.getStringExtra("DEVICE_ADDRESS");

                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mDeviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    mService.connect(mDeviceAddress);

                    updateDeviceText();
                } else {
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
    }

    private void updateData(String data) {
        if (mData1 == null) {
            mData1 = data;
        } else {
            mData2 = mData1;
            mData2 += data;
            mData1 = null;

            /*for (int i = 0; i < mData2.length(); i+=2) {
                String temp = "";
                temp += mData2(i) + mData2(i+1);
                char =
                mFeatherData =
            }*/
            mFeatherData = mData2;

            //mDataField.setText(mFeatherData);
        }
    }

    private void updateDeviceText() {
        if (mDeviceName != null) {
            mDeviceTitle.setText(mDeviceName);
        } else {
            mDeviceTitle.setText(R.string.disconnected);
        }

        if (mState == UART_PROFILE_CONNECTED) {
            mConnectionState.setText(R.string.connected);
            mStatusIndicator.setImageResource(R.drawable.on);
        } else {
            mConnectionState.setText(R.string.disconnected);
            mStatusIndicator.setImageResource(R.drawable.off);
        }
    }

    public void sendTestData(View view) {
        if (mDevice != null) {
            byte[] value = mControlData.getBytes();
            mService.writeRXCharacteristic(value);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0XFF;
            hexChars[i*2] = hexArray[value >>> 4];
            hexChars[i*2+1] = hexArray[value & 0X0F];
        }
        String newString = new String(hexChars);

        /*StringBuilder returnString = new StringBuilder();
        for (int i = 0; i < newString.length(); i+=2) {
            String temp = newString.substring(i, i+2);

            int someInt = Integer.parseInt(temp,16);

            if (someInt > 127) {
                returnString.append('?');
            } else if (someInt < 33) {
                returnString.append('%');
            } else {
                returnString.append((char) someInt);
            }
        }
        return returnString.toString();*/
        return newString;
    }

    public void outletButton (View view) {
        switch (view.getId()) {
            case R.id.outlet1:
                if (mOutlet1State) {
                    mOutlet1State = false;
                } else {
                    mOutlet1State = true;
                }

                //do stuff
                toggleButton(1);
                break;

            case R.id.outlet2:
                if (mOutlet2State) {
                    mOutlet2State = false;
                } else {
                    mOutlet2State = true;
                }

                //do stuff
                toggleButton(2);
                break;
        }
    }

    public void toggleButton(int value) {
        if (value == 1) {
            if (mOutlet1State) {
                mOutlet1.setBackgroundResource(R.drawable.outlet_button_on);
            } else {
                mOutlet1.setBackgroundResource(R.drawable.outlet_button_off);
            }
        } else {
            if (mOutlet2State) {
                mOutlet2.setBackgroundResource(R.drawable.outlet_button_on);
            } else {
                mOutlet2.setBackgroundResource(R.drawable.outlet_button_off);
            }
        }
    }
}
