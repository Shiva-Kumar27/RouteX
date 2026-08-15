# Hyderabad Commute Route Optimizer

A shortest-path routing engine built on real OpenStreetMap data for Hyderabad, India. Given two locations, it computes the optimal route using either Dijkstra's algorithm or A* search, exposed over a REST API.

This project was built to go deep on graph algorithms, real-world data engineering, and clean software design — not to wrap a tutorial. Every design decision below is one I can defend in a technical interview.

---

## What it does

- Parses a live Overpass API export of Hyderabad's road network into an in-memory graph (**700,614 nodes**, **1,518,327 directed edges**)
- Computes shortest paths between any two nodes using either Dijkstra or A*
- Exposes routing over a REST endpoint, with algorithm selectable per-request
- Reports path, total distance, nodes explored, and time taken — so the two algorithms can be benchmarked head-to-head on the same query

## Why this project

Most portfolio projects reuse toy datasets or wrap someone else's algorithm library. This one:
- Uses **real, messy OSM data** — including oneway streets, disconnected clipped regions, and missing node references — which forces actual data-engineering decisions, not just algorithm implementation
- Implements Dijkstra and A* **from scratch**, not via a library, so the tradeoffs are provable rather than assumed
- Is structured around the **Strategy Pattern**, so the algorithm choice is a runtime parameter, not a hardcoded path — this is the detail most portfolio projects skip and the one most likely to come up in an LLD interview

---

## Architecture

```
com.hydcommute
├── graph
│   └── OverpassGraphLoader.java   # Parses Overpass JSON → in-memory Graph (nodes + adjacency)
├── routing
│   ├── RoutingStrategy.java       # Interface: findShortestPath(source, target) -> RouteResult
│   ├── Dijkstras.java             # Dijkstra's algorithm implementation
│   ├── Astar.java                 # A* implementation (haversine heuristic)
│   └── RouteResult.java           # path, distance, nodesExplored, timeTakenMillis
├── config
│   └── GraphConfig.java           # Loads the graph once at startup, exposes it as a Spring bean
├── web
│   └── RouteController.java       # REST endpoint, depends only on RoutingStrategy
└── RouteOptimizerApplication.java # Spring Boot entry point
```

**Why the Strategy Pattern:** `RouteController` depends only on the `RoutingStrategy` interface, never on `Dijkstras` or `Astar` directly. Adding a third algorithm (e.g. Bidirectional Dijkstra) means writing one new class — the controller, the API contract, and every existing caller stay untouched. This is the concrete example I'd walk through if asked "tell me about a design pattern you've used and why."

---

## Graph construction details

- **Nodes**: every OSM node with lat/lon, keyed by OSM node ID
- **Edges**: built by walking each way's ordered node list and connecting consecutive nodes
  - `oneway=yes` ways produce a single directed edge
  - all other ways produce edges in both directions
  - edge weight = **haversine (great-circle) distance in meters** between endpoints
- **Missing nodes**: ways occasionally reference node IDs not present in a clipped/bounded export. These are skipped safely rather than throwing, since a bounded extract of a city will always have some edge-of-map references.

This means the graph is a **directed graph**, not an undirected one dressed up to look symmetric — oneway streets are modeled correctly, which matters for a routing engine meant to reflect real driving constraints.

---

## Algorithms

### Dijkstra
Classic uniform-cost search — explores nodes in order of accumulated distance from the source, guaranteeing the shortest path with no assumptions about the graph beyond non-negative weights.

### A*
Same guarantee of optimality, but guided by a heuristic: `f(n) = g(n) + h(n)`, where `g(n)` is the actual distance traveled so far and `h(n)` is the **haversine distance from `n` to the target** — a straight-line lower bound on remaining distance.

Haversine is an **admissible heuristic** here because it can never overestimate the true road distance (the straight line between two points is always ≤ the road distance between them). This is what keeps A* both optimal and correct, not just fast.

---

## Benchmark: Dijkstra vs A*

Same source/target pair, same graph, same machine:

| Metric | Dijkstra | A* | Difference |
|---|---|---|---|
| Nodes explored | 240,176 | 50,315 | **79% fewer** |
| Time taken | 397 ms | 106 ms | **~3.7x faster** |
| Path distance | 14,091.98 m | 14,091.98 m | **identical** (correctness check) |

The identical path distance is the important control here — it confirms A* isn't finding a *shorter* or *different* path, it's finding the **same optimal path while examining far fewer nodes**, because the heuristic prunes search directions that can't possibly lead toward the target.

> Note: single-run numbers on one source/target pair. Multiple pairs show the same pattern; A* consistently explores a fraction of the nodes Dijkstra does, with the gap widening as source/target distance increases.

---

## API

### `GET /api/route`

| Param | Type | Required | Description |
|---|---|---|---|
| `source` | long | yes | Source node ID (OSM node ID) |
| `target` | long | yes | Target node ID (OSM node ID) |
| `algorithm` | string | no | `dijkstra` or `astar` (default: `astar`) |

**Example:**
```
GET /api/route?source=245640607&target=289658025&algorithm=astar
```

**Response:**
```json
{
  "algorithm": "A*",
  "path": [245640607, 11140396719, ...],
  "distanceMeters": 14091.98,
  "nodesExplored": 50315,
  "timeTakenMillis": 106
}
```

**Error responses:**
- `400 Bad Request` — unknown `source`/`target` node ID, or invalid `algorithm` value
- `404 Not Found` — no path exists between the given nodes (e.g. disconnected graph regions)

---

## Tech stack

**Implemented:**
- Java 21
- Spring Boot 3.3.2 (REST API)
- Jackson (Overpass JSON parsing)
- Maven

**In progress / roadmap:**
- PostgreSQL + JPA (currently graph is rebuilt from JSON on every startup — no persistence layer yet)
- Docker
- JUnit test coverage for routing correctness
- LRU cache for repeated route queries

This section is kept deliberately honest — nothing here is claimed as done unless it's actually running.

---

## Running it locally

```bash
mvn clean compile
mvn spring-boot:run
```

By default the app loads the graph from the file configured in `application.properties`:
```properties
route-optimizer.graph-file=hyderabad.json
```

Override at launch if needed:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--route-optimizer.graph-file=/path/to/export.json
```

Startup logs the graph load time and size:
```
Parsed 182537 ways into graph.
Graph loaded at startup: 700614 nodes, 1518327 edges, 2882 ms
```

## Data source

Road network data exported via [Overpass API](https://overpass-api.de/) / [Overpass Turbo](https://overpass-turbo.eu/) for the Hyderabad, India metro area, filtered to `highway`-tagged ways.

---

## What's next

- JUnit tests covering: shortest-path correctness against known distances, unreachable-node handling, oneway-street directionality
- Custom exceptions instead of generic error maps
- PostgreSQL persistence so the graph doesn't rebuild from JSON on every restart
- LRU cache for frequently-queried route pairs
- Dockerized deployment
