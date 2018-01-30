/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amber.wear.watchface;

import android.animation.ValueAnimator;
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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;


import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private boolean isRound;
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        private Calendar mCalendar;
        private SimpleDateFormat mMonthFormat;
        private SimpleDateFormat mDayFormat;
        private SimpleDateFormat mAmPmFormat;
        private SimpleDateFormat mWeekFormat;
        private SimpleDateFormat mDateFormat;
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mCenterX;
        private float mCenterY;

        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;

        /* Colors for all text */
        private int mTextHourColor;
        private int mTextMinuteColor;
        private int mTextAmPmColor;
        private int mTextWeekColor;
        private int mTextDateColor;

        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mTextHourPaint;
        private Paint mTextMinutePaint;
        private Paint mTextAmPmPaint;
        private Paint mTextWeekPaint;
        private Paint mTextDatePaint;

        private Rect rect;


        private Paint mBackgroundPaint;



        private Bitmap mBackgroundBitmap;
        private Bitmap mBackgroundCenterBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private Bitmap mGrayBackgroundCenterBitmap;


        /* Animators */
        private ValueAnimator roundAnimator;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private final Rect mPeekCardBounds = new Rect();

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            isRound = getResources().getConfiguration().isScreenRound();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.WHITE);
            if (isRound){
                mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            }else{
                mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sbg);
            }
            mBackgroundCenterBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg_center);


            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.BLACK;
            mTextHourColor = Color.argb(255,98,191,255);
            mTextMinuteColor = Color.WHITE;
            mTextAmPmColor = Color.GRAY;
            mTextWeekColor = Color.RED;
            mTextDateColor = Color.argb(255,98,191,255);

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTextHourPaint = new Paint();
            mTextHourPaint.setColor(mTextHourColor);
            mTextHourPaint.setAntiAlias(true);
            mTextHourPaint.setTextAlign(Paint.Align.LEFT);
            mTextHourPaint.setTextSize(dp2px(30));
            mTextHourPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Helvetica Rounded LT Bold Condensed.ttf"));

            mTextMinutePaint = new Paint();
            mTextMinutePaint.setColor(mTextMinuteColor);
            mTextMinutePaint.setAntiAlias(true);
            mTextMinutePaint.setTextAlign(Paint.Align.LEFT);
            mTextMinutePaint.setTextSize(dp2px(15));
            mTextMinutePaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Helvetica Rounded LT Bold Condensed.ttf"));

            mTextAmPmPaint = new Paint();
            mTextAmPmPaint.setColor(mTextAmPmColor);
            mTextAmPmPaint.setAntiAlias(true);
            mTextAmPmPaint.setTextAlign(Paint.Align.LEFT);
            mTextAmPmPaint.setTextSize(dp2px(10));
            mTextAmPmPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Helvetica Rounded LT Bold Condensed.ttf"));

            mTextWeekPaint = new Paint();
            mTextWeekPaint.setColor(mTextWeekColor);
            mTextWeekPaint.setAntiAlias(true);
            mTextWeekPaint.setTextAlign(Paint.Align.LEFT);
            mTextWeekPaint.setTextSize(dp2px(10));
            mTextWeekPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Helvetica Rounded LT Bold Condensed.ttf"));

            mTextDatePaint = new Paint();
            mTextDatePaint.setColor(mTextDateColor);
            mTextDatePaint.setAntiAlias(true);
            mTextDatePaint.setTextAlign(Paint.Align.LEFT);
            mTextDatePaint.setTextSize(dp2px(15));
            mTextDatePaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Helvetica Rounded LT Bold Condensed.ttf"));


            rect = new Rect();

            /* Extract colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    if (palette != null) {
//                        mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                        mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                        mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                        updateWatchHandStyle();
                    }
                }
            });

            mCalendar = Calendar.getInstance();
            mMonthFormat = new SimpleDateFormat("hh",  Locale.getDefault());
            mDayFormat = new SimpleDateFormat("mm",  Locale.getDefault());
            mAmPmFormat = new SimpleDateFormat("a",  Locale.getDefault());
            mWeekFormat = new SimpleDateFormat("E",  Locale.getDefault());
            mDateFormat = new SimpleDateFormat("MMM dd",  Locale.getDefault());
        }

        private float dp2px(float dp) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;
            updateWatchHandStyle();

            mAmbient = inAmbientMode;
            if (mAmbient) {
                if (roundAnimator != null && roundAnimator.isRunning()) {
                    roundAnimator.cancel();
                }
            }
            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle(){
            if (mAmbient){
                mHourPaint.setColor(Color.GRAY);
                mMinutePaint.setColor(Color.GRAY);
                mSecondPaint.setColor(Color.GRAY);
                mTickAndCirclePaint.setColor(Color.GRAY);
                mTextHourPaint.setColor(Color.GRAY);
                mTextMinutePaint.setColor(Color.GRAY);
                mTextAmPmPaint.setColor(Color.GRAY);
                mTextWeekPaint.setColor(Color.GRAY);
                mTextDatePaint.setColor(Color.GRAY);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);
                mTextHourPaint.setAntiAlias(false);
                mTextMinutePaint.setAntiAlias(false);
                mTextAmPmPaint.setAntiAlias(false);
                mTextWeekPaint.setAntiAlias(false);
                mTextDatePaint.setAntiAlias(false);


                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchHandColor);
                mTextHourPaint.setColor(mTextHourColor);
                mTextMinutePaint.setColor(mTextMinuteColor);
                mTextAmPmPaint.setColor(mTextAmPmColor);
                mTextWeekPaint.setColor(mTextWeekColor);
                mTextDatePaint.setColor(mTextDateColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                mTextHourPaint.setAntiAlias(true);
                mTextMinutePaint.setAntiAlias(true);
                mTextAmPmPaint.setAntiAlias(true);
                mTextWeekPaint.setAntiAlias(true);
                mTextDatePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                mTextHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mTextMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mTextAmPmPaint.setAlpha(inMuteMode ? 80 : 255);
                mTextWeekPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.95);
            sMinuteHandLength = (float) (mCenterX * 0.85);
            sHourHandLength = (float) (mCenterX * 0.7);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);

            mBackgroundCenterBitmap = Bitmap.createScaledBitmap(mBackgroundCenterBitmap,
                    (int) (mBackgroundCenterBitmap.getWidth() * scale),
                    (int) (mBackgroundCenterBitmap.getHeight() * scale), true);

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
                initGrayBackgroundCenterBitmap();
            }
        }

        //灰色背景
        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        //灰色中部圈儿
        private void initGrayBackgroundCenterBitmap() {
            mGrayBackgroundCenterBitmap = Bitmap.createBitmap(
                    mBackgroundCenterBitmap.getWidth(),
                    mBackgroundCenterBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundCenterBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundCenterBitmap, 0, 0, grayPaint);
        }
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            //背景
            if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
//            float innerTickRadius = mCenterX - 10;
//            float outerTickRadius = mCenterX;
//            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
//                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
//                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
//                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
//                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
//                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
//                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
//                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
//            }

            float bcWidth = mBackgroundCenterBitmap.getWidth();
            float bcHeight = mBackgroundCenterBitmap.getHeight();

            // date
            String dateStr = mDateFormat.format(now);
            mTextDatePaint.getTextBounds(dateStr,0,dateStr.length(),rect);
            float dateWidth =rect.width();
            float dateHeight =rect.height();
            canvas.drawText(dateStr.toUpperCase(), mCenterX - dateWidth / 2 , mCenterY + dateHeight/2 - 0.3f * bcHeight, mTextDatePaint);

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */

            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

            }


            /* Restore the canvas' original orientation. */
            canvas.restore();

            //中部圈儿
            if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundCenterBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundCenterBitmap, 0, 0, mBackgroundPaint);
            }


            // 时
            String monthStr = mMonthFormat.format(now);
            mTextHourPaint.getTextBounds(monthStr,0,monthStr.length(),rect);
            float monthWidth =rect.width();
            float monthHeight =rect.height();
            canvas.drawText(monthStr, mCenterX - monthWidth / 2, mCenterY+ monthHeight/2 - 0.1f*bcHeight, mTextHourPaint);

            // 分
            String dayStr = mDayFormat.format(now);
            mTextMinutePaint.getTextBounds(dayStr,0,dayStr.length(),rect);
            float dayWidth =rect.width();
            float dayHeight =rect.height();
            canvas.drawText(dayStr, mCenterX - dayWidth / 2, mCenterY + dayHeight + 0.1f*bcHeight, mTextMinutePaint);

            // ampm
            String ampmStr = mAmPmFormat.format(now);
            mTextAmPmPaint.getTextBounds(ampmStr,0,ampmStr.length(),rect);
            float ampmWidth =rect.width();
            float ampmHeight =rect.height();
            canvas.drawText(ampmStr.toUpperCase(), mCenterX - ampmWidth / 2  + 0.1f*bcWidth, mCenterY + ampmHeight/2 , mTextAmPmPaint);


            // Week
            String weekStr = mWeekFormat.format(now);
            mTextWeekPaint.getTextBounds(weekStr,0,weekStr.length(),rect);
            float weekWidth =rect.width();
            float weekHeight =rect.height();
            canvas.drawText(weekStr.toUpperCase(), mCenterX - weekWidth / 2  - 0.1f*bcWidth, mCenterY + weekHeight/2 , mTextWeekPaint);



            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {

            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                Log.i("htht", "我被调用了------发送一直重绘的请求 ");
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
