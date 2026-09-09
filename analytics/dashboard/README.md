# Ethanova Review 2 dashboard

`ethanova_review2.pbix` — Power BI Desktop report, one page, six visuals.
`ethanova_review2.pdf`  — exported PDF fallback for the deck / offline demo.

## Data source

Direct PostgreSQL connection to `localhost:5432/ethanova` (import mode).
Tables loaded from the `gold` schema:

- `gold.fact_weekly_ethanol_demand`
- `gold.fact_weekly_dispatched_volume`
- `gold.forecast_weekly`
- `gold.recommendation_weekly`
- `gold.dim_depot`
- `gold.dim_supplier`
- `gold.dim_date`

To refresh: open the `.pbix`, click **Refresh** in the ribbon. Power BI
re-reads the gold tables and re-renders every visual.

## Visuals

1. Weekly ethanol demand by depot (104 weeks) — M4 → M8 pipeline output.
2. E20 blend policy ramp (10% → 20%) — proves policy trajectory.
3. Forecast MAPE per depot per model — Ridge 3.1–6.8%, seasonal-naive 27–30%.
4. Forecast vs actual, Ridge model, 8-week holdout, small multiples per depot.
5. Weekly dispatched volume by supplier (stacked area, all 5 real suppliers).
6. Recommended orders — next 8 weeks (M10 rule-based output, ANHYDROUS grade).

## Model

Auto-detected star schema with dims `dim_depot`, `dim_supplier`, `dim_date`
and facts `fact_weekly_ethanol_demand`, `fact_weekly_dispatched_volume`,
plus `forecast_weekly` and `recommendation_weekly`. All many-to-one,
single-direction filters.

## Choice of Power BI

Power BI over Superset per ADR #14 — placement-relevance for the OMC /
consulting pool that hires MCAs in India.
