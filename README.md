# FairQueue

A production-grade distributed queue management and admission-control service for high-demand ticketing platforms.

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Tests](https://img.shields.io/badge/tests-71%20passing-brightgreen)
![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green)

## Architecture

FairQueue is built as a microservices architecture with four core services:

```
┌─────────────────┐
│   Client App    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│  Admission Gateway      │  ← Rate limiting, Token issuance
│  (Port 8080)           │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│  Queue Service          │  ← Fair ordering, Heartbeat monitoring
│  (Port 8081)           │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│  Admission Slot Service │  ← Pass issuance, Event management
│  (Port 8082)           │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│  Booking Gate           │  ← Pass validation, Exactly-once access
│  (Port 8083)           │
└─────────────────────────┘

        Supporting Infrastructure:
        ┌──────────┐  ┌──────────┐
        │  Redis   │  │ Postgres │
        └──────────┘  └──────────┘
```

### Service Responsibilities

#### 1. Admission Gateway (Port 8080)
- **Entry Point**: First service users interact with
- **Rate Limiting**: Prevents abuse (10 requests/minute per user/event)
- **Token Issuance**: Generates signed JWT queue tokens
- **Technology**: Spring Boot, Redis

**Key APIs:**
- `POST /queue/join` - Join event queue

#### 2. Queue Service (Port 8081)
- **Fair Ordering**: Maintains sorted queues by join timestamp + penalty score
- **Heartbeat Monitoring**: Tracks active users via periodic refreshes
- **Penalization**: Increases wait time for users who don't maintain heartbeat
- **Technology**: Spring Boot, Redis (Sorted Sets)

**Key APIs:**
- `POST /queue/refresh` - Refresh queue position (heartbeat)
- `GET /queue/status` - Get current queue position

**Queue Ordering Algorithm:**
```
score = joinTimestamp (seconds) + (penaltyScore × 3600)
```
- Each penalty adds ~1 hour to effective join time
- Ensures fairness while punishing inactive users

#### 3. Admission Slot Service (Port 8082)
- **Event Management**: Creates and manages events
- **Admission Control**: Issues time-limited admission passes
- **Persistence**: Stores passes in PostgreSQL for durability
- **Technology**: Spring Boot, PostgreSQL, Redis

**Key APIs:**
- `POST /events` - Create new event
- `GET /events/{eventId}` - Get event details
- `POST /admission/claim` - Claim admission pass (requires queue token)

**Admission Pass:**
- Unique UUID
- 5-minute validity window
- One-time use enforced
- Stored in database for audit

#### 4. Booking Gate (Port 8083)
- **Pass Validation**: Verifies admission passes
- **Exactly-Once Enforcement**: Uses Redis atomic operations
- **Audit Logging**: Records all booking attempts in PostgreSQL
- **Technology**: Spring Boot, PostgreSQL, Redis

**Key APIs:**
- `POST /booking/enter` - Enter booking (requires admission pass)

**Exactly-Once Mechanism:**
```java
// Atomic Redis operation
Boolean wasSet = redis.setIfAbsent(passId, userId, 24h);
if (!wasSet) {
    return "Already used";
}
```

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.1
- **In-Memory State**: Redis 7 (queues, rate limiting, pass tracking)
- **Durable State**: PostgreSQL 15 (admission passes, events, audit logs)
- **Observability**: Micrometer + Prometheus
- **Containerization**: Docker + Docker Compose
- **Token Security**: JWT (JJWT library)

## Data Flow

### Complete User Journey

1. **Join Queue**
   ```
   Client → Admission Gateway (rate limit check)
         → Queue Service (add to sorted set)
         ← Queue Token + Position
   ```

2. **Maintain Position** (heartbeat every 30-60s)
   ```
   Client → Queue Service (with queue token)
         ← Updated position
   ```

3. **Claim Admission**
   ```
   Client → Admission Slot Service (with queue token)
         → Validate token
         → Check queue position (optional)
         ← Admission Pass (5min validity)
   ```

4. **Enter Booking**
   ```
   Client → Booking Gate (with admission pass)
         → Validate pass (Redis atomic check)
         → Mark as used
         → Log to audit trail
         ← Success/Failure
   ```

## Failure Handling & Resilience

### 1. Idempotent Token Issuance
- Queue tokens are deterministic based on userId + eventId
- Re-joining queue returns existing position if already queued
- Prevents duplicate queue entries

### 2. Exactly-Once Booking Access
```java
// Redis atomic operation guarantees single use
redis.setIfAbsent(passId, userId) 
```
- Even if service crashes, Redis persists used state
- Retry-safe: repeated attempts with same pass fail gracefully

### 3. Graceful Shutdown
- Spring Boot shutdown hooks
- Drains in-flight requests
- Queue state persists in Redis
- On restart, queues recovered from Redis

### 4. Heartbeat Timeout Handling
- Scheduled monitor checks `lastHeartbeat` timestamps
- Users inactive >2 minutes receive penalty score
- Penalty increases queue position organically

### 5. Database Transactions
```java
@Transactional
public void markPassAsUsed(String passId) {
    // Atomic update with rollback on failure
}
```

### 6. Service Communication Failures
- RestTemplate with retry logic (can be enhanced with Resilience4j)
- Graceful degradation: Gateway can still accept requests even if queue service is temporarily down

## Tradeoffs & Design Decisions

### Why Redis for Queues?
**Pros:**
- Sorted Sets provide O(log N) insertion and position lookup
- Atomic operations for concurrency safety
- Sub-millisecond latency
- Built-in TTL for automatic cleanup

**Cons:**
- Requires persistent Redis (AOF/RDB) for durability
- Memory constraints for very large queues

**Alternative Considered:** PostgreSQL queues
- Rejected due to slower position updates and lock contention

### Why JWT for Tokens?
**Pros:**
- Stateless validation
- Tamper-proof (HMAC signed)
- Contains user context (userId, eventId)

**Cons:**
- Cannot revoke before expiry
- Slight overhead in token size

**Mitigation:** Short token validity (1 hour) limits revocation window

### Why Separate Services?
**Pros:**
- Independent scaling (queue service gets most traffic)
- Fault isolation (gateway failure doesn't affect bookings)
- Clear separation of concerns

**Cons:**
- Network latency between services
- Complexity in deployment

**When to Use Monolith:** For smaller scale (<10K users), could merge into single service

### Why PostgreSQL for Admission Passes?
**Pros:**
- ACID guarantees for financial/audit compliance
- Complex queries for analytics
- Proven durability

**Cons:**
- Slower than Redis for simple lookups

**Hybrid Approach:** Check Redis first (used pass cache), fall back to PostgreSQL

## Observability

### Metrics (Prometheus)
All services expose `/actuator/prometheus`:

**Gateway:**
- `fairqueue.gateway.join.requests` - Total join attempts
- `fairqueue.ratelimit.exceeded` - Rate limit hits

**Queue:**
- `fairqueue.queue.depth` - Current queue size
- `fairqueue.queue.refresh` - Heartbeat count
- `fairqueue.queue.heartbeat.timeout` - Timeout events

**Admission:**
- `fairqueue.admission.pass.issued` - Passes created

**Booking:**
- `fairqueue.booking.attempt` - Booking attempts
- `fairqueue.booking.success` - Successful bookings

### Structured Logging
All logs include:
- **Correlation ID**: Traces request across services
- **Timestamp**: ISO-8601 format
- **Context**: userId, eventId, passId where relevant

Example:
```
2026-01-27 10:15:30 [abc-123-def] [http-nio-8080] INFO  GatewayController - Join queue request: userId=user1, eventId=evt1
```

### Health Checks
- `/actuator/health` - Service health + dependency status
- Database connectivity
- Redis connectivity

## Running Locally

### Prerequisites
- Docker & Docker Compose
- Java 17 (for local development)
- Maven 3.8+ (for local development)

### Quick Start (Docker)

1. **Clone & Navigate**
   ```bash
   cd FairQueue
   ```

2. **Build & Run**
   ```bash
   docker-compose up --build
   ```

   This will:
   - Build all 4 services
   - Start PostgreSQL (port 5432)
   - Start Redis (port 6379)
   - Start all microservices

3. **Verify Services**
   ```bash
   # Check health
   curl http://localhost:8080/actuator/health  # Gateway
   curl http://localhost:8081/actuator/health  # Queue
   curl http://localhost:8082/actuator/health  # Admission
   curl http://localhost:8083/actuator/health  # Booking
   ```

### Local Development (Without Docker)

1. **Start Infrastructure**
   ```bash
   docker-compose up postgres redis
   ```

2. **Build Common Module**
   ```bash
   mvn -pl fairqueue-common clean install
   ```

3. **Run Services** (in separate terminals)
   ```bash
   # Terminal 1
   cd admission-gateway
   mvn spring-boot:run
   
   # Terminal 2
   cd queue-service
   mvn spring-boot:run
   
   # Terminal 3
   cd admission-slot-service
   mvn spring-boot:run
   
   # Terminal 4
   cd booking-gate
   mvn spring-boot:run
   ```

## API Usage Examples

### 1. Create Event
```bash
curl -X POST http://localhost:8082/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Concert 2026",
    "totalCapacity": 10000,
    "admissionRatePerMinute": 100,
    "eventStartTime": "2026-03-01T20:00:00Z",
    "queueOpenTime": "2026-02-01T10:00:00Z"
  }'
```

Response:
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Concert 2026",
  "active": true
}
```

### 2. Join Queue
```bash
curl -X POST http://localhost:8080/queue/join \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "eventId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

Response:
```json
{
  "queueToken": "eyJhbGciOiJIUzI1NiJ9...",
  "position": 42,
  "estimatedWaitTimeSeconds": 2520,
  "message": "Successfully joined queue"
}
```

### 3. Refresh Position (Heartbeat)
```bash
curl -X POST http://localhost:8081/queue/refresh \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

Response:
```json
{
  "position": 38,
  "queueDepth": 1500,
  "estimatedWaitTimeSeconds": 2280,
  "penaltyScore": 0,
  "eligible": false,
  "message": "Queue position updated"
}
```

### 4. Get Queue Status
```bash
curl http://localhost:8081/queue/status \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

### 5. Claim Admission Pass
```bash
curl -X POST http://localhost:8082/admission/claim \
  -H "Content-Type: application/json" \
  -d '{
    "queueToken": "eyJhbGciOiJIUzI1NiJ9..."
  }'
```

Response:
```json
{
  "admissionPass": "7f3e4d5c-2b1a-4f6e-9c8d-1a2b3c4d5e6f",
  "expiresInSeconds": 300,
  "message": "Admission pass issued successfully"
}
```

### 6. Enter Booking
```bash
curl -X POST http://localhost:8083/booking/enter \
  -H "Content-Type: application/json" \
  -d '{
    "admissionPass": "7f3e4d5c-2b1a-4f6e-9c8d-1a2b3c4d5e6f"
  }'
```

Response:
```json
{
  "message": "Booking access granted"
}
```

## Configuration

### Environment Variables

All services support these variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | localhost | Redis host |
| `REDIS_PORT` | 6379 | Redis port |
| `DATABASE_URL` | jdbc:postgresql://localhost:5432/fairqueue | PostgreSQL URL |
| `DATABASE_USERNAME` | fairqueue | Database user |
| `DATABASE_PASSWORD` | fairqueue | Database password |
| `TOKEN_SECRET` | (default key) | **MUST CHANGE IN PRODUCTION** |

**Security Warning:** The default `TOKEN_SECRET` is for development only. Generate a secure key for production:
```bash
openssl rand -base64 64
```

## Testing

### Manual Flow Test

```bash
# 1. Create event
EVENT_RESPONSE=$(curl -s -X POST http://localhost:8082/events \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Event","totalCapacity":100,"admissionRatePerMinute":10,"eventStartTime":"2026-03-01T20:00:00Z","queueOpenTime":"2026-02-01T10:00:00Z"}')

EVENT_ID=$(echo $EVENT_RESPONSE | jq -r '.eventId')
echo "Created event: $EVENT_ID"

# 2. Join queue
JOIN_RESPONSE=$(curl -s -X POST http://localhost:8080/queue/join \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"testuser\",\"eventId\":\"$EVENT_ID\"}")

QUEUE_TOKEN=$(echo $JOIN_RESPONSE | jq -r '.queueToken')
echo "Queue token: $QUEUE_TOKEN"

# 3. Check status
curl -s http://localhost:8081/queue/status \
  -H "Authorization: Bearer $QUEUE_TOKEN" | jq

# 4. Claim admission
ADMISSION_RESPONSE=$(curl -s -X POST http://localhost:8082/admission/claim \
  -H "Content-Type: application/json" \
  -d "{\"queueToken\":\"$QUEUE_TOKEN\"}")

ADMISSION_PASS=$(echo $ADMISSION_RESPONSE | jq -r '.admissionPass')
echo "Admission pass: $ADMISSION_PASS"

# 5. Enter booking
curl -s -X POST http://localhost:8083/booking/enter \
  -H "Content-Type: application/json" \
  -d "{\"admissionPass\":\"$ADMISSION_PASS\"}" | jq
```

## Monitoring & Operations

### View Metrics
```bash
# Gateway metrics
curl http://localhost:8080/actuator/prometheus | grep fairqueue

# Queue metrics
curl http://localhost:8081/actuator/prometheus | grep fairqueue

# Admission metrics
curl http://localhost:8082/actuator/prometheus | grep fairqueue

# Booking metrics
curl http://localhost:8083/actuator/prometheus | grep fairqueue
```

### Database Queries

**Check active passes:**
```sql
SELECT * FROM admission_passes 
WHERE used = false 
AND expires_at > NOW();
```

**Audit trail:**
```sql
SELECT * FROM audit_logs 
WHERE user_id = 'user123' 
ORDER BY timestamp DESC;
```

### Redis Inspection

```bash
# Connect to Redis
docker exec -it fairqueue-redis redis-cli

# View queue
ZRANGE queue:event-id 0 -1 WITHSCORES

# Check rate limit
GET ratelimit:user123:event-id

# Check used passes
KEYS used:pass:*
```

## Production Considerations

### Scaling

**Horizontal Scaling:**
- All services are stateless (except Redis/PostgreSQL)
- Can run multiple instances behind load balancer
- Session affinity not required

**Redis Scaling:**
- Use Redis Cluster for >10GB data
- Enable AOF + RDB persistence
- Consider Redis Sentinel for HA

**PostgreSQL Scaling:**
- Read replicas for analytics
- Connection pooling (HikariCP configured)
- Partition `audit_logs` by date

### Security

1. **Change Token Secret**
   ```bash
   export TOKEN_SECRET=$(openssl rand -base64 64)
   ```

2. **HTTPS/TLS**
   - Add reverse proxy (nginx/Traefik)
   - Enable TLS between services

3. **Network Segmentation**
   - Internal network for service-to-service
   - Expose only gateway to public

4. **Rate Limiting**
   - Currently 10 req/min per user
   - Adjust in `RateLimiterService.java`

### Monitoring

**Recommended Stack:**
- **Metrics**: Prometheus + Grafana
- **Logs**: ELK Stack or Loki
- **Tracing**: Jaeger (add Spring Cloud Sleuth)

### Backup

**PostgreSQL:**
```bash
docker exec fairqueue-postgres pg_dump -U fairqueue fairqueue > backup.sql
```

**Redis:**
```bash
docker exec fairqueue-redis redis-cli BGSAVE
docker cp fairqueue-redis:/data/dump.rdb ./redis-backup.rdb
```

## Future Enhancements

1. **Dynamic Admission Rate**
   - Adjust based on queue depth
   - Machine learning for optimal throughput

2. **Priority Queues**
   - VIP tiers
   - Early bird discounts

3. **Global Distribution**
   - Multi-region Redis
   - CDN for static content

4. **Advanced Fraud Detection**
   - Bot detection
   - Device fingerprinting

## Testing

### Test Coverage
- **Total Tests**: 71 passing
- **FairQueue Common**: 20 tests
- **Admission Gateway**: 13 tests
- **Queue Service**: 12 tests
- **Admission Slot Service**: 17 tests
- **Booking Gate**: 9 tests

### Running Tests

**All tests:**
```bash
mvn clean test
```

**Specific module:**
```bash
cd admission-gateway
mvn test
```

**Test Features:**
- Unit tests with Mockito
- Service-level testing
- Edge case validation
- Concurrent access testing
- Error handling verification

### Build Requirements
- **Java**: 17 (OpenJDK Temurin)
- **Maven**: 3.8+
- **ByteBuddy**: 1.15.11 (for Java 17+ support)

## License

MIT

## Support

For issues or questions, please open a GitHub issue.

---

**Built with ❤️ for fairness and reliability**
