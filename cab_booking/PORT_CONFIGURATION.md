# Port Configuration Reference

## Architecture Tiers

### TIER 1: Communication Layer (Gateways)
**Entry points for Mobile Apps (Customers & Drivers)**

| Service | Port | Purpose |
|---------|------|---------|
| API Gateway | 8080 | Main REST API entry point for all clients |
| WebSocket Gateway | 8089 | Real-time GPS & communication channel |

---

### TIER 2: Core Services Layer
**Essential business logic services**

| Service | Port | Database | Purpose |
|---------|------|----------|---------|
| Auth Service | 8081 | PostgreSQL | Authentication & JWT tokens |
| User Service | 8082 | PostgreSQL | Customer user management |
| Driver Service | 8083 | PostgreSQL | Driver profile management |
| Booking Service | 8084 | PostgreSQL | Ride booking management |
| Ride Service | 8085 | PostgreSQL | Ride execution & tracking |

---

### TIER 3: Support & AI Services
**Supporting services and AI/ML features**

| Service | Port | Tech Stack | Purpose |
|---------|------|-----------|---------|
| Matching Service | 8086 | Java/Spring | Ride matching algorithm |
| ETA Service | 8087 | Java/Spring | Estimated Time of Arrival |
| Pricing Service | 8088 | Java/Spring | Dynamic pricing calculation |
| Payment Service | 8090 | Java/Spring | Payment processing |
| Review Service | 8091 | Java/Spring | Ride reviews & ratings |
| Notification Service | 8092 | Java/Spring | Email, SMS, push notifications |
| AI Scoring Service | 8000 | Python/FastAPI | ML model scoring |

---

## Infrastructure Services

| Service | Port | Purpose |
|---------|------|---------|
| Eureka Server | 8761 | Service discovery & registration |
| Config Server | 8888 | Centralized configuration |
| Kafka | 9092 | Message broker |
| Redis | 6379 | Caching & session storage |
| MongoDB | 27017 | NoSQL database (Notifications) |

---

## Database Ports

| Database | Container Port | Host Port | Database Name |
|----------|---|---|---|
| Auth DB | 5432 | 5433 | auth_db |
| User DB | 5432 | 5434 | user_db |
| Driver DB | 5432 | 5435 | driver_db |
| Booking DB | 5432 | 5436 | booking_db |
| Ride DB | 5432 | 5437 | ride_db |
| Payment DB | 5432 | 5438 | payment_db |

---

## Environment Variable Mapping

All ports are configured in `.env` file with the following naming convention:

- `{SERVICE_NAME}_SERVICE_PORT` - Service application port
- `{SERVICE_NAME}_DB_JDBC_URL` - Database connection string
- `{SERVICE_NAME}_DB_NAME` - Database name
- `{SERVICE_NAME}_DB_USER` - Database user
- `{SERVICE_NAME}_DB_PASSWORD` - Database password

---

## Communication Flow

```
Mobile App (Customer/Driver)
    ↓
API Gateway (8080)
    ├→ Auth Service (8081)
    ├→ User Service (8082)
    ├→ Driver Service (8083)
    ├→ Booking Service (8084)
    ├→ Ride Service (8085)
    ├→ Matching Service (8086)
    ├→ ETA Service (8087)
    ├→ Pricing Service (8088)
    ├→ Payment Service (8090)
    ├→ Review Service (8091)
    └→ Notification Service (8092)

Real-time Updates
    ↓
WebSocket Gateway (8089)
    ├→ Ride Service (8085) - GPS tracking
    ├→ Notification Service (8092)
    └→ AI Scoring Service (8000)

All Services
    ↓
Service Discovery: Eureka (8761)
Configuration: Config Server (8888)
Messaging: Kafka (9092)
Cache: Redis (6379)
```

---

## Notes

- **API Gateway (8080)**: This is the ONLY port that mobile apps should call
- **WebSocket Gateway (8089)**: Dedicated for real-time GPS updates and live tracking
- **AI Scoring Service (8000)**: Python/FastAPI service, kept separate from Java services
- All internal service-to-service communication happens within the Docker network
- Database ports are mapped to different host ports to avoid conflicts when running locally
