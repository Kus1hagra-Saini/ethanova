\# Ethanova



\*\*An Enterprise Decision Intelligence Platform for India's E20 Biofuel Supply Chain.\*\*



Ethanova unifies procurement, depot inventory, blending, and retail sell-through data across an Oil Marketing Company's ethanol supply chain, and produces auditable, forecast-driven operational recommendations for regional planners.



\---



\## The Hero Decision



> \*Should the Regional Procurement \& Blending Planner order more ethanol from a specific supplier for a specific depot next week?\*



Every capability in this platform justifies itself by contributing to this one decision.



\---



\## Architecture at a Glance



Operational System (Spring Boot)

↓ Incremental CDC

Airflow-Orchestrated Pipeline

↓

Bronze → Silver → Gold (Medallion)

↓

Enterprise Data Warehouse (Star Schema)

↓

Power BI · Forecast \& Anomaly ML · Rule-Based Recommendation Engine

↓

Planner Decision



\---



\## Tech Stack



| Layer | Technology |

|---|---|

| Operational Backend | Spring Boot 3.x, Java 21, PostgreSQL |

| Data Platform | Apache Airflow, Python 3.11, Pandas, SQLAlchemy |

| Warehouse | PostgreSQL (star schema) |

| Business Intelligence | Power BI |

| Machine Learning | scikit-learn, Prophet |

| Orchestration | Docker Compose |

| Version Control | Git + GitHub |



\---



\## Repository Structure



ethanova/

├── backend/ Spring Boot operational system

├── data-platform/ Airflow DAGs and medallion transforms

├── simulator/ Data simulation layer

├── deployment/ Docker Compose and infra scripts

├── docs/ Architecture, ADRs, review artefacts

└── scripts/ Cross-cutting bootstrap scripts



\---



\## Project Status



\*\*Current phase:\*\* Phase 1 — Project Foundation

\*\*Next milestone:\*\* First Review (18–21 August 2026)



See \[`docs/`](./docs) for the Zeroth Review proposal and architecture decisions.



\---



\## Getting Started



Setup instructions will be added as each component is scaffolded. See \[`CONTRIBUTING.md`](./CONTRIBUTING.md) for development workflow (coming in Phase 1).



\---



\## License



MIT — see \[LICENSE](./LICENSE).







