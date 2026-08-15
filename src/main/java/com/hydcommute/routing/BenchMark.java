package com.hydcommute.routing;

import com.hydcommute.graph.OverpassGraphLoader;
import com.hydcommute.graph.OverpassGraphLoader.Graph;

import java.util.List;

/**
 * Runs multiple RoutingStrategy implementations against the same
 * source/target and prints a side-by-side comparison.
 */
public class BenchMark {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Benchmark <path-to-json> <sourceNodeId> <targetNodeId>");
            return;
        }

        OverpassGraphLoader loader = new OverpassGraphLoader();
        Graph graph = loader.load(args[0]);

        long source = Long.parseLong(args[1]);
        long target = Long.parseLong(args[2]);

        List<RoutingStrategy> strategies = List.of(
                new Dijkstras(graph),
                new Astar(graph)
        );

        System.out.printf("%-10s %-12s %-14s %-10s%n", "Algorithm", "Distance(m)", "NodesExplored", "Time(ms)");
        for (RoutingStrategy strategy : strategies) {
            RouteResult result = strategy.findShortestPath(source, target);
            if (!result.isFound()) {
                System.out.printf("%-10s NO PATH FOUND%n", strategy.getName());
                continue;
            }
            System.out.printf("%-10s %-12.1f %-14d %-10d%n",
                    strategy.getName(), result.totalDistanceMeters,
                    result.nodesExplored, result.timeTakenMillis);
        }
    }
}