package com.example.nataliaaulia.tamponsnavigator;

import android.Manifest;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
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
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener, RoutingListener {

    private GoogleMap mMap;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private Button mLogout, mRequest, mSettings, mHistory;
    private Boolean requestBool = false;

    // marks the current position of the customer
    private Marker customerCurrLocMarker, provCurrLocMarker;
    private SupportMapFragment mapFragment;
    private String destination, requestService;
    private LatLng provLatLng, cusLatLng;
    private LinearLayout mProviderInfo;
    private ImageView mProviderProfileImage;
    private TextView mProviderName, mProviderPhone, mProviderLoc;
    private RadioGroup mRadioGroup;
    private RatingBar mRatingBar;
    private ZoomControls mZoomControls;

    private int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;
    private int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    // to save the device location and map's camera position
    private static final String KEY_CAMERA_POSITION = "camera position";
    private static final String KEY_LOCATION = "location";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        provLatLng = new LatLng(0.0, 0.0);

        mProviderInfo = (LinearLayout) findViewById(R.id.providerInfo);

        mProviderProfileImage = (ImageView) findViewById(R.id.providerProfileImage);

        mProviderName = (TextView) findViewById(R.id.providerName);
        mProviderPhone = (TextView) findViewById(R.id.providerPhone);
        mProviderLoc = (TextView) findViewById(R.id.providerLoc);

        mRatingBar = (RatingBar) findViewById(R.id.ratingBar);

        mRadioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.opt1);
        mRadioGroup.check(R.id.opt2);

        mLogout = (Button) findViewById(R.id.logout);
        mRequest = (Button) findViewById(R.id.request);
        mSettings = (Button) findViewById(R.id.settings);
        mHistory = (Button) findViewById(R.id.history);

        mZoomControls = findViewById(R.id.zoom);

        polylines = new ArrayList<>();

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        if (getUID() != null) {
            if (mLastLocation != null) {
                mRequest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (requestBool) {
                            endTransaction();
                        } else {
                            int selectId = mRadioGroup.getCheckedRadioButtonId();

                            final RadioButton radioButton = (RadioButton) findViewById(selectId);

                            if (radioButton.getText() == null) {
                                return;
                            }

                            requestService = radioButton.getText().toString(); // will give either opt1 or opt2
                            requestBool = true;

                            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                            GeoFire geoFire = new GeoFire(ref);
                            geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                            cusLatLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                            customerCurrLocMarker = mMap.addMarker(new MarkerOptions().position(cusLatLng).title("Customer Position").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));

                            mRequest.setText("Getting your Provider..");

                            if(requestService.equals("opt1")) {
                                getClosestProvider();
                            }

                            if(requestService.equals("opt2")) {
                                getClosestStore();
                            }
                        }
                    }
                });
            }
        }

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, HistoryActivity.class);
                intent.putExtra("customerOrProvider", "Customers");
                startActivity(intent);
                return;
            }
        });

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

        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        // filter results by country, only in in the US
        AutocompleteFilter typeFilter = new AutocompleteFilter.Builder().setCountry("US").build();
        autocompleteFragment.setFilter(typeFilter);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                destination = place.getName().toString();
                provLatLng = place.getLatLng();

                if(provCurrLocMarker != null) {
                    provCurrLocMarker.remove();
                }

                // use this to detect every nearby store
                provCurrLocMarker = mMap.addMarker(new MarkerOptions().position(provLatLng).title(destination));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(provLatLng, 16));
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                //Log.i(TAG, "An error occurred: " + status);
                Toast.makeText(CustomerMapActivity.this, "" + status.toString(), Toast.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState != null) {
            cusLatLng = savedInstanceState.getParcelable(KEY_LOCATION);
            Camera mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                Toast.makeText(this, "Place: " + place.toString(), Toast.LENGTH_LONG).show();
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                Toast.makeText(this, "Error: " + status.toString(), Toast.LENGTH_LONG).show();
            } else if (resultCode == RESULT_CANCELED) {

            }
        }
    }

    // save the map's camera position and device location
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastLocation);
            super.onSaveInstanceState(outState);
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        UiSettings mapUiSettings = mMap.getUiSettings();

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
    }


    private int radius = 1;
    private boolean providerFound = false;
    private String providerFoundID;
    GeoQuery geoQuery;

    private void getClosestProvider() {
        DatabaseReference providerLocation = FirebaseDatabase.getInstance().getReference().child("providersAvailable");
        GeoFire geoFire = new GeoFire(providerLocation);

        geoQuery = geoFire.queryAtLocation(new GeoLocation(cusLatLng.latitude, cusLatLng.longitude), radius);

        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!providerFound && requestBool) {
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                                Map<String, Object> providerMap = (Map<String, Object>) dataSnapshot.getValue();
                                if (providerFound) {
                                    return;
                                }

                                if (providerMap.get("service").equals(requestService)) {
                                    providerFound = true;
                                    providerFoundID = dataSnapshot.getKey();

                                    DatabaseReference providerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providerFoundID).child("customerRequest");
                                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    HashMap map = new HashMap();
                                    map.put("customerRideId", customerId);
                                    map.put("destination", destination);
                                    map.put("destinationLat", provLatLng.latitude);
                                    map.put("destinationLng", provLatLng.longitude);
                                    providerRef.updateChildren(map);

                                    getProviderLocation();
                                    getProviderInfo();
                                    getHasExchangeEnded();
                                    mRequest.setText("Looking for Provider Location....");
                                }
                            }
                        }
                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!providerFound) {
                    radius++;
                    getClosestProvider();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void getClosestStore() {
        //if customer decides to get tampons from a store
        //get closest store


    }

    private Marker mProviderMarker;
    private DatabaseReference providerLocationRef;
    private ValueEventListener providerLocationRefListener;

    // customer needs to know where to get the tampon
    private void getProviderLocation(){
        final String providerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        providerLocationRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providerId).child("customerRequest").child("destination");
        providerLocationRefListener = providerLocationRef.addValueEventListener(new ValueEventListener() {

            @Override

            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && providerId.equals("")){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;

                    // get provider's latitude and longitude
                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    provLatLng = new LatLng(locationLat, locationLng);
                    mProviderMarker = mMap.addMarker(new MarkerOptions().position(provLatLng).title("Provider's Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.tampons_pict)));

                    // get the route to the provider
                    getRouteToMarker(provLatLng);
                }
            }

            @Override

            public void onCancelled(DatabaseError databaseError) {

            }

        });

    }

    // method to provide the route to the provider
    private void getRouteToMarker(LatLng provLatLng) {
            if ( provLatLng != null && mLastLocation != null ) {
                Routing routing = new Routing.Builder()
                        .travelMode(AbstractRouting.TravelMode.WALKING)
                        .withListener(this)
                        .alternativeRoutes(false) //DISABLE ALETERNATE ROUTES
                        .waypoints(cusLatLng, provLatLng)
                        .build();
                routing.execute();
            }
        }

    private void getProviderInfo() {
        mProviderInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providerFoundID);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    {
                        if (map.get("name") != null) {
                            mProviderName.setText(map.get("name").toString());
                        }

                        if (map.get("phone") != null) {
                            mProviderPhone.setText(map.get("phone").toString());
                        }

                        if (map.get("location") != null) {
                            mProviderLoc.setText(map.get("location").toString());
                        }

                        if (map.get("profileImageURL") != null) {
                            Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(mProviderProfileImage);
                        }

                        //Calculating provider's rating
                        int ratingSum = 0;
                        float ratingsInTotal = 0; // number of people who give ratings
                        float ratingsAvg = 0;
                        for (DataSnapshot child : dataSnapshot.child("rating").getChildren()) {
                            ratingSum += Integer.valueOf(child.getValue().toString());
                            ratingsInTotal++;
                        }

                        // show provider's rating
                        if (ratingsInTotal != 0) {
                            ratingsAvg = ratingSum / ratingsInTotal;
                            mRatingBar.setRating(ratingsAvg);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private DatabaseReference providerHasEndedRef;
    private ValueEventListener providerHasEndedRefListener;

    private void getHasExchangeEnded() {
        providerHasEndedRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providerFoundID).child("customerRequest").child("customerRideId");
        providerHasEndedRefListener = providerHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()) {

                } else {
                    endTransaction();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void endTransaction() {
        requestBool = false;
        erasePolylines();
        geoQuery.removeAllListeners();
        providerLocationRef.removeEventListener(providerLocationRefListener);
        providerHasEndedRef.removeEventListener(providerHasEndedRefListener);

        if(providerFoundID != null) {
            DatabaseReference providerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providerFoundID).child("customerRequest");
            providerRef.removeValue();
            //CLEAR ID BACK TO NULL FOR NEXT TIME
            providerFoundID = null;
        }

        providerFound = false;
        radius = 1;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        if (customerCurrLocMarker != null) {
            customerCurrLocMarker.remove();
        }

        if (mProviderMarker != null) {
            mProviderMarker.remove();
        }

        //BACK TO DEFAULT WHEN DONE
        mRequest.setText("Request Provider");

        mProviderInfo.setVisibility(View.GONE);
        mProviderName.setText("");
        mProviderPhone.setText("");
        mProviderLoc.setText("Destination: --");
        mProviderProfileImage.setImageResource(R.mipmap.ic_default_user);

    }


    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations()) {
                if (getApplicationContext() != null) {
                    mLastLocation = location;
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                    CameraUpdate update = CameraUpdateFactory.newLatLngZoom(latLng, 10);
                    mMap.animateCamera(update);


                    if (!getProvidersAroundStarted) {
                        getProvidersAround();
                    }
                }
            }
        }
    };

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle("Give Permission")
                        .setMessage("Give Permission Message")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
                            }
                        })
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);

            }
        }
    }

    boolean getProvidersAroundStarted = false;
    List<Marker> markers = new ArrayList();

    private void getProvidersAround() {
        getProvidersAroundStarted = true;
        DatabaseReference providerLocation = FirebaseDatabase.getInstance().getReference().child("providersAvailable");

        GeoFire geoFire = new GeoFire(providerLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 999999999 );

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                for (Marker markerIt : markers) {
                    if (markerIt.getTag().equals(key))
                        return;
                }

                LatLng providerLocation = new LatLng(location.latitude, location.longitude);
                Marker mProviderMarker = mMap.addMarker(new MarkerOptions().position(providerLocation).title(key).icon(BitmapDescriptorFactory.fromResource(R.mipmap.tampons_pict)));
                mProviderMarker.setTag(key);
                markers.add(mProviderMarker);
            }

            @Override
            public void onKeyExited(String key) {
                for (Marker markerIt : markers) {
                    if (markerIt.getTag().equals(key)) {
                        markerIt.remove();
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                for (Marker markerIt : markers) {
                    if (markerIt.getTag().equals(key)) {
                        markerIt.setPosition(new LatLng(location.latitude, location.longitude));
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {
            }
        });
    }


    @Override
    protected void onStop() {
        super.onStop();
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

    //HELPER
    private String getUID() {
        return null;
    }

    @Override
    public boolean onMyLocationButtonClick() {
       // Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show();

        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }
}
