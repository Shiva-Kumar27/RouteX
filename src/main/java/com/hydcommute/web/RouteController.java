package com.hydcommute.web;

import com.hydcommute.graph.OverpassGraphLoader.Graph;
import com.hydcommute.routing.Astar;
import com.hydcommute.routing.Dijkstras;
import com.hydcommute.routing.RouteResult;
import com.hydcommute.routing.RoutingStrategy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes shortest-path routing over HTTP.
 *
 * GET /api/route?source={nodeId}&target={nodeId}&algorithm=dijkstra|astar
 *
 * The controller depends only on the RoutingStrategy interface, not on
 * Dijkstras or Astar directly - the Strategy Pattern is what lets this
 * endpoint stay unchanged if a third algorithm gets added later.
 */
@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final Graph graph;

    public RouteController(Graph graph) {
        this.graph = graph;
    }

    @GetMapping
    public ResponseEntity<?> findRoute(
            @RequestParam long source,
            @RequestParam long target,
            @RequestParam(defaultValue = "astar") String algorithm) {

        if (!graph.nodes.containsKey(source)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unknown source node id: " + source));
        }
        if (!graph.nodes.containsKey(target)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unknown target node id: " + target));
        }

        RoutingStrategy strategy = switch (algorithm.toLowerCase()) {
            case "dijkstra" -> new Dijkstras(graph);
            case "astar" -> new Astar(graph);
            default -> null;
        };

        if (strategy == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unknown algorithm: " + algorithm
                            + " (expected 'dijkstra' or 'astar')"));
        }

        RouteResult result = strategy.findShortestPath(source, target);

        if (!result.isFound()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No path found between " + source + " and " + target));
        }

        return ResponseEntity.ok(Map.of(
                "algorithm", strategy.getName(),
                "path", result.path,
                "distanceMeters", result.totalDistanceMeters,
                "nodesExplored", result.nodesExplored,
                "timeTakenMillis", result.timeTakenMillis
        ));
    }
}