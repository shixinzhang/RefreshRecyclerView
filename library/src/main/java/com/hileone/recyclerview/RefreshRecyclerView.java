package com.hileone.recyclerview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 * The creator is Leone && E-mail: butleone@163.com
 *
 * @author Leone
 * @date 5/17/16
 * @description Edit it! Change it! Beat it! Whatever, just do it!
 */
public class RefreshRecyclerView extends RecyclerView {

    private static final int INVALID = -1;
    private static final float DRAG_RATE = 1.7f;

    private static final int TOUCH_MODE_INVALID = -2;
    private static final int TOUCH_MODE_REST = -1;
    private static final int TOUCH_MODE_DOWN = 0;
    private static final int TOUCH_MODE_TAP = 1;
    private static final int TOUCH_MODE_DONE_WAITING = 2;
    private static final int TOUCH_MODE_SCROLL = 3;
    private static final int TOUCH_MODE_FLING = 4;
    private static final int TOUCH_MODE_RESCROLL = 5;

    private boolean mIsAttached;
    private RefreshEdge mHeaderEdge;
    private RefreshEdge mFooterEdge;
    private boolean mAllowRefresh;
    private boolean mAllowLoadMore;
    private int mFirstTop = 0;
    private int mFirstPosition = 0;
    private int mItemCount = 0;
    private int mLastBottom = 0;
    private int mLastPosition = 0;
    private boolean mAutoLoadMore = true;

    private int mTouchSlop;
    protected float mDensity = 0;
    private int mActivePointerId = INVALID;
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private FlingRunnable mFlingRunnable;
    private int mTouchMode;
    private boolean mShowHeader;
    private boolean mShowFooter;
    private boolean mRefreshing;
    private int mInitialDownX;
    private int mInitialDownY;
    private int mLastMotionY;
    private int mMotionCorrection;
    private Rect mTouchFrame;
    private boolean mBlockLayoutRequests;

    private OnRefreshListener mRefreshListener;
    private OnLoadMoreListener mLoadMoreListener;

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a refresh should implement this interface.
     */
    public interface OnRefreshListener {
        /**
         * on refresh callback
         */
        void onRefresh();
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a loadmore should implement this interface.
     */
    public interface OnLoadMoreListener {
        /**
         * on loadmore callback
         */
        void onLoadMore();
    }

    /**
     * refresh just notifyDataSetChanged
     */
    private Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (getAdapter() != null) {
                getAdapter().notifyDataSetChanged();
            }
        }
    };

    /**
     * reset header
     */
    private Runnable mResetHeaderRunnable = new Runnable() {
        @Override
        public void run() {
            updateViewParams();
            mShowHeader = false;
            final RefreshEdge headerEdge = mHeaderEdge;
            final int childCount = getChildCount();
            final int firstTop = childCount == 0
                    ? mFirstTop : getChildAt(0).getTop();
            final int topOffset = firstTop - headerEdge.getHeight();
            final boolean isOutOfTop = mFirstPosition == 0 && topOffset > 0;
            int state = headerEdge.getState();
            if (topOffset == 0
                    && state == RefreshEdge.STATE_REST && mTouchMode == TOUCH_MODE_REST) {
                if (mFlingRunnable != null) {
                    mFlingRunnable.scrollToAdjustViewsUpOrDown();
                }
            } else if (topOffset > 0
                    && state == RefreshEdge.STATE_PULL && mTouchMode == TOUCH_MODE_RESCROLL) {
                if (mFlingRunnable != null) {
                    mFlingRunnable.scrollToAdjustViewsUpOrDown();
                    headerEdge.onStateChanged(RefreshEdge.STATE_REST);
                }
            } else if (!isOutOfTop
                    && (state == RefreshEdge.STATE_SUCCESS || state == RefreshEdge.STATE_FAIL)) {
                if (mFlingRunnable != null && mTouchMode != TOUCH_MODE_SCROLL) {
                    mFlingRunnable.scrollToAdjustViewsUpOrDown();
                    headerEdge.onStateChanged(RefreshEdge.STATE_REST);
                }
            }
            final int duration = (int) Math.abs(headerEdge.getHeight() / mDensity) + 50;
            postDelayed(mRefreshRunnable, duration);
        }
    };

    /**
     * reset footer
     */
    private Runnable mResetFooterRunnable = new Runnable() {
        @Override
        public void run() {
            updateViewParams();
            mShowFooter = false;
            final RefreshEdge footerEdge = mFooterEdge;
            if (mFlingRunnable != null && mTouchMode != TOUCH_MODE_SCROLL) {
                boolean hasScroll = mFlingRunnable.scrollToAdjustViewsUpOrDown();
                if (!hasScroll) {
                    footerEdge.onStateChanged(RefreshEdge.STATE_REST);
                }
            }

            final int childCount = getChildCount();
            final int lastBottom = mLastBottom;
            final int itemCount = mItemCount;
            final int firstTop = mFirstTop;
            final int height = getHeight();
            final int lastPosition = mLastPosition;
            final boolean isTooShort = childCount == itemCount && lastBottom - firstTop < height;
            final int bottomOffset = isTooShort ? firstTop : lastBottom - height;
            final boolean isOutOfBottom = !isTooShort && lastPosition == itemCount -1;
            int state = footerEdge.getState();
            if (bottomOffset == 0
                    && state == RefreshEdge.STATE_REST && mTouchMode == TOUCH_MODE_REST) {
                if (mFlingRunnable != null) {
                    mFlingRunnable.scrollToAdjustViewsUpOrDown();
                }
            } else if (bottomOffset < 0
                    && state == RefreshEdge.STATE_PULL && mTouchMode == TOUCH_MODE_RESCROLL) {
                if (mFlingRunnable != null) {
                    mFlingRunnable.scrollToAdjustViewsUpOrDown();
                    footerEdge.onStateChanged(RefreshEdge.STATE_REST);
                }
            } else if (isOutOfBottom
                    && (state == RefreshEdge.STATE_SUCCESS || state == RefreshEdge.STATE_FAIL)) {
                if (mFlingRunnable != null && mTouchMode != TOUCH_MODE_SCROLL) {
                    mFlingRunnable.scrollToAdjustViewsUpOrDown();
                    footerEdge.onStateChanged(RefreshEdge.STATE_REST);
                }
            }
            if (getAdapter() != null) {
                getAdapter().notifyDataSetChanged();
            }
        }
    };

    /**
     * RefreshRecyclerView
     * @param context context
     */
    public RefreshRecyclerView(Context context) {
        super(context);
        init();
    }

    /**
     * RefreshRecyclerView
     * @param context context
     * @param attrs attrs
     */
    public RefreshRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * RefreshRecyclerView
     * @param context context
     * @param attrs attrs
     * @param defStyle defStyle
     */
    public RefreshRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mDensity = getResources().getDisplayMetrics().density;
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        setHeaderEdge(new DefaultHeaderEdge(getContext()));
        setAllowRefresh(true);
        mHeaderEdge.onStateChanged(RefreshEdge.STATE_REST);

        setFooterEdge(new DefaultFooterEdge(getContext()));
        setAllowLoadMore(true);
        mFooterEdge.onStateChanged(RefreshEdge.STATE_REST);

        setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
    }

    /**
     * init to set allowed pull to refresh
     * @param allowRefresh  {@link Boolean}
     */
    public void setAllowRefresh(boolean allowRefresh) {
        mAllowRefresh = allowRefresh;
    }

    /**
     * set pull to refresh view
     * @param headerEdge {@link RefreshEdge}
     */
    public void setHeaderEdge(@NonNull RefreshEdge headerEdge) {
        mHeaderEdge = headerEdge;
    }

    /**
     * get pull to refresh view
     * @return {@link RefreshEdge}
     */
    public RefreshEdge getHeaderEdge() {
        return mHeaderEdge;
    }

    /**
     * init to set allowed drag to loadmore
     * @param allowLoadMore {@link Boolean}
     */
    public void setAllowLoadMore(boolean allowLoadMore) {
        mAllowLoadMore = allowLoadMore;
    }

    /**
     * set drag to loadmore view
     * @param footerEdge {@link RefreshEdge}
     */
    public void setFooterEdge(@NonNull RefreshEdge footerEdge) {
        mFooterEdge = footerEdge;
    }

    /**
     * get drag to loadmore view
     * @return {@link RefreshEdge}
     */
    public RefreshEdge getFooterEdge() {
        return mFooterEdge;
    }

    /**
     * setLoadMoreListener
     * @param loadMoreListener loadMoreListener
     */
    public void setLoadMoreListener(OnLoadMoreListener loadMoreListener) {
        mLoadMoreListener = loadMoreListener;
    }

    /**
     * setRefreshListener
     * @param refreshListener refreshListener
     */
    public void setRefreshListener(OnRefreshListener refreshListener) {
        mRefreshListener = refreshListener;
    }

    /**
     * set refreshing done, update refresh status
     * @param status status
     */
    public void setRefreshDone(boolean status) {
        final int state = status ? RefreshEdge.STATE_SUCCESS : RefreshEdge.STATE_FAIL;
        final RefreshEdge headerEdge = mHeaderEdge;
        if (headerEdge != null && headerEdge.getState() == RefreshEdge.STATE_LOADING) {
            mRefreshing = false;
            headerEdge.onStateChanged(state);
            removeCallbacks(mResetHeaderRunnable);
            removeCallbacks(mRefreshRunnable);
            postDelayed(mResetHeaderRunnable, 360);
        }
    }

    /**
     * set loading more done, update load more status
     * @param status status
     */
    public void setLoadMoreDone(boolean status) {
        final int state = status ? RefreshEdge.STATE_SUCCESS : RefreshEdge.STATE_FAIL;
        final RefreshEdge footerEdge = mFooterEdge;
        if (footerEdge != null && footerEdge.getState() == RefreshEdge.STATE_LOADING) {
            footerEdge.onStateChanged(state);
            removeCallbacks(mResetFooterRunnable);
            postDelayed(mResetFooterRunnable, 300);
        }
    }

    /**
     * set this auto refreshing
     */
    public void performRefresh() {
        if (mHeaderEdge == null || mHeaderEdge.getState() == RefreshEdge.STATE_LOADING
                || mRefreshListener == null || mTouchMode != TOUCH_MODE_REST) {
            return;
        }
        if (mFirstPosition == 0) {
            int newTop = mHeaderEdge.getHeight();
            if (mFlingRunnable == null) {
                mFlingRunnable = new FlingRunnable();
            }
            mRefreshing = true;
            mShowHeader = true;
            mHeaderEdge.onStateChanged(RefreshEdge.STATE_LOADING);
            mRefreshListener.onRefresh();
            mFlingRunnable.startScroll(mFirstTop - newTop,
                    (int) (Math.abs(newTop - mFirstTop) / mDensity) + 50);
            mTouchMode = TOUCH_MODE_RESCROLL;
        }
    }

    /**
     * Set the {@link LinearLayoutManager} that this RecyclerView will use default.
     */
    public void setLayoutManager() {
        setLayoutManager(null);
    }

    @Override
    public void setLayoutManager(LayoutManager layout) {
        if (layout == null || !(layout instanceof LinearLayoutManager)) {
            layout = new LinearLayoutManager(getContext());
        }
        ((LinearLayoutManager) layout).setOrientation(RecyclerView.VERTICAL);
        super.setLayoutManager(layout);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        resetList();
    }

    private void resetList() {
        mFirstPosition = 0;
        mFirstTop = 0;
        mShowFooter = false;
        mShowHeader = false;
    }

    @Override
    public void requestLayout() {
        if ((mHeaderEdge != null && mShowHeader
                && mAllowRefresh && mHeaderEdge.getState() == RefreshEdge.STATE_LOADING)
            || (mFooterEdge != null && mShowFooter
                && mAllowLoadMore && mFooterEdge.getState() == RefreshEdge.STATE_LOADING)) {
            return;
        }
        if (!mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIsAttached = true;
    }

    private void updateViewParams() {
        final int childCount = getChildCount();
        mFirstTop = childCount <= 0 ? 0 : getChildAt(0).getTop();
        mLastBottom = childCount <= 0
                ? mFirstTop : getChildAt(childCount - 1).getBottom();

        final RecyclerView.Adapter adapter;
        if ((adapter = getAdapter()) != null) {
            mItemCount = adapter.getItemCount();
        } else {
            mItemCount = 0;
        }

        final RecyclerView.LayoutManager manager;
        if ((manager = getLayoutManager()) != null) {
            mFirstPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
            mLastPosition = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final RefreshEdge header = mHeaderEdge;
        if (header != null && couldPullToRefresh()) {
            final int firstTop = mFirstTop;
            if (header.draw(canvas, 0, 0, getWidth(), firstTop)) {
                postInvalidate(0, 0, getWidth(), firstTop);
            }
        }

        final RefreshEdge footer = mFooterEdge;
        if (footer != null && couldDragToLoadMore()) {
            final int lastBottom = mLastBottom;
            final int viewBottom = getHeight();
            if (footer.draw(canvas, 0, lastBottom, getWidth(), viewBottom)) {
                postInvalidate(0, 0, getWidth(), viewBottom);
            }
        }

        super.dispatchDraw(canvas);
    }

    private boolean couldPullToRefresh() {
        if (mAllowRefresh) {
            final int firstPosition = mFirstPosition;
            final int firstTop = mFirstTop;
            return firstPosition == 0 && firstTop >= 0;
        }
        return false;
    }

    private boolean couldDragToLoadMore() {
        if (mAllowLoadMore) {
            final int lastPosition = mLastPosition;
            final int lastBottom = mLastBottom;
            final int itemCount = mItemCount;
            final int firstTop = mFirstTop;
            final int childCount = getChildCount();
            final boolean isTooShort = childCount == mItemCount && lastBottom - firstTop < getHeight();
            return lastPosition == itemCount - 1 && lastBottom <= getHeight() && !isTooShort;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || !mIsAttached) {
            return false;
        }

        if (!mAllowRefresh && !mAllowLoadMore) {
            return false;
        }

        updateViewParams();
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_DOWN: {
                final int touchMode = mTouchMode;
                final int x = (int) ev.getX();
                final int y = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                if (touchMode != TOUCH_MODE_FLING && touchMode != TOUCH_MODE_RESCROLL) {
                    mTouchMode = TOUCH_MODE_DOWN;
                }
                mInitialDownX = x;
                mInitialDownY = y;
                mLastMotionY = y;
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                if (touchMode == TOUCH_MODE_FLING || touchMode == TOUCH_MODE_RESCROLL) {
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mTouchMode == TOUCH_MODE_INVALID) {
                    return false;
                }
                switch (mTouchMode) {
                    case TOUCH_MODE_DOWN:
                        int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        if (pointerIndex == -1) {
                            pointerIndex = 0;
                            mActivePointerId = ev.getPointerId(pointerIndex);
                        }
                        initVelocityTrackerIfNotExists();
                        mVelocityTracker.addMovement(ev);
                        break;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                mTouchMode = TOUCH_MODE_REST;
                mActivePointerId = INVALID;
                mInitialDownX = 0;
                mInitialDownY = 0;
                recycleVelocityTracker();
            } break;

            case MotionEvent.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
            } break;

        }

        try {
            return super.onInterceptTouchEvent(ev);
        } finally {
            updateViewParams();
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & 0xff00) >> 8;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mInitialDownX = (int) ev.getX(newPointerIndex);
            mInitialDownY = (int) ev.getY(newPointerIndex);
            mMotionCorrection = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private boolean startScrollIfNeeded(int x, int y) {
        final int deltaX = x - mInitialDownX;
        final int distanceX = Math.abs(deltaX);
        final int deltaY = y - mInitialDownY;
        final int distanceY = Math.abs(deltaY);
        if (distanceY > mTouchSlop || distanceX > mTouchSlop) {
            if (distanceY > distanceX * 2) {
                final ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                mTouchMode = TOUCH_MODE_SCROLL;
                scrollIfNeeded(x, y);
                return true;
            } else {
                mTouchMode = TOUCH_MODE_INVALID;
            }
        }
        return false;
    }

    private void scrollIfNeeded(int x, int y) {
        final int rawDeltaY = y - mInitialDownY;
        mMotionCorrection = rawDeltaY > 0 ? mTouchSlop : -mTouchSlop;
        final int deltaY = rawDeltaY - mMotionCorrection;
        int incrementalDeltaY = mLastMotionY != Integer.MIN_VALUE ? y - mLastMotionY : deltaY;
        if (mTouchMode == TOUCH_MODE_SCROLL && y != mLastMotionY) {
            boolean atEdge = false;
            if (incrementalDeltaY != 0) {
                atEdge = trackMotionScroll(incrementalDeltaY);
            }
            if (atEdge && mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
            mLastMotionY = y;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return isClickable() || isLongClickable();
        }

        if (!mIsAttached) {
            return false;
        }

        if (!mAllowRefresh && !mAllowLoadMore) {
            return false;
        }

        updateViewParams();
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_DOWN:
                onTouchDown(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                onTouchMove(ev);
                break;
            case MotionEvent.ACTION_UP:
                onTouchUp(ev);
                break;
            case MotionEvent.ACTION_CANCEL:
                onTouchCancel();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionY = mInitialDownY;
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                final int id = ev.getPointerId(index);
                final int y = (int) ev.getY(index);
                mMotionCorrection = 0;
                mActivePointerId = id;
                mInitialDownY = y;
                mLastMotionY = y;
            } break;
        }

        try {
            return super.onTouchEvent(ev);
        } finally {
            updateViewParams();
        }
    }

    private void onTouchDown(MotionEvent ev) {
        mActivePointerId = ev.getPointerId(0);
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        int motionPosition = pointToPosition(x, y);
        if (mTouchMode == TOUCH_MODE_FLING || mTouchMode == TOUCH_MODE_RESCROLL) {
            if (mFlingRunnable != null) {
                mFlingRunnable.mScroller.abortAnimation();
            }
            mTouchMode = TOUCH_MODE_SCROLL;
            mMotionCorrection = 0;
        } else if (motionPosition >= 0) {
            mTouchMode = TOUCH_MODE_DOWN;
        } else {
            mTouchMode = TOUCH_MODE_SCROLL;
            mMotionCorrection = 0;
        }
        mInitialDownX = x;
        mInitialDownY = y;
        mLastMotionY = Integer.MIN_VALUE;
    }

    private int pointToPosition(int x, int y) {
        updateViewParams();
        Rect frame = mTouchFrame;
        if (frame == null) {
            frame = mTouchFrame = new Rect();
        }
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return mFirstPosition + i;
                }
            }
        }
        return INVALID;
    }

    private void onTouchMove(MotionEvent ev) {
        if (mTouchMode == TOUCH_MODE_INVALID) {
            mTouchMode = TOUCH_MODE_SCROLL;
        }
        int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1) {
            pointerIndex = 0;
            mActivePointerId = ev.getPointerId(pointerIndex);
        }
        final int x = (int) ev.getX(pointerIndex);
        final int y = (int) ev.getY(pointerIndex);
        switch (mTouchMode) {
            case TOUCH_MODE_DOWN:
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                startScrollIfNeeded(x, y);
                break;
            case TOUCH_MODE_SCROLL:
                scrollIfNeeded(x, y);
                break;
        }
    }

    private void onTouchUp(MotionEvent ev) {
        switch (mTouchMode) {
            case TOUCH_MODE_DOWN:
            case TOUCH_MODE_TAP:
            case TOUCH_MODE_DONE_WAITING:
                mTouchMode = TOUCH_MODE_REST;
                break;
            case TOUCH_MODE_SCROLL:
                if (mFlingRunnable == null) {
                    mFlingRunnable = new FlingRunnable();
                }
                if (!mFlingRunnable.scrollToAdjustViewsUpOrDown()) {
                    int initialVelocity = 0;
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                        initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                    }
                    if (Math.abs(initialVelocity) > mMinimumVelocity) {
                        if (mFlingRunnable == null) {
                            mFlingRunnable = new FlingRunnable();
                        }
                        mFlingRunnable.startScroll(-initialVelocity);
                    } else {
                        mTouchMode = TOUCH_MODE_REST;
                        if (mFlingRunnable != null) {
                            mFlingRunnable.endFling();
                        }
                    }
                }
                break;
        }
        invalidate();
        recycleVelocityTracker();
        mActivePointerId = INVALID;
    }

    private void onTouchCancel() {
        if (mTouchMode == TOUCH_MODE_SCROLL) {
            if (mFlingRunnable == null) {
                mFlingRunnable = new FlingRunnable();
            }
            if (!mFlingRunnable.scrollToAdjustViewsUpOrDown()) {
                int initialVelocity = 0;
                final VelocityTracker velocityTracker = mVelocityTracker;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                }
                if (Math.abs(initialVelocity) > mMinimumVelocity) {
                    if (mFlingRunnable == null) {
                        mFlingRunnable = new FlingRunnable();
                    }
                    mFlingRunnable.startScroll(-initialVelocity);
                } else {
                    mTouchMode = TOUCH_MODE_REST;
                    if (mFlingRunnable != null) {
                        mFlingRunnable.endFling();
                    }
                }
            }
        } else {
            mTouchMode = TOUCH_MODE_REST;
            recycleVelocityTracker();
            invalidate();
            mActivePointerId = INVALID;
        }
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private boolean trackMotionScroll(int incrementalDeltaY) {
        updateViewParams();

        final int childCount = getChildCount();
        final int firstPosition = mFirstPosition;
        final int lastPosition = mLastPosition;
        final int firstTop = mFirstTop;
        final int lastBottom = mLastBottom;
        final int itemCount = mItemCount;
        final int height = getHeight();
        if (incrementalDeltaY < 0) {
            incrementalDeltaY = Math.max(-(height - 1), incrementalDeltaY);
        } else {
            incrementalDeltaY = Math.min(height - 1, incrementalDeltaY);
        }
        final RefreshEdge headerEdge = mHeaderEdge;
        final RefreshEdge footerEdge = mFooterEdge;
        final boolean isTooShort = childCount == mItemCount && lastBottom - firstTop < getHeight();
        final int topOffset = firstTop - (mShowHeader ? headerEdge.getHeight() : 0);
        final int bottomOffset = isTooShort ? firstTop : lastBottom - getHeight();
        final boolean isOutOfTop = firstPosition == 0 && topOffset > 0;
        final boolean isOutOfBottom = !isTooShort && lastPosition == itemCount -1;
        final boolean cannotScrollDown = isOutOfTop && incrementalDeltaY > 0;
        final boolean cannotScrollUp = isOutOfBottom && incrementalDeltaY <= 0;
        if (isTooShort && cannotScrollDown && mTouchMode == TOUCH_MODE_RESCROLL) {
            mTouchMode = TOUCH_MODE_FLING;
            return true;
        }

        if (mTouchMode == TOUCH_MODE_SCROLL) {
            if (isOutOfTop && headerEdge != null) {
                incrementalDeltaY /= DRAG_RATE;
                final int state = headerEdge.getState();
                if (topOffset >= headerEdge.getHeight()) {
                    switch (state) {
                        case RefreshEdge.STATE_PULL:
                        case RefreshEdge.STATE_SUCCESS:
                        case RefreshEdge.STATE_FAIL:
                            headerEdge.onStateChanged(RefreshEdge.STATE_RELEASE);
                            break;
                    }
                } else {
                    switch (state) {
                        case RefreshEdge.STATE_REST:
                        case RefreshEdge.STATE_RELEASE:
                        case RefreshEdge.STATE_SUCCESS:
                        case RefreshEdge.STATE_FAIL:
                            headerEdge.onStateChanged(RefreshEdge.STATE_PULL);
                            break;
                    }
                }
            }

            if (isOutOfBottom && footerEdge != null) {
                incrementalDeltaY /= DRAG_RATE;
                final int state = footerEdge.getState();
                if (bottomOffset <= -footerEdge.getHeight()) {
                    switch (state) {
                        case RefreshEdge.STATE_PULL:
                            footerEdge.onStateChanged(RefreshEdge.STATE_RELEASE);
                            break;
                    }
                } else {
                    switch (state) {
                        case RefreshEdge.STATE_REST:
                        case RefreshEdge.STATE_RELEASE:
                            footerEdge.onStateChanged(RefreshEdge.STATE_PULL);
                            break;
                    }
                }
            }
        }

        if (isOutOfTop || isOutOfBottom) {
            if (mTouchMode == TOUCH_MODE_FLING) {
                if (cannotScrollDown) {
                    incrementalDeltaY /= DRAG_RATE;
                    if (firstTop > getHeight() / 6) {
                        return true;
                    }
                } else if (cannotScrollUp && !isOutOfTop) {
                    incrementalDeltaY /= DRAG_RATE;
                    if (bottomOffset < -getHeight() / 6) {
                        return true;
                    }
                }
            } else {
                if (incrementalDeltaY > 0) {
                    if (firstTop > getHeight() / 2) {
                        return true;
                    }
                } else if (incrementalDeltaY < 0 && !isOutOfTop) {
                    if (bottomOffset < -getHeight() / 2) {
                        return true;
                    }
                }
            }
        } else {
            if (headerEdge != null && headerEdge.getState() == RefreshEdge.STATE_PULL) {
                headerEdge.onStateChanged(RefreshEdge.STATE_REST);
            }
            if (footerEdge != null && footerEdge.getState() == RefreshEdge.STATE_PULL) {
                footerEdge.onStateChanged(RefreshEdge.STATE_REST);
            }
        }
        mBlockLayoutRequests = true;
        invalidate();
        updateViewParams();
        offsetChildrenTopAndBottom(incrementalDeltaY);
        updateViewParams();
        mBlockLayoutRequests = false;
        return false;
    }

    private void offsetChildrenTopAndBottom(int offset) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View v = getChildAt(i);
            v.offsetTopAndBottom(offset);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsAttached = false;
    }

    /**
     * deal with after dragging event, update current view state
     */
    private class FlingRunnable implements Runnable {
        private Scroller mScroller;
        private int mLastFlingY;

        private Runnable mLoadMoreRunnable = new Runnable() {
            @Override
            public void run() {
                if (mTouchMode == TOUCH_MODE_SCROLL) {
                    post(mResetFooterRunnable);
                } else if (!mRefreshing && mAllowLoadMore && mLoadMoreListener != null) {
                    mShowFooter = true;
                    mLoadMoreListener.onLoadMore();
                }
            }
        };

        private FlingRunnable() {
            mScroller = new Scroller(getContext(), new Interpolator() {
                @Override
                public float getInterpolation(float t) {
                    t -= 1;
                    return t * t * t * t * t + 1;
                }
            });
        }

        public void startScroll(int distance, int duration) {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            int initialY = distance < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingY = initialY;
            mScroller.startScroll(0, initialY, 0, distance, duration);
            mTouchMode = TOUCH_MODE_FLING;
            ViewCompat.postOnAnimation(RefreshRecyclerView.this, this);
        }

        public void startScroll(int initialVelocity) {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            int initialY = initialVelocity < 0 ? Integer.MAX_VALUE : 0;
            mLastFlingY = initialY;
            mScroller.fling(0, initialY, 0, initialVelocity, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            mTouchMode = TOUCH_MODE_FLING;
            ViewCompat.postOnAnimation(RefreshRecyclerView.this, this);
        }

        public void endFling() {
            int oldTouchMode = mTouchMode;
            mTouchMode = TOUCH_MODE_REST;
            removeCallbacks(this);
            mScroller.abortAnimation();

            if (mHeaderEdge != null && oldTouchMode == TOUCH_MODE_RESCROLL
                    && mHeaderEdge.getState() == RefreshEdge.STATE_RELEASE) {
                scrollToAdjustViewsUpOrDown();
            }
        }

        public boolean scrollToAdjustViewsUpOrDown() {
            updateViewParams();

            final int firstPosition = mFirstPosition;
            final int lastPosition = mLastPosition;
            final int itemCount = mItemCount;
            final int childCount = getChildCount();
            final int firstTop = mFirstTop;
            final int lastBottom = mLastBottom;
            final int height = getHeight();

            final RefreshEdge headerEdge = mHeaderEdge;
            final RefreshEdge footerEdge = mFooterEdge;
            final boolean isOnRefreshing = headerEdge != null
                    && headerEdge.getState() == RefreshEdge.STATE_LOADING;
            final boolean isOnLoading = footerEdge != null
                    && footerEdge.getState() == RefreshEdge.STATE_LOADING;
            final int topOffset = firstTop - (mShowHeader && headerEdge != null ? headerEdge.getHeight() : 0);
            final boolean isTooShort = childCount == mItemCount && lastBottom - firstTop < height;
            final int bottomOffset = isTooShort ? firstTop : lastBottom - height;
            final boolean cannotScrollDown = firstPosition == 0 && topOffset > 0;
            final boolean cannotScrollUp = (lastPosition == -1 || lastPosition == itemCount - 1) && bottomOffset <= 0;
            if (cannotScrollDown) {
                int duration = -firstTop;
                if (headerEdge != null && mAllowRefresh && mRefreshListener != null
                        && headerEdge.getState() == RefreshEdge.STATE_RELEASE) {
                    if (!isOnRefreshing) {
                        mRefreshing = true;
                        mShowHeader = true;
                        headerEdge.onStateChanged(RefreshEdge.STATE_LOADING);
                        mRefreshListener.onRefresh();
                    }
                    duration += headerEdge.getHeight();
                } else if (headerEdge != null && mShowHeader) {
                    duration += headerEdge.getHeight();
                }
                startScroll(-duration, (int) Math.abs(duration / mDensity) + 50);
                mTouchMode = TOUCH_MODE_RESCROLL;
            } else if (cannotScrollUp) {
                int distance = bottomOffset;
                if (mAllowLoadMore && footerEdge != null
                        && mLoadMoreListener != null && !isTooShort) {
                    final int state = footerEdge.getState();
                    if (mAutoLoadMore) {
                        if (!(mTouchMode == TOUCH_MODE_SCROLL && state == RefreshEdge.STATE_PULL)) {
                            distance += footerEdge.getHeight();
                        }
                        if (!isOnLoading && (state == RefreshEdge.STATE_RELEASE || state == RefreshEdge.STATE_REST)) {
                            footerEdge.onStateChanged(RefreshEdge.STATE_LOADING);

                            removeCallbacks(mLoadMoreRunnable);
                            postDelayed(mLoadMoreRunnable, (long) Math.abs(distance / mDensity) + 300);
                        }
                    } else {
                        if (isOnLoading) {
                            distance += footerEdge.getHeight();
                        } else if (state == RefreshEdge.STATE_RELEASE) {
                            distance += footerEdge.getHeight();
                            footerEdge.onStateChanged(RefreshEdge.STATE_LOADING);

                            removeCallbacks(mLoadMoreRunnable);
                            postDelayed(mLoadMoreRunnable, (long) Math.abs(distance / mDensity) + 300);
                        }
                    }
                }
                if (footerEdge != null && mTouchMode == TOUCH_MODE_RESCROLL
                        && (footerEdge.getState() == RefreshEdge.STATE_SUCCESS
                        || footerEdge.getState() == RefreshEdge.STATE_FAIL)) {
                    distance = bottomOffset;
                }
                long duration = (long) Math.abs(distance / mDensity) + 50;
                startScroll(distance, (int) duration);
                mTouchMode = TOUCH_MODE_RESCROLL;
            } else {
                return false;
            }
            updateViewParams();
            return true;
        }

        @Override
        public void run() {
            updateViewParams();

            switch (mTouchMode) {
                default:
                    endFling();
                    return;
                case TOUCH_MODE_SCROLL:
                    if (mScroller.isFinished()) {
                        return;
                    }
                case TOUCH_MODE_RESCROLL:
                case TOUCH_MODE_FLING: {
                    final Scroller scroller = mScroller;
                    boolean more = scroller.computeScrollOffset();
                    final int y = scroller.getCurrY();
                    int delta = mLastFlingY - y;
                    if (delta > 0) {
                        delta = Math.min(getHeight() - 1, delta);
                    } else {
                        delta = Math.max(-(getHeight() - 1), delta);
                    }
                    final boolean atEdge = trackMotionScroll(delta);
                    final boolean atEnd = atEdge && (delta != 0);
                    final int touchMode = mTouchMode;
                    if (atEnd) {
                        endFling();
                        if (touchMode == TOUCH_MODE_FLING) {
                            scrollToAdjustViewsUpOrDown();
                        }
                        break;
                    }
                    if (more) {
                        mLastFlingY = y;
                        ViewCompat.postOnAnimation(RefreshRecyclerView.this, this);
                    } else {
                        endFling();
                        if (touchMode == TOUCH_MODE_FLING) {
                            scrollToAdjustViewsUpOrDown();
                        }
                    }
                    break;
                }
            }
        }
    }

}
