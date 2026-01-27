# FairQueue API Quick Reference

## Base URLs (Default)
- Admission Gateway: http://localhost:8080
- Queue Service: http://localhost:8081
- Admission Slot Service: http://localhost:8082
- Booking Gate: http://localhost:8083

## API Endpoints

### Events (Admission Slot Service)

#### Create Event
```http
POST /events
Content-Type: application/json

{
  "name": "Concert Name",
  "totalCapacity": 10000,
  "admissionRatePerMinute": 100,
  "eventStartTime": "2026-03-01T20:00:00Z",
  "queueOpenTime": "2026-02-01T10:00:00Z"
}
```

**Response:**
```json
{
  "eventId": "uuid",
  "name": "Concert Name",
  "totalCapacity": 10000,
  "admissionRatePerMinute": 100,
  "active": true
}
```

#### Get Event
```http
GET /events/{eventId}
```

---

### Queue Management (Admission Gateway & Queue Service)

#### Join Queue
```http
POST /queue/join
Content-Type: application/json

{
  "userId": "user123",
  "eventId": "event-uuid"
}
```

**Response:**
```json
{
  "queueToken": "jwt-token",
  "position": 42,
  "estimatedWaitTimeSeconds": 2520,
  "message": "Successfully joined queue"
}
```

**Rate Limit:** 10 requests per minute per user/event

#### Refresh Position (Heartbeat)
```http
POST /queue/refresh
Authorization: Bearer {queueToken}
```

**Response:**
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

**Important:** Call every 30-60 seconds to maintain position

#### Get Queue Status
```http
GET /queue/status
Authorization: Bearer {queueToken}
```

**Response:** Same as refresh

---

### Admission (Admission Slot Service)

#### Claim Admission Pass
```http
POST /admission/claim
Content-Type: application/json

{
  "queueToken": "jwt-token"
}
```

**Response:**
```json
{
  "admissionPass": "pass-uuid",
  "expiresInSeconds": 300,
  "message": "Admission pass issued successfully"
}
```

**Note:** Pass valid for 5 minutes

#### Validate Pass (Internal)
```http
GET /admission/validate/{passId}
```

---

### Booking (Booking Gate)

#### Enter Booking
```http
POST /booking/enter
Content-Type: application/json

{
  "admissionPass": "pass-uuid"
}
```

**Success Response:**
```json
{
  "message": "Booking access granted"
}
```

**Failure Response (403):**
```json
{
  "message": "Booking access denied"
}
```

**Reasons for Denial:**
- Pass already used
- Pass expired
- Invalid pass

---

## Error Codes

| Status Code | Meaning |
|-------------|---------|
| 200 | Success |
| 400 | Bad Request (validation error) |
| 403 | Forbidden (pass denied) |
| 404 | Not Found (entity doesn't exist) |
| 409 | Conflict (duplicate operation) |
| 429 | Too Many Requests (rate limit) |
| 500 | Internal Server Error |

---

## Common Error Response Format

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/queue/join",
  "timestamp": "2026-01-27T10:15:30Z",
  "correlationId": "abc-123-def"
}
```

---

## Health & Metrics

### Health Check
```http
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "redis": { "status": "UP" },
    "db": { "status": "UP" }
  }
}
```

### Prometheus Metrics
```http
GET /actuator/prometheus
```

**Key Metrics:**
- `fairqueue_gateway_join_requests_total` - Total join attempts
- `fairqueue_queue_depth` - Current queue size
- `fairqueue_admission_pass_issued_total` - Passes issued
- `fairqueue_booking_success_total` - Successful bookings
- `fairqueue_ratelimit_exceeded_total` - Rate limit hits

---

## Typical User Flow

1. **Create Event** (Admin)
   ```
   POST /events → eventId
   ```

2. **Join Queue** (User)
   ```
   POST /queue/join → queueToken, position
   ```

3. **Heartbeat Loop** (User - every 30-60s)
   ```
   POST /queue/refresh → updated position
   ```

4. **Claim Admission** (User - when eligible)
   ```
   POST /admission/claim → admissionPass
   ```

5. **Enter Booking** (User - within 5 minutes)
   ```
   POST /booking/enter → success
   ```

---

## Queue Penalty System

**Penalty Score Impact:**
- Each penalty = +3600 seconds to queue score
- Effectively moves user back ~1 hour in queue

**Triggers:**
- Missing heartbeat for >2 minutes
- Excessive refresh attempts

**Formula:**
```
effectiveJoinTime = actualJoinTime + (penaltyScore × 3600)
```

---

## Token Details

### Queue Token (JWT)
**Claims:**
- `sub`: userId
- `eventId`: Event identifier
- `type`: "queue"
- `iat`: Issued at
- `exp`: Expiry (1 hour)

**Header:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Admission Pass
**Format:** UUID v4
**Validity:** 5 minutes
**Use:** Single use only

---

## Database Schema

### admission_passes (PostgreSQL)
```sql
id              BIGSERIAL PRIMARY KEY
pass_id         VARCHAR UNIQUE NOT NULL
user_id         VARCHAR NOT NULL
event_id        VARCHAR NOT NULL
issued_at       TIMESTAMP NOT NULL
expires_at      TIMESTAMP NOT NULL
used            BOOLEAN NOT NULL
used_by         VARCHAR
used_at         TIMESTAMP
```

### events (PostgreSQL)
```sql
id                      BIGSERIAL PRIMARY KEY
event_id                VARCHAR UNIQUE NOT NULL
name                    VARCHAR NOT NULL
total_capacity          INT NOT NULL
admission_rate_per_min  INT NOT NULL
event_start_time        TIMESTAMP NOT NULL
queue_open_time         TIMESTAMP NOT NULL
active                  BOOLEAN NOT NULL
created_at              TIMESTAMP
```

### audit_logs (PostgreSQL)
```sql
id              BIGSERIAL PRIMARY KEY
user_id         VARCHAR NOT NULL
event_id        VARCHAR NOT NULL
action          VARCHAR NOT NULL
timestamp       TIMESTAMP NOT NULL
pass_id         VARCHAR
details         TEXT
correlation_id  VARCHAR
```

---

## Redis Data Structures

### Queue (Sorted Set)
```
Key: queue:{eventId}
Score: joinTimestamp + (penaltyScore × 3600)
Value: userId
```

### Queue Entry (String)
```
Key: qentry:{eventId}:{userId}
Value: JSON serialized QueueEntry
```

### Rate Limit (String)
```
Key: ratelimit:{userId}:{eventId}
Value: request count
TTL: 60 seconds
```

### Used Pass (String)
```
Key: used:pass:{passId}
Value: userId
TTL: 24 hours
```

---

## Environment Configuration

```bash
# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# PostgreSQL
DATABASE_URL=jdbc:postgresql://localhost:5432/fairqueue
DATABASE_USERNAME=fairqueue
DATABASE_PASSWORD=fairqueue

# Security
TOKEN_SECRET=your-secure-secret-key-here

# Service URLs
QUEUE_SERVICE_URL=http://queue-service:8081
ADMISSION_SERVICE_URL=http://admission-slot-service:8082
```

---

## Quick Commands

### Start all services
```bash
docker-compose up --build
```

### Check service health
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

### View logs
```bash
docker-compose logs -f admission-gateway
docker-compose logs -f queue-service
docker-compose logs -f admission-slot-service
docker-compose logs -f booking-gate
```

### Access Redis
```bash
docker exec -it fairqueue-redis redis-cli
```

### Access PostgreSQL
```bash
docker exec -it fairqueue-postgres psql -U fairqueue
```

### Run test script
```bash
chmod +x test-flow.sh
./test-flow.sh
```
