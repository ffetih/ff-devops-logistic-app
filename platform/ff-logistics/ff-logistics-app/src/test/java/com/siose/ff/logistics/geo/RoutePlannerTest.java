package com.siose.ff.logistics.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.siose.ff.logistics.geo.routing.HaversineRoadMatrix;
import com.siose.ff.logistics.geo.routing.Location;
import com.siose.ff.logistics.geo.routing.PlannedRoute;
import com.siose.ff.logistics.geo.routing.RoutePlanner;
import com.siose.ff.logistics.geo.routing.RoutePlanner.Visit;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePlannerTest {

    private final RoutePlanner planner = new RoutePlanner(new HaversineRoadMatrix());

    private static final Location DEPOT = new Location(41.0, 29.0);

    @Test
    void drivesToTheNearestStopNext() {
        // Three stops due east of the depot, listed far to near.
        List<Visit> visits = List.of(
                new Visit(new Location(41.0, 29.3), 0),
                new Visit(new Location(41.0, 29.1), 0),
                new Visit(new Location(41.0, 29.2), 0));

        PlannedRoute route = planner.plan(DEPOT, visits, 60);

        assertThat(route.stops()).extracting(PlannedRoute.PlannedStop::stopIndex).containsExactly(1, 2, 0);
    }

    @Test
    void arrivalCountsTheServiceAtTheStopBefore() {
        List<Visit> visits = List.of(new Visit(new Location(41.0, 29.1), 30), new Visit(new Location(41.0, 29.2), 10));

        PlannedRoute route = planner.plan(DEPOT, visits, 60);

        // Each leg: 8.39 km straight x 1.24 = 10.41 km, 11 minutes at 60 km/h.
        assertThat(route.stops()).extracting(PlannedRoute.PlannedStop::arrivalMinutes).containsExactly(11, 11 + 30 + 11);
        assertThat(route.totalMinutes()).isEqualTo(11 + 30 + 11 + 10);
    }
}
