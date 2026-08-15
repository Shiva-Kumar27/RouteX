package com.hydcommute.routing;

import java.util.List;

/**
 * Shared result type for any RoutingStrategy implementation, so Dijkstra
 * and A* (and anything added later) can be benchmarked side by side.
 */
public class RouteResult {
    public final List<Long> path;
    public final double totalDistanceMeters;
    public final long nodesExplored;
    public final long timeTakenMillis;

    public RouteResult(List<Long> path, double totalDistanceMeters,
                       long nodesExplored, long timeTakenMillis) {
        this.path = path;
        this.totalDistanceMeters = totalDistanceMeters;
        this.nodesExplored = nodesExplored;
        this.timeTakenMillis = timeTakenMillis;
    }

    public boolean isFound() {
        return !path.isEmpty();
    }
}