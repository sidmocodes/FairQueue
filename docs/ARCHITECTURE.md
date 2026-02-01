# FairQueue Architecture Diagrams

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                               │
│                    (Web App, Mobile App, etc.)                      │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      ADMISSION GATEWAY                              │
│                         Port 8080                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │ Rate Limiter │  │ Token Issuer │  │ Request Fwd  │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       QUEUE SERVICE                                 │
│                         Port 8081                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │Queue Manager │  │   Heartbeat  │  │  Position    │             │
│  │(Sorted Sets) │  │   Monitor    │  │  Tracker     │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  ADMISSION SLOT SERVICE                             │
│                         Port 8082                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │ Event Mgmt   │  │Pass Issuance │  │Pass Validator│             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       BOOKING GATE                                  │
│                         Port 8083                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │Pass Validator│  │ Exactly-Once │  │Audit Logger  │             │
│  │              │  │  Enforcer    │  │              │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└─────────────────────────────────────────────────────────────────────┘

┌────────────────┐          ┌────────────────┐
│     REDIS      │          │   POSTGRESQL   │
│   Port 6379    │          │   Port 5432    │
│                │          │                │
│  - Queues      │          │ - Events       │
│  - Rate Limits │          │ - Passes       │
│  - Pass Cache  │          │ - Audit Logs   │
└────────────────┘          └────────────────┘
```

## Data Flow: Complete User Journey

```
┌──────┐
│Client│
└──┬───┘
   │
   │ 1. POST /queue/join
   │    {userId, eventId}
   ▼
┌──────────────────┐
│ Admission Gateway│
│                  │
│ ┌──────────────┐ │
│ │Rate Limiter  │ │◄── Redis: ratelimit:{userId}:{eventId}
│ │Check: 10/min │ │
│ └──────┬───────┘ │
│        │         │
│ ┌──────▼───────┐ │
│ │Token Service │ │
│ │Generate JWT  │ │
│ └──────┬───────┘ │
│        │         │
└────────┼─────────┘
         │ 2. Forward to Queue Service
         │    {userId, eventId, queueToken}
         ▼
┌──────────────────┐
│  Queue Service   │
│                  │
│ ┌──────────────┐ │
│ │Queue Manager │ │
│ │              │ │──► Redis ZADD queue:{eventId}
│ │              │ │    score = timestamp + (penalty × 3600)
│ │              │ │    member = userId
│ └──────┬───────┘ │
│        │         │──► Redis SET qentry:{eventId}:{userId}
│        │         │    value = QueueEntry JSON
│        │         │
└────────┼─────────┘
         │ 3. Returns: {queueToken, position, waitTime}
         ▼
┌──────┐
│Client│ Stores queueToken
└──┬───┘
   │
   │ 4. Loop every 30-60s: POST /queue/refresh
   │    Authorization: Bearer {queueToken}
   ▼
┌──────────────────┐
│  Queue Service   │
│                  │
│ ┌──────────────┐ │
│ │Update        │ │──► Redis: Update lastHeartbeat
│ │Heartbeat     │ │
│ └──────┬───────┘ │
│        │         │
│ ┌──────▼───────┐ │
│ │Get Position  │ │◄── Redis ZRANK queue:{eventId}
│ └──────────────┘ │
└────────┼─────────┘
         │ 5. Returns: {position, queueDepth, penalty}
         ▼
┌──────┐
│Client│ When position ≤ threshold
└──┬───┘
   │
   │ 6. POST /admission/claim
   │    {queueToken}
   ▼
┌────────────────────┐
│Admission Slot Svc  │
│                    │
│ ┌────────────────┐ │
│ │Validate Token  │ │
│ │(JWT signature) │ │
│ └────┬───────────┘ │
│      │             │
│ ┌────▼───────────┐ │
│ │Check Existing  │ │◄── PostgreSQL: SELECT * FROM admission_passes
│ │Pass            │ │    WHERE userId = ? AND eventId = ?
│ └────┬───────────┘ │
│      │             │
│ ┌────▼───────────┐ │
│ │Generate Pass   │ │
│ │UUID, 5min TTL  │ │
│ └────┬───────────┘ │
│      │             │
│      │             │──► PostgreSQL: INSERT INTO admission_passes
│      │             │    (passId, userId, eventId, issuedAt, expiresAt)
└──────┼─────────────┘
       │ 7. Returns: {admissionPass, expiresInSeconds}
       ▼
┌──────┐
│Client│ Stores admissionPass
└──┬───┘
   │
   │ 8. POST /booking/enter
   │    {admissionPass}
   ▼
┌──────────────────┐
│  Booking Gate    │
│                  │
│ ┌──────────────┐ │
│ │Atomic Check  │ │──► Redis: SETNX used:pass:{passId}
│ │If pass used  │ │    TTL: 24h
│ └──────┬───────┘ │
│        │ Not used│
│ ┌──────▼───────┐ │
│ │Call Admission│ │──► HTTP GET /admission/validate/{passId}
│ │Service       │ │
│ └──────┬───────┘ │
│        │ Valid   │
│ ┌──────▼───────┐ │
│ │Log to Audit  │ │──► PostgreSQL: INSERT INTO audit_logs
│ └──────────────┘ │    (userId, eventId, action, passId)
└────────┼─────────┘
         │ 9. Returns: {message: "Booking access granted"}
         ▼
┌──────┐
│Client│ Proceeds to actual booking
└──────┘
```

## Queue Ordering Mechanism

```
Queue Entry Structure:
┌─────────────────────────────────────┐
│ userId: "user123"                   │
│ eventId: "event-abc"                │
│ joinTimestamp: 1706356800           │  (Unix seconds)
│ lastHeartbeat: 1706357100           │
│ penaltyScore: 2                     │
└─────────────────────────────────────┘
                  │
                  ▼
        Calculate Score:
        score = 1706356800 + (2 × 3600)
              = 1706356800 + 7200
              = 1706364000

        Redis Sorted Set:
┌─────────────────────────────────────┐
│ queue:event-abc                     │
├─────────────┬───────────────────────┤
│ Score       │ Member (userId)       │
├─────────────┼───────────────────────┤
│ 1706356500  │ user789 (no penalty)  │ ← Position 1
│ 1706356800  │ user123 (2 penalties) │ ← Position 2
│ 1706357000  │ user456 (0 penalty)   │ ← Position 3
└─────────────┴───────────────────────┘

Note: user123 joined before user456, but penalties moved them down
```

## Exactly-Once Booking Mechanism

```
Concurrent Requests for Same Pass:

Thread A                           Thread B
   │                                  │
   │ POST /booking/enter             │ POST /booking/enter
   │ passId: "abc-123"               │ passId: "abc-123"
   │                                  │
   ▼                                  ▼
┌──────────────────────────────────────────────┐
│              Redis                            │
│                                               │
│  SETNX used:pass:abc-123 = "userA"          │
│                                               │
│  Thread A: Success! (returns TRUE)          │◄── Thread A wins
│  Thread B: Already set (returns FALSE)       │◄── Thread B loses
└──────────────────────────────────────────────┘
   │                                  │
   ▼                                  ▼
Thread A proceeds            Thread B rejected
to validate pass             with "Already used"
```

## Service Communication Patterns

```
Synchronous REST Calls:
┌─────────────┐                 ┌─────────────┐
│  Gateway    │────POST────────►│   Queue     │
└─────────────┘   /internal     └─────────────┘
                  /queue/add

┌─────────────┐                 ┌─────────────┐
│Booking Gate │────GET─────────►│  Admission  │
└─────────────┘   /validate     └─────────────┘

All inter-service calls use:
- HTTP/REST
- JSON payloads
- Service discovery via environment variables
- Retry logic (can be enhanced with Resilience4j)
```

## Database Schema Relationships

```
PostgreSQL Tables:

┌──────────────────┐
│     events       │
├──────────────────┤
│ id (PK)          │
│ event_id (UNIQUE)│◄─────┐
│ name             │      │
│ total_capacity   │      │
│ active           │      │
└──────────────────┘      │
                          │
                    Foreign Key
                          │
┌──────────────────┐      │
│admission_passes  │      │
├──────────────────┤      │
│ id (PK)          │      │
│ pass_id (UNIQUE) │      │
│ user_id          │      │
│ event_id         │──────┘
│ issued_at        │
│ expires_at       │
│ used             │
│ used_at          │
└──────────────────┘
        │
        │ Referenced by
        │
┌──────────────────┐
│   audit_logs     │
├──────────────────┤
│ id (PK)          │
│ user_id          │
│ event_id         │
│ action           │
│ pass_id          │◄──────┘
│ timestamp        │
│ correlation_id   │
└──────────────────┘
```

## Observability Flow

```
Request Flow with Tracing:

Client Request
     │
     ├─► [Correlation ID: abc-123] Generated
     │
     ▼
┌──────────────────┐
│  Gateway Service │ Log: [abc-123] Join request received
└────────┬─────────┘
         │
         │ HTTP Header: X-Correlation-ID: abc-123
         ▼
┌──────────────────┐
│  Queue Service   │ Log: [abc-123] Adding to queue
└────────┬─────────┘
         │
         ▼
    Response with correlation ID in error/success

Metrics Collection:
┌──────────────────┐
│   Each Service   │
│                  │
│ /actuator/       │──► Prometheus
│ prometheus       │    Scrapes every 15s
└──────────────────┘
         │
         ▼
┌──────────────────┐
│   Prometheus     │
│   (Time Series)  │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│    Grafana       │ Dashboards
│  (Visualization) │ - Queue depth over time
└──────────────────┘ - Booking success rate
                     - P95/P99 latencies
```

## Failure Scenarios & Handling

```
Scenario 1: Redis Failure
┌──────────────────┐
│  Queue Service   │
│                  │ Redis Down!
│  Try connect     │──X──► Redis
│                  │
│  Exception       │
│  caught          │
│                  │
│  Return HTTP 503 │──► Client: Service Unavailable
└──────────────────┘

Recovery: Redis restart → Queues restored from persistence


Scenario 2: Database Failure
┌──────────────────┐
│ Admission Svc    │
│                  │ DB Down!
│  INSERT pass     │──X──► PostgreSQL
│                  │
│  Transaction     │
│  rollback        │
│                  │
│  Return HTTP 503 │──► Client: Service Unavailable
└──────────────────┘

Recovery: DB restart → Retry INSERT (idempotent with passId UUID)


Scenario 3: Service Crash During Booking
┌──────────────────┐
│  Booking Gate    │
│                  │
│  SETNX pass ✓    │──► Redis: Pass marked used
│                  │
│  Validate... ✓   │
│                  │
│  CRASH! 💥       │
└──────────────────┘

Result: Pass correctly marked as used in Redis
        Audit log may be missing (non-critical)
        Client retries → Gets "Already used" (correct!)
```

## Scaling Architecture

```
Horizontal Scaling:

         Load Balancer
               │
    ┌──────────┼──────────┐
    │          │          │
    ▼          ▼          ▼
┌─────┐    ┌─────┐    ┌─────┐
│ Gw1 │    │ Gw2 │    │ Gw3 │  Admission Gateway (stateless)
└─────┘    └─────┘    └─────┘
    │          │          │
    └──────────┼──────────┘
               │
    ┌──────────┼──────────┐
    │          │          │
    ▼          ▼          ▼
┌─────┐    ┌─────┐    ┌─────┐
│ Qs1 │    │ Qs2 │    │ Qs3 │  Queue Service (stateless)
└─────┘    └─────┘    └─────┘
               │
               ▼
         Redis Cluster
       ┌────────────────┐
       │  Master        │
       │  Replica 1     │
       │  Replica 2     │
       └────────────────┘

PostgreSQL Read Replicas:
┌─────────────┐
│   Primary   │──┬─► Replica 1 (reads)
└─────────────┘  └─► Replica 2 (reads)
```
