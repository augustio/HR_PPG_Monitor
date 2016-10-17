package electria.hr_ppg_monitor.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import electria.hr_ppg_monitor.R;

public class History extends Activity {

    private static final String TAG = History.class.getSimpleName();
    public static final String PATIENT_DEVICE_IDS_FILE_PATH = "patient_device_ids.txt";

    private ListView mHistView;
    private ArrayAdapter<String> mListAdapter;
    private ArrayList<String> mFNameList;
    private String mDirName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mHistView = (ListView) findViewById(R.id.historyListView);
        mListAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        mHistView.setAdapter(mListAdapter);
        mHistView.setOnItemClickListener(mFileClickListener);
        registerForContextMenu(mHistView);

        mFNameList = new ArrayList<>();

        Intent intent = getIntent();
        mDirName = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (mDirName == null) {
            finish();
        }
        readDirectory(mDirName);
    }

    private OnItemClickListener mFileClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){

            Intent intent = new Intent(History.this, HistoryDetail.class);
            intent.putExtra(Intent.EXTRA_TEXT, android.os.Environment
                    .getExternalStorageDirectory() + mDirName + "/" + mFNameList.get(position));
            startActivity(intent);
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_history, menu);
        menu.setHeaderTitle("Edit");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.delete:
                deletePPGFile(info);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void readDirectory(String dirName){
        if(isExternalStorageReadable()){
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File (root.getAbsolutePath() + dirName);
            if(!dir.exists()) {
                showMessage("No PPG file saved");
            }
            else{
                for (File f : dir.listFiles()) {
                    if (f.isFile() && !f.getName().equals(PATIENT_DEVICE_IDS_FILE_PATH)) {
                        mListAdapter.add(formatDate(f.getName()) + "\n" + getFileSize(f.length()));
                        mFNameList.add(f.getName());
                    }
                }
            }
        }
        else {
            showMessage("Cannot read from storage");
        }
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private String getFileSize(double len){
        String size;
        if(len <1000){
            size = len+"B";
        }
        else if(len < 1e+6){
            size = String.format("%.2f", (len/1024))+"KB";
        }
        else if(len < 1e+9){
            size = String.format("%.2f", (len/1.049e+6))+"MB";
        }
        else{
            size = String.format("%.2f", (len/1.074e+9))+"GB";
        }
        return size;
    }

    private void deletePPGFile(AdapterContextMenuInfo info){
        File f = new File(android.os.Environment.getExternalStorageDirectory()
                            + mDirName + "/" + mFNameList.get(info.position));
        if(f.delete()) {
            mListAdapter.remove(mListAdapter.getItem(info.position));
            mFNameList.remove(info.position);
            showMessage("Record deleted");
        }else
            showMessage("Problem deleting record");
    }

    private String formatDate(String fName){
        Date date;
        String res;
        SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmmss", Locale.US);
        String sub = fName.substring((fName.indexOf('_') + 1), fName.indexOf('.'));
        try{
            date = df.parse(sub);
            String str = new SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss", Locale.US).format(date.getTime());
            res = fName.substring(0, fName.indexOf('_')) + "\n" + str ;
        }catch(ParseException pE){
            res = fName;
        }
        return res;
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
