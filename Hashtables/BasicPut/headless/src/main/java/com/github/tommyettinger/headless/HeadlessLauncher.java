package com.github.tommyettinger.headless;

import com.badlogic.gdx.math.GridPoint2;
import com.github.tommyettinger.ds.ObjectObjectMap;

/** Launches the headless application. Can be converted into a utilities project or a server application. */
public class HeadlessLauncher {
    public static void main(String[] args) {
        final long startTimeNanos = System.nanoTime();
        ObjectObjectMap<GridPoint2, GridPoint2> points = new ObjectObjectMap<>(100000000);
        final long constructionTimeNanos = System.nanoTime();
        for (int x = 0; x < 10000; x++) {
            for (int y = 0; y < 10000; y++) {
                GridPoint2 gp = new GridPoint2(x, y);
                points.put(gp, gp);
            }
        }
        final long endTimeNanos = System.nanoTime();
        System.out.println("Started at:     " + startTimeNanos);
        System.out.println("Constructed at: " + constructionTimeNanos);
        System.out.println("Difference:     " + (constructionTimeNanos - startTimeNanos));
        System.out.println("Ended at:       " + endTimeNanos);
        System.out.println("Difference:     " + (endTimeNanos - startTimeNanos));
    }
}