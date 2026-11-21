# RouteX ETA Model
Predicts average route speed (and so ETA) from time of day, day type, distance and location.

## Data
1,799 samples across 300 Hyderabad routes (TomTom Routing API, historical traffic, `routeType=shortest`).

## Run
pip install -r requirements.txt
python collect.py <TOMTOM_KEY> ../Source.json 300
python train.py

## Results (held-out routes)
| | MAE | RMSE |
|---|---|---|
| Constant-speed baseline | 438.8s | 550.3s |
| Gradient boosting | 321.6s | 426.5s |

Split by route to avoid leakage; 5-fold grouped CV used to check overfitting.
Not integrated into the Spring Boot API yet.