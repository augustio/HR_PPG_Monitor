package electria.hr_ppg_monitor.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.View;

import java.util.ArrayList;

public class GraphView extends View {

    private static final int MIN_LINES = 4;
    private static final int MAX_LINES = 7;
    private static final int[] DISTANCES = { 1, 2, 5 };

    private ArrayList<Float> yValue = new ArrayList<>();
    private float limit;
    private Paint paint = new Paint();

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void addDataPoints(float y) {
        if(yValue.size() == limit)
            yValue.remove(0);
        yValue.add(y);
        invalidate();
    }

    public void setLimit(int limit){
        this.limit = limit;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawLineChart(canvas);
    }

    private void drawBackground(Canvas canvas) {
        float maxValue = getMax(yValue);
        int range = getLineDistance(maxValue);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GRAY);
        for (int y = 0; y < maxValue; y += range) {
            final float yPos = getYPos(y);
            canvas.drawLine(0, yPos, getWidth(), yPos, paint);
        }
    }

    public void clearGraph(){
        yValue.clear();
        yValue.add((float)0);
        invalidate();
    }

    private int getLineDistance(float maxValue) {
        int distance;
        int distanceIndex = 0;
        int distanceMultiplier = 1;
        int numberOfLines = MIN_LINES;

        do {
            distance = DISTANCES[distanceIndex] * distanceMultiplier;
            numberOfLines = (int) FloatMath.ceil(maxValue / distance);

            distanceIndex++;
            if (distanceIndex == DISTANCES.length) {
                distanceIndex = 0;
                distanceMultiplier *= 10;
            }
        } while (numberOfLines < MIN_LINES || numberOfLines > MAX_LINES);

        return distance;
    }

    private void drawLineChart(Canvas canvas) {
        Path path = new Path();
        path.moveTo(getXPos(0), getYPos(yValue.get(0)));
        for (int i = 1; i < yValue.size(); i++) {
            path.lineTo(getXPos(i), getYPos(yValue.get(i)));
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(0xFF33B5E5);
        paint.setAntiAlias(true);
        paint.setShadowLayer(4, 2, 2, 0x80000000);
        canvas.drawPath(path, paint);
        paint.setShadowLayer(0, 0, 0, 0);
    }

    private float getMax(ArrayList<Float> yList) {
        if(yList.isEmpty())
            return (float) 0;
        float max = yList.get(0);
        for (int i = 1; i < yList.size(); i++) {
            float value = yList.get(i);
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private float getYPos(float value) {
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float maxValue = getMax(yValue);

        // scale it to the view size
        value = (value / maxValue) * height;

        // invert it so that higher values have lower y
        value = height - value;

        // offset it to adjust for padding
        value += getPaddingTop();

        return value;
    }

    private float getXPos(float value) {
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float maxValue = yValue.size() - 1;

        // scale it to the view size
        value = (value / maxValue) * width;

        // offset it to adjust for padding
        value += getPaddingLeft();

        return value;
    }

}

