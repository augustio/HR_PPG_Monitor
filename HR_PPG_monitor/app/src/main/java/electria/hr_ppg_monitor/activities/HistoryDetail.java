package electria.hr_ppg_monitor.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.stream.JsonReader;

import org.achartengine.GraphicalView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;

import electria.hr_ppg_monitor.R;
import electria.hr_ppg_monitor.measurements.PPGMeasurement;
import electria.hr_ppg_monitor.utils.HRGraphView;

public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();
    private static final String NO_NETWORK_CONNECTION = "Not Connected to Network";
    private Button btnSend;
    private String mFPath;
    private File mFile;
    private PPGMeasurement ppgM;
    private HRGraphView mHrGraph;
    private GraphicalView mGraphView;
    private LinearLayout mGraphLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        btnSend = (Button)findViewById(R.id.send_email);
        btnSend.setEnabled(false);
        TextView patientIdTv, hrTv, avHrTv, recDurationTv;
        patientIdTv = (TextView)findViewById(R.id.patient_id_tv);
        hrTv = (TextView)findViewById(R.id.hr_value_tv);
        avHrTv = (TextView)findViewById(R.id.av_hr_value_tv);
        recDurationTv = (TextView)findViewById(R.id.duration_tv);
        mGraphLayout = (LinearLayout)findViewById(R.id.hr_graph_layout);

        mFile = null;
        ppgM = null;

        Intent intent = getIntent();
        mFPath = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (mFPath == null) {
            finish();
        }

        if(isExternalStorageReadable()){
            if((mFile = validateFile(mFPath))  != null){
                ppgM = getData(mFile);
                if(ppgM != null) {
                    btnSend.setEnabled(true);
                    if(!ppgM.isEmpty()){
                        ArrayList<Integer> hrs = ppgM.getData(1);
                        String  hr = getHr(hrs) + " BPM";
                        String  av = getAvHr(hrs) + " BPM";
                        String  duration = getDuration(ppgM.getStart(), ppgM.getEnd()) + " Seconds";

                        patientIdTv.setText(ppgM.getPatientId());
                        hrTv.setText(hr);
                        avHrTv.setText(av);
                        recDurationTv.setText(duration);

                        setGraphView();
                        displayGraph(hrs);

                    }else
                        showMessage("Empty Record");
                }
            }
            else{
                finish();
            }
        }
        else{
            showMessage("Cannot read from storage");
            finish();
        }

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hasNetworkConnection())
                    // call AsynTask to perform network operation on separate thread
                    sendAttachment();
                else
                    showMessage(NO_NETWORK_CONNECTION);
            }
        });
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mHrGraph = new HRGraphView();
        mGraphView = mHrGraph.getView(this);
        //m.setYRange(MIN_Y, MAX_Y);
        mGraphLayout.addView(mGraphView);
    }

    //Plot a new set of two PPG values on the graph and present on the GUI
    private void displayGraph(ArrayList<Integer> hrs) {
        int counter = 0;
        for(int hr : hrs){
            mHrGraph.addValue(counter, counter, hr);
            counter++;
            mGraphView.repaint();
        }
    }

    private PPGMeasurement getData(File f){
        PPGMeasurement ppgm = new PPGMeasurement();
        try {
            BufferedReader buf = new BufferedReader(new FileReader(f));
            JsonReader reader = new JsonReader(new StringReader(buf.readLine()));
            reader.setLenient(true);
            ppgm.fromJson(reader);
            buf.close();
        } catch (Exception e) {
            Log.e(TAG, "File Access threw " + e.getMessage());
        }
        return ppgm;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    //Send PPG data as attachment to a specified Email address
    private void sendAttachment(){
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "ECG Data");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "Attached is a copy of ECG samples");
        emailIntent.setData(Uri.parse("mailto:electria.metropolia@gmail.com"));
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse( "file://"+mFPath));

        try {
            startActivity(Intent.createChooser(emailIntent, "Sending Email...."));
        }catch (android.content.ActivityNotFoundException ex) {
            showMessage("No email clients installed.");
        }
    }

    private int getDuration(long start, long end){
        return (int)(end - start)/1000;
    }

    private int getHr(ArrayList<Integer> hrs){
        int maxFreq = 0, maxFreqKey = 0;
        HashMap<Integer, Integer> hrMap = new HashMap<>();
        for(int value : hrs){
            if(hrMap.containsKey(value)) {
                int item = hrMap.get(value);
                item++;
                hrMap.put(value, item);
            }else{
                hrMap.put(value, 1);
            }
        }
        for(int key : hrMap.keySet()){
            int item = hrMap.get(key);
            if(item > maxFreq) {
                maxFreq = item;
                maxFreqKey = key;
            }
        }
        return maxFreqKey;
    }

    private int getAvHr(ArrayList<Integer> hrs){
        int sum = 0;
        for(int value : hrs){
            sum += value;
        }

        return sum/hrs.size();
    }

    /*Checks if mFile is a text mFile and is not empty*/
    private File validateFile(String path){
        File f = null;
        if(path.endsWith(("txt"))){
            f = new File(path);
            //File is considered empty if less than or equal to the size of a character
            if(f.length() <= Character.SIZE) {
                showMessage("Empty File");
                return null;
            }
        }
        else
            showMessage("Invalid File Format");
        return f;
    }

    public boolean hasNetworkConnection(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
