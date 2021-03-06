package electria.hr_ppg_monitor.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleService extends Service {
    private final static String TAG = BleService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mRXCharacteristic;
    private BluetoothGattCharacteristic mTXCharacteristic;
    private ArrayList<Integer> mSamples, mFilteredSamples, mSamples4HRCalc;
    private int mConnectionState = STATE_DISCONNECTED;
    private ArrayList<Double> mMaxPoints = new ArrayList<>();
    private ArrayList<Double> mMinPoints = new ArrayList<>();
    private ArrayList<Double> mMaxPos = new ArrayList<>();
    private ArrayList<Double> mMinPos = new ArrayList<>();
    private int mHr = 0;
    private int count = 0;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int MIN_Y = 40000;//Minimum PPG data value
    private static final int SAMPLING_RATE = 100;
    private static final int TEN_SECONDS_SAMPLES = 1000;

    public final static String ACTION_GATT_CONNECTED =
            "electria.electriahrm.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "electria.electriahrm.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "electria.electriahrm.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_RX_DATA_AVAILABLE =
            "electria.electriahrm.ACTION_RX_DATA_AVAILABLE";
    public final static String ACTION_TX_CHAR_WRITE =
            "electria.electriahrm.ACTION_TX_CHAR_WRITE";
    public final static String EXTRA_DATA =
            "electria.electriahrm.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "electria.electriahrm.DEVICE_DOES_NOT_SUPPORT_UART";
    public final static String ACTION_HEART_RATE_READ =
            "electria.electriahrm.ACTION_HEART_RATE_READ";

    private final static UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID TX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                mSamples = new ArrayList<>();
                mSamples4HRCalc = new ArrayList<>();
                mFilteredSamples = new ArrayList<>();
                broadcastUpdate(intentAction);
                Log.d(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.d(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.d(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "mBluetoothGatt = " + mBluetoothGatt);
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    if (service.getUuid().equals(UART_SERVICE_UUID)) {
                        mRXCharacteristic = service.getCharacteristic(RX_CHAR_UUID);
                        mTXCharacteristic = service.getCharacteristic(TX_CHAR_UUID);
                        enableRXNotification();
                    }
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if(characteristic.getUuid().equals(RX_CHAR_UUID)) {
                int value = Integer.parseInt(characteristic.getStringValue(0).trim());
                if(value > MIN_Y ){
                    if(mSamples4HRCalc.size() == TEN_SECONDS_SAMPLES){
                        calculateHR(new ArrayList<>(mSamples4HRCalc));
                        mSamples4HRCalc.clear();
                    }
                    value = iIRFilter(detrend(value));
                    if(value > 0) {
                        mSamples4HRCalc.add(value);
                        broadcastUpdate(ACTION_RX_DATA_AVAILABLE, value);
                    }
                }
                else {
                    broadcastUpdate(ACTION_RX_DATA_AVAILABLE, 0);
                    mSamples.clear();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(characteristic.getUuid().equals(TX_CHAR_UUID)) {
                    Log.d(TAG, "TX written: "+ characteristic.getStringValue(0));
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(descriptor.getCharacteristic().getUuid().equals(RX_CHAR_UUID)) {
                    Log.d(TAG, "RX Descriptor written ");
                }
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final int value) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final String str) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, str);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    //31 point moving average filter
    private int iIRFilter(int sample){
        int filtered = 0;
        mSamples.add(sample);
        if(mSamples.size() < 31)
            return filtered;
        for(int i = 0; i < 31; i++)
            filtered += mSamples.get(i);
        mSamples.remove(0);
        return filtered/31;
    }

    private int detrend(int sample){
        int detrended = 0;
        mFilteredSamples.add(sample);
        if(mFilteredSamples.size() < 100)
            return detrended;
        for(int i = 0; i < 100; i++)
            detrended += mFilteredSamples.get(i);
        mFilteredSamples.remove(0);
        detrended = detrended/100;
        return Math.abs(sample - detrended);
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        Log.d(TAG, "mBluetoothGatt closed");
        mBluetoothGatt = null;
        mBluetoothDeviceAddress = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if(characteristic != null && mConnectionState == STATE_CONNECTED) {
            boolean status = mBluetoothGatt.readCharacteristic(characteristic);
            Log.d(TAG, "Read char - status=" + status);
        }
        else if(characteristic == null){
            Log.e(TAG, "Charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
        }
    }

    public void enableRXNotification()
    {
        if (mRXCharacteristic != null && mConnectionState == STATE_CONNECTED) {
            mBluetoothGatt.setCharacteristicNotification(mRXCharacteristic, true);
            BluetoothGattDescriptor descriptor = mRXCharacteristic.getDescriptor(CCCD_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }else if(mRXCharacteristic == null){
            Log.e(TAG, "Charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
        }
    }

    public void writeTXCharacteristic(byte[] value)
    {
        if(mTXCharacteristic != null && mConnectionState == STATE_CONNECTED) {
            mTXCharacteristic.setValue(value);
            boolean status = mBluetoothGatt.writeCharacteristic(mTXCharacteristic);
            Log.d(TAG, "write TXchar - status=" + status);
        }
        else if(mTXCharacteristic == null){
            Log.e(TAG, "Charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
        }
    }

    private void calculateHR(final ArrayList<Integer> samples){
        new Thread(new Runnable() {
            public void run(){
                double mx = Double.NEGATIVE_INFINITY, mn = Double.POSITIVE_INFINITY;
                double mnPos = Double.NaN, mxPos = Double.NaN;
                double lookForMax = 1;
                double delta;
                double sum = 0;

                for(int i = 0; i < samples.size(); i++){
                    sum+=samples.get(i);
                }
                delta = (sum/samples.size())*0.5;

                for(int i = 0; i < samples.size(); i++) {
                    double cur = samples.get(i);
                    if (cur > mx) {
                        mx = cur;
                        mxPos = i;
                    }
                    if (cur < mn) {
                        mn = cur;
                        mnPos = i;
                    }

                    if(lookForMax == 1){
                        if(cur < (mx - delta )){
                            mMaxPoints.add(mx);
                            mMaxPos.add(mxPos);
                            mn = cur;
                            mnPos = i;
                            lookForMax = 0;
                        }
                    }else{
                        if(cur > (mn + delta)){
                            mMinPoints.add(mn);
                            mMinPos.add(mnPos);
                            mx = cur;
                            mxPos = i;
                            lookForMax = 1;
                        }
                    }
                }
                double peaks = mMaxPoints.size();
                double duration = samples.size()/SAMPLING_RATE;
                mHr = (int)Math.round((peaks*60)/duration);
                Log.w(TAG, "Number of peaks: " + mMaxPoints.size());
                broadcastUpdate(ACTION_HEART_RATE_READ, (int)Math.round(mHr));
                /*if(mMaxPoints.size() > 1) {
                    double sum = 0;
                    double x = 0;
                    for (int i = 1; i < mMaxPos.size(); i++){
                        sum += (mMaxPos.get(i) - mMaxPos.get(i - 1));
                        x++;
                    }
                    double av = sum/x;
                    double time = av/100;
                    double hr = 60/time;

                    if(hr < 60)
                        hr = 60;
                    mHr = (mHr * count) + (int)Math.round(hr);
                    count++;
                    mHr = mHr/count;
                }*/
                mMaxPoints.clear();
                mMaxPos.clear();
                mMinPoints.clear();
                mMinPos.clear();
            }
        }).start();
    }
}
