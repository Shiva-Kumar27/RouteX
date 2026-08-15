package com.hydcommute.routing;

/**
 * Strategy interface for shortest-path algorithms over the road network
 * Graph. Lets Dijkstra, A*, and future algorithms (e.g. bidirectional
 * search) be swapped and benchmarked interchangeably.
 */
public interface RoutingStrategy {
    RouteResult findShortestPath(long sourceId, long targetId);

    /** Short label for benchmark output, e.g. "Dijkstra", "A*". */
    String getName();
}