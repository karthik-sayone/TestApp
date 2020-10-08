package com.test.locationupdatetest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if(geofencingEvent.hasError()){
            Log.e("GEOFENCE1", "ERROR");
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        Log.e("GEOFENCE1", geofenceTransition+"");
        if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL){
            Log.e("GEOFENCE1", "DWELL");
            Toast.makeText(context, "Dwell", Toast.LENGTH_SHORT).show();
            Intent i = new Intent("broadCastName");
            i.putExtra("message", true);
            context.sendBroadcast(i);
        }else if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT){
            Log.e("GEOFENCE1", "EXIT");
            Toast.makeText(context, "Exit", Toast.LENGTH_SHORT).show();
            Intent i = new Intent("broadCastName");
            i.putExtra("message", false);
            context.sendBroadcast(i);
        }else{
            Log.e("GEOFENCE1", "ELSE");
        }
    }
}
