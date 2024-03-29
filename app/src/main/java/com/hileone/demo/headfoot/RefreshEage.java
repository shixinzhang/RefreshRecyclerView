package com.hileone.demo.headfoot;

import android.graphics.Canvas;

/**
 * The creator is Leone && E-mail: butleone@163.com
 *
 * @author Leone
 * @date 5/12/16
 * @description Edit it! Change it! Beat it! Whatever, just do it!
 */
public interface RefreshEage {

    /**
     * nothing normal
     */
    public static final int STATE_REST = 0;
    /**
     * pull
     */
    public static final int STATE_PULL = 1;
    /**
     * release
     */
    public static final int STATE_RELEASE = 2;
    /**
     * loading
     */
    public static final int STATE_LOADING = 3;
    /**
     * refreshing success
     */
    public static final int STATE_SUCCESS = 4;
    /**
     * refreshing fail
     */
    public static final int STATE_FAIL = 5;

    /**
     * draw header with canvas
     * @param canvas canvas
     * @param left left
     * @param top top
     * @param right right
     * @param bottom bottom
     * @return boolean
     */
    public boolean draw(Canvas canvas, int left, int top, int right, int bottom);

    /**
     * get statesview height
     * @return int
     */
    public int getHeight();

    /**
     * refresh status change callback
     * @param state above
     */
    public void onStateChanged(int state);

    /**
     * get current state
     * @return above state
     */
    public int getState();

    /**
     * set pull to refresh text
     * @param text String
     */
    public void setPullStateText(String text);

    /**
     * set release to refresh text
     * @param text String
     */
    public void setReleaseStateText(String text);

    /**
     * set refreshing text
     * @param text String
     */
    public void setRefreshingText(String text);

    /**
     * set refresh success text
     * @param text String
     */
    public void setRefreshSuccessText(String text);

    /**
     * set refresh fail text
     * @param text text
     */
    public void setRefreshFailText(String text);

    /**
     * set background color
     * @param color int
     */
    public void setBackgroundColor(int color);

}
