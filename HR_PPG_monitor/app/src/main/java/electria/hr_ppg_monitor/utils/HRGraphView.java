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

public class HRGraphView {
    private TimeSeries mSeries = new TimeSeries("");
    private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
    private XYMultipleSeriesRenderer mMultiRenderer = new XYMultipleSeriesRenderer();

    public HRGraphView() {
        XYSeriesRenderer mRenderer = new XYSeriesRenderer();
        mDataset.addSeries(mSeries);
        mRenderer.setColor(Color.RED);
        mRenderer.setLineWidth(5);



        final XYMultipleSeriesRenderer renderer = mMultiRenderer;
        renderer.setMargins(new int[]{50, 65, 40, 5});
        renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
        renderer.setAxesColor(Color.BLACK);
        renderer.setLabelsTextSize(25);
        renderer.setXTitle("HR (i = 0, 1, ...n)");
        renderer.setYTitle("HR (BPS)");
        renderer.setAxisTitleTextSize(25);
        renderer.setLegendTextSize(25);
        renderer.setInScroll(true);
        renderer.setPanEnabled(true, true);
        renderer.setZoomEnabled(false, false);
        renderer.addSeriesRenderer(mRenderer);
    }

    public GraphicalView getView(Context context) {
        return ChartFactory.getLineChartView(context, mDataset, mMultiRenderer);
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
