package com.alexharman.appathon2015;

import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;

public class Spawn {

    private LatLng position;
    private int spawnrate;
    //private spawnType race;
    private ArrayList<Polyline> lines = new ArrayList<Polyline>();
    private ArrayList<LatLng> points = new ArrayList<LatLng>();

    // Create spawnpoint at a random point within an area
    Spawn(LatLngBounds bounds) {
        Random rand = new Random();
        double rangeNS = bounds.northeast.latitude - bounds.southwest.latitude;
        double rangeEW = bounds.northeast.longitude - bounds.southwest.longitude;
        position = new LatLng(rand.nextDouble() * rangeNS + bounds.southwest.latitude,
                rand.nextDouble() * rangeEW + bounds.southwest.longitude);
        Log.w("spawnpoint", "Placed at " + position.toString());
        return;
    }

    Spawn(LatLngBounds innerBounds, LatLngBounds outerBounds) {
        Random rand = new Random();
        //Distance between inner and outer Lat and Lng
        double rangeLat = outerBounds.northeast.latitude - innerBounds.northeast.latitude;
        double rangeLng = outerBounds.northeast.longitude - innerBounds.northeast.longitude;

        //Random lat/lng in that area.
        double randLat = (rand.nextDouble() * 2 - 1.0f) * rangeLat;
        double randLng = (rand.nextDouble() * 2 - 1.0f) * rangeLng;

        if (randLat > 0) {
            randLat += innerBounds.northeast.latitude;
        } else {
            randLat += innerBounds.southwest.latitude;
        }
        if (randLng > 0) {
            randLng += innerBounds.northeast.longitude;
        } else {
            randLng += innerBounds.southwest.longitude;
        }
        position = new LatLng(randLat, randLng);

        Log.w("spawnpoint", "Placed at " + position.toString());
        return;
    }

    // Create spawnpoint at a specific point
    Spawn(LatLng position) {
        this.position = position;
        return;
    }

    public LatLng getPosition() {
        return position;
    }
}

