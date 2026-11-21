"""Predicts average speed (m/s) for a route; ETA = route length / predicted speed."""
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.model_selection import GroupKFold, GroupShuffleSplit, cross_val_score

df = pd.read_csv("samples.csv")
df["speed"] = df.length_m / df.travel_s
df["h_sin"] = np.sin(2 * np.pi * df.hour / 24)
df["h_cos"] = np.cos(2 * np.pi * df.hour / 24)
df["weekend"] = (df.dow >= 5).astype(int)

FEATURES = ["h_sin", "h_cos", "weekend", "length_m", "straight_m", "o_lat", "o_lon", "d_lat", "d_lon"]
X, y = df[FEATURES], df.speed

# split by route so the same route never appears in both train and test
tr, te = next(GroupShuffleSplit(test_size=0.2, random_state=42).split(X, y, groups=df.route_id))
model = GradientBoostingRegressor(n_estimators=200, max_depth=3, learning_rate=0.05, subsample=0.8, random_state=42)
model.fit(X.iloc[tr], y.iloc[tr])


def report(name, idx, speed_pred):
    true = df.travel_s.iloc[idx]
    pred = df.length_m.iloc[idx] / speed_pred
    print(f"{name:<24} MAE {mean_absolute_error(true, pred):7.1f}s  "
          f"RMSE {np.sqrt(mean_squared_error(true, pred)):7.1f}s")


const_speed = df.length_m.iloc[tr].sum() / df.travel_s.iloc[tr].sum()
report("baseline (const speed)", te, np.full(len(te), const_speed))
report("model (test)", te, model.predict(X.iloc[te]))
report("model (train)", tr, model.predict(X.iloc[tr]))  # large train/test gap = overfitting

cv = cross_val_score(model, X, y, groups=df.route_id, cv=GroupKFold(5), scoring="neg_mean_absolute_error")
print(f"5-fold grouped CV MAE (m/s): {-cv.mean():.3f} +/- {cv.std():.3f}")

joblib.dump(model, "eta_model.joblib")