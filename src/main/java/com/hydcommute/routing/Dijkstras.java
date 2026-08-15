package com.hydcommute.routing;

import com.hydcommute.graph.OverpassGraphLoader.Edge;
import com.hydcommute.graph.OverpassGraphLoader.Graph;

import java.util.*;

/**
 * Dijkstra's shortest-path algorithm over the road network Graph produced
 * by OverpassGraphLoader. Instrumented with timing + node-exploration
 * counters for benchmarking against A*.
 */
public class Dijkstras implements RoutingStrategy {

    private final Graph graph;

    public Dijkstras(Graph graph) {
        this.graph = graph;
    }

    @Override
    public String getName() {
        return "Dijkstra";
    }

    // ---- Core algorithm ----
    @Override
    public RouteResult findShortestPath(long sourceId, long targetId) {
        long startTime = System.currentTimeMillis();
        long nodesExplored = 0;

        Map<Long, Double> distances = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        record QueueEntry(long nodeId, double distance) {}
        PriorityQueue<QueueEntry> pq = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::distance)
        );

        distances.put(sourceId, 0.0);
        pq.add(new QueueEntry(sourceId, 0.0));

        while (!pq.isEmpty()) {
            QueueEntry current = pq.poll();
            long currentId = current.nodeId();
            double currentDist = current.distance();

            if (visited.contains(currentId)) continue;
            visited.add(currentId);
            nodesExplored++;

            if (currentId == targetId) break;

            for (Edge edge : graph.adjacency.getOrDefault(currentId, List.of())) {
                if (visited.contains(edge.to)) continue;

                double newDist = currentDist + edge.weightMeters;
                double knownDist = distances.getOrDefault(edge.to, Double.POSITIVE_INFINITY);

                if (newDist < knownDist) {
                    distances.put(edge.to, newDist);
                    previous.put(edge.to, currentId);
                    pq.add(new QueueEntry(edge.to, newDist));
                }
            }
        }

        long endTime = System.currentTimeMillis();

        if (!distances.containsKey(targetId)) {
            return new RouteResult(List.of(), Double.POSITIVE_INFINITY,
                    nodesExplored, endTime - startTime);
        }

        List<Long> path = new ArrayList<>();
        Long step = targetId;
        while (step != null) {
            path.add(step);
            step = previous.get(step);
        }
        Collections.reverse(path);

        return new RouteResult(path, distances.get(targetId), nodesExplored, endTime - startTime);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Dijkstras <path-to-json> <sourceNodeId> <targetNodeId>");
            return;
        }

        com.hydcommute.graph.OverpassGraphLoader loader = new com.hydcommute.graph.OverpassGraphLoader();
        Graph graph = loader.load(args[0]);

        long source = Long.parseLong(args[1]);
        long target = Long.parseLong(args[2]);

        Dijkstras router = new Dijkstras(graph);
        RouteResult result = router.findShortestPath(source, target);

        if (!result.isFound()) {
            System.out.println("No path found between " + source + " and " + target);
            return;
        }

        System.out.printf("Path found: %d nodes, %.1f meters%n", result.path.size(), result.totalDistanceMeters);
        System.out.printf("Nodes explored: %d%n", result.nodesExplored);
        System.out.printf("Time taken: %d ms%n", result.timeTakenMillis);
    }
}