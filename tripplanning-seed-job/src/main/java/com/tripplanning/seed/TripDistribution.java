package com.tripplanning.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Splits a fixed trip count across users with a min/max per user (perf dataset). */
final class TripDistribution {

    private TripDistribution() {}

    static List<Integer> tripsPerUser(int numUsers, int total, int lo, int hi, Random rng) {
        if (numUsers * lo > total || numUsers * hi < total) {
            throw new IllegalArgumentException(
                    "Cannot split "
                            + total
                            + " trips across "
                            + numUsers
                            + " users with "
                            + lo
                            + ".."
                            + hi);
        }
        int[] counts = new int[numUsers];
        for (int i = 0; i < numUsers; i++) {
            counts[i] = lo;
        }
        int remaining = total - numUsers * lo;
        while (remaining > 0) {
            List<Integer> choices = new ArrayList<>();
            for (int i = 0; i < numUsers; i++) {
                if (counts[i] < hi) {
                    choices.add(i);
                }
            }
            if (choices.isEmpty()) {
                throw new IllegalStateException("distribute: no headroom");
            }
            counts[choices.get(rng.nextInt(choices.size()))]++;
            remaining--;
        }
        List<Integer> out = new ArrayList<>(numUsers);
        for (int c : counts) {
            out.add(c);
        }
        return out;
    }
}
