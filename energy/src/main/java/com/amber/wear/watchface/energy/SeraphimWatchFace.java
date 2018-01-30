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

package com.amber.wear.watchface.energy;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
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


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class SeraphimWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "swwMyWatchFace";

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

    private class Engine extends CanvasWatchFaceService.Engine implements MessageApi.MessageListener{
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
        private SimpleDateFormat mDaysFormat;
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

        private Bitmap mCenterBitmap;
        private Bitmap mHourBitmap;
        private Bitmap mMinuteBitmap;
        private Bitmap mSecHandBitmap;

        private Bitmap mDateTextView;
        private Bitmap mLeftIphone;
        private Bitmap mRightWear;
        private Bitmap mHeart;

        private Bitmap mPointerIphone;
        private Bitmap mPointerWatch;
        private Bitmap mPointerHeart;

        //bat,temp;
        private final long HANDHELD_UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);
        private String mWatchBatteryPercentage = "";
        private long mLastUpdateRequestTime = 0;
        private String mTemperature = "";
        private String mPhoneBatteryPercentage = "";
        private GoogleApiClient mGoogleApiClient;


        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e(TAG, "onReceive: "+"Broadcast--mTimeZoneReceiver" );
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.e(TAG, "onCreate: "+"surfaceHolder" );
            isRound = getResources().getConfiguration().isScreenRound();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SeraphimWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            //添加googleClient;
            mGoogleApiClient = new GoogleApiClient.Builder(SeraphimWatchFace.this)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle connectionHint) {
                            Wearable.MessageApi.addListener(mGoogleApiClient, Engine.this);
                            requestUpdateFromHandheld();
                        }

                        @Override
                        public void onConnectionSuspended(int cause) {
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                        }
                    })
                    // Request access only to the Wearable API
                    .addApi(Wearable.API)
                    .build();
            updateWatchBatteryPercentage();



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
//com.amber.wear.watchface
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
//com.amber.wear.watchface
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
            mTextHourPaint.setColor(Color.BLACK);
            mTextHourPaint.setAntiAlias(true);
            mTextHourPaint.setTextAlign(Paint.Align.LEFT);
            mTextHourPaint.setTextSize(dp2px(7));
            mTextHourPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "kenyan coffee rg.ttf"));

            mTextMinutePaint = new Paint();
            mTextMinutePaint.setColor(Color.BLACK);
            mTextMinutePaint.setAntiAlias(true);
            mTextMinutePaint.setTextAlign(Paint.Align.LEFT);
            mTextMinutePaint.setTextSize(dp2px(8));
            mTextMinutePaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "kenyan coffee rg.ttf"));

            mTextAmPmPaint = new Paint();
            mTextAmPmPaint.setColor(mTextAmPmColor);
            mTextAmPmPaint.setAntiAlias(true);
            mTextAmPmPaint.setTextAlign(Paint.Align.LEFT);
            mTextAmPmPaint.setTextSize(dp2px(10));
            mTextAmPmPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "kenyan coffee rg.ttf"));

            mTextWeekPaint = new Paint();
            mTextWeekPaint.setColor(mWatchHandShadowColor);
            mTextWeekPaint.setAntiAlias(true);
            mTextWeekPaint.setTextAlign(Paint.Align.LEFT);
            mTextWeekPaint.setTextSize(dp2px(8));
            mTextWeekPaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "kenyan coffee rg.ttf"));

            mTextDatePaint = new Paint();
            mTextDatePaint.setColor(mWatchHandShadowColor);
            mTextDatePaint.setAntiAlias(true);
            mTextDatePaint.setTextAlign(Paint.Align.LEFT);
            mTextDatePaint.setTextSize(dp2px(11));
            mTextDatePaint.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "kenyan coffee rg.ttf"));


            rect = new Rect();
            Resources resources = getResources();
            mHourBitmap = BitmapFactory.decodeResource(resources, R.drawable.hour_hand);
            mCenterBitmap=BitmapFactory.decodeResource(resources,R.drawable.clock_center);
//            mHourBitmap = Bitmap.createScaledBitmap(mHourBitmap, 50, 135, true);
            mMinuteBitmap = BitmapFactory.decodeResource(resources, R.drawable.minute_hand);
//            mMinuteBitmap = Bitmap.createScaledBitmap(mMinuteBitmap, 28, 136, true);
            mSecHandBitmap=BitmapFactory.decodeResource(resources,R.drawable.sec_hand);

            mDateTextView=BitmapFactory.decodeResource(resources,R.drawable.date_text);
            mHeart=BitmapFactory.decodeResource(resources,R.drawable.bottom_heart);
            mLeftIphone=BitmapFactory.decodeResource(resources,R.drawable.left_iphone);
            mRightWear=BitmapFactory.decodeResource(resources,R.drawable.right_wear);
            mPointerIphone =BitmapFactory.decodeResource(resources,R.drawable.function_pointer);
            mPointerWatch=BitmapFactory.decodeResource(resources,R.drawable.function_pointer);
            mPointerHeart=BitmapFactory.decodeResource(resources,R.drawable.function_pointer);

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
            mMonthFormat = new SimpleDateFormat("MM",  Locale.getDefault());
            mDayFormat = new SimpleDateFormat("dd",  Locale.getDefault());
            mAmPmFormat = new SimpleDateFormat("a",  Locale.getDefault());
            mWeekFormat = new SimpleDateFormat("E",  Locale.getDefault());
            mDateFormat = new SimpleDateFormat("MM dd",  Locale.getDefault());

        }

        private float dp2px(float dp) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }
        //性能改变（低电量，发热程度预警）；
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        /**
         * 非交互式状态下，更新表盘；
         */
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.e(TAG, "onTimeTick: tick one time" );

            invalidate();
        }

        //交互模式发生改变的时候；
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.e(TAG, "onAmbientModeChanged: +change mode between ambientMode and internal--"+inAmbientMode);
            mAmbient = inAmbientMode;
            updateWatchHandStyle();

            mAmbient = inAmbientMode;
            if (mAmbient) {
                /*if (roundAnimator != null && roundAnimator.isRunning()) {
                    roundAnimator.cancel();
                }*/
            }else{
                updateWatchBatteryPercentage();
                requestUpdateFromHandheld();
            }
            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        /**
         * 根据交互模式设置界面风格；
         */
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

            mHourBitmap=Bitmap.createScaledBitmap(mHourBitmap,
                    (int) (mHourBitmap.getWidth() * scale),
                    (int) (mHourBitmap.getHeight() * scale), true);
            mMinuteBitmap=Bitmap.createScaledBitmap(mMinuteBitmap,
                    (int) (mMinuteBitmap.getWidth() * scale),
                    (int) (mMinuteBitmap.getHeight() * scale), true);
            mSecHandBitmap=Bitmap.createScaledBitmap(mSecHandBitmap,
                    (int) (mSecHandBitmap.getWidth() * scale),
                    (int) (mSecHandBitmap.getHeight() * scale), true);
            mCenterBitmap=Bitmap.createScaledBitmap(mCenterBitmap,
                    (int)(mCenterBitmap.getWidth()*scale),
                    (int)(mCenterBitmap.getHeight()*scale),true);

            mDateTextView=Bitmap.createScaledBitmap(mDateTextView,
                    (int)(mDateTextView.getWidth()*scale),
                    (int)(mDateTextView.getHeight()*scale),true);

            mLeftIphone=Bitmap.createScaledBitmap(mLeftIphone,
                    (int)(mLeftIphone.getWidth()*scale),
                    (int)(mLeftIphone.getHeight()*scale),true);
            mRightWear=Bitmap.createScaledBitmap(mRightWear,
                    (int)(mRightWear.getWidth()*scale),
                    (int)(mRightWear.getHeight()*scale),true);
            mHeart=Bitmap.createScaledBitmap(mHeart,
                    (int)(mHeart.getWidth()*scale),
                    (int)(mHeart.getHeight()*scale),true);
            mPointerIphone =Bitmap.createScaledBitmap(mPointerIphone,
                    (int)(mPointerIphone.getWidth()*scale),
                    (int)(mPointerIphone.getHeight()*scale),true);
            mPointerWatch=Bitmap.createScaledBitmap(mPointerWatch,
                    (int)(mPointerWatch.getWidth()*scale),
                    (int)(mPointerWatch.getHeight()*scale),true);
            mPointerHeart=Bitmap.createScaledBitmap(mPointerHeart,
                    (int)(mPointerHeart.getWidth()*scale),
                    (int)(mPointerHeart.getHeight()*scale),true);
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
//                initGrayBackgroundCenterBitmap();
            }
        }

        private void rotateAngelBatIp(Canvas canvas,Bitmap bitmap,float angle){
            canvas.save();
            canvas.rotate(angle, mCenterX-mLeftIphone.getWidth()/2-21f, mCenterY);
            canvas.drawBitmap(mPointerIphone,mCenterX-mLeftIphone.getWidth()/2- mPointerIphone.getWidth()/2-21f, mCenterY- mPointerIphone.getHeight()/2, null);
            canvas.restore();
        }
        private void rotateAngelBatWat(Canvas canvas,Bitmap bitmap,float angle){
            canvas.save();
            canvas.rotate(angle, mCenterX+mLeftIphone.getWidth()/2+21f, mCenterY);
            canvas.drawBitmap(mPointerWatch,mCenterX+mLeftIphone.getWidth()/2-mPointerIphone.getWidth()/2+21f, mCenterY- mPointerWatch.getHeight()/2, null);
            canvas.restore();
        }

        private void rotateAngelHeart(Canvas canvas,Bitmap bitmap,float angle){
            canvas.save();
            canvas.rotate(angle, mCenterX, mCenterY+mLeftIphone.getHeight()/2+21f);
            canvas.drawBitmap(mPointerHeart,mCenterX-mPointerHeart.getWidth()/2, mCenterY+mLeftIphone.getHeight()/2-mPointerHeart.getHeight()/2+21f, null);
            canvas.restore();
        }

        //灰色背景
        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
//            grayPaint.setAlpha(0);
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);

//            initGrayBackgroundBitmapAlpha();
        }

        /*private void initGrayBackgroundBitmapAlpha() {
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            grayPaint.setAlpha(100);
            canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, grayPaint);
        }*/

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
            canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            Log.e(TAG, "onDraw: "+"runing..." );
            Log.e(TAG, "onDraw--: "+mTemperature+"°" );
            Log.e(TAG, "onDraw--: "+mPhoneBatteryPercentage);
//            Log.e(TAG, "onDraw: "+Integer.valueOf(mPhoneBatteryPercentage.substring(0,mPhoneBatteryPercentage.length()-2)));
            if (mPhoneBatteryPercentage.length()!=0){
                Log.e(TAG, "onDraw: "+Integer.valueOf(mPhoneBatteryPercentage.substring(0,mPhoneBatteryPercentage.length()))*3.6);
            }
//            Log.e(TAG, "onDraw: "+Integer.valueOf(mPhoneBatteryPercentage.substring(0,mPhoneBatteryPercentage.length()-2))*100*3.6);
            Log.e(TAG, "onDraw--: "+mWatchBatteryPercentage+"%" );
            //背景
            if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }

            float bcWidth = mBackgroundCenterBitmap.getWidth();
            float bcHeight = mBackgroundCenterBitmap.getHeight();

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

            if (!mAmbient){
//update1:
//                canvas.drawBitmap(mDateTextView,mCenterX-mDateTextView.getWidth()/2,70f,null);
                canvas.drawBitmap(mDateTextView,mCenterX-mDateTextView.getWidth()/2,mCenterY-mLeftIphone.getHeight(),null);
                // Week
                String weekStr = mWeekFormat.format(now);
                mTextWeekPaint.getTextBounds(weekStr,0,weekStr.length(),rect);
                float weekWidth =rect.width();
                float weekHeight =rect.height();
                mTextWeekPaint.setColor(Color.BLACK);
//update2:
//                canvas.drawText(weekStr.toUpperCase(), mCenterX - mDateTextView.getWidth()/2+10f, 70f+weekHeight*2+3f , mTextWeekPaint);
                canvas.drawText(weekStr.toUpperCase(), mCenterX - mDateTextView.getWidth()/2+10f, mCenterY-mLeftIphone.getHeight()+mDateTextView.getHeight()*3/4, mTextWeekPaint);

                // day
                String dayStr = mDayFormat.format(now)+"/";
                mTextMinutePaint.getTextBounds(dayStr,0,dayStr.length(),rect);
                float dayWidth =rect.width();
                float dayHeight =rect.height();
                mTextMinutePaint.setColor(Color.BLACK);
                canvas.drawText(dayStr, mCenterX +3f, mCenterY-mLeftIphone.getHeight()+mDateTextView.getHeight()*3/4, mTextMinutePaint);


                // month;
                String monthStr = mMonthFormat.format(now);
                mTextHourPaint.getTextBounds(monthStr,0,monthStr.length(),rect);
                float monthWidth =rect.width();
                float monthHeight =rect.height();
                mTextHourPaint.setColor(Color.BLACK);
                canvas.drawText(monthStr, mCenterX+3f+dayWidth , mCenterY-mLeftIphone.getHeight()+mDateTextView.getHeight()*3/4, mTextHourPaint);

                canvas.drawBitmap(mLeftIphone,mCenterX-mLeftIphone.getWidth()-21f,mCenterY-mLeftIphone.getHeight()/2,null);
//            canvas.drawBitmap(mPointerIphone,mCenterX-mLeftIphone.getWidth()/2-mPointerIphone.getWidth()/2-24f, mCenterY-mPointerIphone.getHeight()/2, null);

                canvas.drawBitmap(mRightWear,mCenterX+21f,mCenterY-mRightWear.getHeight()/2,null);
                canvas.drawBitmap(mHeart,mCenterX-mHeart.getWidth()/2,mCenterY+21f,null);

                if (mPhoneBatteryPercentage.length()!=0){
                    Log.e(TAG, "onDraw: "+Integer.valueOf(mPhoneBatteryPercentage.substring(0,mPhoneBatteryPercentage.length()))*3.6);
                    rotateAngelBatIp(canvas, mPointerIphone,Integer.valueOf(mPhoneBatteryPercentage.substring(0,mPhoneBatteryPercentage.length()))*3.6f);
                }

//            Integer.valueOf(mWatchBatteryPercentage);
                rotateAngelBatWat(canvas,mPointerWatch,Integer.valueOf(mWatchBatteryPercentage)*3.6f);
                int max=310;
                int min=280;
                Random random = new Random();
                int s = random.nextInt(max)%(max-min+1) + min;
                Log.e(TAG, "onDraw: "+s );
                rotateAngelHeart(canvas,mPointerHeart,s);

            }

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawBitmap(mHourBitmap,mCenterX-mHourBitmap.getWidth()/2, mCenterY-mHourBitmap.getHeight()/2, null);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawBitmap(mMinuteBitmap,mCenterX-mMinuteBitmap.getWidth()/2, mCenterY-mMinuteBitmap.getHeight()/2, null);

            canvas.restore();

//            rotateAngelBatWat(canvas,mPointerWatch,secondsRotation - minutesRotation);
            /*canvas.save();
            canvas.rotate(secondsRotation - minutesRotation, mCenterX-mLeftIphone.getWidth()/2-24f, mCenterY);
            canvas.drawBitmap(mPointerIphone,mCenterX-mLeftIphone.getWidth()/2-mPointerIphone.getWidth()/2-24f, mCenterY-mPointerIphone.getHeight()/2, null);
            canvas.restore();*/
//            rotateAngel(canvas,mPointerIphone,secondsRotation - minutesRotation);
            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            canvas.save();
            if (!mAmbient) {
                Log.e(TAG, "onDraw: "+"second --- isDrawing" );
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawBitmap(mSecHandBitmap,mCenterX-mSecHandBitmap.getWidth()/2,mCenterY-mSecHandBitmap.getHeight()/2,null);

            }

            /* Restore the canvas' original orientation. */
            canvas.restore();
            canvas.save();
            canvas.drawBitmap(mCenterBitmap,mCenterX,mCenterY,null);
            canvas.restore();




            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                //可见的，则连接；
                mGoogleApiClient.connect();
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                //不可见，则断开；
                if (mGoogleApiClient.isConnected()) {
                    Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
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
            SeraphimWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SeraphimWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         * 系统提供的第二个定时器；
         */
        private void updateTimer() {

            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                Log.i("htht", "我被调用了------发送一直重绘的请求 ");
                Log.e(TAG, "updateTimer: "+"多久执行一次" );
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
        //电池广播；sww
        private void updateWatchBatteryPercentage() {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent intent = SeraphimWatchFace.this.registerReceiver(null, ifilter);
            mWatchBatteryPercentage = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) );
        }

        private void requestUpdateFromHandheld() {
            if (System.currentTimeMillis() - HANDHELD_UPDATE_INTERVAL_MS < mLastUpdateRequestTime)
                return;
            if (!mGoogleApiClient.isConnected())
                return;

            PendingResult<CapabilityApi.GetCapabilityResult> result =
                    Wearable.CapabilityApi.getCapability(
                            mGoogleApiClient, "seraphim_handheld",
                            CapabilityApi.FILTER_REACHABLE);

            result.setResultCallback(new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                @Override
                public void onResult(final CapabilityApi.GetCapabilityResult result) {
                    if (result.getStatus().isSuccess()) {
                        String handheldNodeId = null;
                        for (Node node : result.getCapability().getNodes()) {
                            handheldNodeId = node.getId();
                            if (node.isNearby())
                                break;
                        }
                        if (handheldNodeId != null && mGoogleApiClient.isConnected()) {
                            Wearable.MessageApi.sendMessage(mGoogleApiClient, handheldNodeId, "/seraphim-update-request", null);
                            mLastUpdateRequestTime = System.currentTimeMillis();
                        }
                    }
                }
            });
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            if (messageEvent.getPath().equals("/seraphim-update-phonebattery")) {
                mPhoneBatteryPercentage = new String(messageEvent.getData());
            }
            if (messageEvent.getPath().equals("/seraphim-update-temperature")) {
                mTemperature = new String(messageEvent.getData());
            }
        }


    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SeraphimWatchFace.Engine> mWeakReference;

        public EngineHandler(SeraphimWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SeraphimWatchFace.Engine engine = mWeakReference.get();
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
