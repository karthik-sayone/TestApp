package com.test.locationupdatetest;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class GPSService extends Service {

    private static final String TAG = "GPSServiceHandler";
    private static final String PACKAGE_NAME = "com.test.locationupdatetest";
    private static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    private static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION =
            PACKAGE_NAME + ".started_from_notification";
    private static final int NOTIFICATION_ID = 12345678;
    private static final String CHANNEL_ID = "channel_01";
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private final IBinder mIBinder = new LocalBinder();
    private FusedLocationProviderClient mFusedLocationProviderClient;

    private Location mLocation;
    private NotificationManager mNotificationManager;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private Handler mServiceHandler;
    private boolean mChangingConfiguration = false;
    Double latitude, longitude;


    public GPSService() {

    }

    @Override
    public void onCreate() {
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        createLocationRequest();
        getLastLocation();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence name = "LocationUpdateService";
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean startFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false);

        if(startFromNotification){
            removeLocationUpdates();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration = false;
        return mIBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(!mChangingConfiguration && Utils.requestingLocationUpdates(this)){
            startForeground(NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        }
        return true;
    }

    @Override
    public void onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null);
    }

    private void getLastLocation(){
        try{
            mFusedLocationProviderClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if(task.isSuccessful() && task.getResult()!= null){
                                mLocation = task.getResult();
                            }
                        }
                    });
        }catch (SecurityException e){
            e.printStackTrace();
        }
    }

    private void onNewLocation(Location location) {

        mLocation = location;

        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        if (serviceIsRunningInForeground(this)){
            mNotificationManager.notify(NOTIFICATION_ID, getNotification());

            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }
    }

    private Notification getNotification(){
        Intent intent = new Intent(this, GPSService.class);
        CharSequence text = Utils.getLocationText(mLocation);

        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch, "Launch activity",
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel, "Remove location updates",
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            builder.setChannelId(CHANNEL_ID);
        }
        return builder.build();
    }

    private void createLocationRequest(){
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (service.foreground) {
                return true;
            }
        }
        return false;
    }

    public void requestLocationUpdates(){
        Utils.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), GPSService.class));
        try{
            mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        }catch (SecurityException e){
            Utils.setRequestingLocationUpdates(this, false);
            e.printStackTrace();
        }
    }

    public void removeLocationUpdates(){
        try{
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            Utils.setRequestingLocationUpdates(this, false);
            stopSelf();
        }catch (SecurityException e){
            Utils.setRequestingLocationUpdates(this, true);
            e.printStackTrace();
        }
    }

    public class LocalBinder extends Binder{
        GPSService getService(){
            return GPSService.this;
        }
    }
}
