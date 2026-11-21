"""Usage: python collect.py <TOMTOM_KEY> <overpass.json> <num_routes>
Samples random node pairs from the OSM graph and records traffic-aware travel times
at several departure times (historical traffic via departAt)."""
import csv, json, random, sys, time
from datetime import datetime, timedelta
from math import asin, cos, radians, sin, sqrt

import requests

KEY, GRAPH, N = sys.argv[1], sys.argv[2], int(sys.argv[3])
TIMES_PER_ROUTE = 6


def haversine(a, b):
    la1, lo1, la2, lo2 = map(radians, (*a, *b))
    h = sin((la2 - la1) / 2) ** 2 + cos(la1) * cos(la2) * sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * asin(sqrt(h))


nodes = [(e["lat"], e["lon"]) for e in json.load(open(GRAPH, encoding="utf-8"))["elements"] if e["type"] == "node"]
base = (datetime.now() + timedelta(days=1)).replace(minute=0, second=0, microsecond=0)

with open("samples.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["route_id", "o_lat", "o_lon", "d_lat", "d_lon", "straight_m",
                "hour", "dow", "length_m", "travel_s"])
    rid = 0
    while rid < N:
        o, d = random.sample(nodes, 2)
        straight = haversine(o, d)
        if not 2000 <= straight <= 15000:
            continue
        for _ in range(TIMES_PER_ROUTE):
            t = base + timedelta(days=random.randint(0, 6), hours=random.randint(0, 23))
            try:
                r = requests.get(
                    f"https://api.tomtom.com/routing/1/calculateRoute/{o[0]},{o[1]}:{d[0]},{d[1]}/json",
                    params={"key": KEY, "traffic": "true", "routeType": "shortest",
                            "departAt": t.strftime("%Y-%m-%dT%H:%M:%S+05:30")},
                    timeout=20)
                s = r.json()["routes"][0]["summary"]
            except Exception as e:
                print("skip:", e)
                continue
            w.writerow([rid, *o, *d, round(straight), t.hour, t.weekday(),
                        s["lengthInMeters"], s["travelTimeInSeconds"]])
            time.sleep(0.3)
        rid += 1