# RouteX

A shortest-path routing engine over real OpenStreetMap data for Hyderabad, India, with a machine-learning model that estimates travel time from time of day. Routing is exposed over a Spring Boot REST API using either Dijkstra's algorithm or A*.

## Highlights

- Parses an Overpass API export of Hyderabad's road network into an in-memory directed graph (700,614 nodes, 1,518,327 directed edges)
- Dijkstra and A* implemented from scratch, swappable per request via the Strategy Pattern
- A* with a haversine heuristic explores 79% fewer nodes and runs ~3.7x faster than Dijkstra, with identical path cost
- Python ML pipeline (`ml/`) that cuts ETA error by 27% against a constant-speed baseline on held-out routes

## Architecture

```
route-optimizer
├── src/main/java/com/hydcommute
│   ├── graph
│   │   └── OverpassGraphLoader.java   # Overpass JSON -> in-memory Graph (nodes + adjacency)
│   ├── routing
│   │   ├── RoutingStrategy.java       # Interface: findShortestPath(source, target) -> RouteResult
│   │   ├── Dijkstras.java
│   │   ├── Astar.java                 # haversine heuristic
│   │   ├── RouteResult.java           # path, distance, nodesExplored, timeTakenMillis
│   │   └── BenchMark.java             # side-by-side algorithm comparison
│   ├── config
│   │   └── GraphConfig.java           # loads the graph once at startup as a Spring bean
│   ├── web
│   │   └── RouteController.java       # REST endpoint, depends only on RoutingStrategy
│   └── RouteOptimizerApplication.java
└── ml
    ├── collect.py                     # builds the training set from the TomTom Routing API
    └── train.py                       # trains and evaluates the ETA model
```

**Why the Strategy Pattern:** `RouteController` depends only on the `RoutingStrategy` interface. Adding a third algorithm (e.g. bidirectional Dijkstra) means writing one new class; the controller and API contract stay unchanged.

## Graph construction

- **Nodes:** every OSM node with lat/lon, keyed by OSM node ID
- **Edges:** consecutive nodes along each way are connected
  - `oneway=yes` produces a single directed edge; all other ways produce edges in both directions
  - Edge weight is the haversine distance in meters
- **Missing nodes:** ways that reference nodes absent from a clipped export are skipped instead of throwing

## Algorithms

**Dijkstra:** uniform-cost search; explores nodes in order of distance from the source and guarantees the shortest path for non-negative weights.

**A\*:** uses `f(n) = g(n) + h(n)`, where `h(n)` is the haversine distance to the target. Haversine never overestimates the road distance, so it is admissible and A* stays optimal.

### Benchmark

Same source/target pair, same graph, same machine (single run):

| Metric | Dijkstra | A* | Difference |
|---|---|---|---|
| Nodes explored | 240,176 | 50,315 | 79% fewer |
| Time taken | 397 ms | 106 ms | ~3.7x faster |
| Path distance | 14,091.98 m | 14,091.98 m | identical |

The identical distance confirms A* finds the same optimal path while examining far fewer nodes.

## ETA prediction model (`ml/`)

Distance-only routing assumes a constant speed. This component learns how average speed varies with the time of day, day type, route length and location.

**Data:** 1,799 samples across 300 Hyderabad routes. Each route's origin and destination are random graph nodes 2-15 km apart, queried at 6 random hour/day combinations through the TomTom Routing API (`routeType=shortest`, historical traffic via `departAt`).

**Model:** gradient boosting regressor predicting average speed (m/s); ETA = route length / predicted speed.

**Features:** cyclic hour encoding (sin/cos), weekend flag, route length, straight-line distance, origin and destination coordinates.

**Evaluation:** train/test split grouped by route, so the same route never appears in both sets, plus 5-fold grouped cross-validation.

| Held-out routes | MAE | RMSE |
|---|---|---|
| Constant-speed baseline | 438.8 s | 550.3 s |
| Gradient boosting | 321.6 s | 426.5 s |

MAE improves by about 27%, RMSE by about 22%.

**Limitations**
- Train MAE (173.6 s) is well below test MAE (321.6 s), so the model overfits somewhat given the small dataset
- Labels come from historical traffic patterns, not live traffic
- An average error of about 5 minutes is still large; more data and more features (road class, speed limits) are the obvious next steps
- The model is not yet integrated into the Spring Boot API

**Run it**

```
cd ml
pip install -r requirements.txt
python collect.py <TOMTOM_API_KEY> ../Source.json 300
python train.py
```

`samples.csv` and `eta_model.joblib` are generated locally and not committed.

## API

### `GET /api/route`

| Param | Type | Required | Description |
|---|---|---|---|
| `source` | long | yes | Source OSM node ID |
| `target` | long | yes | Target OSM node ID |
| `algorithm` | string | no | `dijkstra` or `astar` (default `astar`) |

```
GET /api/route?source=245640607&target=289658025&algorithm=astar
```

```json
{
  "algorithm": "A*",
  "path": [245640607, 11140396719, ...],
  "distanceMeters": 14091.98,
  "nodesExplored": 50315,
  "timeTakenMillis": 106
}
```

**Errors:** `400` for an unknown node ID or invalid algorithm; `404` when no path exists (e.g. disconnected regions).

## Tech stack

- Java 21, Spring Boot 3.3.2, Jackson, Maven
- Python, Pandas, NumPy, scikit-learn
- Data: OpenStreetMap via Overpass API; TomTom Routing API for travel-time labels

## Running the API locally

```
mvn clean compile
mvn spring-boot:run
```

The graph file is set in `application.properties`:

```
route-optimizer.graph-file=Source.json
```

Override at launch:

```
mvn spring-boot:run -Dspring-boot.run.arguments=--route-optimizer.graph-file=/path/to/export.json
```

Startup logs the graph size and load time:

```
Parsed 182537 ways into graph.
Graph loaded at startup: 700614 nodes, 1518327 edges, 2882 ms
```

## Data source

Road network exported via Overpass API / Overpass Turbo for the Hyderabad metro area, filtered to highway-tagged ways.

## Roadmap

- Serve the ETA model to the REST API (Flask inference service + `etaSeconds` in the response)
- Use road class and `maxspeed` tags as model features
- JUnit tests: shortest-path correctness, unreachable nodes, oneway directionality
- Custom exceptions instead of generic error maps
- PostgreSQL persistence so the graph is not rebuilt on every restart
- LRU cache for repeated route queries; Docker deployment
