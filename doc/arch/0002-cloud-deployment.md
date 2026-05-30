# 0002 - Cloud Deployment on OCI

## Status
Accepted

## Context
The system needs to run in a reliable cloud environment. It must support users accessing the web application, Telegram bot requests, backend services, authentication, database access, caching and monitoring.

The previous component analysis identified OCI infrastructure, a load balancer, application instances, Redis cache, a primary database, a read replica and a health monitor as relevant components.

## Decision
We decided to deploy the system using Oracle Cloud Infrastructure.

The deployment includes:

- OCI Load Balancer to distribute incoming traffic.
- Application instances or containers for the Spring Boot Task API.
- React Web Application as the user-facing frontend.
- Telegram Bot connected to the backend API.
- Oracle Primary Database for persistent data.
- Oracle Read Replica Database for read-only queries.
- Redis Cache to improve response time.
- Health Monitor to verify service availability.

## Consequences
This deployment strategy improves availability and scalability. If one application instance fails, the load balancer can redirect traffic to another healthy instance. The read replica reduces the workload on the primary database, while Redis helps reduce response time for frequently requested data.

The main tradeoff is that the team must manage more cloud components, environment variables, infrastructure configuration and monitoring rules.