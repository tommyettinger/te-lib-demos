package com.github.tommyettinger.headless;

import com.badlogic.gdx.math.GridPoint2;
import com.github.tommyettinger.ds.ObjectObjectMap;

/** Launches the headless application. */
public class HeadlessLauncher {
    public static void main(String[] args) {
        final long startTimeNanos = System.nanoTime();
        ObjectObjectMap<GridPoint2, Integer> points = new ObjectObjectMap<>(256, 0.45f);
        final long constructionTimeNanos = System.nanoTime();
        for (int x = 0; x < 3000; x++) {
            for (int y = 0; y < 3000; y++) {
                points.put(new GridPoint2(x, y), x - y);
            }
        }
        final long endTimeNanos = System.nanoTime();
        System.out.println("Construction took: " + (constructionTimeNanos - startTimeNanos));
        System.out.println("Processing took:   " + (endTimeNanos - constructionTimeNanos));
        System.out.println("Total took:        " + (endTimeNanos - startTimeNanos));
        System.out.println("Checking code " + points.hashCode());
    }
}