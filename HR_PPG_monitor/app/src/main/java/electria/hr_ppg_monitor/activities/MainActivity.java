package electria.hr_ppg_monitor.activities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import org.achartengine.GraphicalView;

import electria.hr_ppg_monitor.measurements.PPGMeasurement;
import electria.hr_ppg_monitor.utils.ConnectDialog;
import electria.hr_ppg_monitor.utils.LineGraphView;
import electria.hr_ppg_monitor.R;
import electria.hr_ppg_monitor.services.BleService;


public class MainActivity extends Activity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CONNECT_PARAMS = 3;
    private static final String TAG = "HR_PPG_MONITOR";
    private static final String DIRECTORY_NAME = "/PPGDATA";
    public static final String PATIENT_DEVICE_IDS_FILE_PATH = "patient_device_ids.txt";
    private static final int CONNECTED = 20;
    private static final int DISCONNECTED = 21;
    private static final int CONNECTING = 22;
    private static final int X_RANGE = 400;
    private static final int MAX_DATA_RECORDING_TIME = 120;//Two minutes
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND = 1000;// 1000 milliseconds in one second

    private boolean mShowGraph;
    private boolean mGraphViewActive;
    private boolean mRecording;
    private boolean mFingerDetected;

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private TextView hrView, patientId, avHrView;
    private Button btnConnectDisconnect, btnHistory;
    private ViewGroup mainLayout;
    private List<String> mRecord;

    private int mCounter, mHrCounter;
    private int mRecTimerCounter, min, sec, hr;
    private int mAvHr;
    private BleService mService;
    private int mState;
    private String mTimerString, mSensorId, mPatientId;
    private Handler mHandler;
    private BluetoothDevice mDevice;
    private PPGMeasurement ppgM;
    private BluetoothAdapter mBtAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnConnectDisconnect=(Button) findViewById(R.id.btn_connect);
        btnHistory = (Button)findViewById(R.id.btn_history);
        hrView = (TextView) findViewById(R.id.hr_value);
        avHrView = (TextView) findViewById(R.id.av_hr_value);
        patientId = (TextView) findViewById(R.id.patient_id);
        mRecord = new ArrayList<>();

        mRecording = false;
        mShowGraph = false;
        mGraphViewActive = false;
        mFingerDetected = false;

        mCounter = 0;
        mAvHr = mHrCounter = 0;
        mRecTimerCounter = 1;
        min = sec =  hr = 0;
        mService = null;
        mDevice = null;
        mTimerString = "";
        mSensorId = "";
        mPatientId = "";
        mState = DISCONNECTED;
        mHandler = new Handler();
        ppgM = null;

        service_init();

        getPatientAndDeviceIds();

        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (mState == DISCONNECTED){
                        Intent newIntent = new Intent(MainActivity.this, ConnectDialog.class);
                        ArrayList<String> connParams = new ArrayList<>();
                        connParams.add(mSensorId);
                        connParams.add(mPatientId);
                        newIntent.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
                        startActivityForResult(newIntent, REQUEST_CONNECT_PARAMS);
                    } else {
                        //Disconnect button pressed
                        if (mState == CONNECTED) {
                            mService.disconnect();
                        }
                    }
                }
            }
        });

        /*if (mState == CONNECTED) {
            if(mDataRecording){
                stopRecordingData();
            }
            else{

                mDataRecording = true;
                mRecordTimer.run();
            }
        }*/

        // Handle History button
        btnHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == DISCONNECTED) {
                    Intent intent = new Intent(MainActivity.this, History.class);
                    intent.putExtra(Intent.EXTRA_TEXT, DIRECTORY_NAME);
                    startActivity(intent);
                }
            }
        });

        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onClick - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }else {
            Intent newIntent = new Intent(MainActivity.this, ConnectDialog.class);
            ArrayList<String> connParams = new ArrayList<>();
            connParams.add(mSensorId);
            connParams.add(mPatientId);
            newIntent.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
            startActivityForResult(newIntent, REQUEST_CONNECT_PARAMS);
        }
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = new LineGraphView();
        mGraphView = mLineGraph.getView(this);
        //mLineGraph.setYRange(MIN_Y, MAX_Y);
        mainLayout = (ViewGroup) findViewById(R.id.graph_layout);
        mainLayout.addView(mGraphView);
        mGraphViewActive = true;
    }

    //Plot a new PPG value on the graph and present on the GUI
    private void updateGraph(int value) {
        if(mCounter > X_RANGE){
            mCounter = 0;
        }
        if(mLineGraph.getItemCount() > mCounter){
            mLineGraph.removeValue(mCounter);
        }
        mLineGraph.addValue(mCounter, mCounter, value);
        mCounter++;
        mGraphView.repaint();
    }

    private void startGraph(){
        setGraphView();
        mShowGraph = true;
    }

    private void stopGraph(){
        clearGraph();
    }

    private void setHeartRateValue(int value) {
        if (value != 0) {
            double sum = (mAvHr*mHrCounter) + value;
            mHrCounter++;
            mAvHr = (int)Math.round(sum/mHrCounter);
            String hr = value + " BPM";
            String av = mAvHr + " BPM";
            avHrView.setText(av);
            hrView.setText(hr);
        } else {
            hrView.setText(" ");
            avHrView.setText(" ");
        }
    }

    private void clearGraph() {
        if(mGraphViewActive) {
            mGraphViewActive = false;
            mLineGraph.clearGraph();
            mCounter = 0;
            mAvHr = 0;
            mainLayout.removeView(mGraphView);
            setHeartRateValue(0);
        }
    }
    ;
    private void resetGUIComponents(){
        btnConnectDisconnect.setText(R.string.connect);
        btnHistory.setEnabled(true);
        ((TextView) findViewById(R.id.deviceName)).setText(R.string.no_device);
        patientId.setText("");
    }

    //BLE service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((BleService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };

    private final BroadcastReceiver BLEStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(BleService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "CONNECT_MSG");
                        btnConnectDisconnect.setText(R.string.disconnect);
                        btnHistory.setEnabled(false);
                        patientId.setText(mPatientId);
                        String str = mDevice.getName() + "- Connected";
                        ((TextView) findViewById(R.id.deviceName)).setText(str);
                        mState = CONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.d(TAG, "DISCONNECT_MSG");
                        mService.close();
                        clearGraph();
                        resetGUIComponents();
                        if(mRecording)
                            stopRecordingData();
                        mState = DISCONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_RX_DATA_AVAILABLE)) {
                int rxInt = intent.getIntExtra(BleService.EXTRA_DATA, 0);
                if (rxInt > 0){
                    if(!mFingerDetected){
                        mFingerDetected = true;
                        startRecordingData();
                    }

                    if(mRecording) {
                        ppgM.addToData(rxInt, 0);
                        ppgM.setEnd(System.currentTimeMillis());
                    }

                    if(!mShowGraph)
                        startGraph();

                    updateGraph(rxInt);
                }else{
                    if(mFingerDetected) {
                        mFingerDetected = false;
                        stopRecordingData();
                    }
                    if(mShowGraph)
                        mShowGraph = false;
                        stopGraph();
                }
            }

            if (action.equals(BleService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }

            if(action.equals(BleService.ACTION_HEART_RATE_READ)){
                int hr = intent.getIntExtra(BleService.EXTRA_DATA, 0);
                if(mShowGraph && mFingerDetected) {
                    setHeartRateValue(hr);
                }else{
                    mAvHr = 0;
                }
                if(mRecording){
                    ppgM.addToData(hr, 1);
                    ppgM.setEnd(System.currentTimeMillis());
                }
            }
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(BLEStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private void startRecordingData(){
        ppgM = new PPGMeasurement(mPatientId, System.currentTimeMillis());
        mRecording = true;
        mRecordTimer.run();
    }

    private void stopRecordingData(){
        if(mRecording) {
            mRecording = false;
            saveRecord();
            mHandler.removeCallbacks(mRecordTimer);
            ((TextView) findViewById(R.id.timer_view)).setText("");
            refreshTimer();
        }
    }

    private void saveRecord(){
        if(isExternalStorageWritable()){
            new Thread(new Runnable(){
                public void run(){
                    File root = android.os.Environment.getExternalStorageDirectory();
                    File dir = new File (root.getAbsolutePath() + DIRECTORY_NAME);
                    PPGMeasurement record = new PPGMeasurement();
                    record.copy(ppgM);
                    if(!dir.isDirectory())
                        dir.mkdirs();
                    File file;
                    String start = new SimpleDateFormat("yyMMddHHmmss", Locale.US).format(record.getStart());
                    String fileName = record.getPatientId()+"_"+start+".txt";
                    file = new File(dir, fileName);
                    try {
                        FileWriter fw = new FileWriter(file, true);
                        fw.append(record.toJson());
                        fw.flush();
                        fw.close();
                        showMessage("PPG Record Saved");
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                        showMessage("Problem writing to Storage");
                    }
                }
            }).start();
        }
        else
            showMessage("Cannot write to storage");
    }

    private void savePatientAndDeviceIds() {
        if (isExternalStorageWritable()) {
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + DIRECTORY_NAME);
            if (!dir.isDirectory())
                dir.mkdirs();
            File file = new File(dir, PATIENT_DEVICE_IDS_FILE_PATH);
            try {
                FileWriter fw = new FileWriter(file);
                fw.write(mPatientId + "\n" + mSensorId);
                fw.flush();
                fw.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                Log.e(TAG, "Problem writing to Storage");
            }
        }else{
            showMessage("Cannot write to storage!");
        }
    }

    private void getPatientAndDeviceIds(){
        if(!isExternalStorageReadable()) {
            showMessage("Cannot access external storage");
        }
        File root = android.os.Environment.getExternalStorageDirectory();
        File dir = new File(root.getAbsolutePath() + DIRECTORY_NAME);
        File file = new File(dir, PATIENT_DEVICE_IDS_FILE_PATH);
        if (file.exists()) {
            try {
                BufferedReader buf = new BufferedReader(new FileReader(file));
                mPatientId = buf.readLine();
                mSensorId = buf.readLine();
                buf.close();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                showMessage("Problem accessing mFile");
            }
        }else{
            showMessage("No saved ids");
        }
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    private Runnable mRecordTimer = new Runnable() {
        @Override
        public void run() {
            if (mRecTimerCounter < MAX_DATA_RECORDING_TIME && mRecording) {
                if (mRecTimerCounter < SECONDS_IN_ONE_MINUTE) {
                    sec = mRecTimerCounter;
                } else if (mRecTimerCounter < SECONDS_IN_ONE_HOUR) {
                    min = mRecTimerCounter / SECONDS_IN_ONE_MINUTE;
                    sec = mRecTimerCounter % SECONDS_IN_ONE_MINUTE;
                } else {
                    hr = mRecTimerCounter / SECONDS_IN_ONE_HOUR;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) / SECONDS_IN_ONE_MINUTE;
                    min = (mRecTimerCounter % SECONDS_IN_ONE_HOUR) % SECONDS_IN_ONE_MINUTE;
                }

                mRecTimerCounter++;
                updateTimer();
                mHandler.postDelayed(mRecordTimer, ONE_SECOND);
            }else{
                stopRecordingData();
            }
        }
    };

    private void refreshTimer(){
        mRecTimerCounter = 1;
        hr = min = sec = 0;
    }

    private void updateTimer(){
        mTimerString = String.format("%02d:%02d:%02d", hr,min,sec);
        ((TextView) findViewById(R.id.timer_view)).setText(mTimerString);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_RX_DATA_AVAILABLE);
        intentFilter.addAction(BleService.DEVICE_DOES_NOT_SUPPORT_UART);
        intentFilter.addAction(BleService.ACTION_TX_CHAR_WRITE);
        intentFilter.addAction(BleService.ACTION_HEART_RATE_READ);
        return intentFilter;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(BLEStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;
        stopRecordingData();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (mShowGraph) {
            stopGraph();
        }
    }

    @Override
    protected void onPause() {
        savePatientAndDeviceIds();
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_SELECT_DEVICE:
                //When the SensorList return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(SensorList.EXTRA_SENSOR_ADDRESS);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    String str = mDevice.getName()+ " - connecting";
                    ((TextView) findViewById(R.id.deviceName)).setText(str);
                    mService.connect(deviceAddress);
                }else if(resultCode == SensorList.DEVICE_NOT_FOUND){
                    showMessage(mSensorId + " not found, try again");
                    Intent newIntent = new Intent(MainActivity.this, ConnectDialog.class);
                    ArrayList<String> connParams = new ArrayList<>();
                    connParams.add(mSensorId);
                    connParams.add(mPatientId);
                    newIntent.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
                    startActivityForResult(newIntent, REQUEST_CONNECT_PARAMS);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    showMessage("Bluetooth has turned on ");

                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    showMessage("Problem in BT Turning ON ");
                    finish();
                }
                break;
            case REQUEST_CONNECT_PARAMS:
                if(resultCode == Activity.RESULT_OK && data != null){
                    ArrayList<String> connParams = data.getStringArrayListExtra(Intent.EXTRA_TEXT);
                    if(connParams != null && connParams.size() == 2) {
                        mSensorId = connParams.get(0);
                        mPatientId = connParams.get(1);
                    }
                    Intent getSensorIntent = new Intent(MainActivity.this, SensorList.class);
                    getSensorIntent.putExtra(Intent.EXTRA_TEXT, mSensorId);
                    startActivityForResult(getSensorIntent, REQUEST_SELECT_DEVICE);
                }else if(resultCode == Activity.RESULT_CANCELED){
                    Log.d(TAG, "User cancled request");
                }
                break;
            default:
                Log.e(TAG, "wrong request code");
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if(mState == CONNECTED){
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.disconnect_message)
                    .setPositiveButton(R.string.popup_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }else {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.popup_title)
                    .setMessage(R.string.quit_message)
                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.popup_no, null)
                    .show();
        }
    }

    private void showMessage(final String msg) {
        Runnable showMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        };
        mHandler.post(showMessage);

    }
}
