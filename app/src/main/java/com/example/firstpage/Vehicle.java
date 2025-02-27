package com.example.firstpage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

public class Vehicle extends Sprite {

    public int roadHeight; // Represents the ground level

    public Vehicle(Bitmap image, Context context, Rect hitbox, Rect screen, int roadHeight) {
        super(image, context, hitbox, screen);
        this.roadHeight = roadHeight;
        this.vx = -30; // Set horizontal speed (adjust as needed)
        // Position the obstacle off-screen to the right.
        this.setX(screen.right);
        // Position the obstacle so its top aligns with the ground level.
        this.setY(roadHeight);
    }

    public static Rect generate(Rect screen) {
        return new Rect(0, 0, 300, 140);
    }

    public boolean isOffScreen() {
        return this.getRight() < screen.left;
    }
}
