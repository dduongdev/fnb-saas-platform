# FNB SaaS Platform - GCP Deployment Analysis & Configuration

**Date:** April 6, 2026  
**Project:** fnb-saas-platform (Food & Beverage SaaS)  
**Status:** Production-ready multi-tenant POS platform

---

## 1. PROJECT COMPLEXITY ANALYSIS

### 1.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (React + Vite)                   │
│  ├─ React 19.2 with Router v7                               │
│  ├─ Real-time: STOMP/WebSocket + SockJS                     │
│  ├─ Auth: Keycloak v26.2                                    │
│  └─ UI: Lucide React, Framer Motion, Charts (Recharts)      │
└─────────────────────────────────────────────────────────────┘
                            ↕ (REST + WebSocket)
┌─────────────────────────────────────────────────────────────┐
│              BACKEND (Spring Boot 3.5.8 / Java 21)           │
│  ├─ Core: Spring Data JPA, OAuth2, Validation               │
│  ├─ Real-time: WebSocket (STOMP)                            │
│  ├─ Modules (5):                                            │
│  │  ├─ Global (Auth, Users, Tenants, AccessKeys)            │
│  │  ├─ Menu (Categories, Products, Images)                  │
│  │  ├─ POS (Sessions, Tables, Orders, Items)                │
│  │  ├─ Payment (VNPay, MOMO integration)                    │
│  │  └─ Reporting (Daily stats, analytics)                   │
│  ├─ Database: MySQL 8.0 (Multi-tenant + soft delete)        │
│  ├─ Storage: MinIO (Object storage for images)              │
│  └─ Load Testing: k6 with 4 concurrent scenarios            │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 System Complexity Metrics

| Metric | Value | Category |
|--------|-------|----------|
| **Source Files** | ~50 Java + 30 React | Medium |
| **Database Entities** | 14 JPA entities | Medium |
| **API Endpoints** | ~60+ RESTful endpoints | Medium-High |
| **Concurrent Modules** | 5 functional modules | Medium |
| **Real-time Features** | WebSocket + STOMP + k6 testing | High |
| **External Integrations** | VNPay, MOMO, Keycloak | High |
| **Multi-tenancy** | Full isolation per tenant | High |
| **Data Volume** | Sessions, Orders, Notifications (transactional + high-frequency reads) | Medium-High |

### 1.3 Functional Complexity

#### A. **Global Module** (Authentication & Tenant Management)
- **Entities:** User, Tenant, AccessKey
- **Complexity:** Medium
- **Concerns:**
  - JWT-based OAuth2 with Keycloak
  - Multi-tenant isolation via tenant_id filtering
  - API key rotation & access control
- **Scale:** ~1000s users, 100s tenants

#### B. **Menu Module** (Catalog Management)
- **Entities:** Category, Product, ProductImage
- **Complexity:** Low-Medium
- **Concerns:**
  - Soft delete with cascading
  - Tenant-scoped visibility
  - Image storage via MinIO
- **Scale:** 100s-1000s products per tenant

#### C. **POS Module** (Core Business Logic)
- **Entities:** ServingSession, DiningTable, Order, OrderItem
- **Complexity:** **HIGH** ⚠️
- **Concerns:**
  - **Session state machine:** PENDING → ACTIVE → COMPLETED (with validation)
  - **Table availability tracking** (AVAILABLE/OCCUPIED/RESERVED/SERVING)
  - **Order lifecycle coupling** with session state
  - **Table pool separation** (30% QR/customer vs 70% ops/staff)
  - **Load test stress:** 0.65% error rate at SCALE=0.7
  - **Real-time notifications** via WebSocket for session changes
- **Scale:** 1000+ concurrent sessions, 100+ tables per tenant
- **Critical Path:** Stress test results show 97% customer order success (after fix)

#### D. **Payment Module** (Financial Processing)
- **Entities:** PaymentTransaction
- **Complexity:** High
- **Concerns:**
  - Third-party gateway integration (VNPay, MOMO)
  - Transaction state management (PENDING/SUCCESS/FAILED)
  - IPN callback handling & reconciliation
  - PCI-DSS compliance (payment data handling)
- **Scale:** 100s-1000s transactions/day

#### E. **Reporting Module** (Analytics & Auditing)
- **Entities:** DailyStat, PosActionAudit
- **Complexity:** Medium
- **Concerns:**
  - Time-range aggregations (hourly/daily/monthly)
  - GROUP BY + JOIN operations on large date ranges
  - Audit trail immutability
- **Scale:** Years of historical data

### 1.4 Database Complexity

**Schema Size:** 14 entities, 16 unique tables

**Critical Hotspots:**

| Query | Frequency | Complexity | Example |
|-------|-----------|-----------|---------|
| Notification badge counter | 1000s/sec | O(n) | `COUNT(*) WHERE is_read=false` |
| Session with all details (5-table JOIN) | 100s/sec | O(m*n) | `SELECT * FROM sessions JOIN tables JOIN orders JOIN items JOIN products` |
| Product menu listing | 100s/sec | O(n) | `SELECT * FROM products WHERE category_id=? AND status=AVAILABLE` |
| Date-range reporting | 10s/sec | O(n log n) | `SELECT * FROM orders WHERE completed_at BETWEEN ? AND ? GROUP BY HOUR` |

**Indexes Required (Database schema recommendations documented):**

35+ indexes across 14 tables:
- 12 CRITICAL indexes (session lookup, product filtering, notification badge)
- 15 HIGH priority (tenant filtering, status queries, FK relationships)
- 8 MEDIUM priority (audit trails, analytics)

### 1.5 Load Test Insights

**Current Stress Test Performance (SCALE=0.7):**
- ✅ Business error rate: **0.65%** (threshold pass)
- ✅ HTTP request failure: **0.65%** (threshold pass)
- ✅ p95 latency: **533ms** (threshold <2000ms)
- ✅ Customer order success: **97%** (up from 14% before lifecycle fix)
- ✅ Session completion: **91.3%** (3236/3545 closes)

**Concurrent Users Simulated:**
- Session Management: scale × 15 instances
- Order Processing (QR): scale × 30 instances  
- Menu/Product: scale × 20 instances
- Transaction Flow: scale × 10 instances
- **Total @ scale=0.7:** ~100 concurrent users

**Metric Cardinality:** Normalized to prevent memory spike (replacing UUIDs with :id)

---

## 2. GCP DEPLOYMENT ARCHITECTURE

### 2.1 Recommended GCP Services

#### **2.1.1 Compute: Google Kubernetes Engine (GKE)**
**Why:** Multi-tenant app with WebSocket support needs orchestration

```yaml
Cluster Configuration:
├─ Node Pool: Backend (Standard-N7/N2, autoscaling 3-10 nodes)
├─ Node Pool: Background Jobs (Preemptible, autoscaling 1-5 nodes)
├─ Resource Allocation:
│  ├─ Backend pod: 2 CPU, 4GB RAM (with HPA)
│  ├─ Database pod: 4-8 CPU, 16GB RAM (stateful)
│  └─ Cache pod: 1 CPU, 2GB RAM (Redis)
├─ Auto-scaling:
│  ├─ HPA: CPU >70%, Memory >80%
│  └─ Cluster autoscaler: enabled
└─ Network:
   └─ VPC with private subnet + NAT gateway
```

**Expected Pod Count:**
- Backend replicas: 3-8 (HPA managed)
- Database: 1 primary + 1 replica (HA)
- Cache: 1 master + 1 replica
- Monitoring: Prometheus + Grafana

#### **2.1.2 Database: Cloud SQL (MySQL 8.0)**
**Configuration:**

```
Instance Class: db-custom-4-16 (4 CPU, 16GB RAM)
├─ Storage:
│  ├─ Primary: 100GB SSD (scalable)
│  ├─ Backup: Automated daily + weekly
│  └─ PITR: 7-day retention
├─ Replication:
│  ├─ HA: Read replica in different zone
│  ├─ Binary logging: enabled
│  └─ Backups: automated 24-hour cycle
└─ Networking:
   ├─ Private IP only
   ├─ Cloud SQL Proxy for access
   └─ VPC Service Controls enabled
```

**Estimated Growth:**
- Initial: 10-20GB (14 entities × 1M+ records)
- Year 1: 50-100GB (multi-tenant growth)
- Year 2+: Plan for 200GB+ with archival strategy

**Indexes:** 35+ indexes already planned (see repo memory)

#### **2.1.3 Storage: Google Cloud Storage (GCS) for MinIO replacement**
**Configuration:**

```
Buckets:
├─ public-assets (images, documents)
│  ├─ Storage class: Standard
│  ├─ Versioning: disabled
│  └─ Retention: 90 days
├─ user-profiles (avatars)
│  ├─ Storage class: Standard
│  ├─ Versioning: enabled (30-day retention)
│  └─ CDN: CloudFront integration
└─ tenant-assets (menu items, branding)
   ├─ Storage class: Standard
   ├─ Access: Private + signed URLs
   └─ Lifecycle: Archive to Nearline after 1 year

Alternative: Keep MinIO in GKE as StatefulSet (more control, higher ops cost)
```

#### **2.1.4 Cache: Cloud Memorystore (Redis)**
**Configuration:**

```
Instance Tier: Basic (1GB, 1 replica)
├─ Size: 5-10GB for:
│  ├─ Session state (short-lived, ~15min)
│  ├─ Notification badge cache (high-frequency reads)
│  ├─ Product menu cache (1-hour TTL)
│  └─ JWT token blacklist (logout)
├─ Eviction: allkeys-lru
├─ Persistence: disabled (cache-only)
└─ Auto-failover: enabled
```

**Production Instance:** db-custom-1-4 (1 CPU, 4GB)

#### **2.1.5 Networking & Load Balancing**

```
┌─────────────────────────────────────────────────────────┐
│          Google Cloud Load Balancer (TCP/UDP)            │
│  ├─ HTTPS frontend (Auto SSL/TLS with cert management) │
│  ├─ WebSocket support: enabled                         │
│  └─ Session affinity: 30min (for WebSocket continuity) │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│              GKE Service (Backend)                       │
│  ├─ Service type: ClusterIP (internal LB)               │
│  ├─ Horizontal Pod Autoscaler (HPA)                     │
│  │  ├─ Min: 3 pods                                      │
│  │  ├─ Max: 8 pods                                      │
│  │  ├─ CPU threshold: 70%                               │
│  │  └─ Memory threshold: 80%                            │
│  └─ Update strategy: Rolling (surge=1, unavailable=0)  │
└─────────────────────────────────────────────────────────┘
```

**DNS:** Cloud DNS + CDN for static assets

#### **2.1.6 Monitoring & Logging**
**Stack:**

```
GCP Native (Recommended):
├─ Cloud Monitoring (Stackdriver Metrics)
│  ├─ Dashboard: Business metrics (orders/hour, revenue)
│  ├─ Alerts: Error rate >1%, latency >500ms, CPU >80%
│  └─ Custom metrics: Session count, notification queue
├─ Cloud Logging (Centralized logs)
│  ├─ Retention: 30 days (configurable)
│  ├─ Log routing: Error→Slack, Warning→Dashboard
│  └─ Query: BigQuery for analysis
└─ Cloud Trace (Distributed tracing)
   └─ Latency breakdown per request path

Optional (Prometheus + Grafana):
├─ Prometheus on GKE for deeper metrics
├─ Grafana for custom dashboards
└─ Estimated cost: +$200-400/month
```

#### **2.1.7 Authentication & Security**

```
Keycloak Setup:
├─ Deployment: GKE StatefulSet + PostgreSQL CloudSQL
├─ Realm isolation: Per-tenant
├─ Token TTL: 15min (JWT validation via Redis cache)
├─ Social login integration: Google, Facebook (optional)
└─ LDAP/AD integration: For enterprise tenants

Security:
├─ VPC Service Controls: Restrict data exfiltration
├─ Binary Authorization: Only signed container images
├─ Workload Identity: GKE pods → GCS/BigQuery permissions
├─ Network Policies: Pod-to-pod communication rules
└─ Secret Manager: Store API keys, DB passwords centrally
```

### 2.2 Cost Estimation (Monthly)

| Component | Estimate | Notes |
|-----------|----------|-------|
| **GKE Cluster** | $150-250 | 3-8 node pool (n2-standard-4) + autoscaling |
| **Cloud SQL MySQL** | $200-400 | db-custom-4-16 ($300/mo) + backup storage |
| **Cloud Memorystore Redis** | $80-120 | 5GB instance + replication |
| **GCS Storage** | $50-100 | 200GB @ $0.02/GB + egress |
| **Cloud Load Balancer** | $25-50 | Per LB + data transfer |
| **Cloud Monitoring** | $50-100 | Metrics ingestion + storage |
| **Cloud Logging** | $30-50 | Log storage (30-day retention) |
| **Networking/Egress** | $50-150 | Data transfer out (scaling dependent) |
| **Keycloak (if separate)** | $100-200 | GKE pod allocation |
| **Miscellaneous** | $30-50 | Node image pulls, secrets, etc. |
| **---** | **---** | **TOTAL (Prod 1 Region)** |
| **TOTAL** | **$765-1,350/month** | ~$9,180-16,200/year |

**Scaling Profiles:**

| Load Level | Users | GKE Nodes | Cloud SQL | Cost/Month |
|-----------|-------|-----------|-----------|-----------|
| Dev | 10-50 | 1-2 (e2-small) | db-n1-standard-1 | $150-200 |
| Staging | 100-500 | 2-4 (n2-std-2) | db-custom-2-8 | $300-400 |
| Prod Small | 1K-5K | 3-8 (n2-std-4) | db-custom-4-16 | $800-1,200 |
| Prod Large | 5K-20K | 6-15 (n2-std-4) | db-custom-8-32 | $1,500-2,500 |
| Prod Enterprise | 20K+ | 10-20 (n2-std-8) | Multi-instance | $3,000+ |

---

## 3. DEPLOYMENT MANIFESTS & CONFIGURATION

### 3.1 Kubernetes Backend Deployment

```yaml
# deploy/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fnb-backend
  namespace: production
spec:
  replicas: 3  # HPA will scale this
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: fnb-backend
  template:
    metadata:
      labels:
        app: fnb-backend
    spec:
      serviceAccountName: fnb-backend
      containers:
      - name: backend
        image: gcr.io/YOUR_PROJECT/fnb-backend:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8081
          name: http
          protocol: TCP
        - containerPort: 8081
          name: websocket
          protocol: TCP
        env:
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        - name: REDIS_URL
          valueFrom:
            configMapKeyRef:
              name: redis-config
              key: url
        - name: MINIO_URL
          valueFrom:
            configMapKeyRef:
              name: storage-config
              key: url
        - name: APP_JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: jwt-secret
        - name: APP_FRONTEND_URL
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: frontend-url
        - name: SERVER_PORT
          value: "8081"
        resources:
          requests:
            cpu: 500m
            memory: 1Gi
          limits:
            cpu: 2000m
            memory: 4Gi
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 10
          periodSeconds: 5
        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true

---
apiVersion: v1
kind: Service
metadata:
  name: fnb-backend-service
  namespace: production
spec:
  type: ClusterIP
  selector:
    app: fnb-backend
  ports:
  - port: 80
    targetPort: 8081
    protocol: TCP
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 1800

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: fnb-backend-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: fnb-backend
  minReplicas: 3
  maxReplicas: 8
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
```

### 3.2 Cloud SQL Configuration (Terraform)

```hcl
# infrastructure/cloud-sql.tf
resource "google_sql_database_instance" "fnb_mysql" {
  name           = "fnb-prod-mysql"
  database_version = "MYSQL_8_0"
  region         = var.gcp_region

  settings {
    tier              = "db-custom-4-16"
    activation_policy = "ALWAYS"
    
    backup_configuration {
      enabled                        = true
      start_time                     = "03:00"
      backup_retention_settings {
        retained_backups = 30
        retention_unit   = "COUNT"
      }
    }

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.vpc.id
      require_ssl     = true
    }

    database_flags {
      name  = "max_connections"
      value = "500"
    }
    
    database_flags {
      name  = "slow_query_log"
      value = "on"
    }

    location_preference {
      zone           = var.gcp_zone
      follow_gke_cluster = google_container_cluster.primary.id
    }
  }

  deletion_protection = true
}

resource "google_sql_database" "fnb_db" {
  name     = "fnb_database"
  instance = google_sql_database_instance.fnb_mysql.name
}

resource "google_sql_database_instance" "fnb_mysql_replica" {
  name                 = "fnb-prod-mysql-replica"
  database_version     = "MYSQL_8_0"
  region               = var.gcp_replica_region
  master_instance_name = google_sql_database_instance.fnb_mysql.name

  replica_configuration {
    kind            = "FAILOVER"
    mysql_replica_configuration {
      client_certificate = ""
      master_heartbeat_period = 5000
    }
  }
}
```

### 3.3 Environment Variables (.env for GKE)

```bash
# Common
APP_ENV=production
APP_DEBUG=false
SERVER_PORT=8081

# Database (from Cloud SQL)
DB_URL=jdbc:mysql://10.x.x.x:3306/fnb_database?useSSL=true
DB_USERNAME=fnb_user
DB_PASSWORD=<from Secret Manager>

# Redis (from Memorystore)
REDIS_URL=redis://10.x.x.x:6379/0
REDIS_PASSWORD=<from Secret Manager>

# Storage (MinIO or GCS)
MINIO_URL=https://minio.fnb-prod.gke/
MINIO_ACCESS_KEY=<from Secret Manager>
MINIO_SECRET_KEY=<from Secret Manager>
MINIO_EXTERNAL_URL=https://cdn.fnb-prod.com/

# Security
APP_JWT_SECRET=<from Secret Manager>
APP_JWT_EXPIRATION_MS=3600000
APP_AES_SECRET=<from Secret Manager>

# Keycloak
KEYCLOAK_SERVER_URL=https://keycloak.fnb-prod.gke/
KEYCLOAK_REALM=fnb-production

# Payment Gateways
VNPAY_API_URL=https://sandbox.vnpayment.vn
VNPAY_RETURN_URL=https://api.fnb-prod.com/api/payments/callback
MOMO_API_URL=https://test-payment.momo.vn

# Frontend
APP_FRONTEND_URL=https://app.fnb-prod.com
```

---

## 4. DEPLOYMENT STRATEGY

### 4.1 Phase 1: Infrastructure Setup (Week 1-2)
```
☐ Create GCP project & enable APIs (GKE, Cloud SQL, GCS, Memorystore)
☐ Set up VPC, subnets, Cloud NAT
☐ Deploy Cloud SQL with HA replica
☐ Deploy Memorystore Redis
☐ Configure Cloud Load Balancer
☐ Set up Cloud DNS for custom domain
```

### 4.2 Phase 2: Application Containerization (Week 1-3)
```
☐ Create Dockerfile for backend (multi-stage, minimal image)
☐ Push to Google Container Registry (GCR)
☐ Build and push frontend static assets to GCS
☐ Set up CI/CD pipeline (Cloud Build)
```

### 4.3 Phase 3: Application Deployment (Week 3-4)
```
☐ Deploy GKE cluster (3 nodes initial)
☐ Configure RBAC & service accounts
☐ Deploy backend using Helm charts
☐ Run initial load tests (k6)
☐ Set up monitoring & alerting
```

### 4.4 Phase 4: Validation & Cutover (Week 4-5)
```
☐ Smoke tests: 100% pass
☐ Stress tests: All thresholds passing
☐ Data migration from dev/staging
☐ DNS cutover with traffic shadowing
☐ Go live with runbook procedures
```

---

## 5. OBSERVABILITY & MONITORING

### 5.1 Key Metrics Dashboard

```
Business Metrics:
├─ Orders/hour (target: >100)
├─ Revenue/day (tracked)
├─ Payment success rate (target: >99%)
└─ Customer satisfaction (NPS questionnaire)

Technical Metrics:
├─ Backend Health:
│  ├─ Request latency p50/p95/p99
│  ├─ Error rate (<0.1%)
│  ├─ CPU utilization (target: <70%)
│  └─ Memory utilization (target: <80%)
├─ Database Health:
│  ├─ Query latency (p95 < 100ms)
│  ├─ Connection pool utilization
│  ├─ Replication lag (<1s)
│  └─ Slow query log (alerting)
├─ Cache Health:
│  ├─ Hit rate (target: >90%)
│  ├─ Eviction rate (target: <5%)
│  └─ Memory utilization
└─ WebSocket Health:
   ├─ Connected sessions
   ├─ Message throughput
   └─ Latency
```

### 5.2 Alerting Rules

```yaml
Alert: HighErrorRate
  condition: error_rate > 1%
  severity: CRITICAL
  action: Page on-call engineer

Alert: HighLatency
  condition: p95_latency > 500ms
  severity: WARNING
  action: Notify Slack #alerts

Alert: DatabaseConnectionPoolExhaustion
  condition: connection_utilization > 80%
  severity: CRITICAL
  action: Page DBA + Email ops

Alert: OutOfMemory
  condition: pod_memory > request + 10%
  severity: HIGH
  action: Trigger pod eviction & alert

Alert: ReplicationLag
  condition: db_replication_lag > 5s
  severity: HIGH
  action: Trigger failover check
```

---

## 6. COST OPTIMIZATION STRATEGIES

### 6.1 Immediate Wins
```
1. Use Preemptible VMs for non-critical workloads → Save 70%
2. Set resource requests/limits accurately → Reduce waste
3. Enable cluster autoscaling → Pay only for what's needed
4. Use committed use discounts (CUDs) for 1-3 year terms → Save 30-50%
```

### 6.2 Medium-term (Months 1-6)
```
1. Implement caching layer (Redis → BigQuery) → Reduce DB queries 50%
2. Optimize image sizes (multi-stage Docker) → Faster deploys
3. Archive old logs to BigQuery for analysis → Save storage 30%
4. Enable storage lifecycle policies → Auto-archive to Coldline
```

### 6.3 Long-term (Year 1+)
```
1. Evaluate GKE Autopilot (fully managed) → Reduce ops overhead
2. Implement database sharding → Scale beyond single instance limits
3. Consider Spanner for global distribution → <10ms latency worldwide
4. Use OnDemand Analysis (BigQuery) for reporting → Replace batch jobs
```

---

## 7. DISASTER RECOVERY & BACKUP PLAN

### 7.1 RTO/RPO Targets
```
Service Level Objectives (SLOs):
├─ Availability: 99.5% (43 minutes downtime/month)
├─ Recovery Time Objective (RTO): 1 hour
├─ Recovery Point Objective (RPO): 15 minutes
└─ Data Durability: 99.99999999% (11 nines)
```

### 7.2 Backup Strategy
```
Database Backups:
├─ Continuous: Transaction logs (Cloud SQL automated)
├─ Daily: Full backup @ 03:00 UTC
├─ Weekly: Long-term backup (7-30 day retention)
├─ Cross-region: Replica in different region
└─ Disaster Recovery: Automated failover to replica

Application Data:
├─ GCS buckets: Geo-redundant (2 regions minimum)
├─ Versioning: Keep 30 days of object versions
├─ Cross-region replication: Every 24 hours
└─ Archive: Move to Coldline after 90 days

Configuration:
├─ Kubernetes manifests: Version-controlled in Git
├─ Secrets: Encrypted in Cloud Secret Manager
├─ Infrastructure: IaC (Terraform) in separate repo
```

### 7.3 Failover Procedure
```
Database Failover:
1. Detect unhealthy primary (3 consecutive health check failures)
2. Promote read replica to primary (30-60 seconds)
3. Update DNS A record to replica endpoint
4. Notify ops team
5. Validate data consistency
```

---

## 8. SECURITY CONSIDERATIONS

### 8.1 Network Security
```
Layers:
├─ Perimeter: Google Cloud Armor DDoS protection
├─ Load Balancer: HTTPS/TLS 1.3 termination
├─ VPC: Private subnets + Cloud NAT egress
├─ Pods: Network policies restrict pod-to-pod traffic
└─ Database: Private Cloud SQL instance (no public IP)
```

### 8.2 Application Security
```
Checklist:
☐ Enable Binary Authorization (sign container images)
☐ Implement workload identity (no service account keys)
☐ Enable Pod Security Policies (restrict container privileges)
☐ Use Network Policies for pod-to-pod communication
☐ Enable Cloud Audit Logs for all API calls
☐ Set up secret rotation (JWT keys, DB passwords)
☐ Implement rate limiting on public APIs
☐ Enable WAF rules (Cloud Armor)
```

### 8.3 Data Security
```
Encryption:
├─ At Rest:
│  ├─ Cloud SQL: Google-managed encryption keys (GMEK)
│  ├─ GCS: GMEK or CMEK (customer-managed)
│  └─ Cloud Memorystore: GMEK
├─ In Transit:
│  ├─ HTTPS: TLS 1.3 for all external traffic
│  ├─ mTLS: Pod-to-pod with Istio (optional)
│  └─ Database: SSL/TLS connections required
└─ PCI-DSS: Payment data never touches GCP (via VNPay/MOMO)
```

---

## 9. MIGRATION PLAN FROM DOCKER-COMPOSE

### 9.1 Current State (Docker Compose):
```
docker-compose.yml:
├─ MySQL (single instance, no replication)
├─ MinIO (single instance, no HA)
└─ Backend (single container, no autoscaling)
```

### 9.2 Target State (GCP):
```
GKE Deployment:
├─ Cloud SQL (primary + replica HA)
├─ GCS (multi-region geo-redundant)
└─ Backend (3-8 replicas with HPA)
```

### 9.3 Migration Steps:
```
Week 1: Infrastructure
☐ Provision GCP resources
☐ Create GCS buckets
☐ Set up Cloud SQL with backup from docker MySQL
☐ Test connectivity from GKE to Cloud SQL

Week 2: Application
☐ Build optimized Docker image
☐ Push to GCR
☐ Deploy to staging GKE cluster
☐ Run smoke tests against staging

Week 3: Validation
☐ Load test staging environment
☐ Verify all integrations (payments, storage)
☐ Test failover procedures
☐ Performance validation

Week 4: Cutover
☐ Final data sync
☐ DNS update (traffic shadowing)
☐ Monitor error rates
☐ Rollback plan active
```

---

## 10. SUMMARY & RECOMMENDATIONS

### Quick Decision Matrix

| Aspect | Recommendation | Rationale |
|--------|---|---|
| **Compute** | GKE | Native Kubernetes, WebSocket support, autoscaling |
| **Database** | Cloud SQL MySQL | Managed, HA replica, automated backups |
| **Cache** | Memorystore Redis | Managed, single-region initially |
| **Storage** | GCS (MinIO phase-out) | Geo-redundancy, CDN integration, cost-effective |
| **Networking** | Google Cloud LB + CDN | HTTPS termination, DDoS protection, session affinity |
| **Monitoring** | Cloud Monitoring + Logging | Native integration, BigQuery export for analysis |
| **Authentication** | Cloud IAM + Workload Identity | Eliminate service account keys |
| **CI/CD** | Cloud Build + Artifact Registry | Native, minimal setup |

### Final Estimate (Production Deployment)

**Monthly Cost:** $765–$1,350
- Annually: ~$9,180–$16,200
- Includes: Compute, database, cache, storage, networking, monitoring

**Team Requirements:**
- DevOps Engineer (0.5 FTE): Kubernetes, CI/CD
- Database Admin (0.25 FTE): Schema, backups, performance
- On-call rotation (shared among team)

**Timeline:**
- Full GCP deployment: **4-5 weeks**
- Go-live: **Week 5**
- Stable operations: **Week 8-12**

---

## Appendix: Key Files for Reference

**Repo Memory:**
- Backend schema & query patterns: `/memories/repo/backend_schema_and_query_patterns.md`
- K6 load test data: `/memories/repo/perf_session_summary_and_ws_delta.md`

**Code Files:**
- Docker setup: `docker-compose.yml`
- Backend config: `backend/src/main/resources/application.yml`
- Load test: `backend/k6_load_test.js`
- Frontend deps: `frontend/package.json`

---

**Document Version:** 1.0  
**Last Updated:** April 6, 2026  
**Next Review:** After GCP pilot deployment
