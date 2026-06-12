# 0001 - Architecture Style Selection

## Context
The system is a task management platform deployed on Oracle Cloud Infrastructure. It includes a web application, a Telegram bot, a backend API, authentication, caching, databases, background processing and monitoring.

The architecture needs to support scalability, availability, maintainability and clear separation of responsibilities.

## Decision
We decided to use a combination of architectural styles:

1. **Layered Architecture**  
   This is applied mainly in the backend. The Spring Boot application is organized using controllers, services and repositories. This helps separate presentation logic, business logic and data access.

2. **Client-Server Architecture**  
   This is applied between the React web application and the Spring Boot backend API. The frontend is responsible for the user interface, while the backend manages business rules and persistence.

3. **Event-driven / Asynchronous Processing**  
   This is applied in the Telegram Bot and Background Job Scheduler. Some actions, such as notifications or background reports, do not need to be executed immediately inside the main request flow.

4. **Cloud-native Architecture**  
   This is applied through the use of OCI services such as Load Balancer, monitoring, database infrastructure and scalable application instances.

## Consequences
This decision makes the system easier to maintain because each part has a clear responsibility. It also improves scalability because traffic can be distributed using the load balancer and read operations can be supported by a replica database.

The main disadvantage is that the architecture becomes more complex than a simple monolithic application, since it requires more infrastructure components and deployment configuration.
