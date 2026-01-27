# Liquibase Database Migration Guide

## Overview

FairQueue uses Liquibase for database schema version control and migrations. This provides:
- **Version Control**: Track all database changes in Git
- **Rollback Support**: Revert problematic changes
- **Environment Consistency**: Same schema across dev, test, prod
- **Audit Trail**: Complete history of all schema changes

## Structure

### Admission Slot Service

```
admission-slot-service/src/main/resources/
└── db/
    └── changelog/
        ├── db.changelog-master.yaml          # Master changelog
        └── changes/
            ├── 001-create-events-table.yaml
            └── 002-create-admission-passes-table.yaml
```

**Tables Created:**
- `events` - Event metadata and configuration
- `admission_passes` - Issued admission passes

### Booking Gate Service

```
booking-gate/src/main/resources/
└── db/
    └── changelog/
        ├── db.changelog-master.yaml          # Master changelog
        └── changes/
            └── 001-create-audit-logs-table.yaml
```

**Tables Created:**
- `audit_logs` - Complete audit trail of all booking attempts

## Migration Files

### 001-create-events-table.yaml

Creates the `events` table with:
- Primary key: `id` (BIGSERIAL)
- Unique constraint: `event_id`
- Indexes:
  - `idx_events_event_id` on `event_id`
  - `idx_events_active` on `active`

**Columns:**
- `id` - Auto-incrementing primary key
- `event_id` - Unique event identifier (UUID)
- `name` - Event name
- `total_capacity` - Maximum attendees
- `admission_rate_per_minute` - Admission throttle rate
- `event_start_time` - When event begins
- `queue_open_time` - When queue opens
- `active` - Whether event is currently active
- `created_at` - Audit timestamp

### 002-create-admission-passes-table.yaml

Creates the `admission_passes` table with:
- Primary key: `id` (BIGSERIAL)
- Unique constraint: `pass_id`
- Indexes:
  - `idx_pass_id` on `pass_id` (unique)
  - `idx_user_event` on `user_id, event_id` (composite)
  - `idx_expires_at` on `expires_at`
  - `idx_admission_passes_used` on `used`

**Columns:**
- `id` - Auto-incrementing primary key
- `pass_id` - Unique pass identifier (UUID)
- `user_id` - User who owns the pass
- `event_id` - Associated event
- `issued_at` - When pass was created
- `expires_at` - When pass expires (5 minutes)
- `used` - Whether pass has been consumed
- `used_by` - Service/user that used the pass
- `used_at` - When pass was used

### 001-create-audit-logs-table.yaml

Creates the `audit_logs` table with:
- Primary key: `id` (BIGSERIAL)
- Indexes:
  - `idx_user_id` on `user_id`
  - `idx_event_id` on `event_id`
  - `idx_timestamp` on `timestamp`
  - `idx_audit_logs_pass_id` on `pass_id`
  - `idx_audit_logs_action` on `action`

**Columns:**
- `id` - Auto-incrementing primary key
- `user_id` - User performing action
- `event_id` - Related event
- `action` - Type of action (e.g., BOOKING_APPROVED)
- `timestamp` - When action occurred
- `pass_id` - Associated admission pass
- `details` - Additional context (TEXT)
- `correlation_id` - Request tracing ID

## How Liquibase Works

### Startup Process

1. **Service Starts**: Spring Boot application initializes
2. **Liquibase Runs**: Before application context loads
3. **Check DATABASECHANGELOG**: Liquibase checks which changesets ran
4. **Apply Pending**: Runs any new changesets
5. **Record**: Updates DATABASECHANGELOG table
6. **Application Starts**: Services begin accepting requests

### DATABASECHANGELOG Table

Liquibase automatically creates this tracking table:

```sql
CREATE TABLE databasechangelog (
  id VARCHAR(255) NOT NULL,
  author VARCHAR(255) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  dateexecuted TIMESTAMP NOT NULL,
  orderexecuted INTEGER NOT NULL,
  exectype VARCHAR(10) NOT NULL,
  md5sum VARCHAR(35),
  description VARCHAR(255),
  comments VARCHAR(255),
  tag VARCHAR(255),
  liquibase VARCHAR(20)
);
```

### DATABASECHANGELOGLOCK Table

Prevents concurrent migrations:

```sql
CREATE TABLE databasechangeloglock (
  id INTEGER NOT NULL,
  locked BOOLEAN NOT NULL,
  lockgranted TIMESTAMP,
  lockedby VARCHAR(255),
  CONSTRAINT pk_databasechangeloglock PRIMARY KEY (id)
);
```

## Configuration

### application.properties

**Admission Slot Service:**
```properties
# Hibernate validation only (no auto-DDL)
spring.jpa.hibernate.ddl-auto=validate

# Liquibase enabled
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
spring.liquibase.enabled=true
```

**Booking Gate:**
```properties
# Same configuration
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
spring.liquibase.enabled=true
```

## Adding New Migrations

### Step 1: Create New Changelog File

```bash
# Example: Adding a new column
cd admission-slot-service/src/main/resources/db/changelog/changes/
touch 003-add-event-description.yaml
```

### Step 2: Define the Change

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-add-event-description
      author: your-name
      changes:
        - addColumn:
            tableName: events
            columns:
              - column:
                  name: description
                  type: TEXT
      rollback:
        - dropColumn:
            tableName: events
            columnName: description
```

### Step 3: Update Master Changelog

Edit `db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-events-table.yaml
  - include:
      file: db/changelog/changes/002-create-admission-passes-table.yaml
  - include:
      file: db/changelog/changes/003-add-event-description.yaml  # New
```

### Step 4: Restart Service

```bash
docker-compose restart admission-slot-service
```

Liquibase will automatically apply the new changeset.

## Common Operations

### Add Column

```yaml
- addColumn:
    tableName: events
    columns:
      - column:
          name: new_column
          type: VARCHAR(255)
          constraints:
            nullable: true
```

### Create Index

```yaml
- createIndex:
    indexName: idx_events_new_column
    tableName: events
    columns:
      - column:
          name: new_column
```

### Add Foreign Key

```yaml
- addForeignKeyConstraint:
    baseTableName: admission_passes
    baseColumnNames: event_id
    constraintName: fk_admission_passes_events
    referencedTableName: events
    referencedColumnNames: event_id
```

### Rename Column

```yaml
- renameColumn:
    tableName: events
    oldColumnName: old_name
    newColumnName: new_name
    columnDataType: VARCHAR(255)
```

### Modify Column Type

```yaml
- modifyDataType:
    tableName: events
    columnName: name
    newDataType: VARCHAR(500)
```

### Drop Table (with caution!)

```yaml
- dropTable:
    tableName: old_table
```

## Rollback Support

Every changeset includes a rollback section:

```yaml
rollback:
  - dropTable:
      tableName: events
```

### Manual Rollback

```bash
# Rollback last changeset
liquibase rollback-count 1

# Rollback to specific tag
liquibase rollback <tag-name>

# Rollback to specific date
liquibase rollback-to-date 2026-01-01
```

## Best Practices

### 1. Never Modify Existing Changesets
❌ **Wrong:**
```yaml
# Editing 001-create-events-table.yaml after deployment
- addColumn:  # Don't add to existing changeset!
```

✅ **Correct:**
```yaml
# Create new changeset: 004-add-column-to-events.yaml
- addColumn:
```

### 2. Use Descriptive IDs
❌ **Wrong:**
```yaml
id: change1
```

✅ **Correct:**
```yaml
id: 003-add-event-description-column
```

### 3. Always Include Rollback
❌ **Wrong:**
```yaml
changes:
  - addColumn: ...
# No rollback!
```

✅ **Correct:**
```yaml
changes:
  - addColumn: ...
rollback:
  - dropColumn: ...
```

### 4. Sequential Numbering
```
001-create-events-table.yaml
002-create-admission-passes-table.yaml
003-add-event-description.yaml
004-add-pass-metadata.yaml
```

### 5. Use YAML Format
- More readable than XML/SQL
- Better Git diffs
- Easier to review in PRs

### 6. One Logical Change Per Changeset
❌ **Wrong:**
```yaml
# One changeset doing too much
- createTable: events
- createTable: passes
- createTable: logs
```

✅ **Correct:**
```yaml
# Separate changesets
# 001-create-events.yaml
# 002-create-passes.yaml
# 003-create-logs.yaml
```

## Troubleshooting

### Issue: Checksum Validation Failed

**Problem:** Modified existing changeset after it ran

**Error:**
```
Validation Failed:
  1 changesets check sum
```

**Solution:**
```bash
# Clear checksums (dangerous!)
liquibase clear-checksums

# Better: Revert change and create new changeset
```

### Issue: Lock Not Released

**Problem:** Previous migration crashed

**Error:**
```
Waiting for changelog lock...
```

**Solution:**
```sql
-- Manually unlock
UPDATE databasechangeloglock SET locked=FALSE;
```

### Issue: Migration Failed Mid-Run

**Problem:** Syntax error in changeset

**Solution:**
1. Fix the changeset file
2. Check what was applied:
   ```sql
   SELECT * FROM databasechangelog ORDER BY dateexecuted DESC;
   ```
3. Manually rollback if needed
4. Restart service

## Verification

### Check Applied Migrations

```sql
SELECT id, author, filename, dateexecuted 
FROM databasechangelog 
ORDER BY orderexecuted;
```

### Check Current Schema

```sql
-- List all tables
\dt

-- Describe specific table
\d events
\d admission_passes
\d audit_logs

-- List indexes
\di
```

### Verify Service Health

```bash
# Check if tables exist
docker exec -it fairqueue-postgres psql -U fairqueue -c "\dt"

# Check service health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Environment-Specific Migrations

### Production Considerations

1. **Test First**: Always test migrations in staging
2. **Backup**: Take database backup before migrating
3. **Read Replicas**: Stop replication during migration
4. **Downtime**: Plan for brief downtime if needed
5. **Monitoring**: Watch logs during migration

### Production Migration Checklist

- [ ] Tested in dev environment
- [ ] Tested in staging environment
- [ ] Database backup taken
- [ ] Rollback plan documented
- [ ] Team notified
- [ ] Monitoring ready
- [ ] Run migration
- [ ] Verify schema
- [ ] Verify application works
- [ ] Monitor for errors

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: Run Liquibase Migrations
  run: |
    docker-compose up -d postgres
    docker-compose up --no-start admission-slot-service
    docker-compose run admission-slot-service \
      mvn liquibase:update
```

### Pre-Deployment Validation

```bash
# Validate changelog syntax
mvn liquibase:validate

# Generate SQL without applying
mvn liquibase:updateSQL

# Review generated SQL before deployment
```

## Migration Logs

Liquibase logs are integrated with Spring Boot logging:

```
2026-01-27 10:15:30 [liquibase] INFO: Running Changeset: db/changelog/changes/001-create-events-table.yaml::001-create-events-table::fairqueue
2026-01-27 10:15:31 [liquibase] INFO: Table events created
2026-01-27 10:15:31 [liquibase] INFO: Changeset db/changelog/changes/001-create-events-table.yaml::001-create-events-table::fairqueue ran successfully
```

---

## Quick Reference

| Task | Command/Action |
|------|----------------|
| View applied migrations | `SELECT * FROM databasechangelog;` |
| Check lock status | `SELECT * FROM databasechangeloglock;` |
| Clear lock | `UPDATE databasechangeloglock SET locked=FALSE;` |
| Validate changelog | `mvn liquibase:validate` |
| Generate SQL | `mvn liquibase:updateSQL` |
| Force checksum update | `mvn liquibase:clearChecksums` |

---

**Migration files are now production-ready!** 🚀

All database tables will be created automatically by Liquibase when services start.
