package com.example.nataliaaulia.tamponsnavigator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.icu.text.DateFormat;
import android.icu.text.SimpleDateFormat;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback, RoutingListener {

    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;
    private String rideId, currentUserId, customerId, providersId, userProviderOrCustomer;

    private TextView locationRide;
    private TextView distanceRide;
    private TextView dateRide;
    private TextView nameUser;
    private TextView phoneUser;

    private ImageView imageUser;

    private RatingBar mRatingBar;

    private Button mPay;

    private boolean customerPaid = false;

    private LatLng destinationLatLng, pickupLatLng;
    private String distance;
    private double ridePrice;
    private DatabaseReference historyExchangeInfoDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);

        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        rideId = getIntent().getExtras().getString("rideId");

        mMapFragment.getMapAsync(this);

        polylines = new ArrayList<>();

        locationRide = findViewById(R.id.rideLocation);
        distanceRide = findViewById(R.id.rideDistance);
        dateRide = findViewById(R.id.rideDate);
        nameUser = findViewById(R.id.userName);
        phoneUser = findViewById(R.id.userPhone);
        imageUser = findViewById(R.id.userImage);
        mRatingBar = findViewById(R.id.ratingBar);


        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        historyExchangeInfoDb = FirebaseDatabase.getInstance().getReference().child("history").child(rideId);
        getDeliveryInformation();

    }

    private void getDeliveryInformation() {
        historyExchangeInfoDb.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                        if (child.getKey().equals("customer")) {
                            customerId = child.getValue().toString();
                            if (!customerId.equals(currentUserId)) {
                                userProviderOrCustomer = "Providers";
                                getUserInformation("Customers", customerId);
                            }
                        }

                        if (child.getKey().equals("providers")) {
                            providersId = child.getValue().toString();
                            if (!providersId.equals(currentUserId)) {
                                userProviderOrCustomer = "Customers";
                                getUserInformation("Providers", providersId);
                                displayCustomerRelatedObject();
                            }
                        }

                        if (child.getKey().equals("timestamp")) {
                             dateRide.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }

                        if (child.getKey().equals("rating")) {
                            mRatingBar.setRating(Integer.valueOf(child.getValue().toString()));
                        }

                        if (child.getKey().equals("distance")) {
                            distance = child.getValue().toString();
                            distanceRide.setText(distance.substring(0, Math.min(distance.length(), 5)) + " kilometers ");
                        }

                        if (child.getKey().equals("destination")) {
                            locationRide.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }

                        if (child.getKey().equals("location")) {
                            locationRide.setText(getDate(Long.valueOf(child.getValue().toString())));
                            pickupLatLng = new LatLng(Double.valueOf(child.child("from").child("lat").getValue().toString()), Double.valueOf(child.child("from").child("lng").getValue().toString()));
                            if (destinationLatLng != new LatLng(0,0)) {
                                getRouteToMarker();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    private void displayCustomerRelatedObject() {
        // Save rating in database
        mRatingBar.setVisibility(View.VISIBLE);

        mRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                historyExchangeInfoDb.child("rating").setValue(rating);
                DatabaseReference mProviderRatingDB = FirebaseDatabase.getInstance().getReference().child("Users").child("Providers").child(providersId).child("rating");
                mProviderRatingDB.child(rideId).setValue(rating);
            }
        });
    }

    private void getUserInformation(String otherUserProviderOrCustomer, String otherUserId) {
        DatabaseReference mOtherUserDB = FirebaseDatabase.getInstance().getReference().child("Users").child(otherUserProviderOrCustomer).child(otherUserId);
        mOtherUserDB.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("name") != null) {
                        nameUser.setText(map.get("name").toString());
                    }

                    if (map.get("phone") != null) {
                        phoneUser.setText(map.get("phone").toString());
                    }

                    if (map.get("profileImageUrl") != null) {
                        Glide.with(getApplication()).load(map.get("profileImageUrl").toString()).into(imageUser);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getRouteToMarker() {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false) //DISABLE ALETERNATE ROUTES
                .waypoints(new LatLng(pickupLatLng.latitude, pickupLatLng.longitude), destinationLatLng)
                .build();
        routing.execute();
    }

    //CONVERT TIMESTAMP INTO A DATE
    private String getDate(Long timestamp) {
        //GET THE LOCAL TIMEZONE
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(timestamp * 1000);

        DateFormat df = new SimpleDateFormat("MM--dd--yyyy hh:mm");
        String date = df.format(new Date()).toString();

        return date;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
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

        // ZOOM INTO MAP AUTOMATICALLY
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickupLatLng);
        builder.include(destinationLatLng);
        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;

        // can change value if app crash
        int padding = (int) (width * 0.2);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        mMap.animateCamera(cameraUpdate);

        mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("pickup location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_pickup)));
        mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("destination"));

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
}
