package com.example.nataliaaulia.tamponsnavigator;

import android.Manifest;

import android.content.DialogInterface;

import android.content.Intent;

import android.content.pm.PackageManager;

import android.graphics.Camera;
import android.location.Location;

import android.media.Image;
import android.os.Build;

import android.os.Looper;

import android.support.annotation.NonNull;

import android.support.v4.app.ActivityCompat;

import android.support.v4.app.FragmentActivity;

import android.os.Bundle;

import android.support.v4.content.ContextCompat;

import android.view.View;

import android.widget.Button;

import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;

import com.firebase.geofire.GeoLocation;

import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.location.FusedLocationProviderClient;

import com.google.android.gms.location.LocationCallback;

import com.google.android.gms.location.LocationRequest;

import com.google.android.gms.location.LocationResult;

import com.google.android.gms.location.LocationServices;

import com.google.android.gms.maps.CameraUpdateFactory;

import com.google.android.gms.maps.GoogleMap;

import com.google.android.gms.maps.OnMapReadyCallback;

import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;

import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import com.google.firebase.database.DatabaseError;

import com.google.firebase.database.DatabaseReference;

import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;

import java.util.Map;

public class ProviderMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener, RoutingListener {

    private GoogleMap mMap;
    //GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private FusedLocationProviderClient mFusedLocationClient;

    private Button mLogout, mSettings, mTransactionStat, mHistory;

    private Switch mWorkingSwitch;

    private int status = 0;

    private String customerId = "", destination;
    private LatLng provLatLng, cusLatLng;
    private float rideDistance;

    private boolean isLoggingOut = false;

    private SupportMapFragment mapFragment;

    private LinearLayout mCustomerInfo;

    private ImageView mCustomerProfileImage;

    private TextView mCustomerName, mCustomerPhone;

    private ZoomControls mZoomControls;

    @Override

    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_provider_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        polylines = new ArrayList<>();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFragment = (SupportMapFragment) getSupportFragmentManager()

                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

        mCustomerInfo = (LinearLayout) findViewById(R.id.customerInfo);

        mCustomerProfileImage = (ImageView) findViewById(R.id.customerProfileImage);

        mCustomerName = (TextView) findViewById(R.id.customerName);

        mCustomerPhone = (TextView) findViewById(R.id.customerPhone);

        mSettings = (Button) findViewById(R.id.settings);

        mTransactionStat = (Button) findViewById(R.id.transactionStat);

        mTransactionStat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (status) {
                    //WHEN THE CUSTOMER IS ON THEIR WAY TO PICKUP
                    case 1:
                        //ERASE ROUTES
                        status = 2;
                        erasePolylines();

                        //default is 0.0
//                        if (cusLatLng.latitude != 0.0 && cusLatLng.longitude != 0.0) {
//                            //getRouteToMarker(destinationLatLng);
//                        }

                        mTransactionStat.setText("Tampon has already been delivered");
                        break;

                    //WHEN THE PROVIDER IS DONE WITH THE CUSTOMER
                    case 2:
                        recordTransaction();
                        endTransaction();
                        break;
                }
            }
        });

        mHistory = (Button) findViewById(R.id.history);

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProviderMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrProvider", "Providers");
                startActivity(intent);
                return;
            }
        });

        mLogout = (Button) findViewById(R.id.logout);

        mLogout.setOnClickListener(new View.OnClickListener() {

            @Override

            public void onClick(View view) {
                isLoggingOut = true;
                disconnectProvider();

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(ProviderMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }

        });

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(ProviderMapActivity.this, ProviderSettingsActivity.class);
                startActivity(intent);
                finish();
                return;

            }
        });

        mZoomControls = findViewById(R.id.zoomControls);

        mZoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });

        mZoomControls.setOnZoomInClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        mWorkingSwitch = (Switch) findViewById(R.id.workingSwitch);

        // Nothing should work if the button working is not switched on
        mWorkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    connectProvider();
                } else {
                    disconnectProvider();
                }
            }
        });

        getAssignedCustomer();

    }


    private void getAssignedCustomer() {

        String providerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providerId).child("customerRequest").child("customerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {

            @Override

            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists()) {

                    status = 1;

                    customerId = dataSnapshot.getValue().toString();

                    getAssignedCustomerLocation();

                    getAssignedCustomerInfo();

                } else {
                    endTransaction();
                }

            }

            @Override

            public void onCancelled(DatabaseError databaseError) {

            }

        });

    }

    Marker mCustomerMarker;
    private DatabaseReference assignedCustomerLocationRef;
    private ValueEventListener assignedCustomerLocationRefListener;

    private void getAssignedCustomerLocation() {
        assignedCustomerLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");
        assignedCustomerLocationRefListener = assignedCustomerLocationRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    mTransactionStat.setText("Customer Found!");

                    if (map.get(0) != null) {
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }

                    if (map.get(1) != null) {
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    LatLng providerLatLng = new LatLng(locationLat, locationLng);
                    if (mCustomerMarker != null) {
                        mCustomerMarker.remove();
                    }


                    // customer's location
                    Location cusCurrLoc = new Location("");
                    cusCurrLoc.setLatitude(cusLatLng.latitude);
                    cusCurrLoc.setLongitude(cusLatLng.longitude);

                    // provider's location
                    Location provLoc = new Location("");
                    provLoc.setLatitude(providerLatLng.latitude);
                    provLoc.setLongitude(providerLatLng.longitude);

                    float distance = cusCurrLoc.distanceTo(provLoc);

                    if (distance < 100) {
                        mTransactionStat.setText("Customer's here!");
                    } else {
                        mTransactionStat.setText("Customer found: " + String.valueOf(distance + " away"));
                    }

                    mCustomerMarker = mMap.addMarker(new MarkerOptions().position(providerLatLng).title("Your Customer").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_default_user)));


                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    //GETTING THE CUSTOMER'S INFORMATION
    private void getAssignedCustomerInfo() {
        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    {
                        if (map.get("name") != null) {
                            mCustomerName.setText(map.get("name").toString());
                        }

                        if (map.get("phone") != null) {
                            mCustomerPhone.setText(map.get("phone").toString());
                        }

                        if (map.get("profileImageURL") != null) {
                            Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mCustomerProfileImage);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    //If the customer has received the tampon, update the App to default
    private void endTransaction() {
        mTransactionStat.setText("Tampons Delivered!");
        erasePolylines();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference providerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(userId).child("cus");
        providerRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
        customerId = "";
        rideDistance = 0;

        if (mCustomerMarker != null) {
            mCustomerMarker.remove();
        }

        if (assignedCustomerLocationRefListener != null) {
            assignedCustomerLocationRef.removeEventListener(assignedCustomerLocationRefListener);
        }

        mCustomerInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mCustomerPhone.setText("");
        mCustomerProfileImage.setImageResource(R.mipmap.ic_default_user);
    }

    //Enter the cutomer's info to the Firebase history
    //Only the provider have access to the customer's history
    private void recordTransaction() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference providerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(userId).child("history");
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("history");
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("history");

        //Get info about every ride in history
        String requestId = historyRef.push().getKey();
        providerRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        //Populate the references
        HashMap map = new HashMap();
        map.put("provider", userId);
        map.put("customer", customerId);
        map.put("rating", 0);
        map.put("timestamp", getCurrentTimeStamp());
        map.put("location/from/lat", cusLatLng.latitude);
        map.put("location/from/lng", cusLatLng.longitude);
        map.put("location/to/lat", provLatLng.latitude);
        map.put("location/to/lng", provLatLng.longitude);
        map.put("distance", rideDistance);
        historyRef.child(requestId).updateChildren(map);
    }

    // helper method
    private Long getCurrentTimeStamp() {
        Long timestamp = System.currentTimeMillis() / 1000;
        return timestamp;
    }

    @Override

    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        UiSettings mapUiSettings = mMap.getUiSettings();

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Marker is in Seattle
        LatLng seattle = new LatLng(48, -122);
        mMap.addMarker(new MarkerOptions().position(seattle).title("Marker is Seattle"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(seattle));

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //if user grants permission
                mMap.setMyLocationEnabled(true);
                mMap.setOnMyLocationButtonClickListener(this);
                mMap.setOnMyLocationClickListener(this);

                mapUiSettings.setCompassEnabled(true);
                mapUiSettings.setMapToolbarEnabled(true);
                mapUiSettings.setZoomGesturesEnabled(true);
                mapUiSettings.setScrollGesturesEnabled(true);
                mapUiSettings.setTiltGesturesEnabled(true);
                mapUiSettings.setRotateGesturesEnabled(true);
            } else {
                checkLocationPermission();
            }
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }


    LocationCallback mLocationCallback = new LocationCallback(){

        @Override

        public void onLocationResult(LocationResult locationResult) {

            for(Location location : locationResult.getLocations()){

                if(getApplicationContext()!=null){

                    if(!customerId.equals("") && mLastLocation!=null && location != null){
                        rideDistance += mLastLocation.distanceTo(location)/1000; // change the distance from meter to kilometer
                    }

                    mLastLocation = location;

                    LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());

                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

                    mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("providersAvailable");

                    DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("providersWorking");

                    GeoFire geoFireAvailable = new GeoFire(refAvailable);

                    GeoFire geoFireWorking = new GeoFire(refWorking);

                    if (userId != null) {

                        switch (customerId) {

                            case "":

                                geoFireWorking.removeLocation(userId);

                                geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));

                                break;

                            default:

                                geoFireAvailable.removeLocation(userId);

                                geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));

                                break;

                        }
                    }

                }

            }

        }

    };

    private void checkLocationPermission() {

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {

                new android.app.AlertDialog.Builder(this)

                        .setTitle("Give Permission")

                        .setMessage("Give Permission Message")

                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                            @Override

                            public void onClick(DialogInterface dialogInterface, int i) {

                                ActivityCompat.requestPermissions(ProviderMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

                            }

                        })

                        .create()

                        .show();

            } else {

                ActivityCompat.requestPermissions(ProviderMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

            }

        }

    }

    // ALLOWING THE APP TO ACCESS LOCATION
    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {

            case LOCATION_REQUEST_CODE: {

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission.", Toast.LENGTH_LONG).show();
                    finish();
                }

                break;
            }
        }
    }

    private void connectProvider() {
        checkLocationPermission();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    private void disconnectProvider() {
        if ( mFusedLocationClient != null ) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("providersAvailable");
        GeoFire geoFire = new GeoFire(ref);

        if (userId != null) {
            geoFire.removeLocation(userId);
        }
    }

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i < route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {

    }

    //CLEAR ROUTE FROM MAP
    private void erasePolylines() {
        for (Polyline line : polylines) {
            line.remove();
        }

        //ERASE THE ARRAY
        polylines.clear();
    }

    @Override
    public boolean onMyLocationButtonClick() {
        //Toast.makeText(this, "My Location button clicked", Toast.LENGTH_SHORT).show();

        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }
}