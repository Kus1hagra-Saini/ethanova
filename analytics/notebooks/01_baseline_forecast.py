# %% [markdown]
# # M9 — Baseline weekly ethanol demand forecast
#
# Loads `gold.fact_weekly_ethanol_demand`, trains two models, evaluates on the
# last 8 weeks per depot held out, and persists results to `gold.forecast_weekly`.
#
# - **seasonal_naive**: per-depot forecast = actual value 52 weeks prior.
# - **ridge**: single global Ridge regressor across all depots, with lag
#   features + week-of-year sin/cos + depot one-hots (per-depot intercepts).

# %%
import sys
from pathlib import Path

sys.path.insert(0, str(Path.cwd().parent / "src"))

import pandas as pd
import matplotlib.pyplot as plt

from ethanova_forecast.forecast import run, load_demand, build_engine

pd.set_option("display.float_format", lambda x: f"{x:,.2f}")

# %% [markdown]
# ## Train + evaluate + persist

# %%
result = run()

# %% [markdown]
# ## Per-depot metrics per model

# %%
result.metrics.pivot_table(
    index="depot_code", columns="model_name",
    values=["mae_kl", "mape_pct", "rmse_kl"]
).round(2)

# %% [markdown]
# ## Overall averaged metrics

# %%
result.metrics.groupby("model_name")[["mae_kl", "mape_pct", "rmse_kl"]].mean().round(2)

# %% [markdown]
# ## Forecast vs actual — per depot

# %%
engine = build_engine()
demand = load_demand(engine)

depots = sorted(demand["depot_code"].unique())
fig, axes = plt.subplots(len(depots), 1, figsize=(11, 3 * len(depots)), sharex=True)
if len(depots) == 1:
    axes = [axes]

for ax, depot in zip(axes, depots):
    hist = demand[demand["depot_code"] == depot]
    ax.plot(hist["week_start_date"], hist["ethanol_required_kl"],
            color="#888", label="history", linewidth=1)
    for model_name, colour in [("seasonal_naive", "tab:blue"), ("ridge", "tab:orange")]:
        pred = result.frame[
            (result.frame["depot_code"] == depot)
            & (result.frame["model_name"] == model_name)
        ].sort_values("week_start_date")
        ax.plot(pred["week_start_date"], pred["forecast_kl"],
                marker="o", markersize=4, label=f"{model_name} forecast",
                color=colour, linewidth=1.5)
    ax.set_title(f"{depot} — last 8 weeks held out")
    ax.set_ylabel("ethanol required (kL)")
    ax.legend(loc="upper left", fontsize=8)

fig.tight_layout()
fig.savefig("forecast_by_depot.png", dpi=120, bbox_inches="tight")
plt.show()
