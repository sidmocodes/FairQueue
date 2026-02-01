# FairQueue - Quick Reference Card

## 🚀 Quick Start Commands

### Build & Test
```bash
# Set Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Build everything (skip tests)
mvn clean install -DskipTests

# Build with tests
mvn clean install

# Run tests only
mvn test -pl fairqueue-common,admission-gateway
```

### Start System
```bash
# Start all services
docker-compose up --build -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Check Health
```bash
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Queue
curl http://localhost:8082/actuator/health  # Admission
curl http://localhost:8083/actuator/health  # Booking
```

---

## 📊 Service Ports

| Service | Port | Description |
|---------|------|-------------|
| admission-gateway | 8080 | Entry point, rate limiting |
| queue-service | 8081 | Queue management |
| admission-slot-service | 8082 | Event & pass management |
| booking-gate | 8083 | Booking validation |
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache & queue store |

---

## 🔗 API Endpoints

### Admission Gateway (8080)
```bash
# Join queue
POST /queue/join
Body: {
  "userId": "user123",
  "eventId": "event456"
}
```

### Queue Service (8081)
```bash
# Refresh position (heartbeat)
POST /queue/refresh
Headers: Authorization: Bearer {queue_token}

# Get status
GET /queue/status
Headers: Authorization: Bearer {queue_token}
```

### Admission Slot Service (8082)
```bash
# Create event
POST /events
Body: {
  "name": "Concert 2026",
  "description": "Amazing show",
  "totalSlots": 1000,
  "startTime": "2026-06-01T19:00:00"
}

# Get event
GET /events/{eventId}

# Claim admission pass
POST /admission/claim
Headers: Authorization: Bearer {queue_token}
```

### Booking Gate (8083)
```bash
# Enter booking
POST /booking/enter
Headers: Authorization: Bearer {admission_pass}
Body: {
  "eventId": "event456",
  "bookingData": {
    "seatNumbers": ["A1", "A2"]
  }
}
```

---

## 🧪 Testing

### Unit Tests
```bash
# Run all tests
mvn test

# Run specific module
mvn test -pl fairqueue-common

# Run specific test class
mvn test -Dtest=TokenServiceTest
```

### Test Results
- fairqueue-common: **20/20 passing** ✅
- admission-gateway: **13/13 passing** ✅
- Total: **33/33 passing** ✅

---

## 📁 Project Structure

```
FairQueue/
├── fairqueue-common/          # Shared library (JWT, filters, DTOs)
├── admission-gateway/         # Rate limiting, token issuance
├── queue-service/             # Queue management, heartbeats
├── admission-slot-service/    # Events, admission passes
├── booking-gate/              # Booking validation, audit
├── docker-compose.yml         # Infrastructure setup
└── docs/
    ├── README.md
    ├── QUICKSTART.md
    ├── API_REFERENCE.md
    ├── ARCHITECTURE.md
    └── TESTING.md
```

---

## 🔧 Common Tasks

### Add New Dependency
1. Edit `pom.xml` in specific module
2. Run `mvn clean install`

### Database Migration
```bash
# Migrations are in:
admission-slot-service/src/main/resources/db/changelog/
booking-gate/src/main/resources/db/changelog/

# Applied automatically on startup
```

### View Logs
```bash
# Single service
docker-compose logs admission-gateway

# All services
docker-compose logs

# Follow logs
docker-compose logs -f queue-service
```

### Restart Service
```bash
# Restart single service
docker-compose restart admission-gateway

# Rebuild and restart
docker-compose up --build -d admission-gateway
```

---

## 🐛 Troubleshooting

### Port Already in Use
```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>
```

### Java Version Issues
```bash
# Check Java version
java -version

# Should be Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Docker Issues
```bash
# Remove all containers and volumes
docker-compose down -v

# Clean rebuild
docker-compose build --no-cache
docker-compose up -d
```

### Redis Connection Issues
```bash
# Connect to Redis
docker exec -it fairqueue-redis redis-cli

# Check keys
KEYS *

# Clear all
FLUSHALL
```

### Database Issues
```bash
# Connect to PostgreSQL
docker exec -it fairqueue-postgres psql -U fairqueue

# List tables
\dt

# View schema
\d admission_passes
```

---

## 📈 Monitoring

### Metrics Endpoints
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info
```

### Key Metrics
- `fairqueue.gateway.join.requests` - Join attempts
- `fairqueue.ratelimit.exceeded` - Rate limit hits
- `fairqueue.queue.depth` - Queue size
- `fairqueue.heartbeat.timeout` - Timeout count

---

## 🔐 Environment Variables

### Required for Production
```bash
# JWT Secret (256-bit minimum)
TOKEN_SECRET=your-super-secret-key-here-at-least-32-characters-long

# Database
DATABASE_URL=jdbc:postgresql://postgres:5432/fairqueue
DATABASE_USERNAME=fairqueue
DATABASE_PASSWORD=secure-password-here

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=optional-redis-password
```

---

## 📝 Development Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/your-feature
   ```

2. **Make Changes**
   - Edit code
   - Write tests
   - Update documentation

3. **Test Locally**
   ```bash
   mvn clean test
   docker-compose up --build
   ```

4. **Commit & Push**
   ```bash
   git add .
   git commit -m "Add feature: description"
   git push origin feature/your-feature
   ```

5. **Create Pull Request**

---

## 🎯 Best Practices

### Code
- ✅ Use Java 17
- ✅ Write tests for new features
- ✅ Follow existing patterns
- ✅ Use meaningful variable names
- ✅ Add JavaDoc for public methods

### Git
- ✅ Descriptive commit messages
- ✅ Small, focused commits
- ✅ Keep branches up-to-date
- ✅ Review before merging

### Testing
- ✅ Run tests before commit
- ✅ Test happy path + errors
- ✅ Use Arrange-Act-Assert pattern
- ✅ Mock external dependencies

### Documentation
- ✅ Update README for new features
- ✅ Document API changes
- ✅ Add examples
- ✅ Keep docs current

---

## 📞 Support

- **Issues**: https://github.com/sidmocodes/FairQueue/issues
- **Documentation**: See `/docs` folder
- **Questions**: Open a discussion on GitHub

---

**Version**: 1.0.0  
**Last Updated**: January 30, 2026  
**Maintained by**: @sidmocodes
