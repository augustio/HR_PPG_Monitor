package electria.hr_ppg_monitor.utils;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;

import electria.hr_ppg_monitor.R;

public class ConnectDialog extends Activity {

    private String mSensorId, mPatientId;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_dialog);

        final EditText sensorIdInputText = (EditText) findViewById(R.id.sensor_id);
        final EditText patientIdInputText = (EditText) findViewById(R.id.patient_id);
        Button okButton = (Button) findViewById(R.id.ok_btn);
        Button cancelButton = (Button) findViewById(R.id.cancel_btn);

        mSensorId = "";
        mPatientId = "";

        mHandler = new Handler();

        Bundle extras = getIntent().getExtras();
        if (extras != null){
            ArrayList<String> connParams;
            connParams = extras.getStringArrayList(Intent.EXTRA_TEXT);
            if(connParams != null && connParams.size() > 1){
                mSensorId = connParams.get(0);
                mPatientId = connParams.get(1);
            }
        }

        sensorIdInputText.setText(mSensorId);
        patientIdInputText.setText(mPatientId);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mSensorId = sensorIdInputText.getText().toString();
                mPatientId = patientIdInputText.getText().toString();

                if (!mSensorId.trim().isEmpty() && !mPatientId.trim().isEmpty()) {
                    ArrayList<String> connParams = new ArrayList<>();
                    connParams.add(0, mSensorId);
                    connParams.add(1, mPatientId);

                    Intent result = new Intent();
                    result.putStringArrayListExtra(Intent.EXTRA_TEXT, connParams);
                    setResult(Activity.RESULT_OK, result);
                    finish();
                } else {
                    showMessage("You must enter connection params to proceed");
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
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

