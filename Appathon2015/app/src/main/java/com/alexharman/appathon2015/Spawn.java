package com.alexharman.appathon2015;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public class Spawn {

    private LatLng position;
    private int spawnrate;
    //private spawnType race;

    // Create spawnpoint at a random point within an area
    Spawn(LatLngBounds bounds) {
        return;
    }

    // Create spawnpoint at a specific point
    Spawn(LatLng position) {
        this.position = position;
        return;
    }
}
