package com.hileone.recyclerview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.util.TypedValue;

/**
 * The creator is Leone && E-mail: butleone@163.com
 *
 * @author Leone
 * @date 5/12/16
 * @description Edit it! Change it! Beat it! Whatever, just do it!
 */
public class DefaultFooterEdge implements RefreshEdge {

    private int mPice = 6;
    private Paint mPaint;
    private int mTime = 0;
    private int mCircleColor;
    private int mHeight;
    private int mState = STATE_REST;

    /**
     * DefaultFooterEdge
     * @param context context
     */
    public DefaultFooterEdge(Context context) {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Style.FILL);
        int fontSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 20, context.getResources().getDisplayMetrics());
        mPaint.setTextSize(fontSize);
        mPaint.setTextAlign(Align.CENTER);
        mCircleColor = 0xFF03A9F4;
        mHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 56, context.getResources().getDisplayMetrics());
    }

    @Override
    public boolean draw(Canvas canvas, int left, int top, int right, int bottom) {
        final int width = right - left;
        final int height = mHeight;
        final int viewHeight = bottom - top;
        canvas.save();
        canvas.clipRect(left + 5, top + 1, right + 5, bottom - 1);
        mPaint.setColor(mCircleColor);
        for (int i = 0; i < mPice; i++) {
            int angleParam = mTime * 5;
            float angle = -(i * (360 / mPice) - angleParam) * (float) Math.PI / 180;
            float radius = height / 4;
            float x = (float) (width / 2 + radius * Math.cos(angle));
            float y = (float) (top + Math.max(height, viewHeight) / 2 + radius * Math.sin(angle));
            canvas.drawCircle(x, y, height / 15, mPaint);
        }
        mTime++;
        canvas.restore();
        return true;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public void onStateChanged(int state) {
        mState = state;
    }

    @Override
    public int getState() {
        return mState;
    }

}
