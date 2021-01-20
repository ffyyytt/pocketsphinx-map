package edu.cmu.pocketsphinx.demo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback,
        LocationListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, RoutingListener, RecognitionListener {
    String TAG = "MapsFragment";
    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    LatLng mlatLng, destinationLocation;
    String Des = "";

    SpeechRecognizer recognizer;
    String KWS_SEARCH = "wakeup";
    String KEYPHRASE = "hi";
    String GOPHRASE = "go";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        startListen();
    }

    void startListen()
    {
        try {
            Assets assets = new Assets(MainActivity.this);
            File assetDir = assets.syncAssets();
            recognizer = SpeechRecognizerSetup.defaultSetup()
                    .setAcousticModel(new File(assetDir, "en-us-ptm"))
                    .setDictionary(new File(assetDir, "cmudict-en-us.dict"))
                    .setKeywordThreshold(1e-5f)
                    .setRawLogDir(assetDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                    .getRecognizer();
            recognizer.addListener(this);
            //recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);
            File menuGrammar = new File(assetDir, "menu.gram");
            recognizer.addGrammarSearch(KWS_SEARCH, menuGrammar);
            switchSearch(KWS_SEARCH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        Places.initialize(this, getResources().getString(R.string.google_maps_key));
        //set editText non focusable

    }

    @Override
    public void onConnected(Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {

        mLastLocation = location;
        if (mCurrLocationMarker != null) {
            mCurrLocationMarker.remove();
        }
        //Place current location marker
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mlatLng = latLng;
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        mCurrLocationMarker = mMap.addMarker(markerOptions);

        //move map camera
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

        //stop location updates
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onRoutingFailure(RouteException e) {
        Toast.makeText(getApplicationContext(),"Finding Route failed!"+e.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRoutingStart() {
        //Toast.makeText(getApplicationContext(),"Tìm kiếm đường đi!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> arrayList, int i) {
        mMap.clear();
        showRoute(arrayList.get(i),mMap);
    }

    @Override
    public void onRoutingCancelled() {
        Toast.makeText(getApplicationContext(),"Finding Route cancelled!", Toast.LENGTH_LONG).show();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;
        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE))
        {
            recognizer.stop();
            startSpeechToText(1);
        }
        else if (text.equals(GOPHRASE) && !Des.equals(""))
        {
            final Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://maps.google.com/maps?" + "saddr="+ mlatLng.latitude + "," + mlatLng.longitude + "&daddr=" + destinationLocation.latitude + "," + destinationLocation.longitude+"&travelmode=driving&dir_action=navigate"));
            intent.setClassName("com.google.android.apps.maps","com.google.android.maps.MapsActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            recognizer.stop();
        }
        //Toast.makeText(getApplicationContext(),"PartialResult: "+text,Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recognizer.shutdown();
    }

    void exit()
    {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory( Intent.CATEGORY_HOME );
        intent.setFlags(0);
        startActivity(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.finishAndRemoveTask();
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            //Toast.makeText(getApplicationContext(),"Result: "+text,Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onError(Exception e) {

    }

    @Override
    public void onTimeout() {

    }

    public void searchLocation(String Destinationlocation) {
        Des = Destinationlocation;
        List<Address> addressList = null;

        if (Destinationlocation != null || !Destinationlocation.equals("")) {
            Geocoder geocoder = new Geocoder(this);
            try {
                addressList = geocoder.getFromLocationName(Destinationlocation, 1);

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (addressList.isEmpty())
            {
                Toast.makeText(getApplicationContext(),"Cant find address!", Toast.LENGTH_SHORT).show();
                return;
            }
            Address address = addressList.get(0);
            LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
            destinationLocation = latLng;
            mMap.addMarker(new MarkerOptions().position(latLng).title(Destinationlocation));
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));

            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(false)
                    .waypoints(mlatLng, destinationLocation)
                    .key(getResources().getString(R.string.google_maps_key))
                    .build();

            routing.execute();
            //.makeText(getApplicationContext(),destinationLocation.toString(),Toast.LENGTH_LONG).show();
        }
    }

    public void showRoute(Route r, GoogleMap mMap){
        PolylineOptions polyOptions = new PolylineOptions();
        polyOptions.color(Color.argb(255,0,0,255));
        polyOptions.width(7);
        polyOptions.addAll(r.getPoints());
        mMap.addPolyline(polyOptions);

        mMap.addMarker(new MarkerOptions().position(mlatLng)
                .title("start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))).showInfoWindow();
        mMap.addMarker(new MarkerOptions().position(destinationLocation)
                .title("destination")).showInfoWindow();
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mlatLng,14));

        startListen();
    }

    private void startSpeechToText(int i) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getApplicationContext(), getString(R.string.speech_not_supported), Toast.LENGTH_SHORT).show();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 1:
                if (resultCode == -1 && data != null) {
                    searchLocation(data.getStringArrayListExtra("android.speech.extra.RESULTS").get(0));
                }
                //switchSearch(KEYPHRASE);
        }
    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);

        //Toast.makeText(getApplicationContext(),"Ready!!!",Toast.LENGTH_SHORT).show();
    }
}