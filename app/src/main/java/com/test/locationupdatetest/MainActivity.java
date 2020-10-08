package com.test.locationupdatetest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "resPMain";

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    private MyReceiver mMyReceiver;

    private LocationUpdatesService mService = null;

    private boolean mBound = false;

    private Button mRequestUpdateButton;
    private Button mRemoveUpdateButton;



    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            LocationUpdatesService.LocalBinder binder = (LocationUpdatesService.LocalBinder) iBinder;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBound = false;
        }
    };

    GoogleMap mMap;
    List<LatLng> mLatLngs = Arrays.asList(new LatLng(51.40695986541333, -1.0237490638334188),
            new LatLng(51.4249404388344, -1.023366063708723),
            new LatLng(51.424697345804795, -0.9946053622003813),
            new LatLng(51.40671692782798, -0.9949996397241306));
    LatLngBounds mainMapFocus;
        Projection proj;
    ArrayList<Point> routePoints = new ArrayList<>();
    List<LatLng> mSplitPoints = new ArrayList<>();
    Button snap;
    EditText etLat, etLng;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMyReceiver = new MyReceiver();
        setContentView(R.layout.activity_main);

        if (Utils.requestingLocationUpdates(this)) {
            if (!checkPermissions()) {
                requestPermissions();
            }
        }


        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);



        snap = findViewById(R.id.btnSnap);
        etLat = findViewById(R.id.etLat);
        etLng = findViewById(R.id.etLng);

        snap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String lat = etLat.getText().toString();
                String lng = etLng.getText().toString();
                LatLng latLng = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));

                Point pointClicked = proj.toScreenLocation(latLng);
                Log.e("Latlng", "Lat:"+latLng.latitude+", Lng:"+latLng.longitude);
                Log.e("POintClicked", "X:"+pointClicked.x+", Y:"+pointClicked.y);
                Point snappedPoint = getClosestPointOnLine(pointClicked, routePoints);

                LatLng snappedLatLng = proj.fromScreenLocation(snappedPoint);

//                LatLng snappedLatLng = snapToPolyline(latLng);
                Log.e("SnappedLatLng", snappedLatLng.latitude + "," + snappedLatLng.longitude);
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(snappedLatLng);

                mMap.addMarker(markerOptions);
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        mRequestUpdateButton = (Button) findViewById(R.id.request_location_updates_button);
        mRemoveUpdateButton = (Button) findViewById(R.id.remove_location_updates_button);

        mRequestUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checkPermissions()) {
                    requestPermissions();
                } else {
                    mService.requestLocationUpdates();
//                    setupGeoFence();
                }
            }
        });

        mRemoveUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.removeLocationUpdates();
            }
        });

        setButtonsState(Utils.requestingLocationUpdates(this));


        bindService(new Intent(this, LocationUpdatesService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

//        registerReceiver(mGeofenceBroadcastReceiver,new IntentFilter());
        LocalBroadcastManager.getInstance(this).registerReceiver(mMyReceiver,
                new IntentFilter(LocationUpdatesService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMyReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        /*if(mBound){
            unbindService(mServiceConnection);
            mBound = false;
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
*/

        super.onStop();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapClickListener(this);
        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng l : mLatLngs) {
                    builder.include(l);
                }
                mainMapFocus = builder.build();
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mainMapFocus, 300));
                mMap.addPolyline(new PolylineOptions()
                        .addAll(mLatLngs));
                /*for (int i = 0; i < mLatLngs.size(); i++) {
                    LatLng src = mLatLngs.get(i);

                    if (mLatLngs.size() > i + 1) {
                        LatLng dest = mLatLngs.get(i + 1);
                        List<LatLng> splitPoints = splitPathIntoPoints(src, dest);
                        mSplitPoints.addAll(splitPoints);
                    } else {
                        break;
                    }
                }*/
//                Log.e("SplitPoints", mSplitPoints.size() + "");
                proj = mMap.getProjection();
                for(int i=0;i<mLatLngs.size();i++){
                    routePoints.add(proj.toScreenLocation(mLatLngs.get(i)));
                }

            }
        });

    }

    @Override
    public void onMapClick(LatLng latLng) {

        Point pointClicked = proj.toScreenLocation(latLng);
        Log.e("Latlng", "Lat:"+latLng.latitude+", Lng:"+latLng.longitude);
        Log.e("POintClicked", "X:"+pointClicked.x+", Y:"+pointClicked.y);
        Point snappedPoint = getClosestPointOnLine(pointClicked, routePoints);

        LatLng snappedLatLng = proj.fromScreenLocation(snappedPoint);
//        LatLng snappedLatLng = snapToPolyline(latLng);
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(snappedLatLng);

        mMap.addMarker(markerOptions);
    }

    /*public static List<LatLng> splitPathIntoPoints(LatLng source, LatLng destination) {
        Float distance = findDistance(source, destination);

        List<LatLng> splitPoints = new ArrayList<>();
        splitPoints.add(source);
        splitPoints.add(destination);

        while (distance > 0.1) {
            int polypathSize = splitPoints.size();
            List<LatLng> tempPoints = new ArrayList<>();
            tempPoints.addAll(splitPoints);

            int injectionIndex = 1;

            for (int i = 0; i < (polypathSize - 1); i++) {
                LatLng a1 = tempPoints.get(i);
                LatLng a2 = tempPoints.get(i + 1);

                splitPoints.add(injectionIndex, findMidPoint(a1, a2));
                injectionIndex += 2;
            }

            distance = findDistance(splitPoints.get(0), splitPoints.get(1));
        }

        return splitPoints;
    }*/

    /*public static Float findDistance(LatLng source, LatLng destination) {
        Location srcLoc = new Location("srcLoc");
        srcLoc.setLatitude(source.latitude);
        srcLoc.setLongitude(source.longitude);

        Location destLoc = new Location("destLoc");
        destLoc.setLatitude(destination.latitude);
        destLoc.setLongitude(destination.longitude);

        return srcLoc.distanceTo(destLoc);
    }

    public static LatLng findMidPoint(LatLng source, LatLng destination) {
        double x1 = Math.toRadians(source.latitude);
        double y1 = Math.toRadians(source.longitude);

        double x2 = Math.toRadians(destination.latitude);
        double y2 = Math.toRadians(destination.longitude);

        double Bx = Math.cos(x2) * Math.cos(y2 - y1);
        double By = Math.cos(x2) * Math.sin(y2 - y1);
        double x3 = Math.toDegrees(Math.atan2(Math.sin(x1) + Math.sin(x2), Math.sqrt((Math.cos(x1) + Bx) * (Math.cos(x1) + Bx) + By * By)));
        double y3 = y1 + Math.atan2(By, Math.cos(x1) + Bx);
        y3 = Math.toDegrees((y3 + 540) % 360 - 180);

        return new LatLng(x3, y3);
    }*/

    /*public LatLng snapToPolyline(LatLng currentLocation) {
        LatLng snappedLatLng = null;

        Location current = new Location("current");
        current.setLatitude(currentLocation.latitude);
        current.setLongitude(currentLocation.longitude);

        List<Float> distances = new ArrayList<>();

        for (LatLng point : mSplitPoints) {
            Location pointLoc = new Location("pointLoc");
            pointLoc.setLatitude(point.latitude);
            pointLoc.setLongitude(point.longitude);

            distances.add(current.distanceTo(pointLoc));
        }
        float minDist = Collections.min(distances);
        int index = distances.indexOf(minDist);
        snappedLatLng = mSplitPoints.get(index);

        return snappedLatLng;
    }*/

    private Point getClosestPointOnLine(Point point, ArrayList<Point> route){
        double minDist = 0;
        double dist, fTo=0, fFrom;
        int x=0,y=0;
        int n =0;

        if(route.size()>1){
            for(int i=1; i<route.size();i++){
                if(route.get(i).x != route.get(i-1).x){
                    int a = (route.get(i).y - route.get(i-1).y)/(route.get(i).x - route.get(i-1).x);
                    int b = route.get(i).y - a * route.get(i).x;
                    dist = Math.abs(a*point.x+b-point.y)/Math.sqrt(a*a+1);
                }
                else
                    dist = Math.abs(point.x - route.get(i).x);

                double rl2 = Math.pow(route.get(i).y - route.get(i-1).y,2)
                        + Math.pow(route.get(i).x - route.get(i-1).x,2);

                double ln2 = Math.pow(route.get(i).y - point.y,2)
                        + Math.pow(route.get(i).x - point.x,2);

                double lnm12 = Math.pow(route.get(i-1).y - point.y,2)
                        + Math.pow(route.get(i-1).x-point.x,2);

                double dist2 = Math.pow(dist, 2);

                double calcrl2 = ln2 - dist2 + lnm12 - dist2;

                if(calcrl2 > rl2)
                    dist = Math.sqrt(Math.min(ln2, lnm12));

                if((minDist==0) || (minDist>dist)){
                    if(calcrl2>rl2){
                        if(lnm12< ln2){
                            fTo = 0;
                            fFrom = 1;
                        }
                        else{
                            fFrom = 0;
                            fTo = 0;
                        }
                    }
                    else {
                        fTo = ((Math.sqrt(lnm12 - dist2))/Math.sqrt(rl2));
                        fFrom = ((Math.sqrt(ln2 - dist2))/Math.sqrt(rl2));
                    }
                    minDist = dist;
                    n=i;
                }
            }

            double dx = route.get(n-1).x - route.get(n).x;
            double dy = route.get(n-1).y - route.get(n).y;

            x = (int)(route.get(n-1).x-(dx*fTo));
            y = (int)(route.get(n-1).y-(dy*fTo));

        }
        Log.e("POint", "X:"+x+", Y:"+y);
        return new Point(x,y);
    }

    private boolean checkPermissions() {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request permission
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                mService.requestLocationUpdates();
//                setupGeoFence();
            } else {
                // Permission denied.
                setButtonsState(false);
                Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        getApplicationContext().getPackageName(), null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(Utils.KEY_REQUESTING_LOCATION_UPDATES)) {
            setButtonsState(sharedPreferences.getBoolean(Utils.KEY_REQUESTING_LOCATION_UPDATES,
                    false));
        }
    }

    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION);
            if (location != null) {
                Toast.makeText(context, Utils.getLocationText(location), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setButtonsState(boolean requestingLocationUpdates) {
        if (requestingLocationUpdates) {
            mRequestUpdateButton.setEnabled(false);
            mRemoveUpdateButton.setEnabled(true);
        } else {
            mRequestUpdateButton.setEnabled(true);
            mRemoveUpdateButton.setEnabled(false);
        }
    }



}