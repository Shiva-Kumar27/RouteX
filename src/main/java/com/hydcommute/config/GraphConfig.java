package com.hydcommute.config;

import com.hydcommute.graph.OverpassGraphLoader;
import com.hydcommute.graph.OverpassGraphLoader.Graph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Loads the Hyderabad OSM graph exactly once at application startup and
 * exposes it as a singleton Spring bean. Without this, a naive controller
 * would re-parse the ~700K-node JSON export on every single HTTP request,
 * which is both slow (seconds per request) and wasteful.
 *
 * Path to the JSON export is configurable via application.properties:
 *   route-optimizer.graph-file=Source.json
 * or an environment variable / command-line override:
 *   --route-optimizer.graph-file=/path/to/Source.json
 */
@Configuration
public class GraphConfig {

    @Value("${route-optimizer.graph-file:sample-graph.json}")
    private String graphFilePath;

    @Bean
    public Graph graph() throws IOException {
        OverpassGraphLoader loader = new OverpassGraphLoader();
        long start = System.currentTimeMillis();
        Graph graph = loader.load(graphFilePath);
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Graph loaded at startup: %d nodes, %d edges, %d ms%n",
                graph.nodeCount(), graph.edgeCount(), elapsed);
        return graph;
    }
}
