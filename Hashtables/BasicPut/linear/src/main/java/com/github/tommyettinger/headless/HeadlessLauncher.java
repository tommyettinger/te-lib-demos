package com.github.tommyettinger.headless;

import com.badlogic.gdx.math.GridPoint2;
import io.fury.collection.FuryObjectMap;

/** Launches the headless application. */
public class HeadlessLauncher {
    public static void main(String[] args) {
        final long startTimeNanos = System.nanoTime();
        FuryObjectMap<GridPoint2, Integer> points = new FuryObjectMap<>(100000000, 0.67f);
        final long constructionTimeNanos = System.nanoTime();
        for (int x = 0; x < 10000; x++) {
            for (int y = 0; y < 10000; y++) {
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