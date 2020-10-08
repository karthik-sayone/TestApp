package com.test.locationupdatetest;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public class LocationUpdatesService extends Service {

    private static final String PACKAGE_NAME = "com.test.locationupdatetest";
    private static final String TAG = "resPoint";
    private static final String CHANNEL_ID = "channel_01";
    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION =
            PACKAGE_NAME + ".started_from_notification";
    private final IBinder mIBinder = new LocalBinder();
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private static final long FASTEST_SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS =
            SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private static final int NOTIFICATION_ID = 12345678;
    private boolean mChangingConfiguration = false;
    private NotificationManager mNotificationManager;
    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private LocationCallback mLocationCallback;
    private Handler mServiceHandler;
    Double latitude, longitude;
    private Location mLocation;

    private GeofencingClient mGeofencingClient;
    BroadcastReceiver mGeofenceBroadcastReceiver;

    public LocationUpdatesService() {

    }
    private void setupGeoFence() {
        mGeofencingClient = LocationServices.getGeofencingClient(this);
        Geofence geofence = new Geofence.Builder()
                .setRequestId("02")
                .setCircularRegion(10.0334608, 76.3309960, 30)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setLoiteringDelay( 1000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .build();

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofence(geofence).build();

        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        PendingIntent geoFencePendingIntent = PendingIntent.getBroadcast(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            mGeofencingClient.addGeofences(geofencingRequest, geoFencePendingIntent)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.e("ADDFence", "Success");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            e.printStackTrace();
                            Log.e("ADDFence", "Failed");
                        }
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.e("ADDFence", "Failed");
        }
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
        setupGeoFence();

        mGeofenceBroadcastReceiver = new GeofenceBroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                if (intent.getAction().equals("broadCastName"))
                    if(intent.getBooleanExtra("message", false)) {
                        Log.e("Geofence2", "ChangeRequestSpeed");
                        changeLocationRequestTimeToFast();
                    }else{
                        changeLocationRequestTimeToSlow();
                    }
            }
        };
        registerReceiver(mGeofenceBroadcastReceiver, new IntentFilter("broadCastName"));
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            mNotificationManager.createNotificationChannel(mChannel);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false);

        if (startedFromNotification) {
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

        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            startForeground(NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        }
        return true;
    }

    @Override
    public void onDestroy() {
        List<String> geofences = new ArrayList<>();
        geofences.add("02");
        mGeofencingClient.removeGeofences(geofences);
        mServiceHandler.removeCallbacksAndMessages(null);
    }

    public void requestLocationUpdates() {
        Utils.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), LocationUpdatesService.class));
        try {
            mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException execution) {
            Utils.setRequestingLocationUpdates(this, false);
            execution.printStackTrace();
        }
    }

    public void removeLocationUpdates() {
        try {
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            Utils.setRequestingLocationUpdates(this, false);
            stopSelf();
        } catch (SecurityException exception) {
            Utils.setRequestingLocationUpdates(this, true);
            exception.printStackTrace();
        }
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, LocationUpdatesService.class);
        CharSequence text = Utils.getLocationText(mLocation);

        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }

        return builder.build();
    }

    private void getLastLocation() {
        try {
            mFusedLocationProviderClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                            }
                        }
                    });
        } catch (SecurityException exception) {
            exception.printStackTrace();
        }
    }

    private void onNewLocation(Location location) {

        mLocation = location;

        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        Log.e("NewLocation", "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(NOTIFICATION_ID, getNotification());

            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }

    }

    private void createLocationRequest() {
        Log.e("CreateLocRequest", "Start");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void changeLocationRequestTimeToFast(){
        Log.e("ChangeLocRequest", "Start");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        requestLocationUpdates();
    }

    public void changeLocationRequestTimeToSlow(){
        Log.e("ChangeLocRequest", "Start");
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_SLOWER_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        requestLocationUpdates();
    }

    public class LocalBinder extends Binder {
        LocationUpdatesService getService() {
            return LocationUpdatesService.this;
        }
    }

    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }
}
