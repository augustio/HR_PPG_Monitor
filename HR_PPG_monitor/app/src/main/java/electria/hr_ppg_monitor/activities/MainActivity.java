package electria.hr_ppg_monitor.activities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.graphics.Point;

import org.achartengine.GraphicalView;

import electria.hr_ppg_monitor.measurements.PPGMeasurement;
import electria.hr_ppg_monitor.utils.LineGraphView;
import electria.hr_ppg_monitor.R;
import electria.hr_ppg_monitor.services.BleService;


public class MainActivity extends Activity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "HR_PPG_MONITOR";
    private static final String DIRECTORY_NAME = "/PPGDATA";
    private static final int CONNECTED = 20;
    private static final int DISCONNECTED = 21;
    private static final int CONNECTING = 22;
    private static final int X_RANGE = 200;
    private static final int MIN_Y = 40000;//Minimum PPG data value
    private static final int MAX_Y = 50000;//Maximum PPG data value
    private static final int MAX_DATA_RECORDING_TIME = 120;//Two minutes(60 seconds)
    private static final int MAX_COLLECTION_SIZE = 10000;
    private static final int SECONDS_IN_ONE_MINUTE = 60;
    private static final int SECONDS_IN_ONE_HOUR = 3600;
    private static final int ONE_SECOND = 1000;// 1000 milliseconds in one second

    private boolean mShowGraph;
    private boolean mGraphViewActive;
    private boolean mDataRecording;

    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private TextView hrView;
    private EditText editMessage;
    private LinearLayout.LayoutParams mParamEnable, mParamDisable;
    private Button btnConnectDisconnect,btnReset,btnSend,btnStore, btnHistory;
    private ViewGroup mainLayout;
    private List<String> mRecord;

    private int mCounter;
    private int mRecTimerCounter, min, sec, hr;
    private BleService mService;
    private int mState;
    private String mTimerString;
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
        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
        btnReset=(Button) findViewById(R.id.btn_reset);
        btnReset.setBackgroundColor(getResources().getColor(R.color.blue));
        btnSend=(Button) findViewById(R.id.sendButton);
        btnStore = (Button)findViewById(R.id.btn_store);
        btnStore.setBackgroundColor(getResources().getColor(R.color.green));
        btnHistory = (Button)findViewById(R.id.btn_history);
        btnHistory.setBackgroundColor(getResources().getColor(R.color.blue));
        editMessage=(EditText) findViewById(R.id.sendText);
        hrView = (TextView) findViewById(R.id.heart_rate);
        mParamEnable = new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT, 2.0f);
        mParamDisable = new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT, 0.0f);
        mRecord = new ArrayList<>();

        mDataRecording = false;
        mShowGraph = false;
        mGraphViewActive = false;

        mCounter = 0;
        mRecTimerCounter = 1;
        min = sec =  hr = 0;
        mService = null;
        mDevice = null;
        mTimerString = "";
        mState = DISCONNECTED;
        mHandler = new Handler();

        service_init();

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
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice != null) {
                            mService.disconnect();
                        }
                    }
                }
            }
        });

        editMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSend.setEnabled(true);
                editMessage.setHint("");
            }
        });

        // Handle Send button
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editMessage.getText().toString();
                byte[] value;
                try {
                    //send data to service
                    value = message.getBytes("UTF-8");
                    if(value != null)
                        mService.writeTXCharacteristic(value);
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, e.getMessage());
                }
                editMessage.setText("");
                btnSend.setEnabled(false);
                editMessage.setHint(R.string.text_hint);
                //Close keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
            }
        });

        // Handle reset Graph button
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == CONNECTED) {
                    if (mShowGraph) {
                        mShowGraph = false;
                        stopGraph();
                    } else
                        startGraph();
                }
            }
        });

        // Handle Record button
        btnStore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mState == CONNECTED) {
                    if(mDataRecording){
                        stopRecordingData();
                    }
                    else{
                        ppgM = new PPGMeasurement(mDevice.getName(),
                                new SimpleDateFormat("yyMMddHHmmss", Locale.US).format(new Date()));
                        mDataRecording = true;
                        btnStore.setText("Stop");
                        mRecordTimer.run();
                    }
                }
            }
        });

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
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = LineGraphView.getLineGraphView();
        mGraphView = mLineGraph.getView(this);
        mLineGraph.setYRange(MIN_Y, MAX_Y);
        mainLayout = (ViewGroup) findViewById(R.id.graph_layout);
        mainLayout.addView(mGraphView);
        mGraphViewActive = true;
    }

    //Plot a new set of two PPG values on the graph and present on the GUI
    private void updateGraph(int value) {
        double maxX = mCounter;
        double minX = (maxX < X_RANGE) ? 0 : (maxX - X_RANGE);
        mLineGraph.setXRange(minX, maxX);
        mLineGraph.addValue(new Point(mCounter, value));
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
            hrView.setText(value + "BPM");
        } else {
            hrView.setText(" ");
        }
    }

    private void clearGraph() {
        if(mGraphViewActive) {
            mGraphViewActive = false;
            mShowGraph = false;
            mLineGraph.clearGraph();
            mCounter = 0;
            mainLayout.removeView(mGraphView);
            setHeartRateValue(0);
        }
    }
    ;
    private void resetGUIComponents(){
        btnStore.setText("Record");
        btnConnectDisconnect.setText("Connect");
        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.green));
        editMessage.setHint("");
        editMessage.setEnabled(false);
        btnReset.setLayoutParams(mParamDisable);
        btnStore.setLayoutParams(mParamDisable);
        btnHistory.setLayoutParams(mParamEnable);
        ((TextView) findViewById(R.id.deviceName)).setText(R.string.no_device);
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
                        btnConnectDisconnect.setText("Disconnect");
                        btnConnectDisconnect.setBackgroundColor(getResources().getColor(R.color.red));
                        editMessage.setHint(R.string.text_hint);
                        editMessage.setEnabled(true);
                        btnReset.setLayoutParams(mParamEnable);
                        btnStore.setLayoutParams(mParamEnable);
                        btnHistory.setLayoutParams(mParamDisable);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ "- Connected");
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
                        if(mDataRecording)
                            stopRecordingData();
                        mState = DISCONNECTED;
                    }
                });
            }

            if (action.equals(BleService.ACTION_RX_DATA_AVAILABLE)) {
                String rxString = intent.getStringExtra(BleService.EXTRA_DATA);
                if (rxString != null){
                        if (mDataRecording)
                            mRecord.add(rxString);
                }
            }

            if (action.equals(BleService.ACTION_FILTERED_DATA_AVAILABLE)) {
                int rxInt = intent.getIntExtra(BleService.EXTRA_DATA, 0);
                if (rxInt != 0){
                    if(!mShowGraph)
                        startGraph();
                    updateGraph(rxInt);
                    setHeartRateValue(60);
                }
                else if(mShowGraph)
                    stopGraph();
            }

            if (action.equals(BleService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }

            if(action.equals(BleService.ACTION_TX_CHAR_WRITE)){
                Log.d(TAG, "Write RX done");
            }

            if(action.equals(BleService.ACTION_HEART_RATE_READ)){
                if(mShowGraph == true)
                    setHeartRateValue(intent.getIntExtra(BleService.EXTRA_DATA, 0));
            }
        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, BleService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(BLEStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private void stopRecordingData(){
        if(mDataRecording) {
            saveToDisk();
            mDataRecording = false;
            btnStore.setText("Record");
            mHandler.removeCallbacks(mRecordTimer);
            ((TextView) findViewById(R.id.timer_view)).setText("");
            refreshTimer();
        }
    }

    private void saveToDisk(){
        if(mRecord.isEmpty()){
            showMessage("No data recorded");
            return;
        }

        if(isExternalStorageWritable()){
            new Thread(new Runnable(){
                public void run(){
                    File root = android.os.Environment.getExternalStorageDirectory();
                    File dir = new File (root.getAbsolutePath() + DIRECTORY_NAME);
                    if(!dir.isDirectory())
                        dir.mkdirs();
                    File file;
                    String fileName = ppgM.getSensor()+"_"+ppgM.getTimeStamp()+".txt";
                    file = new File(dir, fileName);
                    ppgM.setData(Arrays.toString(mRecord.toArray(new String[mRecord.size()])));
                    mRecord.clear();
                    try {
                        FileWriter fw = new FileWriter(file, true);
                        fw.append(ppgM.toJson());
                        ppgM = null;
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

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private Runnable mRecordTimer = new Runnable() {
        @Override
        public void run() {
            if(!mRecord.isEmpty()) {
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
                updateTimer();
                if (mRecTimerCounter >= MAX_DATA_RECORDING_TIME) {
                    stopRecordingData();
                    if (mShowGraph) {
                        stopGraph();
                    }
                    return;
                }
                if ((MAX_DATA_RECORDING_TIME - mRecTimerCounter) < 5)//Five seconds to the end of timer
                    ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.green));
                mRecTimerCounter++;
            }
            mHandler.postDelayed(mRecordTimer, ONE_SECOND);
        }
    };

    private void refreshTimer(){
        mRecTimerCounter = 1;
        hr = min = sec = 0;
        ((TextView) findViewById(R.id.timer_view)).setTextColor(getResources().getColor(R.color.red));
    }

    private void updateTimer(){
        mTimerString = mTimerString.format("%02d:%02d:%02d", hr,min,sec);
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
        intentFilter.addAction(BleService.ACTION_FILTERED_DATA_AVAILABLE);
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
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - connecting");
                    mService.connect(deviceAddress);
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
