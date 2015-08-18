package electria.hr_ppg_monitor.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
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

import electria.hr_ppg_monitor.R;

public class History extends Activity {

    private static final String TAG = History.class.getSimpleName();

    private ListView mHistView;
    private ArrayAdapter<String> mListAdapter;
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

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
        }
        mDirName = extras.getString(Intent.EXTRA_TEXT);
        readDirectory(mDirName);
    }

    private OnItemClickListener mFileClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){

            Intent intent = new Intent(History.this, HistoryDetail.class);
            intent.putExtra(Intent.EXTRA_TEXT, getFilePath(position));
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
                return;
            }
            else{
                for (File f : dir.listFiles()) {
                    if (f.isFile())
                        mListAdapter.add(f.getName()+"\n"+getFileSize(f.length()));
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
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
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

    private String getFilePath(int pos){
        String item = mListAdapter.getItem(pos);//Get item from list adapter
        String fn = item.substring(0, item.indexOf('\n'));//Get filename from item string
        String path = android.os.Environment.getExternalStorageDirectory()+
                mDirName+"/"+fn;
        return path;
    }

    private void deletePPGFile(AdapterContextMenuInfo info){
        File f = new File(getFilePath(info.position));
        if(f.delete()) {
            mListAdapter.remove(mListAdapter.getItem(info.position));
            showMessage("PPG record deleted");
        }else
            showMessage("Problem deleting record");
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
