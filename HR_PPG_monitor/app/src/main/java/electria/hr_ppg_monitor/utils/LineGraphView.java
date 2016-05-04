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
    private TimeSeries mSeries = new TimeSeries("");
    private XYSeriesRenderer mRenderer = new XYSeriesRenderer();
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mMultiRenderer = new XYMultipleSeriesRenderer();

    public LineGraphView() {
        mDataset.addSeries(mSeries);
        mRenderer.setColor(Color.RED);
        mRenderer.setLineWidth(10.0f);



        final XYMultipleSeriesRenderer renderer = mMultiRenderer;
        renderer.setBackgroundColor(Color.GRAY);
        renderer.setMargins(new int[]{10, 10, 10, 10});
        renderer.setMarginsColor(Color.WHITE);
        renderer.setAxesColor(Color.BLUE);
        renderer.setAxisTitleTextSize(24);
        renderer.setLegendTextSize(20);
        renderer.setInScroll(true);
        renderer.setPanEnabled(false, false);
        renderer.setZoomEnabled(false, false);
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