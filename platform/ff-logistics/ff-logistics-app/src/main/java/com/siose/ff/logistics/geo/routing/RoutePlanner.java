package com.siose.ff.logistics.geo.routing;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Puts a route's stops in driving order ({@code BR-GEO-04}) and times them.
 *
 * <p>Nearest neighbour: from the depot, always drive to the closest stop not yet visited. Quick
 * and usually within a quarter of the best order for a short list; ties go to the stop listed first.
 */
@Component
public class RoutePlanner {

    private final RoadMatrix roads;

    public RoutePlanner(RoadMatrix roads) {
        this.roads = roads;
    }

    /** A stop as the planner sees it: where it is, and how long the vehicle stays. */
    public record Visit(Location location, int serviceMinutes) {
    }

    public PlannedRoute plan(Location depot, List<Visit> visits, double averageSpeedKmh) {
        Travel travel = roads.between(depot, visits.stream().map(Visit::location).toList(), averageSpeedKmh);
        return schedule(nearestNeighbour(travel, visits.size()), visits, travel);
    }

    /** Stop indexes from 0, in driving order. */
    static List<Integer> nearestNeighbour(Travel travel, int stopCount) {
        boolean[] visited = new boolean[stopCount];
        List<Integer> order = new ArrayList<>(stopCount);
        int at = 0;
        while (order.size() < stopCount) {
            int next = -1;
            for (int stop = 0; stop < stopCount; stop++) {
                if (!visited[stop] && (next < 0 || travel.kilometers(at, stop + 1) < travel.kilometers(at, next + 1))) {
                    next = stop;
                }
            }
            visited[next] = true;
            order.add(next);
            at = next + 1;
        }
        return order;
    }

    /** Arrival at each stop: the drive from the one before, after its service ends. */
    static PlannedRoute schedule(List<Integer> order, List<Visit> visits, Travel travel) {
        List<PlannedRoute.PlannedStop> stops = new ArrayList<>(order.size());
        int at = 0;
        int clock = 0;
        for (int stop : order) {
            int arrival = clock + travel.minutes(at, stop + 1);
            stops.add(new PlannedRoute.PlannedStop(stop, travel.kilometers(at, stop + 1), arrival));
            clock = arrival + visits.get(stop).serviceMinutes();
            at = stop + 1;
        }
        return new PlannedRoute(List.copyOf(stops), clock);
    }
}
