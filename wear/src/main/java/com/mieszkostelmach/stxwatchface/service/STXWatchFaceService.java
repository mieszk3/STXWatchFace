package com.mieszkostelmach.stxwatchface.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.mieszkostelmach.stxwatchface.R;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * @author Mieszko Stelmach
 *         Created on 04-01-2015.
 */
public class STXWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "STXWatchFaceService";

    public static final String KEY_WATCH_FACE = "WATCH_FACE";
    public static final String PATH_WITH_FEATURE = "/watch_face_config/STXWatchFace";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new STXEngine();
    }

    private class STXEngine extends Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, ResultCallback<DataApi.DataItemResult> {
        private static final int MSG_UPDATE_TIME = 0;

        Paint mCenterPaint;
        Paint mCenterPaintBlack;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mTickPaint;
        boolean mMute;
        Time mTime;

        Path mPath;
        Matrix mMatrix;

        int backgroundBitmapId;
        Bitmap mBackgroundBitmap;
        Bitmap mBackgroundScaledBitmap;

        boolean mRegisteredTimeZoneReceiver;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        boolean mBurnInProtectMode;

        /**
         * Handler to update the time once a second in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(STXWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            /* initialize your watch face */

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(STXWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = STXWatchFaceService.this.getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.superhero);
            this.mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            final int centerColor = resources.getColor(R.color.stx_color);
            this.mCenterPaint = new Paint();
            mCenterPaint.setColor(centerColor);
            mCenterPaint.setStrokeWidth(2.f);
            mCenterPaint.setStyle(Paint.Style.FILL);
            mCenterPaint.setAntiAlias(true);

            this.mCenterPaintBlack = new Paint();
            mCenterPaintBlack.setARGB(255, 0, 0, 0);
            mCenterPaintBlack.setStyle(Paint.Style.FILL);
            mCenterPaintBlack.setAntiAlias(true);

            final int hourColor = resources.getColor(R.color.stx_color);
            this.mHourPaint = new Paint();
            mHourPaint.setColor(hourColor);
            mHourPaint.setStrokeWidth(8.f);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);

            final int minuteColor = resources.getColor(R.color.minute_color);
            this.mMinutePaint = new Paint();
            mMinutePaint.setColor(minuteColor);
            mMinutePaint.setStrokeWidth(5.f);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);

            final int secondColor = resources.getColor(R.color.second_color);
            this.mSecondPaint = new Paint();
            mSecondPaint.setColor(secondColor);
            mSecondPaint.setStrokeWidth(2.f);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);

            final int thickColor = resources.getColor(R.color.tick_color);
            this.mTickPaint = new Paint();
            mTickPaint.setColor(thickColor);
            mTickPaint.setStrokeWidth(2.f);
            mTickPaint.setAntiAlias(true);

            this.mPath = new Path();
            this.mMatrix = new Matrix();

            this.mTime = new Time();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            /* get device features (burn-in, low-bit ambient) */
            super.onPropertiesChanged(properties);
            this.mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            this.mBurnInProtectMode = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            /* the time changed */
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            /* the wearable switched between modes */
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mCenterPaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mTickPaint.setAntiAlias(antiAlias);
            }
            if (inAmbientMode) {
                mCenterPaint.setColor(getResources().getColor(R.color.stx_ambient_color));
                mHourPaint.setColor(getResources().getColor(R.color.hour_ambient_color));
                mMinutePaint.setColor(getResources().getColor(R.color.minute_ambient_color));
                mTickPaint.setColor(getResources().getColor(R.color.tick_ambient_color));
                if (mBurnInProtectMode) {
                    mHourPaint.setStyle(Paint.Style.STROKE);
                    mHourPaint.setStrokeWidth(2f);
                    mMinutePaint.setStyle(Paint.Style.STROKE);
                    mMinutePaint.setStrokeWidth(1f);
                    mCenterPaint.setStyle(Paint.Style.STROKE);
                }
            } else {
                mCenterPaint.setColor(getResources().getColor(R.color.stx_color));
                mHourPaint.setColor(getResources().getColor(R.color.hour_color));
                mMinutePaint.setColor(getResources().getColor(R.color.minute_color));
                mTickPaint.setColor(getResources().getColor(R.color.tick_color));
                if (mBurnInProtectMode) {
                    mHourPaint.setStyle(Paint.Style.FILL);
                    mHourPaint.setStrokeWidth(8f);
                    mMinutePaint.setStyle(Paint.Style.FILL);
                    mMinutePaint.setStrokeWidth(5f);
                    mCenterPaint.setStyle(Paint.Style.FILL);
                }
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* draw your watch face */
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background, scaled to fit.
            if (!isInAmbientMode()) {
                if (mBackgroundScaledBitmap == null
                        || mBackgroundScaledBitmap.getWidth() != width
                        || mBackgroundScaledBitmap.getHeight() != height) {
                    mBackgroundScaledBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap, width, height, true);
                }
                canvas.drawBitmap(mBackgroundScaledBitmap, 0, 0, null);
            } else {
                canvas.drawRGB(0, 0, 0);
            }

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            if (!isInAmbientMode() || !mBurnInProtectMode) {
                // Draw the ticks.
                float innerTickRadius = centerX - 15;
                //noinspection UnnecessaryLocalVariable
                float outerTickRadius = centerX;
                for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                    float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                    float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                    float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                    float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                    float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                    canvas.drawLine(centerX + innerX, centerY + innerY,
                            centerX + outerX, centerY + outerY, mTickPaint);
                }
            }

            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            if (!isInAmbientMode() || !mBurnInProtectMode) {
                canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHourPaint);
            } else {
                mPath.reset();
                mPath.addRect(centerX, centerY - 4, centerX + hrLength, centerY + 4, Path.Direction.CW);
                mMatrix.reset();
                mMatrix.postRotate((float) (Math.toDegrees(hrRot) - 90), centerX, centerY);
                mPath.transform(mMatrix);
                canvas.drawPath(mPath, mHourPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            if (!isInAmbientMode() || !mBurnInProtectMode) {
                canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mMinutePaint);
            } else {
                mPath.reset();
                mPath.addRect(centerX, centerY - 2, centerX + minLength, centerY + 2, Path.Direction.CW);
                mMatrix.reset();
                mMatrix.postRotate((float) (Math.toDegrees(minRot) - 90), centerX, centerY);
                mPath.transform(mMatrix);
                canvas.drawPath(mPath, mMinutePaint);
            }

            if (!isInAmbientMode()) {
                float secLength = centerX - 20;
                float secRot = mTime.second / 30f * (float) Math.PI;
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mSecondPaint);
            }

            if (isInAmbientMode() && mBurnInProtectMode) {
                canvas.drawOval(centerY - 10, centerX - 10, centerY + 10, centerX + 10, mCenterPaintBlack);
            }
            canvas.drawOval(centerY - 10, centerX - 10, centerY + 10, centerX + 10, mCenterPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            /* the watch face became visible or invisible */
            super.onVisibilityChanged(visible);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            STXWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            STXWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onConnected(Bundle bundle) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + bundle);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, STXEngine.this);
            fetchData(mGoogleApiClient);
        }

        @Override
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + connectionResult);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            try {
                for (DataEvent dataEvent : dataEvents) {
                    if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                        continue;
                    }

                    DataItem dataItem = dataEvent.getDataItem();
                    if (!dataItem.getUri().getPath().equals(PATH_WITH_FEATURE)) {
                        continue;
                    }

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                    DataMap config = dataMapItem.getDataMap();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Config DataItem updated:" + config);
                    }
                    setUpSettings(config);
                }
            } finally {
                dataEvents.close();
            }
        }

        private void fetchData(final GoogleApiClient client) {
            Wearable.NodeApi.getLocalNode(client).setResultCallback(
                    new ResultCallback<NodeApi.GetLocalNodeResult>() {
                        @Override
                        public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                            String localNode = getLocalNodeResult.getNode().getId();
                            Uri uri = new Uri.Builder()
                                    .scheme("wear")
                                    .path(PATH_WITH_FEATURE)
                                    .authority(localNode)
                                    .build();
                            Wearable.DataApi.getDataItem(client, uri)
                                    .setResultCallback(STXEngine.this);
                        }
                    }
            );
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem configDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(configDataItem);
                    DataMap config = dataMapItem.getDataMap();
                    setUpSettings(config);
                } else {
                    setUpSettings(null);
                }
            }
        }

        private void setUpSettings(DataMap config) {
            boolean needBackgroundUpdate = false;
            if (config == null || !config.containsKey(KEY_WATCH_FACE)) {
                //select default
                if (backgroundBitmapId != R.drawable.superhero) {
                    this.backgroundBitmapId = R.drawable.superhero;
                    needBackgroundUpdate = true;
                }
            } else {
                String faceSelected = config.getString(KEY_WATCH_FACE);
                switch (faceSelected) {
                    case "superhero":
                        if (backgroundBitmapId != R.drawable.superhero) {
                            this.backgroundBitmapId = R.drawable.superhero;
                            needBackgroundUpdate = true;
                        }
                        break;
                    case "superwhero":
                        if (backgroundBitmapId != R.drawable.superwhero) {
                            this.backgroundBitmapId = R.drawable.superwhero;
                            needBackgroundUpdate = true;
                        }
                        break;
                    case "superhero_alpha":
                        if (backgroundBitmapId != R.drawable.superhero_alpha) {
                            this.backgroundBitmapId = R.drawable.superhero_alpha;
                            needBackgroundUpdate = true;
                        }
                        break;
                    case "superwhero_alpha":
                        if (backgroundBitmapId != R.drawable.superwhero_alpha) {
                            this.backgroundBitmapId = R.drawable.superwhero_alpha;
                            needBackgroundUpdate = true;
                        }
                        break;
                }
            }
            if (needBackgroundUpdate) {
                Resources resources = STXWatchFaceService.this.getResources();
                Drawable backgroundDrawable = resources.getDrawable(backgroundBitmapId);
                this.mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
                this.mBackgroundScaledBitmap = null;
                postInvalidate();
            }
        }
    }
}
