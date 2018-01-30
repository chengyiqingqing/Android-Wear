package com.amber.wear.watchfaceyellow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.amber.weather.watchface.watchfaceyellow.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.result.DailyTotalResult;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by joker on 2017/10/12.
 */

public class MainWatchFaceService extends CanvasWatchFaceService {
    private static final int MSG_UPDATE_TIME = 0x123;
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private int percent;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            ResultCallback<DailyTotalResult> {
        private static final int DEFAULT_HOUR_COLOR = Color.WHITE;
        private final int DEFAULT_BATTERY_TEXT_COLOR = Color.WHITE;
        private final int DEFAULT_WEEK_COLOR = Color.WHITE;
        private final int DEFAULT_STEP_COLOR = Color.WHITE;
        private final int yellowCircleColor = Color.rgb(255, 194, 46);
        private final int transparentColor = Color.argb(127, 225, 225, 225);
        private final int DEFAULT_BATTERY_OUTER_COLOR = yellowCircleColor;
        private final int DEFAULT_BATTERY_INNER_COLOR = yellowCircleColor;
        private final int DEFAULT_YELLOW_AROUND_COLOR = yellowCircleColor;
        private final int DEFAULT_TRANSPARENT_AROUND_COLOR = transparentColor;
        private Handler mHandler = new InnerHandler(this);
        private boolean mRegisteredTimeZoneReceiver = false;
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int current = intent.getExtras().getInt("level");// 获得当前电量
                int total = intent.getExtras().getInt("scale");// 获得总电量
                percent = current * 100 / total;
            }
        };
        private boolean inAmbientMode;
        private boolean screenRound;
        private boolean lowBitAmbient;
        private boolean burnInProtection;
        private SimpleDateFormat hourAndMin;
        private Paint yellowAroundPaint;
        private Paint transparentAroundPaint;
        private Paint hourAndMinPaint;
        private Paint weekPaint;
        private Paint stepPaint;
        private Paint batteryTextPaint;
        private Paint batteryOuterPaint;
        private Paint batteryInnerPaint;
        private SimpleDateFormat weekAndMon;
        private Bitmap backgroundBitmap;
        private Bitmap stepBitmap;
        private Bitmap originStepBitmap;
        private Bitmap calendarBitmap;
        private Bitmap originCalendarBitmap;
        private final Paint normalPaint = new Paint();
        private int weekAndStepTextDescent;
        private DecimalFormat decimalFormat;
        private Path aroundPath = new Path();
        private Path transparentAroundPath = new Path();
        /**
         * steps
         */
        private boolean stepsRequested = false;
        private GoogleApiClient mGoogleApiClient;
        private int mStepsTotal = 0;
        private int bottom;

        // Initialize the watch face elements
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(MainWatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Fitness.HISTORY_API)
                    .addApi(Fitness.RECORDING_API)
                    .useDefaultAccount()
                    .build();

            screenRound = getResources().getConfiguration().isScreenRound();
            if (screenRound) {
                backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_circle);
            } else {
                backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_rectangle);
            }
            stepBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon_step);
            calendarBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon_calendar);

            settingPaints();
            dateDataFormat();

            mCalendar = Calendar.getInstance();
            decimalFormat = new DecimalFormat("##0.000");
        }

        private void dateDataFormat() {
            hourAndMin = new SimpleDateFormat("hh:mm", Locale.getDefault());
            weekAndMon = new SimpleDateFormat("EEE - MMM dd", Locale.getDefault());
        }

        private void settingPaints() {
            settingAroundPaint();
            settingHourAndMinPaint();
            settingBatteryPowerPaint();
            settingWeekPaint();
            settingStepsPaint();
        }

        private void settingStepsPaint() {
            stepPaint = new Paint();
            stepPaint.setColor(DEFAULT_STEP_COLOR);
            stepPaint.setAntiAlias(true);
            stepPaint.setTextSize(dp2px(16));
            stepPaint.setTypeface(Typeface.createFromAsset(getAssets(), "Helvetica LT Narrow Bold.ttf"));
        }

        private void settingWeekPaint() {
            weekPaint = new Paint();
            weekPaint.setColor(DEFAULT_WEEK_COLOR);
            weekPaint.setAntiAlias(true);
            weekAndStepTextDescent = weekPaint.getFontMetricsInt().descent;
            weekPaint.setTextSize(dp2px(17));
            weekPaint.setTypeface(Typeface.createFromAsset(getAssets(), "Helvetica LT Narrow Bold.ttf"));
        }

        private void settingBatteryPowerPaint() {
            batteryTextPaint = new Paint();
            batteryTextPaint.setColor(DEFAULT_BATTERY_TEXT_COLOR);
            batteryTextPaint.setAntiAlias(true);
            batteryTextPaint.setTextSize(dp2px(13));
            batteryTextPaint.setTypeface(Typeface.createFromAsset(getAssets(), "Helvetica LT Narrow Bold.ttf"));

            batteryOuterPaint = new Paint();
            batteryOuterPaint.setColor(DEFAULT_BATTERY_OUTER_COLOR);
            batteryOuterPaint.setAntiAlias(true);
            batteryOuterPaint.setStrokeWidth(1F);
            batteryOuterPaint.setStyle(Paint.Style.STROKE);

            batteryInnerPaint = new Paint();
            batteryInnerPaint.setColor(DEFAULT_BATTERY_INNER_COLOR);
            batteryInnerPaint.setAntiAlias(true);
            batteryInnerPaint.setStrokeWidth(1F);
            batteryInnerPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        }

        private void settingHourAndMinPaint() {
            hourAndMinPaint = new Paint();
            hourAndMinPaint.setColor(DEFAULT_HOUR_COLOR);
            hourAndMinPaint.setTextAlign(Paint.Align.CENTER);
            hourAndMinPaint.setAntiAlias(true);
            hourAndMinPaint.setTextSize(dp2px(30));
            hourAndMinPaint.setTypeface(Typeface.createFromAsset(getAssets(), "HelveticaNeueLTPro-LtEx.otf"));
        }

        private void settingAroundPaint() {
            yellowAroundPaint = new Paint();
            yellowAroundPaint.setColor(DEFAULT_YELLOW_AROUND_COLOR);
            yellowAroundPaint.setAntiAlias(true);
            yellowAroundPaint.setStyle(Paint.Style.STROKE);
            yellowAroundPaint.setStrokeJoin(Paint.Join.ROUND);
            yellowAroundPaint.setStrokeWidth(6F);

            transparentAroundPaint = new Paint();
            transparentAroundPaint.setColor(DEFAULT_TRANSPARENT_AROUND_COLOR);
            transparentAroundPaint.setAntiAlias(true);
            transparentAroundPaint.setStyle(Paint.Style.STROKE);
            transparentAroundPaint.setStrokeJoin(Paint.Join.ROUND);
            transparentAroundPaint.setStrokeWidth(6F);
        }

        /**
         * 跟随 {@link #onVisibilityChanged(boolean)} 一起调用？
         *
         * @param properties
         */
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        // 屏亮/屏暗时调用
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            this.inAmbientMode = inAmbientMode;
            changePaintStyle(inAmbientMode);
            changBitmapColor(inAmbientMode);
            updateTimer();
        }

        private void changBitmapColor(boolean inAmbientMode) {
            if (inAmbientMode) {
                stepBitmap = replaceBitmapColor(stepBitmap);
                calendarBitmap = replaceBitmapColor(calendarBitmap);
            } else {
                stepBitmap = originStepBitmap;
                calendarBitmap = originCalendarBitmap;
            }
        }

        private void changePaintStyle(boolean inAmbientMode) {
            if (inAmbientMode) {
                yellowAroundPaint.setColor(Color.GRAY);
                transparentAroundPaint.setColor(Color.GRAY);
                hourAndMinPaint.setColor(Color.GRAY);
                weekPaint.setColor(Color.GRAY);
                stepPaint.setColor(Color.GRAY);
                batteryTextPaint.setColor(Color.GRAY);
                batteryOuterPaint.setColor(Color.GRAY);
                batteryInnerPaint.setColor(Color.GRAY);

                yellowAroundPaint.setAntiAlias(false);
                batteryInnerPaint.setStrokeWidth(1F);
                batteryOuterPaint.setStrokeWidth(1F);
                transparentAroundPaint.setAntiAlias(false);
                hourAndMinPaint.setAntiAlias(false);
                weekPaint.setAntiAlias(false);
                stepPaint.setAntiAlias(false);
                batteryTextPaint.setAntiAlias(false);
                batteryOuterPaint.setAntiAlias(false);
                batteryInnerPaint.setAntiAlias(false);
            } else {
                yellowAroundPaint.setColor(DEFAULT_YELLOW_AROUND_COLOR);
                transparentAroundPaint.setColor(DEFAULT_TRANSPARENT_AROUND_COLOR);
                hourAndMinPaint.setColor(DEFAULT_HOUR_COLOR);
                weekPaint.setColor(DEFAULT_WEEK_COLOR);
                stepPaint.setColor(DEFAULT_STEP_COLOR);
                batteryTextPaint.setColor(DEFAULT_BATTERY_TEXT_COLOR);
                batteryOuterPaint.setColor(DEFAULT_BATTERY_OUTER_COLOR);
                batteryInnerPaint.setColor(DEFAULT_BATTERY_INNER_COLOR);

                yellowAroundPaint.setAntiAlias(true);
                batteryInnerPaint.setStrokeWidth(1F);
                batteryOuterPaint.setStrokeWidth(1F);
                transparentAroundPaint.setAntiAlias(true);
                hourAndMinPaint.setAntiAlias(true);
                weekPaint.setAntiAlias(true);
                stepPaint.setAntiAlias(true);
                batteryTextPaint.setAntiAlias(true);
                batteryOuterPaint.setAntiAlias(true);
                batteryInnerPaint.setAntiAlias(true);
            }
        }

        // only once
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            float scale = ((float) width) / (float) backgroundBitmap.getWidth();

            // background bitmap adjust
            backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap,
                    (int) (backgroundBitmap.getWidth() * scale),
                    (int) (backgroundBitmap.getHeight() * scale), true);

            // calendar bitmap adjust
            calendarBitmap = originCalendarBitmap = Bitmap.createScaledBitmap(calendarBitmap,
                    (int) (calendarBitmap.getWidth() * 0.3),
                    (int) (calendarBitmap.getHeight() * 0.3), true);

            // step bitmap adjust
            stepBitmap = originStepBitmap = Bitmap.createScaledBitmap(stepBitmap,
                    (int) (stepBitmap.getWidth() * 0.3),
                    (int) (stepBitmap.getHeight() * 0.3), true);

            if (burnInProtection || lowBitAmbient) {
                calendarBitmap = replaceBitmapColor(calendarBitmap);
                stepBitmap = replaceBitmapColor(stepBitmap);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            SimpleDateFormat ss = new SimpleDateFormat("ss", Locale.getDefault());
            String seconds = ss.format(now);

            int maxHeight = backgroundBitmap.getHeight();
            int maxWidth = backgroundBitmap.getWidth();
            // 1.draw background
            canvas.drawBitmap(backgroundBitmap, 0, 0, normalPaint);
            float per = Float.parseFloat(decimalFormat.format(Integer.parseInt(seconds) / 60F));
            if (per == 0F) {
                per = 1F;
            }
            if (screenRound) {
                // 2.draw around line
                if (!inAmbientMode) {
                    int outR = maxHeight / 2;
                    int inR = (int) (outR * 0.95);
                    // left top
                    float v1 = (float) ((Math.sqrt(2) * outR) - Math.sqrt(2 * Math.pow(inR, 2)));
                    // right bottom
                    float v2 = v1 + 1.95F * inR;
                    // black clock
                    if (bottom > 0) {
                        float blackSwap = (float) (Math.acos((outR - bottom) / outR) * 90 / Math.PI);
                        aroundPath.reset();
                        transparentAroundPath.reset();

                        transparentAroundPath.addArc(v1, v1, v2, v2, -(270 - blackSwap / 2), 360 - blackSwap);
                        transparentAroundPath.close();
                        canvas.drawPath(transparentAroundPath, transparentAroundPaint);

                        float currentSwap = per * 360F;
                        float otherSwap = 180F - blackSwap / 2;
                        float per1 = otherSwap / 360F;
                        if (currentSwap <= 180F - blackSwap / 2) {
                            aroundPath.addArc(v1, v1, v2, v2, -90, currentSwap);
                        } else if (currentSwap > 180F - blackSwap / 2 && currentSwap <= 180F + blackSwap / 2) {
                            aroundPath.addArc(v1, v1, v2, v2, -90, 180F - blackSwap / 2);
                            aroundPath.rLineTo(-(float) (((Math.sin(blackSwap) * inR) - 2 * yellowAroundPaint.getStrokeWidth()) * (per - per1) * (360F / blackSwap)), 0);
                        } else {
                            aroundPath.addArc(v1, v1, v2, v2, -90, 180F - blackSwap / 2);
                            aroundPath.rLineTo(-(float) ((Math.sin(blackSwap) * inR) - 2 * yellowAroundPaint.getStrokeWidth()), 0);
                            aroundPath.arcTo(v1, v1, v2, v2, (90F + blackSwap / 2), currentSwap - (180F + blackSwap / 2), true);
                        }
                        canvas.drawPath(aroundPath, yellowAroundPaint);
                    } else {
                        canvas.drawArc(v1, v1, v2, v2, -90F, per * 360F, false, yellowAroundPaint);
                        canvas.drawArc(v1, v1, v2, v2, per * 360F - 90F, 360F * (1 - per), false, transparentAroundPaint);
                    }
                }
                // 3.draw week and mon
                canvas.drawBitmap(calendarBitmap, maxWidth / 4F, maxHeight * 0.65F, normalPaint);
                canvas.drawBitmap(stepBitmap, maxWidth / 4F, maxHeight * 0.65F + calendarBitmap.getHeight() * 1.2F, normalPaint);
                canvas.drawText(weekAndMon.format(now), maxWidth / 4F + calendarBitmap.getWidth() * 1.5F,
                        maxHeight * 0.65F + calendarBitmap.getHeight() - weekAndStepTextDescent * 2,
                        weekPaint);
                canvas.drawText(String.valueOf(mStepsTotal), maxWidth / 4F + stepBitmap.getWidth() * 1.5F,
                        maxHeight * 0.65F + calendarBitmap.getHeight() + stepBitmap.getHeight(),
                        stepPaint);
            } else {
                // 2.draw around line
                if (!inAmbientMode) {
                    float width = maxWidth * 0.9F;
                    Path[] paths = getPath(per, maxWidth * 0.05F + width / 2, maxWidth * 0.05F, width, maxWidth * 0.05F);
                    canvas.drawPath(paths[0], yellowAroundPaint);
                    canvas.drawPath(paths[1], transparentAroundPaint);
                }
                // 3.draw week and mon
                canvas.drawBitmap(calendarBitmap, maxWidth / 6F, maxHeight * 0.65F, normalPaint);
                canvas.drawBitmap(stepBitmap, maxWidth / 6F, maxHeight * 0.65F + calendarBitmap.getHeight() * 1.2F, normalPaint);
                canvas.drawText(weekAndMon.format(now), maxWidth / 6F + calendarBitmap.getWidth() * 1.5F,
                        maxHeight * 0.65F + calendarBitmap.getHeight() - weekAndStepTextDescent * 2,
                        weekPaint);
                canvas.drawText(String.valueOf(mStepsTotal), maxWidth / 6F + stepBitmap.getWidth() * 1.5F,
                        maxHeight * 0.65F + calendarBitmap.getHeight() + stepBitmap.getHeight(),
                        stepPaint);
            }
            // 4.draw battery text
            canvas.drawText(percent + "%", maxWidth / 2F, maxHeight / 6F, batteryTextPaint);
            // 5.draw battery view
            Paint.FontMetricsInt fontMetricsInt = batteryTextPaint.getFontMetricsInt();
            int batteryViewMaxHeight = -(fontMetricsInt.bottom + fontMetricsInt.top);
            float bottom = maxHeight / 6F;
            float right = (maxWidth - batteryInnerPaint.measureText(String.valueOf(percent))) / 2F;
            float top = bottom - batteryViewMaxHeight;
            float left = right - 0.55F * batteryViewMaxHeight;
            canvas.drawRoundRect(left, top, right, bottom, 1, 1, batteryOuterPaint);
//            float anodeHeight = 0.2F * batteryViewMaxHeight;
//            float anodeExcursion = (0.4F * batteryViewMaxHeight - anodeHeight) / 2F;
//            canvas.drawRect(left + anodeExcursion, top - anodeExcursion, right - anodeExcursion, top, batteryInnerPaint);
            float margin = 0.15F * (right - left);
            float innerRight = right - margin - batteryInnerPaint.getStrokeWidth() / 2F;
            float innerBottom = bottom - margin - batteryInnerPaint.getStrokeWidth() / 2F;
            float innerLeft = left + margin + batteryInnerPaint.getStrokeWidth() / 2F;
            float innerTop = top + margin + batteryInnerPaint.getStrokeWidth() / 2F;
            canvas.drawRect(innerLeft, innerTop + (100 - percent) / 100F * (innerBottom - innerTop), innerRight, innerBottom, batteryInnerPaint);
            // 6.draw hour and min
            canvas.drawText(hourAndMin.format(now), maxWidth / 2, maxHeight / 3, hourAndMinPaint);
        }

        @Size(2)
        private Path[] getPath(float percentage, float startX, float startY, float length, float margin) {
            Path yellowPath = new Path();
            Path transparentPath = new Path();
            yellowPath.moveTo(startX, startY);
            float right = startX + length / 2;
            float bottom = startY + length;
            float left = startX - length / 2;

            if (percentage >= 0F && percentage <= 0.125F) {
                // ok
                yellowPath.lineTo(startX + percentage * length * 4 - margin, startY);

                transparentPath.moveTo(startX + percentage * length * 4 - margin, startY);
                transparentPath.lineTo(right, startY);
                transparentPath.lineTo(right, bottom);
                transparentPath.lineTo(left, bottom);
                transparentPath.lineTo(left, startY);
                transparentPath.lineTo(startX, startY);
            } else if (percentage > 0.125F && percentage <= 0.375F) {
                // ok
                yellowPath.lineTo(right, startY);
                yellowPath.lineTo(right, startY + (percentage - 0.125F) * length * 4);

                transparentPath.moveTo(right, startY + (percentage - 0.125F) * length * 4);
                transparentPath.lineTo(right, bottom);
                transparentPath.lineTo(left, bottom);
                transparentPath.lineTo(left, startY);
                transparentPath.lineTo(startX, startY);
            } else if (percentage > 0.375F && percentage <= 0.625F) {
                // ok
                yellowPath.lineTo(right, startY);
                yellowPath.lineTo(right, bottom);
                yellowPath.lineTo((0.625F - percentage) * length * 4 + margin, bottom);

                transparentPath.moveTo((0.625F - percentage) * length * 4 + margin, bottom);
                transparentPath.lineTo(left, bottom);
                transparentPath.lineTo(left, startY);
                transparentPath.lineTo(startX, startY);
            } else if (percentage > 0.625F && percentage <= 0.875F) {
                yellowPath.lineTo(right, startY);
                yellowPath.lineTo(right, bottom);
                yellowPath.lineTo(left, bottom);
                yellowPath.lineTo(left, startY + (0.875F - percentage) * length * 4);

                transparentPath.moveTo(left, startY + (0.875F - percentage) * length * 4);
                transparentPath.lineTo(left, startY);
                transparentPath.lineTo(startX, startY);
            } else {
                // > 0.875F
                yellowPath.lineTo(right, startY);
                yellowPath.lineTo(right, bottom);
                yellowPath.lineTo(left, bottom);
                yellowPath.lineTo(left, startY);
                yellowPath.lineTo(margin + (percentage - 0.875F) * length * 4, startY);

                transparentPath.moveTo(margin + (percentage - 0.875F) * length * 4, startY);
                transparentPath.lineTo(startX, startY);
            }

            return new Path[]{yellowPath, transparentPath};
        }

        // Initialize the custom timer
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter timeZoneFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MainWatchFaceService.this.registerReceiver(mTimeZoneReceiver, timeZoneFilter);
            IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            MainWatchFaceService.this.registerReceiver(mBatteryReceiver, batteryFilter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MainWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
            MainWatchFaceService.this.unregisterReceiver(mBatteryReceiver);
        }

        private void updateTimer() {
            mHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !inAmbientMode;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void updateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private float dp2px(float dp) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }

        private Bitmap replaceBitmapColor(Bitmap oldBitmap) {
            int width = oldBitmap.getWidth();
            int height = oldBitmap.getHeight();
            Bitmap faceIconGreyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(faceIconGreyBitmap);
            Paint paint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0.1F);
            ColorMatrixColorFilter colorMatrixFilter = new ColorMatrixColorFilter(colorMatrix);
            paint.setColorFilter(colorMatrixFilter);
            canvas.drawBitmap(oldBitmap, 0, 0, paint);

            return faceIconGreyBitmap;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            stepsRequested = false;
            Fitness.RecordingApi.subscribe(mGoogleApiClient, DataType.TYPE_STEP_COUNT_DELTA);
            getTotalSteps();
        }

        private void getTotalSteps() {
            if ((mGoogleApiClient != null)
                    && (mGoogleApiClient.isConnected())
                    && (!stepsRequested)) {

                stepsRequested = true;

                PendingResult<DailyTotalResult> stepsResult =
                        Fitness.HistoryApi.readDailyTotal(
                                mGoogleApiClient,
                                DataType.TYPE_STEP_COUNT_DELTA);

                stepsResult.setResultCallback(this);
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        }

        @Override
        public void onResult(@NonNull DailyTotalResult dailyTotalResult) {
            stepsRequested = false;
            if (dailyTotalResult.getStatus().isSuccess()) {
                List<DataPoint> points = dailyTotalResult.getTotal().getDataPoints();
                if (!points.isEmpty()) {
                    mStepsTotal = points.get(0).getValue(Field.FIELD_STEPS).asInt();
                }
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            bottom = insets.getSystemWindowInsetBottom();
        }
    }


    private static class InnerHandler extends Handler {
        private WeakReference<MainWatchFaceService.Engine> weakReference;

        InnerHandler(MainWatchFaceService.Engine engine) {
            weakReference = new WeakReference<>(engine);
        }

        @Override
        public void handleMessage(Message msg) {
            MainWatchFaceService.Engine engine = weakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.updateTimeMessage();
                        break;
                    default:
                        break;
                }
            }
        }
    }
}
