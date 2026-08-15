package com.hydcommute.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Parses an Overpass API JSON export (nodes + ways with highway tag)
 * into an in-memory road-network graph:
 *   - nodes: id -> [lat, lon]
 *   - adjacency: id -> List<Edge> (neighbor id + distance-weighted edge)
 *
 * Usage:
 *   OverpassGraphLoader loader = new OverpassGraphLoader();
 *   Graph graph = loader.load("hyderabad.json");
 *   System.out.println(graph.nodeCount() + " nodes, " + graph.edgeCount() + " edges");
 */
public class OverpassGraphLoader {

    /** A directed edge to a neighbor node, weighted by distance in meters. */
    public static class Edge {
        public final long to;
        public final double weightMeters;

        public Edge(long to, double weightMeters) {
            this.to = to;
            this.weightMeters = weightMeters;
        }
    }

    /** The parsed road-network graph. */
    public static class Graph {
        public final Map<Long, double[]> nodes = new HashMap<>();          // id -> [lat, lon]
        public final Map<Long, List<Edge>> adjacency = new HashMap<>();     // id -> outgoing edges

        public int nodeCount() {
            return nodes.size();
        }

        public int edgeCount() {
            return adjacency.values().stream().mapToInt(List::size).sum();
        }

        private void addEdgeBothWays(long a, long b) {
            double[] coordA = nodes.get(a);
            double[] coordB = nodes.get(b);
            if (coordA == null || coordB == null) {
                // Node referenced by a way but missing from the export (can happen
                // with clipped/bounded extracts) - skip this edge safely.
                return;
            }
            double dist = haversineMeters(coordA[0], coordA[1], coordB[0], coordB[1]);
            adjacency.computeIfAbsent(a, k -> new ArrayList<>()).add(new Edge(b, dist));
            adjacency.computeIfAbsent(b, k -> new ArrayList<>()).add(new Edge(a, dist));
        }
    }

    /**
     * Loads a Graph from an Overpass JSON export file.
     */
    public Graph load(String jsonFilePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(jsonFilePath));
        JsonNode elements = root.get("elements");

        Graph graph = new Graph();

        if (elements == null || !elements.isArray()) {
            throw new IOException("No 'elements' array found - is this a valid Overpass JSON export?");
        }

        // First pass: collect all nodes (need coordinates before we can build edges)
        for (JsonNode element : elements) {
            String type = element.path("type").asText();
            if ("node".equals(type)) {
                long id = element.path("id").asLong();
                double lat = element.path("lat").asDouble();
                double lon = element.path("lon").asDouble();
                graph.nodes.put(id, new double[]{lat, lon});
            }
        }

        // Second pass: walk each way's ordered node list and create consecutive edges
        int wayCount = 0;
        for (JsonNode element : elements) {
            String type = element.path("type").asText();
            if ("way".equals(type)) {
                wayCount++;
                JsonNode wayNodes = element.path("nodes");
                boolean oneway = "yes".equals(element.path("tags").path("oneway").asText());

                for (int i = 0; i < wayNodes.size() - 1; i++) {
                    long from = wayNodes.get(i).asLong();
                    long to = wayNodes.get(i + 1).asLong();

                    if (oneway) {
                        double[] coordFrom = graph.nodes.get(from);
                        double[] coordTo = graph.nodes.get(to);
                        if (coordFrom != null && coordTo != null) {
                            double dist = haversineMeters(coordFrom[0], coordFrom[1], coordTo[0], coordTo[1]);
                            graph.adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, dist));
                        }
                    } else {
                        graph.addEdgeBothWays(from, to);
                    }
                }
            }
        }

        System.out.printf("Parsed %d ways into graph.%n", wayCount);
        return graph;
    }

    /**
     * Great-circle distance between two lat/lon points, in meters.
     * Standard haversine formula.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_M = 6_371_000.0;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_M * c;
    }

    /**
     * Quick standalone sanity check - run this after downloading your
     * Hyderabad JSON export to confirm parsing works before wiring it
     * into Spring Boot.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java OverpassGraphLoader <path-to-overpass-export.json>");
            return;
        }

        OverpassGraphLoader loader = new OverpassGraphLoader();
        Graph graph = loader.load(args[0]);

        System.out.println("Nodes: " + graph.nodeCount());
        System.out.println("Edges (directed, both-ways counted separately): " + graph.edgeCount());

        // Print one sample node and its neighbors, just to eyeball correctness
        graph.adjacency.entrySet().stream().findFirst().ifPresent(entry -> {
            long nodeId = entry.getKey();
            double[] coord = graph.nodes.get(nodeId);
            System.out.printf("Sample node %d at (%.5f, %.5f) has %d neighbor(s):%n",
                    nodeId, coord[0], coord[1], entry.getValue().size());
            for (Edge e : entry.getValue()) {
                System.out.printf("  -> node %d, %.1f meters away%n", e.to, e.weightMeters);
            }
        });
    }
}
