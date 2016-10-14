package electria.hr_ppg_monitor.measurements;

/**
 * Created by augustio on 17.8.2015.
 */
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.util.ArrayList;


public class PPGMeasurement {

    private String patientId;
    private long start;
    private long end;
    private ArrayList<Integer> ppgData;
    private ArrayList<Integer> hrData;

    public PPGMeasurement(){}

    public PPGMeasurement(String patientId, long start){
        this.patientId = patientId;
        this.start = start;
        this.end = start;
        ppgData = new ArrayList<>();
        hrData = new ArrayList<>();
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd(){
        return end;
    }

    public void setEnd(long end){
        this.end = end;
    }

    public ArrayList<Integer> getData(int type) {
        switch (type){
            case 0:
                return new ArrayList<>(ppgData);
            case 1:
                return new ArrayList<>(hrData);
            default:
                return null;
        }
    }

    public void setData(ArrayList<Integer> data, int type) {
        switch (type){
            case 0:
                this.ppgData = new ArrayList<>(data);
                break;
            case 1:
                this.hrData = new ArrayList<>(data);
                break;
        }
    }

    public void addToData(int sample, int type){
        switch (type){
            case 0:
                this.ppgData.add(sample);
                break;
            case 1:
                this.hrData.add(sample);
                break;
        }
    }

    public boolean isEmpty(){
        return (ppgData.isEmpty() && hrData.isEmpty());
    }

    public ArrayList<Integer> size(){
        ArrayList<Integer> s = new ArrayList<>();
        s.add(0, ppgData.size());
        s.add(1, hrData.size());

        return s;
    }

    public String toJson(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void copy(PPGMeasurement record){
        this.patientId = record.getPatientId();
        this.start = record.getStart();
        this.end = record.getEnd();
        this.ppgData = record.getData(0);
        this.hrData = record.getData(1);
    }

    public void fromJson(JsonReader jsonReader){
        Gson gson = new Gson();
        PPGMeasurement ppgM = gson.fromJson(jsonReader, PPGMeasurement.class);
        this.patientId = ppgM.getPatientId();
        this.start = ppgM.getStart();
        this.end = ppgM.getEnd();
        this.ppgData = ppgM.getData(0);
        this.hrData = ppgM.getData(1);
    }

}
