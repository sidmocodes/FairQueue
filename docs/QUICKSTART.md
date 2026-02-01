# Quick Start Guide

## Prerequisites Check

Before starting, ensure you have:
- [ ] Docker installed (version 20.10+)
- [ ] Docker Compose installed (version 2.0+)
- [ ] At least 4GB free RAM
- [ ] Ports 5432, 6379, 8080-8083 available

Check versions:
```bash
docker --version
docker-compose --version
```

## Step-by-Step Startup

### 1. Build and Start Services

From the FairQueue directory:

```bash
# Build all services and start infrastructure
docker-compose up --build -d

# This will take 3-5 minutes on first run
```

### 2. Wait for Services to be Ready

```bash
# Watch logs until all services are up
docker-compose logs -f

# Wait for these messages:
# - "Started AdmissionGatewayApplication"
# - "Started QueueServiceApplication"
# - "Started AdmissionSlotServiceApplication"
# - "Started BookingGateApplication"

# Press Ctrl+C to stop following logs
```

### 3. Verify All Services

```bash
# Check all health endpoints
curl http://localhost:8080/actuator/health  # Should return {"status":"UP"}
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

### 4. Run Test Flow

```bash
# Make test script executable
chmod +x test-flow.sh

# Run end-to-end test
./test-flow.sh
```

Expected output:
```
=========================================
FairQueue End-to-End Test
=========================================

Step 1: Creating a new event...
✓ Event created with ID: ...

Step 2: User joining queue...
✓ Joined queue at position: 1

...

✓ All tests completed successfully!
```

## Troubleshooting

### Services Won't Start

```bash
# Check if ports are in use
lsof -i :8080
lsof -i :8081
lsof -i :8082
lsof -i :8083
lsof -i :5432
lsof -i :6379

# Stop any conflicting processes or change ports in docker-compose.yml
```

### Database Connection Errors

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check logs
docker-compose logs postgres

# Restart PostgreSQL
docker-compose restart postgres
```

### Redis Connection Errors

```bash
# Check Redis is running
docker ps | grep redis

# Test connection
docker exec -it fairqueue-redis redis-cli ping
# Should return: PONG

# Restart Redis
docker-compose restart redis
```

### Service-Specific Issues

```bash
# View specific service logs
docker-compose logs admission-gateway
docker-compose logs queue-service
docker-compose logs admission-slot-service
docker-compose logs booking-gate

# Restart specific service
docker-compose restart admission-gateway
```

### Clean Restart

```bash
# Stop all services
docker-compose down

# Remove volumes (WARNING: deletes all data)
docker-compose down -v

# Rebuild and restart
docker-compose up --build -d
```

## Stopping Services

### Graceful Shutdown
```bash
docker-compose down
```

### Stop Without Removing Containers
```bash
docker-compose stop
```

### Start Stopped Containers
```bash
docker-compose start
```

## Next Steps

Once services are running:

1. **Read the API Reference**
   - See `API_REFERENCE.md` for detailed API documentation
   - Try different API endpoints using curl or Postman

2. **Explore the Architecture**
   - Review `README.md` for system design details
   - Understand data flow and failure handling

3. **Monitor the System**
   - View metrics: http://localhost:8080/actuator/prometheus
   - Check queue depth and booking success rates

4. **Customize Configuration**
   - Edit `docker-compose.yml` to change ports or resources
   - Modify `application.properties` in each service

## Development Mode

To run services locally (not in Docker):

```bash
# Start only infrastructure
docker-compose up postgres redis -d

# Build common module
mvn -pl fairqueue-common clean install

# In separate terminals, run each service:
cd admission-gateway && mvn spring-boot:run
cd queue-service && mvn spring-boot:run
cd admission-slot-service && mvn spring-boot:run
cd booking-gate && mvn spring-boot:run
```

## Useful Commands

### View all running containers
```bash
docker ps
```

### Check resource usage
```bash
docker stats
```

### Access database
```bash
docker exec -it fairqueue-postgres psql -U fairqueue

# Example queries:
SELECT COUNT(*) FROM admission_passes;
SELECT * FROM events;
SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 10;
```

### Access Redis
```bash
docker exec -it fairqueue-redis redis-cli

# Example commands:
KEYS *
ZRANGE queue:* 0 -1
GET ratelimit:*
```

### Clean up disk space
```bash
# Remove stopped containers
docker container prune

# Remove unused images
docker image prune -a

# Remove unused volumes
docker volume prune
```

## Production Deployment Checklist

Before deploying to production:

- [ ] Change `TOKEN_SECRET` to a secure random value
- [ ] Enable HTTPS/TLS
- [ ] Configure proper database backups
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Configure log aggregation
- [ ] Enable Redis persistence (AOF + RDB)
- [ ] Set resource limits in docker-compose
- [ ] Configure proper network security
- [ ] Enable rate limiting at load balancer level
- [ ] Set up health checks in orchestrator (K8s/ECS)

## Support

- **Documentation**: See README.md and API_REFERENCE.md
- **Issues**: Check service logs for error messages
- **Architecture**: Review system design in README.md

---

**You're all set! 🚀**

Start with: `docker-compose up --build -d` and then run `./test-flow.sh`
