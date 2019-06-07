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
import android.support.constraint.Group;
import android.support.v4.content.ContextCompat;
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
import static android.view.View.GONE;
import static android.view.View.VISIBLE;


public class MainActivity extends AppCompatActivity implements TimePickerFragment.OnCompleteListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;

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
    private String mTimer1Data = "2154004C";
    private String mTimer2Data = "21540052";

    //status values
    private boolean mOutlet1State = false;
    private boolean mOutlet2State = false;
    private boolean mO1Verified = true;
    private int mO1Count = 0;
    private boolean mO2Verified = true;
    private int mO2Count = 0;

    private boolean mTimer1State = false;
    private int mTimer1hour = 1;
    private int mTimer1min = 0;
    private boolean mTimer2State = false;
    private int mTimer2hour = 1;
    private int mTimer2min = 0;

    //UI elements
    private Group mHideGroup;
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

        mHideGroup = findViewById(R.id.hideGroup);

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
        disconnectDevice();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        disconnectDevice();
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

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
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
                    disconnectDevice();
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

    void disconnectDevice() {
        mService.disconnect();
        mDeviceName = null;
        mState = UART_PROFILE_DISCONNECTED;

        mOutlet1State = false;
        mOutlet2State = false;
        mTimer1State = false;
        mTimer2State = false;
        toggleButton(1);
        toggleButton(2);
        displayData();
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

    private void updateDeviceText() {
        if (mDeviceName != null) {
            mDeviceTitle.setText(mDeviceName);
        } else {
            mDeviceTitle.setText(R.string.disconnected);
        }

        if (mState == UART_PROFILE_CONNECTED) {
            mConnectionState.setText(R.string.connected);
            mStatusIndicator.setImageResource(R.drawable.on);
            mHideGroup.setVisibility(GONE);
            mOutlet1.setClickable(true);
            mOutlet2.setClickable(true);
            mTimer1Button.setClickable(true);
            mTimer2Button.setClickable(true);

        } else {
            mConnectionState.setText(R.string.disconnected);
            mStatusIndicator.setImageResource(R.drawable.off);
            mHideGroup.setVisibility(VISIBLE);
            mOutlet1.setClickable(false);
            mOutlet2.setClickable(false);
            mTimer1Button.setClickable(false);
            mTimer2Button.setClickable(false);
        }
    }

    private void updateData(String data) {
        if (mData1 == null) {
            mData1 = data;
        } else {
            mFeatherData = mData1;
            mFeatherData += data;
            mData1 = null;

            //packet check
            if (getStringFromByte(0).equals("21") && getStringFromByte(1).equals("53") && mFeatherData.length() == 42) {
                //overcurrent check
                if (getStringFromByte(18).equals("01")) {
                    AlertDialog.Builder aDialog = new AlertDialog.Builder(this);
                    aDialog.setTitle("Over-current Warning!");
                    aDialog.setMessage(R.string.overcurrent_warning);

                    aDialog.setPositiveButton(R.string.ok,null);
                    aDialog.show();
                    return;
                }

                //update outlets
                //left on/off
                if (getStringFromByte(3).equals("01") && !mOutlet1State) {
                    mOutlet1State = true;
                    mO1Verified = true;
                    mO1Count = 0;
                    toggleButton(1);
                } else if (getStringFromByte(3).equals("00") && mOutlet1State) {
                    mOutlet1State = false;
                    mO1Verified = true;
                    mO1Count = 0;
                    toggleButton(1);
                } else if (mO1Count >= 1) {
                    mO1Verified = true;
                    mO1Count = 0;
                    toggleButton(1);

                    Toast myToast = Toast.makeText(this, "Error sending data for Left Outlet", Toast.LENGTH_SHORT);
                    myToast.setGravity(Gravity.CENTER,0,0);
                    myToast.show();
                } else if (!mO1Verified) {
                    mO1Count++;
                }

                //right on/off
                if (getStringFromByte(11).equals("01") && !mOutlet2State) {
                    mOutlet2State = true;
                    mO2Verified = true;
                    mO2Count = 0;
                    toggleButton(2);
                } else if (getStringFromByte(11).equals("00") && mOutlet2State) {
                    mOutlet2State = false;
                    mO2Verified = true;
                    mO2Count = 0;
                    toggleButton(2);
                } else if (mO2Count >= 1) {
                    mO2Verified = true;
                    mO2Count = 0;
                    toggleButton(2);

                    Toast myToast = Toast.makeText(this, "Error sending data for Right Outlet", Toast.LENGTH_SHORT);
                    myToast.setGravity(Gravity.CENTER,0,0);
                    myToast.show();
                } else if (!mO2Verified) {
                    mO2Count++;
                }

                //update timers
                //left on/off
                if (getStringFromByte(6).equals("01") && !mTimer1State) {
                    mTimer1State = true;
                } else if (getStringFromByte(6).equals("00") && mTimer1State) {
                    mTimer1State = false;
                }

                //right on/off
                if (getStringFromByte(14).equals("01") && !mTimer2State) {
                    mTimer2State = true;
                } else if (getStringFromByte(14).equals("00") && mTimer2State) {
                    mTimer2State = false;
                }

                //display data
                displayData();
            }
        }
    }

    private void displayData() {
        //outlet 1
        //print current
        if (mOutlet1State) {
            //print value
            mCurrent1.setText(returnCurrent(4));
        } else {
            mCurrent1.setText(R.string.off);
        }
        //print timer data
        if (mTimer1State) {
            //print time remaining
            mTimer1.setText(returnControllerTime(7));
        } else {
            mTimer1.setText(R.string.disabled);
        }

        //outlet 2
        //print current
        if (mOutlet2State) {
            //print value
            mCurrent2.setText(returnCurrent(12));
        } else {
            mCurrent2.setText(R.string.off);
        }
        //print timer data
        if (mTimer2State) {
            //print time remaining
            mTimer2.setText(returnControllerTime(15));
        } else {
            mTimer2.setText(R.string.disabled);
        }
    }

    private String returnCurrent (int index) {
        String myString = "";
        myString += mFeatherData.charAt(index*2);
        myString += mFeatherData.charAt(index*2+1);
        myString += mFeatherData.charAt(index*2+2);
        myString += mFeatherData.charAt(index*2+3);

        int myInt = Integer.parseInt(myString,16);
        float someFloat = ((float)myInt/1000)*120;
        String returnString = Float.toString(someFloat);
        returnString += " W";
        return returnString;
    }

    private String returnControllerTime(int index) {
        String tempString = "";
        tempString += mFeatherData.charAt(index*2);
        tempString += mFeatherData.charAt(index*2+1);
        tempString += mFeatherData.charAt(index*2+2);
        tempString += mFeatherData.charAt(index*2+3);
        int seconds = Integer.parseInt(tempString,16);

        String returnString = "";
        int hourRem = seconds/3600;
        int minRem = (seconds%3600)/60;
        int secRem = (seconds%3600)%60;
        if (hourRem != 0) {
            returnString += hourRem;
            returnString += "h ";
        }
        if (minRem != 0) {
            returnString += minRem;
            returnString += "m ";
        }
        returnString += secRem;
        returnString += "s";

        return returnString;
    }

    public void outletButton (View view) {
        if (mState == UART_PROFILE_CONNECTED) {
            switch (view.getId()) {
                case R.id.outlet1:
                    mO1Status.setText(R.string.switching);
                    mO1Status.setTextColor(ContextCompat.getColor(this, R.color.colorText));
                    mO1Verified = false;
                    sendData(mPowerLData);
                    break;

                case R.id.outlet2:
                    mO2Status.setText(R.string.switching);
                    mO2Status.setTextColor(ContextCompat.getColor(this, R.color.colorText));
                    mO2Verified = false;
                    sendData(mPowerRData);
                    break;
            }
        } else {
            Toast myToast = Toast.makeText(this, String.valueOf(R.string.data_send_error), Toast.LENGTH_SHORT);
            myToast.setGravity(Gravity.CENTER,0,0);
            myToast.show();
        }
    }

    public void toggleButton(int value) {
        if (value == 1) {
            if (mOutlet1State) {
                mOutlet1.setBackgroundResource(R.drawable.outlet_button_on);
                mO1Status.setText(R.string.on);
                mO1Status.setTextColor(ContextCompat.getColor(this, R.color.green));
            } else {
                mOutlet1.setBackgroundResource(R.drawable.outlet_button_off);
                mO1Status.setText(R.string.off);
                mO1Status.setTextColor(ContextCompat.getColor(this, R.color.red));
            }

        } else {
            if (mOutlet2State) {
                mOutlet2.setBackgroundResource(R.drawable.outlet_button_on);
                mO2Status.setText(R.string.on);
                mO2Status.setTextColor(ContextCompat.getColor(this, R.color.green));
            } else {
                mOutlet2.setBackgroundResource(R.drawable.outlet_button_off);
                mO2Status.setText(R.string.off);
                mO2Status.setTextColor(ContextCompat.getColor(this, R.color.red));
            }
        }
    }

    public void timerSet(View view) {
        if (mState == UART_PROFILE_CONNECTED) {
            TimePickerFragment newFragment;
            switch (view.getId()) {
                case R.id.o1TimerSet:
                    if (mTimer1State) {
                        //prompt for cancel or change time
                        AlertDialog.Builder aDialog = new AlertDialog.Builder(this);
                        aDialog.setTitle("Left Outlet Timer");
                        aDialog.setMessage("Cancel timer or set a new timer?");

                        aDialog.setPositiveButton(R.string.set_timer, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TimePickerFragment newFragment = TimePickerFragment.newInstance(mTimer1hour, mTimer1min, 1);
                                newFragment.show(getSupportFragmentManager(),"TimePicker");
                            }
                        });

                        aDialog.setNegativeButton(R.string.disable_timer, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                sendTimeData(1,false);
                            }
                        });

                        aDialog.setNeutralButton(R.string.back,null);

                        aDialog.show();
                    } else {
                        newFragment = TimePickerFragment.newInstance(mTimer1hour, mTimer1min,1);
                        newFragment.show(getSupportFragmentManager(),"TimePicker");
                    }
                    break;

                case R.id.o2TimerSet:
                    if (mTimer2State) {
                        //prompt for cancel or change time
                        AlertDialog.Builder aDialog = new AlertDialog.Builder(this);
                        aDialog.setTitle("Right Outlet Timer");
                        aDialog.setMessage("Cancel timer or set a new timer?");

                        aDialog.setPositiveButton(R.string.set_timer, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TimePickerFragment newFragment = TimePickerFragment.newInstance(mTimer2hour, mTimer2min,2);
                                newFragment.show(getSupportFragmentManager(),"TimePicker");
                            }
                        });

                        aDialog.setNegativeButton(R.string.disable_timer, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                sendTimeData(2,false);
                            }
                        });

                        aDialog.setNeutralButton(R.string.back,null);

                        aDialog.show();
                    } else {
                        newFragment = TimePickerFragment.newInstance(mTimer2hour, mTimer2min, 2);
                        newFragment.show(getSupportFragmentManager(),"TimePicker");
                    }
                    break;
            }
        } else {
            Toast myToast = Toast.makeText(this, String.valueOf(R.string.data_send_error), Toast.LENGTH_SHORT);
            myToast.setGravity(Gravity.CENTER,0,0);
            myToast.show();
        }

    }

    @Override
    public void returnData(int hour, int min, int index) {
        // Use the returned value
        switch (index) {
            case 1:
                mTimer1hour = hour;
                mTimer1min = min;

                //send data
                sendTimeData(1,true);
                break;
            case 2:
                mTimer2hour = hour;
                mTimer2min = min;

                //send data
                sendTimeData(2,true);
                break;
        }
    }

    private void sendTimeData(int index, boolean enable) {
        StringBuilder returnString = new StringBuilder();
        switch (index) {
            case 1:
                returnString.append(mTimer1Data);

                if (enable) {
                    returnString.append("53");

                    int seconds = mTimer1hour*3600 + mTimer1min*60;
                    String secondString = Integer.toHexString(seconds).toUpperCase();

                    for (int i = 0; i < 4 - secondString.length(); i++) {
                        returnString.append("0");
                    }
                    returnString.append(secondString);
                } else {
                    returnString.append("43");
                    returnString.append("0000");
                }

                returnString.append(calcChecksum(returnString.toString()));
                sendData(returnString.toString());
                break;
            case 2:
                returnString.append(mTimer2Data);

                if (enable) {
                    returnString.append("53");

                    int seconds = mTimer2hour*3600 + mTimer2min*60;
                    String secondString = Integer.toHexString(seconds).toUpperCase();

                    for (int i = 0; i < 4 - secondString.length(); i++) {
                        returnString.append("0");
                    }
                    returnString.append(secondString);
                } else {
                    returnString.append("43");
                    returnString.append("0000");
                }

                returnString.append(calcChecksum(returnString.toString()));
                sendData(returnString.toString());
                break;
        }
    }

    public void sendData(String data) {
        if (mDevice != null) {
            byte[] value = hexToBytes(data);
            mService.writeRXCharacteristic(value);
        }
    }

    private String getStringFromByte(int index) {
        String myString = "";
        myString += mFeatherData.charAt(index*2);
        myString += mFeatherData.charAt(index*2+1);
        return myString;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0XFF;
            hexChars[i*2] = hexArray[value >>> 4];
            hexChars[i*2+1] = hexArray[value & 0X0F];
        }

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

    private String calcChecksum(String data) {
        StringBuilder returnString = new StringBuilder();
        int val = 0;

        for (int i = 0; i < 7; i++) {
            String temp = "";
            temp += data.charAt(i*2);
            temp += data.charAt(i*2+1);
            val += Integer.parseInt(temp,16);
        }
        int comp = ~val;

        String compString = Integer.toHexString(comp & 0xFF).toUpperCase();

        for (int i = 0; i < 2 - compString.length(); i++) {
            returnString.append("0");
        }
        returnString.append(compString);
        return returnString.toString();
    }
}
