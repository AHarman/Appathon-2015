package com.alexharman.appathon2015;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
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
import com.google.maps.android.SphericalUtil;

import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;

import static com.google.maps.android.PolyUtil.*;
import static com.google.maps.android.SphericalUtil.*;

public class MapsActivity extends FragmentActivity implements GoogleMap.OnMapLongClickListener {
    private static final int ONE_MINUTE = 1000 * 60;
    private static final double TOWER_RADIUS = 100.0f;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastKnownLocation;
    private Circle currentLocationMarker;
    private ArrayList<ArrayList<LatLng>> paths = new ArrayList<ArrayList<LatLng>>();
    private ArrayList<Spawn> spawnPoints = new ArrayList<Spawn>();
    private ArrayList<Tower> towers = new ArrayList<Tower>(5);
    private int[][] collisionMap = new int[5][];

    private static final HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
    private static final JsonFactory jsonFactory = new JacksonFactory();


    Button lockButton;
    private boolean mapLoaded = false;
    private boolean gameStarted = false;
    private boolean gameOver = false;
    private boolean spawning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_maps);
        lockButton = (Button) findViewById(R.id.lockButton);
        lockButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mapLoaded) {
                    ViewGroup layout = (ViewGroup) lockButton.getParent();
                    if (null != layout) {
                        layout.removeView(lockButton);
                        startGame();
                    }
                    centreOnPlayer();
                    locationManager.removeUpdates(locationListener);
                }
            }
        });

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
        setUpMapIfNeeded();
        mMap.setOnMapLongClickListener(this);
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

    private void centreOnPlayer() {
        final LatLngBounds bounds = getArea(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), 600, 400);
        Log.w("bounds", "Bounds are " + bounds.toString());
        if (mapLoaded) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 10), 2000, new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                }

                @Override
                public void onCancel() {
                }
            });
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
        CircleOptions circleOptions = new CircleOptions().center(currentLatLng).radius(3).fillColor(Color.RED).strokeColor(Color.RED).zIndex(15); // In meters
        currentLocationMarker = mMap.addCircle(circleOptions);

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                mapLoaded = true;
                centreOnPlayer();
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

    private void updateCurrentLocationMarker(LatLng latlng) {
        currentLocationMarker.remove();
        CircleOptions circleOptions = new CircleOptions().center(latlng).radius(3).fillColor(Color.RED).strokeColor(Color.RED).zIndex(15);
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
        gameStarted = true;
        createSpawn(400.0f, 300.0f, 550.0f, 350.0f);
    }

    private void createSpawn(double minRadius, double maxRadius) {
        createSpawn(minRadius, minRadius, maxRadius, maxRadius);
    }

    private void createSpawn(double minNS, double minEW, double maxNS, double maxEW) {
        LatLng currentLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        LatLngBounds bInner = getArea(currentLatLng, minNS, minEW);
        LatLngBounds bOuter = getArea(currentLatLng, maxNS, maxEW);
        Spawn s = new Spawn(bInner, bOuter);
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

    @Override
    public void onMapLongClick(LatLng latLng) {
        // It's pronounced tuh-ay
        if (gameStarted && towers.size() < 5 && computeDistanceBetween(latLng, spawnPoints.get(0).getPosition()) > 150
                && computeDistanceBetween(latLng, currentLocationMarker.getCenter()) > 150) {
            PolygonOptions squareOptions = new PolygonOptions()
                    .add(computeOffset(latLng, 8, 45),
                            computeOffset(latLng, 8, 45 + 90),
                            computeOffset(latLng, 8, 45 + 180),
                            computeOffset(latLng, 8, 45 + 270))
                    .fillColor(Color.RED)
                    .strokeColor(Color.RED);
            CircleOptions area = new CircleOptions().radius(100).fillColor(Color.argb(50, 150, 0, 0)).strokeWidth(0).center(latLng);
            Polygon square = mMap.addPolygon(squareOptions);
            Circle circle = mMap.addCircle(area);
            Tower turret = new Tower(square, circle);
            towers.add(turret);
            precalcThing();
        }
        if (towers.size() == 5 && !spawning) {
            startEnemies();
        }
    }

    private void precalcThing() {
        int towerId = towers.size() - 1;
        ArrayList<LatLng> path = paths.get(0);
        ArrayList<Integer> ints = new ArrayList<Integer>();
        LatLng center = towers.get(towerId).getRangeCircle().getCenter();


        for (int i = path.size() - 1; i >= 0; i--) {
            if (computeDistanceBetween(center, path.get(i)) < TOWER_RADIUS) {
                ints.add(i);
            }
        }
        collisionMap[towerId] = new int[ints.size()];
        for (int i = 0; i < ints.size(); i++) {
            collisionMap[towerId][i] = ints.get(i);
        }
    }

    private class DirectionsGetter extends AsyncTask<URL, Integer, Void> {
        GenericUrl url;

        public DirectionsGetter(GenericUrl url) {
            this.url = url;
        }

        @Override
        protected Void doInBackground(URL... params) {
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

                //Interpolate lines
                ArrayList<LatLng> path = (ArrayList<LatLng>) decode(polyline);
                ArrayList<LatLng> interpolatedPath = new ArrayList<LatLng>();
                for (int j = 0; j < path.size() - 1; j++) {
                    LatLng p0 = path.get(j);
                    LatLng p1 = path.get(j + 1);
                    interpolatedPath.add(p0);

                    double distance = computeDistanceBetween(p0, p1);
                    double heading = computeHeading(p0, p1);
                    for (double i = 2.0f; i <= distance - 2.0f; i += 2.0f) {
                        p0 = computeOffset(p0, 2, heading);
                        interpolatedPath.add(p0);
                    }
                }
                interpolatedPath.add(path.get(path.size() - 1));
                paths.add(interpolatedPath);

            } catch (Exception e) {
                Log.e("HTTP", "Myaah!: " + e.toString());
            }

            return null;
        }

        protected void onPostExecute(Void result) {
            final ArrayList<LatLng> path = paths.get(paths.size() - 1);
            Spawn s = spawnPoints.get(spawnPoints.size() - 1);
            s.setPosition(path.get(0));
            mMap.addCircle(new CircleOptions().center(s.getPosition()).radius(3).strokeColor(Color.BLACK).fillColor(Color.BLACK));

            //PolylineOptions options = new PolylineOptions().color(Color.argb(150, 0, 0, 0)).zIndex(5);

            //options.addAll(path);
            //mMap.addPolyline(options);
        }

    }

    void startEnemies() {
        spawning = true;
        final Handler h = new Handler();
        h.postDelayed(new Runnable() {
            private int index = 0;
            private ArrayList<LatLng> thisPath = paths.get(0);
            private final CircleOptions template = new CircleOptions().fillColor(Color.GREEN).strokeColor(Color.GREEN).radius(1).zIndex(10).center(thisPath.get(0));
            private ArrayList<Invader> enemyList = new ArrayList<Invader>();
            private Invader currentEnemy;

            private Invader[] enemy_lookup = new Invader[thisPath.size()];

            @Override
            public void run() {
                if (index % 20 == 0) {
                    Circle temp = mMap.addCircle(template);
                    Invader newEnemy = new Invader(temp, index);
                    newEnemy.position = 0;
                    enemy_lookup[0] = newEnemy;
                    enemyList.add(newEnemy);
                }

                for (int i = 0; i < towers.size(); i++) {
                    for (int j = 0; j < collisionMap[i].length; j++) {
                        currentEnemy = enemy_lookup[collisionMap[i][j]];
                        if (currentEnemy != null) {
                            currentEnemy.justGotHit = true;
                            if (currentEnemy.takeDamage(1) <= 0) {
                                currentEnemy.getCircle().remove();
                                enemyList.remove(currentEnemy);
                                enemy_lookup[collisionMap[i][j]] = null;
                            }
                            //Break for j loop
                            break;
                        }
                    }
                }


                for (int i = 0; i < enemyList.size(); i++) {
                    currentEnemy = enemyList.get(i);
                    if (index - currentEnemy.getId() == thisPath.size()) {
                        gameOver = true;
                    } else {
                        currentEnemy.getCircle().setCenter(thisPath.get(index - currentEnemy.getId()));
                        enemy_lookup[currentEnemy.position] = null;
                        currentEnemy.position++;
                        enemy_lookup[currentEnemy.position] = currentEnemy;
                        //Code duplication for performance
                        if (currentEnemy.justGotHit) {

                            currentEnemy.justGotHit = false;
                            currentEnemy.getCircle().setFillColor(Color.YELLOW);
                            currentEnemy.getCircle().setStrokeColor(Color.YELLOW);
                        } else {
                            currentEnemy.getCircle().setFillColor(Color.GREEN);
                            currentEnemy.getCircle().setStrokeColor(Color.GREEN);
                        }
                    }
                }

                index++;
                if (!gameOver) {
                    h.postDelayed(this, 200);
                }

            }
        }

                , 1000); // 1 second delay (takes millis)
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
