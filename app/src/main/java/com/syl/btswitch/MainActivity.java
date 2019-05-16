/*
 * File Name: MainActivity.java
 * Editor: Suyang Liu
 * Notes: Certain functions sourced from Nordic Semiconductor example code.
 * Date: May 2019
 *
 * Description:
 *
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
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
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

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;


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

    //connection info
    private String mDeviceName;
    private String mDeviceAddress;
    private TextView mDeviceTitle;
    private TextView mConnectionState;

    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter;

    //received data packets
    private String mData1 = null;
    private String mFeatherData = null;

    //sent data packets
    private String mPowerLData = "2150004C42";
    private String mPowerRData = "215000523C";
    private String mTimerLData = "2154004C0000";
    private String mTimerRData = "215400520000";

    //status values
    private boolean mOutlet1State = false;
    private boolean mOutlet2State = false;
    private boolean mTimer1State = false;
    private int mTimer1hour = 0;
    private int mTimer1min = 0;
    private boolean mTimer2State = false;
    private int mTimer2hour = 0;
    private int mTimer2min = 0;

    //UI elements
    private View mHide;
    private ImageView mArrow;
    private TextView mUIHideText;
    private TextView mO1Status;
    private TextView mO2Status;
    private TextView mCurrent1;
    private TextView mCurrent2;
    private TextView mTimer1;
    private TextView mTimer2;
    private ImageView mStatusIndicator;

    private Button mOutlet1;
    private Button mOutlet2;
    private Button mTimer1Button;
    private Button mTimer2Button;

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
                String newString = bytesToHex(txValue);
                updateData(newString);
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
        mO1Status = findViewById(R.id.o1Status);
        mO2Status = findViewById(R.id.o2Status);
        mCurrent1 = findViewById(R.id.o1CurrentText);
        mCurrent2 = findViewById(R.id.o2CurrentText);
        mTimer1 = findViewById(R.id.o1TimerText);
        mTimer2 = findViewById(R.id.o2TimerText);
        mStatusIndicator = findViewById((R.id.statusIndicator));

        mHide = findViewById(R.id.uiHide);
        mUIHideText = findViewById(R.id.uiHideText);
        mArrow = findViewById(R.id.arrow);

        mOutlet1 = findViewById(R.id.outlet1);
        mOutlet2 = findViewById(R.id.outlet2);
        mTimer1Button = findViewById(R.id.o1TimerSet);
        mTimer2Button = findViewById(R.id.o2TimerSet);
        mOutlet1.setClickable(false);
        mOutlet2.setClickable(false);
        mTimer1Button.setClickable(false);
        mTimer2Button.setClickable(false);

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
        if (mDeviceName != null) {
            AlertDialog.Builder aDialog = new AlertDialog.Builder(this);
            aDialog.setTitle("Disconnect?");
            String myString = "Disconnect from ";
            myString += mDeviceName;
            myString += "?";
            aDialog.setMessage(myString);

            aDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mService.disconnect();
                    //mDevice = null;
                    mDeviceName = null;
                    mState = UART_PROFILE_DISCONNECTED;

                    mOutlet1State = false;
                    mOutlet2State = false;
                    toggleButton(1);
                    toggleButton(2);
                    updateDeviceText();
                }
            });

            aDialog.setNegativeButton(R.string.cancel,null);

            aDialog.show();
        } else {
            Intent myIntent = new Intent(this, DeviceScanActivity.class);
            startActivityForResult(myIntent, REQUEST_SELECT_DEVICE);
            updateDeviceText();
        }
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
            mFeatherData = mData1;
            mFeatherData += data;
            mData1 = null;

            //overcurrent check

            //update outlets
            if (mFeatherData.charAt(7) == '1' && !mOutlet1State) {
                mOutlet1State = true;
                toggleButton(1);
            } else if (mFeatherData.charAt(7) == '0' && mOutlet1State) {
                mOutlet1State = false;
                toggleButton(1);
            }

            if (mFeatherData.charAt(23) == '1' && !mOutlet2State) {
                mOutlet2State = true;
                toggleButton(2);
            } else if (mFeatherData.charAt(23) == '0' && mOutlet2State) {
                mOutlet2State = false;
                toggleButton(2);
            }

            //update timers
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
            mHide.setVisibility(GONE);
            mUIHideText.setVisibility(GONE);
            mArrow.setVisibility(GONE);
            mOutlet1.setClickable(true);
            mOutlet2.setClickable(true);
            mTimer1Button.setClickable(true);
            mTimer2Button.setClickable(true);

        } else {
            mConnectionState.setText(R.string.disconnected);
            mStatusIndicator.setImageResource(R.drawable.off);
            mHide.setVisibility(VISIBLE);
            mUIHideText.setVisibility(VISIBLE);
            mArrow.setVisibility(VISIBLE);
            mOutlet1.setClickable(false);
            mOutlet2.setClickable(false);
            mTimer1Button.setClickable(false);
            mTimer2Button.setClickable(false);
        }
    }

    public void sendData(String data) {
        if (mDevice != null) {
            //byte[] value = data.getBytes();
            byte[] value = hexToBytes(data);
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
        //String newString = new String(hexChars);

        return new String(hexChars);
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public void outletButton (View view) {
        toggleButton(1);
        if (mState == UART_PROFILE_CONNECTED) {
            switch (view.getId()) {
                case R.id.outlet1:
                    /*if (mOutlet1State) {
                        mOutlet1State = false;
                    } else {
                        mOutlet1State = true;
                    }*/

                    //do stuff
                    //toggleButton(1);
                    sendData(mPowerLData);
                    break;

                case R.id.outlet2:
                    /*if (mOutlet2State) {
                        mOutlet2State = false;
                    } else {
                        mOutlet2State = true;
                    }*/

                    //do stuff
                    //toggleButton(2);
                    sendData(mPowerRData);
                    break;
            }
        }
    }

    public void toggleButton(int value) {
        if (value == 1) {
            if (mOutlet1State) {
                mOutlet1.setBackgroundResource(R.drawable.outlet_button_on);
                mO1Status.setText(R.string.on);
                mO1Status.setTextColor(getResources().getColor(R.color.green));
            } else {
                mOutlet1.setBackgroundResource(R.drawable.outlet_button_off);
                mO1Status.setText(R.string.off);
                mO1Status.setTextColor(getResources().getColor(R.color.red));
            }

        } else {
            if (mOutlet2State) {
                mOutlet2.setBackgroundResource(R.drawable.outlet_button_on);
                mO2Status.setText(R.string.on);
                mO2Status.setTextColor(getResources().getColor(R.color.green));
            } else {
                mOutlet2.setBackgroundResource(R.drawable.outlet_button_off);
                mO2Status.setText(R.string.off);
                mO2Status.setTextColor(getResources().getColor(R.color.red));
            }
        }
    }

    public void timerSet(View view) {
        TimePickerFragment newFragment = TimePickerFragment.newInstance();
        newFragment.show(getSupportFragmentManager(),"TimePicker");
    }
}
