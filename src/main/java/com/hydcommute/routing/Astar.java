package com.hydcommute.routing;

import com.hydcommute.graph.OverpassGraphLoader;
import com.hydcommute.graph.OverpassGraphLoader.Edge;
import com.hydcommute.graph.OverpassGraphLoader.Graph;

import java.util.*;

public class Astar implements RoutingStrategy {

    private final Graph graph;

    public Astar(Graph graph) {
        this.graph = graph;
    }

    @Override
    public String getName() {
        return "A*";
    }

    @Override
    public RouteResult findShortestPath(long sourceId, long targetId) {
        long startTime = System.currentTimeMillis();
        long nodesExplored = 0;

        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        record QueueEntry(long nodeId, double fScore) {}
        PriorityQueue<QueueEntry> pq = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::fScore)
        );

        gScore.put(sourceId, 0.0);
        pq.add(new QueueEntry(sourceId, heuristic(sourceId, targetId)));

        while (!pq.isEmpty()) {
            QueueEntry current = pq.poll();
            long currentId = current.nodeId();

            if (visited.contains(currentId)) continue;
            visited.add(currentId);
            nodesExplored++;

            if (currentId == targetId) break;

            double currentG = gScore.get(currentId);

            for (Edge edge : graph.adjacency.getOrDefault(currentId, List.of())) {
                if (visited.contains(edge.to)) continue;

                double newG = currentG + edge.weightMeters;
                double knownG = gScore.getOrDefault(edge.to, Double.POSITIVE_INFINITY);

                if (newG < knownG) {
                    gScore.put(edge.to, newG);
                    previous.put(edge.to, currentId);

                    double f = newG + heuristic(edge.to, targetId);
                    pq.add(new QueueEntry(edge.to, f));
                }
            }
        }

        long endTime = System.currentTimeMillis();

        if (!gScore.containsKey(targetId)) {
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

        return new RouteResult(path, gScore.get(targetId), nodesExplored, endTime - startTime);
    }

    private double heuristic(long fromId, long toId) {
        double[] from = graph.nodes.get(fromId);
        double[] to = graph.nodes.get(toId);
        return OverpassGraphLoader.haversineMeters(from[0], from[1], to[0], to[1]);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Astar <path-to-json> <sourceNodeId> <targetNodeId>");
            return;
        }

        OverpassGraphLoader loader = new OverpassGraphLoader();
        Graph graph = loader.load(args[0]);

        long source = Long.parseLong(args[1]);
        long target = Long.parseLong(args[2]);

        Astar router = new Astar(graph);
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