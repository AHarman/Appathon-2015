package com.alexharman.appathon2015;

import android.graphics.Color;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;


public class Tower {
    private Circle rangeCircle;
    private CircleOptions rangeOptions;
    private Polygon square;
    private PolygonOptions squareOptions;

    //Build a tower
    public Tower(Polygon square, Circle rangeCircle) {
        this.square = square;
        this.rangeCircle = rangeCircle;
    }

    public Circle getRangeCircle() {
        return rangeCircle;
    }

    public CircleOptions getRangeOptions() {
        return rangeOptions;
    }

    public Polygon getSquare() {
        return square;
    }

    public PolygonOptions getSquareOptions() {
        return squareOptions;
    }

    public void setRangeCircle(Circle rangeCircle) {
        this.rangeCircle = rangeCircle;
    }

    public void setSquare(Polygon square) {
        this.square = square;
    }
}
