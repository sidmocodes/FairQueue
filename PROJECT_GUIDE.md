# FairQueue Project Structure

## 📁 Directory Layout

```
FairQueue/
├── README.md                          # Main documentation
├── QUICKSTART.md                      # Getting started guide
├── API_REFERENCE.md                   # API documentation
├── ARCHITECTURE.md                    # System architecture diagrams
├── docker-compose.yml                 # Docker orchestration
├── test-flow.sh                       # End-to-end test script
├── .gitignore                         # Git ignore rules
├── pom.xml                            # Parent Maven POM
│
├── fairqueue-common/                  # Shared library module
│   ├── pom.xml
│   └── src/main/java/com/fairqueue/common/
│       ├── model/                     # Domain models
│       │   ├── QueueEntry.java
│       │   ├── Event.java
│       │   └── AdmissionPass.java
│       ├── dto/                       # Data Transfer Objects
│       │   ├── JoinQueueRequest.java
│       │   ├── JoinQueueResponse.java
│       │   ├── QueueStatusResponse.java
│       │   ├── CreateEventRequest.java
│       │   ├── AdmissionClaimRequest.java
│       │   ├── AdmissionClaimResponse.java
│       │   └── BookingEnterRequest.java
│       ├── util/                      # Utilities
│       │   └── TokenService.java      # JWT token generation/validation
│       ├── exception/                 # Exception handling
│       │   ├── ErrorResponse.java
│       │   └── GlobalExceptionHandler.java
│       ├── filter/                    # Request filters
│       │   └── CorrelationIdFilter.java
│       └── config/                    # Configuration
│           └── CommonConfig.java
│
├── admission-gateway/                 # Service 1: Admission Gateway
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/fairqueue/gateway/
│       │   ├── AdmissionGatewayApplication.java
│       │   ├── controller/
│       │   │   └── GatewayController.java
│       │   ├── service/
│       │   │   ├── GatewayService.java
│       │   │   └── RateLimiterService.java
│       │   └── config/
│       │       └── GatewayConfig.java
│       └── resources/
│           └── application.properties
│
├── queue-service/                     # Service 2: Queue Service
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/fairqueue/queue/
│       │   ├── QueueServiceApplication.java
│       │   ├── controller/
│       │   │   ├── QueueController.java
│       │   │   └── InternalQueueController.java
│       │   ├── service/
│       │   │   ├── QueueManager.java
│       │   │   └── HeartbeatMonitor.java
│       │   └── config/
│       │       └── QueueConfig.java
│       └── resources/
│           └── application.properties
│
├── admission-slot-service/            # Service 3: Admission Slot Service
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/fairqueue/admission/
│       │   ├── AdmissionSlotServiceApplication.java
│       │   ├── controller/
│       │   │   ├── EventController.java
│       │   │   └── AdmissionController.java
│       │   ├── service/
│       │   │   ├── EventService.java
│       │   │   ├── AdmissionService.java
│       │   │   └── PassCleanupService.java
│       │   ├── entity/
│       │   │   ├── EventEntity.java
│       │   │   └── AdmissionPassEntity.java
│       │   ├── repository/
│       │   │   ├── EventRepository.java
│       │   │   └── AdmissionPassRepository.java
│       │   └── config/
│       │       └── AdmissionConfig.java
│       └── resources/
│           └── application.properties
│
└── booking-gate/                      # Service 4: Booking Gate
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── java/com/fairqueue/booking/
        │   ├── BookingGateApplication.java
        │   ├── controller/
        │   │   └── BookingController.java
        │   ├── service/
        │   │   ├── BookingGateService.java
        │   │   └── AuditService.java
        │   ├── entity/
        │   │   └── AuditLog.java
        │   ├── repository/
        │   │   └── AuditLogRepository.java
        │   ├── dto/
        │   │   └── ValidatePassResponse.java
        │   └── config/
        │       └── BookingConfig.java
        └── resources/
            └── application.properties
```

## 📚 Documentation Guide

| Document | Purpose | When to Read |
|----------|---------|--------------|
| **README.md** | Comprehensive overview, architecture, tradeoffs | First read - understanding the system |
| **QUICKSTART.md** | Step-by-step startup instructions | When setting up for the first time |
| **API_REFERENCE.md** | Complete API documentation | When using the APIs |
| **ARCHITECTURE.md** | Detailed architecture diagrams | When understanding data flows |

## 🚀 Quick Commands

### First Time Setup
```bash
# 1. Start everything
docker-compose up --build -d

# 2. Wait for services (check logs)
docker-compose logs -f

# 3. Test the system
./test-flow.sh
```

### Daily Development
```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f [service-name]

# Rebuild after changes
docker-compose up --build [service-name]
```

### Debugging
```bash
# Check service health
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/prometheus

# Access database
docker exec -it fairqueue-postgres psql -U fairqueue

# Access Redis
docker exec -it fairqueue-redis redis-cli
```

## 🔧 Key Technologies Used

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Language** | Java 17 | Modern LTS version |
| **Framework** | Spring Boot 3.2.1 | Microservices framework |
| **Build Tool** | Maven | Dependency management |
| **In-Memory Store** | Redis 7 | Queues, caching, rate limiting |
| **Database** | PostgreSQL 15 | Persistent storage |
| **Token Security** | JWT (JJWT) | Signed authentication tokens |
| **Metrics** | Micrometer + Prometheus | Observability |
| **Containerization** | Docker | Service isolation |
| **Orchestration** | Docker Compose | Local development |

## 📊 Service Port Map

| Service | Port | Purpose |
|---------|------|---------|
| Admission Gateway | 8080 | Public entry point |
| Queue Service | 8081 | Queue management |
| Admission Slot Service | 8082 | Pass issuance, events |
| Booking Gate | 8083 | Final booking access |
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache/Queue store |

## 🔐 Environment Variables

Required for production (defaults provided for dev):

```bash
# Security (MUST CHANGE)
TOKEN_SECRET=<generate-secure-random-key>

# Database
DATABASE_URL=jdbc:postgresql://host:5432/fairqueue
DATABASE_USERNAME=fairqueue
DATABASE_PASSWORD=<secure-password>

# Redis
REDIS_HOST=redis-host
REDIS_PORT=6379

# Service URLs (for inter-service communication)
QUEUE_SERVICE_URL=http://queue-service:8081
ADMISSION_SERVICE_URL=http://admission-slot-service:8082
```

## 🧪 Testing Strategy

### Manual Testing
```bash
# Run provided test script
./test-flow.sh
```

### API Testing
```bash
# Use examples from API_REFERENCE.md
curl -X POST http://localhost:8082/events -H "Content-Type: application/json" -d '...'
```

### Load Testing (Future)
- Use tools like JMeter, Gatling, or k6
- Focus on queue join/refresh endpoints
- Test rate limiting behavior

## 🎯 Key Features Implemented

✅ **Rate Limiting** - 10 requests/minute per user/event  
✅ **Fair Queue Ordering** - Timestamp + penalty score  
✅ **Heartbeat Monitoring** - Automatic penalization  
✅ **JWT Token Security** - Signed, time-limited tokens  
✅ **Exactly-Once Booking** - Redis atomic operations  
✅ **Audit Logging** - PostgreSQL persistent logs  
✅ **Graceful Shutdown** - Proper cleanup on stop  
✅ **Health Checks** - All services expose /actuator/health  
✅ **Prometheus Metrics** - Built-in observability  
✅ **Correlation IDs** - Request tracing across services  

## 🔄 Data Persistence Strategy

| Data Type | Storage | Persistence | TTL |
|-----------|---------|-------------|-----|
| Queue Entries | Redis | AOF/RDB | Until event ends |
| Rate Limit Counters | Redis | Volatile | 60 seconds |
| Used Pass Cache | Redis | Volatile | 24 hours |
| Events | PostgreSQL | Permanent | - |
| Admission Passes | PostgreSQL | Permanent | - |
| Audit Logs | PostgreSQL | Permanent | - |

## 🚨 Common Issues & Solutions

### Build Failures
```bash
# Clean and rebuild
mvn clean install -pl fairqueue-common
mvn clean package
```

### Port Conflicts
```bash
# Find process using port
lsof -i :8080

# Kill process or change port in docker-compose.yml
```

### Service Won't Connect to DB/Redis
```bash
# Check if containers are running
docker ps

# Restart specific service
docker-compose restart postgres
docker-compose restart redis
```

### Clean Slate Needed
```bash
# WARNING: Deletes all data
docker-compose down -v
docker-compose up --build -d
```

## 📈 Scalability Considerations

**Vertical Scaling:**
- Increase JVM heap: `-Xmx2g -Xms2g`
- More CPU cores for concurrent requests
- Larger Redis instance for bigger queues

**Horizontal Scaling:**
- All services are stateless (except Redis/PostgreSQL)
- Deploy multiple instances behind load balancer
- Use Redis Cluster for distributed queue
- PostgreSQL read replicas for analytics

**Current Limits:**
- Queue Service: ~10K concurrent users per instance
- Admission Gateway: Limited by rate limiter (Redis)
- Booking Gate: DB write throughput dependent

## 🎓 Learning Resources

To understand the codebase:

1. **Start with README.md** - Get the big picture
2. **Review ARCHITECTURE.md** - Understand data flows
3. **Read fairqueue-common/** - See shared models
4. **Follow a request in ARCHITECTURE.md** - Trace end-to-end
5. **Read service code in order:**
   - admission-gateway
   - queue-service
   - admission-slot-service
   - booking-gate

## 🤝 Contributing

When making changes:

1. Build common module first:
   ```bash
   mvn -pl fairqueue-common clean install
   ```

2. Test locally before Docker:
   ```bash
   mvn spring-boot:run
   ```

3. Update documentation if changing APIs

4. Test with `./test-flow.sh` before committing

## 📝 Code Structure Conventions

**Package Structure:**
```
com.fairqueue.[service-name]/
├── [ServiceName]Application.java      # Spring Boot main class
├── controller/                         # REST endpoints
├── service/                            # Business logic
├── repository/                         # Data access (if applicable)
├── entity/                             # JPA entities (if applicable)
├── dto/                                # DTOs (mostly in common/)
└── config/                             # Spring configuration
```

**Naming Conventions:**
- Controllers: `*Controller.java`
- Services: `*Service.java`
- Repositories: `*Repository.java`
- DTOs: `*Request.java`, `*Response.java`
- Entities: `*Entity.java`

## 🎯 Next Steps

After getting familiar with the system:

1. **Run the test script** - See it in action
2. **Try API calls manually** - Using curl or Postman
3. **Explore metrics** - Check Prometheus endpoints
4. **Review logs** - See correlation IDs in action
5. **Experiment with scale** - Try high concurrent requests
6. **Customize** - Adjust rate limits, queue ordering, etc.

---

**Ready to start?** Run: `docker-compose up --build -d` then `./test-flow.sh`

**Need help?** Check the documentation files or service logs!
