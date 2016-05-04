package electria.hr_ppg_monitor.utils;

/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 *
 * The information contained herein is property of Nordic Semiconductor ASA.
 * Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided.
 * This heading must NOT be removed from the file.
 ******************************************************************************/

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import android.content.Context;
import android.graphics.Color;

public class LineGraphView {
<<<<<<< HEAD
    private TimeSeries mSeries = new TimeSeries("");
=======
    //TimeSeries will hold the data in x,y format for single chart
    //private TimeSeries mSeries = new TimeSeries("PPG");
    private TimeSeries mSeries = new TimeSeries("");
    //XYSeriesRenderer is used to set the properties like chart color, style of each point, etc. of single chart
>>>>>>> d5e9c00a882d60a933fa81d44b58f67427fa0a63
    private XYSeriesRenderer mRenderer = new XYSeriesRenderer();
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mMultiRenderer = new XYMultipleSeriesRenderer();

    public LineGraphView() {
        mDataset.addSeries(mSeries);
        mRenderer.setColor(Color.RED);
        mRenderer.setLineWidth(10.0f);



        final XYMultipleSeriesRenderer renderer = mMultiRenderer;
<<<<<<< HEAD
        renderer.setBackgroundColor(Color.GRAY);
        renderer.setMargins(new int[]{10, 10, 10, 10});
        renderer.setMarginsColor(Color.WHITE);
        renderer.setAxesColor(Color.BLUE);
        renderer.setAxisTitleTextSize(24);
=======
        //set whole graph background color to transparent color
        renderer.setBackgroundColor(Color.TRANSPARENT);
        renderer.setMargins(new int[] { 50, 65, 40, 5 }); // top, left, bottom, right
        renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
        //renderer.setAxesColor(Color.BLACK);
        //renderer.setAxisTitleTextSize(24);
        //renderer.setShowGrid(true);
        /*renderer.setShowGrid(false);
        renderer.setGridColor(Color.LTGRAY);
        renderer.setLabelsColor(Color.BLACK);
        renderer.setYLabelsColor(0, Color.DKGRAY);
        renderer.setYLabelsAlign(Align.RIGHT);
        renderer.setYLabelsPadding(4.0f);
        renderer.setXLabelsColor(Color.DKGRAY);
        renderer.setLabelsTextSize(20);
>>>>>>> d5e9c00a882d60a933fa81d44b58f67427fa0a63
        renderer.setLegendTextSize(20);
        renderer.setInScroll(true);*/
        renderer.setYLabels(0);
        renderer.setXLabels(0);
        renderer.setInScroll(false);
        renderer.setPanEnabled(false, false);
        renderer.setZoomEnabled(false, false);
<<<<<<< HEAD
=======
        /*set title to x-axis and y-axis
        renderer.setXTitle("    Time (mS)");
        renderer.setYTitle("    Voltage (mV)");*/
>>>>>>> d5e9c00a882d60a933fa81d44b58f67427fa0a63
        renderer.addSeriesRenderer(mRenderer);
    }

    public GraphicalView getView(Context context) {
        final GraphicalView graphView = ChartFactory.getLineChartView(context, mDataset, mMultiRenderer);
        return graphView;
    }

    public void addValue(int index, int x, int y){
        mSeries.add(index, x, y);
    }

    public int getItemCount(){
        return mSeries.getItemCount();
    }

    public void removeValue(int index){
        mSeries.remove(index);
    }
    public void clearGraph() {
        mSeries.clear();
    }

}