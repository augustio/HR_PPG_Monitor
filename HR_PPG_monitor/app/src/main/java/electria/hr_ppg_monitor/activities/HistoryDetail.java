package electria.hr_ppg_monitor.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.achartengine.GraphicalView;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import electria.hr_ppg_monitor.utils.LineGraphView;
import electria.hr_ppg_monitor.R;
import electria.hr_ppg_monitor.measurements.PPGMeasurement;

public class HistoryDetail extends Activity {

    private static final String TAG = HistoryDetail.class.getSimpleName();
    private static final String SUCCESS = "File Access Successful";
    private static final String SERVER_ERROR = "No Response From Server!";
    private static final String NO_NETWORK_CONNECTION = "Not Connected to Network";
    private static final String CONNECTION_ERROR= "Server Not Reachable, Check Internet Connection";
    private static final String SERVER_URL = "http://52.18.112.240:3000/records";
    private static final int X_RANGE = 500;
    private static final int MIN_Y = 30000;//Minimum PPG data value
    private GraphicalView mGraphView;
    private LineGraphView mLineGraph;
    private ViewGroup mHistLayout;
    private Button btnSendEmail, btnSendCloud;
    private TextView accessStatus;
    private String mFPath;
    private File mFile;
    private PPGMeasurement ppgM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);
        btnSendEmail = (Button)findViewById(R.id.send_email);
        btnSendCloud = (Button)findViewById(R.id.send_cloud);
        accessStatus = (TextView)findViewById(R.id.server_access_status);
        btnSendEmail.setEnabled(false);
        btnSendCloud.setEnabled(false);
        mFile = null;
        ppgM = new PPGMeasurement();
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        mFPath = extras.getString(Intent.EXTRA_TEXT);
        if(isExternalStorageReadable()){
            if((mFile = validateFile(mFPath))  != null){
                setGraphView();
                new DisplayECGTask().execute(mFile);
            }
            else{
                finish();
            }
        }
        else{
            showMessage("Cannot read from storage");
            finish();
        }

        btnSendEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hasNetworkConnection())
                    // call AsynTask to perform network operation on separate thread
                    sendAttachment();
                else
                    showMessage(NO_NETWORK_CONNECTION);
            }
        });

        btnSendCloud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hasNetworkConnection())
                    // call AsynTask to perform network operation on separate thread
                    new HttpAsyncTask().execute(SERVER_URL);
                else
                    showMessage(NO_NETWORK_CONNECTION);
            }
        });
    }

    //Prepare the initial GUI for graph
    private void setGraphView() {
        mLineGraph = new LineGraphView();
        mGraphView = mLineGraph.getView(this);
        mHistLayout = (ViewGroup) findViewById(R.id.history_detail);
        mHistLayout.addView(mGraphView);
    }

    private class DisplayECGTask extends AsyncTask<File, Integer, String> {

        private Exception exception;
        private int xValue = 0;

        @Override
        protected String doInBackground(File... mFiles) {
            try {
                BufferedReader buf = new BufferedReader(new FileReader(mFiles[0]));
                ppgM.fromJson(buf.readLine());
                buf.close();
                String data = ppgM.getData().substring(1);//Remove opening square bracket
                data = data.substring(1).replace("]", "");//Remove closing square bracket
                String[] dataArray = data.split(",");
                for( int counter = 0; counter < dataArray.length; counter++){
                    String ppgSample = dataArray[counter].trim();
                    if(android.text.TextUtils.isDigitsOnly(ppgSample)) {
                        int value = Integer.parseInt(ppgSample);
                        if(value > MIN_Y)
                            publishProgress(value);
                    }
                    counter++;
                }
            } catch (Exception e) {
                exception = e;
                return null;
            }
            return SUCCESS;
        }

        /*Displays PPG data on the graph*/
        protected void onProgressUpdate(Integer... value) {
            int yValue = value[0];
            if(xValue > X_RANGE){
                xValue = 0;
            }
            updateGraph(xValue, yValue);
            xValue++;
        }

        /*Handles any exception and reports result of operation*/
        @Override
        protected void onPostExecute(String result) {
            if(exception != null){
                Log.e(TAG, exception.toString());
                showMessage("Problem accessing mFile");
                finish();
            }
            else {
                Log.d(TAG, SUCCESS);
                btnSendEmail.setEnabled(true);
                btnSendCloud.setEnabled(true);
            }
        }
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    //Add a point to the graph
    private void updateGraph(int x, int y){
        if(mLineGraph.getItemCount() > x){
            mLineGraph.removeValue(x);
        }
        mLineGraph.addValue(x, x*2, y);
        mGraphView.repaint();
    }

    public static String POST(String url, PPGMeasurement ecgM){
        InputStream inputStream;
        String result;
        try {
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);
            String json = ecgM.toJson();

            StringEntity se = new StringEntity(json);
            httpPost.setEntity(se);

            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            HttpResponse httpResponse = httpclient.execute(httpPost);

            HttpEntity httpEntity = httpResponse.getEntity();

            if(httpEntity != null){
                inputStream = httpEntity.getContent();
                result = convertInputStreamToString(inputStream);
            }else
                result = SERVER_ERROR;

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
            result =  CONNECTION_ERROR;
        }

        return result;
    }

    private class HttpAsyncTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... urls) {
            publishProgress("Sending Data to Server ...");
            return POST(urls[0], ppgM);
        }

        protected void onProgressUpdate(String... value) {
            accessStatus.setText(value[0]);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            accessStatus.setText("");
            if (result.equals(SERVER_ERROR))
                showMessage("Error Connecting to Server");
            else if (result.equals(CONNECTION_ERROR))
                showMessage(CONNECTION_ERROR);
            else {
                showMessage("Data Sent");
            }
        }

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

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;
    }

    public boolean hasNetworkConnection(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
