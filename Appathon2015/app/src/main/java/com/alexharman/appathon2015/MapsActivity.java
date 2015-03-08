package com.alexharman.appathon2015;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson.JacksonFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;

import static com.google.maps.android.SphericalUtil.*;

public class MapsActivity extends FragmentActivity {
    private static final int ONE_MINUTE = 1000 * 60;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastKnownLocation;
    private Circle currentLocationMarker;

    private ArrayList<Spawn> spawnPoints = new ArrayList<Spawn>();
    private ArrayList<LatLng> latLngs = new ArrayList<LatLng>();
    private static final HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
    private static final JsonFactory jsonFactory = new JacksonFactory();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                if (isBetterLocation(location, lastKnownLocation)) {
                    lastKnownLocation = location;
                    updateCurrentLocationMarker(new LatLng(location.getLatitude(), location.getLongitude()));
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(locationListener);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.getUiSettings().setAllGesturesEnabled(false);

        LatLng currentLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        CircleOptions circleOptions = new CircleOptions().center(currentLatLng).radius(0.5); // In meters
        currentLocationMarker = mMap.addCircle(circleOptions);

        final LatLngBounds bounds = getArea(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), 250);
        Log.w("bounds", "Bounds are " + bounds.toString());

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 10), 2000, new GoogleMap.CancelableCallback() {
                    @Override
                    public void onFinish() {
                        startGame();
                    }

                    @Override
                    public void onCancel() {
                    }
                });
            }
        });
    }

    //Quick n dirty, assume 1 degree of lat = 111,111 metres, 1 degree lng = 111,111 * cos(latitude)
    private double metresToLat(double metres) {
        return metres / 111111;
    }

    private double metresToLng(double metres, double lat) {
        return metres / (111111 * Math.cos(lat));
    }

    private double latToMetres(double lat) {
        return 111111 / lat;
    }

    private double lngToMetres(double lng, double lat) {
        return (111111 * Math.cos(lat)) / lng;
    }

    private void updateCurrentLocationMarker(LatLng latlng) {
        currentLocationMarker.remove();
        CircleOptions circleOptions = new CircleOptions().center(latlng).radius(1); // In meters
        currentLocationMarker = mMap.addCircle(circleOptions);
    }


    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > ONE_MINUTE;
        boolean isSignificantlyOlder = timeDelta < -ONE_MINUTE;
        boolean isNewer = timeDelta > 0;

        // If it's been more than one minute since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private void startGame() {

        createSpawn(70.0f, 200.0f);
    }

    private void createSpawn(double minRadius, double maxRadius) {
        LatLng currentLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        LatLngBounds bInner = getArea(currentLatLng, minRadius);
        LatLngBounds bOuter = getArea(currentLatLng, maxRadius);
        Spawn s = new Spawn(bInner, bOuter);
        mMap.addMarker(new MarkerOptions().position(s.getPosition()).title("Spawn point uno"));
        new DirectionsGetter(createDirectionsURL(s.getPosition(), currentLatLng)).execute();
        spawnPoints.add(s);
    }

    private LatLngBounds getArea(LatLng centre, double radius) {
        return getArea(centre, radius, radius);
    }

    private LatLngBounds getArea(LatLng centre, double boundsNS, double boundsEW) {
        LatLng northEast = new LatLng(centre.latitude + metresToLat(boundsNS), centre.longitude + metresToLng(boundsEW, centre.latitude));
        LatLng southWest = new LatLng(centre.latitude - metresToLat(boundsNS), centre.longitude - metresToLng(boundsEW, centre.latitude));
        return new LatLngBounds(southWest, northEast);
    }

    private class DirectionsGetter extends AsyncTask<URL, Integer, String> {
        private ArrayList<LatLng> points = new ArrayList<LatLng>();

        GenericUrl url;

        public DirectionsGetter(GenericUrl url) {
            this.url = url;
        }


        @Override
        protected String doInBackground(URL... params) {
            HttpRequestFactory requestFactory = httpTransport.createRequestFactory(new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request) {
                    request.setParser(new JsonObjectParser(jsonFactory));
                    Log.e("Test", "I SAID HEY");
                }
            });
            try {
                Log.e("Test", "WHAT'S GOIN' OOOOONNNNNNNN?");
                HttpRequest request = requestFactory.buildGetRequest(url);
                HttpResponse httpResponse = request.execute();
                Log.e("Test", "AND HE TRIES: " + httpResponse.toString());
                //Log.e("Test", "OMG DON'T I TRY! " + httpResponse.parseAsString());
                Log.e("Test", "I TRY ALL THE TIME,");
                // 10/10 would JSON again.
                String polyline = new JSONObject(httpResponse.parseAsString())
                                        .getJSONArray("routes")
                                        .getJSONObject(0)
                                        .getJSONObject("overview_polyline")
                                        .getString("points");
                Log.e("Test", "IN THIS INSTITUTION!" + polyline);

                // Get individual steps
                /*JSONObject jsonObject = new JSONObject(httpResponse.parseAsString());
                if (!jsonObject.getString("status").equals("OK"))
                    throw new Exception("Directions Failed.");

                JSONArray jsonArray = jsonObject.getJSONArray("routes");
                // is this correct?
                jsonObject = jsonArray.getJSONObject(0);
                jsonArray = jsonObject.getJSONArray("legs");
                // is this correct?
                jsonObject = jsonArray.getJSONObject(0);
                ArrayList<LatLng> polyLine = new ArrayList<LatLng>();
                jsonArray = jsonObject.getJSONArray("steps");

                jsonObject = jsonArray.getJSONObject(0);
                JSONObject origin = jsonObject.getJSONObject("start_location");
                LatLng firstPoint = new LatLng(Double.parseDouble(origin.getString("lat")), Double.parseDouble(origin.getString("lng")));
                JSONObject destination = jsonObject.getJSONObject("end_location");
                LatLng lastPoint = new LatLng(Double.parseDouble(destination.getString("lat")), Double.parseDouble(destination.getString("lng")));
                double heading = computeHeading(firstPoint, lastPoint);
                // Could replace with Double.parseDouble(jsonObject.getJSONObject("distance").getString("value"));
                double distance = computeDistanceBetween(firstPoint, lastPoint);

                polyLine.add(firstPoint);
                for (int i = 1; i <= distance; ++i) {
                    polyLine.add(computeOffset(firstPoint, i, heading));
                }
                polyLine.add(lastPoint);

                int l = jsonArray.length();
                for (int i = 1, p = 1; i < l; ++i) {
                    jsonObject = jsonArray.getJSONObject(i);
                    origin = jsonObject.getJSONObject("start_location");
                    firstPoint = new LatLng(Double.parseDouble(origin.getString("lat")), Double.parseDouble(origin.getString("lng")));
                    destination = jsonObject.getJSONObject("end_location");
                    lastPoint = new LatLng(Double.parseDouble(destination.getString("lat")), Double.parseDouble(destination.getString("lng")));
                    heading = computeHeading(firstPoint, lastPoint);
                    distance = computeDistanceBetween(firstPoint, lastPoint);

                    polyLine.add(firstPoint);
                    for (; p <= distance; ++p) {
                        polyLine.add(computeOffset(firstPoint, p, heading));
                    }
                    p = 1;
                    polyLine.add(lastPoint);
                }
                Log.e("Test", "AND HE PRAYS!" + polyLine.toString());*/


            } catch (Exception e) {
                Log.e("HTTP", "Myaah!: " + e.toString());
            }

            return null;
        }

        protected void onPostExecute(Void result) {
            /*addPolylineToMap(latLngs);
            GoogleMapUtis.fixZoomForLatLngs(googleMap, latLngs);
            getActivity().setProgressBarIndeterminateVisibility(Boolean.FALSE);*/
            //mMap.add
        }
    }

    public GenericUrl createDirectionsURL(LatLng origin, LatLng destination) {
        GenericUrl url = new GenericUrl("https://maps.googleapis.com/maps/api/directions/json");

        url.put("origin", origin.latitude + "," + origin.longitude);
        url.put("destination", destination.latitude + "," + destination.longitude);
        url.put("mode", "walking");
        url.put("units", "metric");
        //This breaks it despite being a "required" parameter.
        //url.put("key", "AIzaSyCd9eyMzbpfUqVMiYcl9TyKXhfdDulsBsU");

        Log.e("URL", "That genericurl: " + url.toString());

        return url;
    }
}
