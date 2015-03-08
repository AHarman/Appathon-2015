package com.alexharman.appathon2015;

import com.google.android.gms.maps.model.Circle;

public class Invader {
    private int health = 60;
    private Circle circle;
    private int id;
    public int position;
    boolean justGotHit = false;

    public Invader(Circle circle, int id) {
        this.circle = circle;
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Circle getCircle() {
        return circle;
    }

    public int takeDamage(int amount) {
        health -= amount;
        return health;
    }
}
