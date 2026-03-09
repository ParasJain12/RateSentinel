# ⚡ RateSentinel

> **Production-grade Distributed Rate Limiting System** built with Spring Boot, Redis, and Kubernetes.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen?style=flat-square&logo=springboot)
![Redis](https://img.shields.io/badge/Redis-7.0-red?style=flat-square&logo=redis)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square&logo=mysql)
![Kubernetes](https://img.shields.io/badge/Kubernetes-1.29-326CE5?style=flat-square&logo=kubernetes)
![Docker](https://img.shields.io/badge/Docker-24.0-2496ED?style=flat-square&logo=docker)

---

## 📌 What is RateSentinel?

RateSentinel is a **distributed rate limiting system** that protects APIs from abuse, DDoS attacks, and overuse. It enforces request quotas across multiple application instances using **atomic Redis Lua scripts**, ensuring zero race conditions even under extreme concurrent load.

The system is deployed as **3 Kubernetes pods behind an Nginx load balancer**, all sharing a single Redis instance — proving that rate limits are enforced globally across the entire cluster, not just per instance.

---

## 🏗️ Architecture

```
                          ┌─────────────────────────────────────────┐
                          │           CLIENT REQUESTS                │
                          └──────────────────┬──────────────────────┘
                                             │
                                             ▼
                          ┌─────────────────────────────────────────┐
                          │          NGINX LOAD BALANCER             │
                          │         (Round Robin, port 80)           │
                          └────────┬──────────────┬─────────────────┘
                                   │              │              │
                    ┌──────────────▼──┐  ┌────────▼────────┐  ┌─▼───────────────┐
                    │  Spring Boot    │  │  Spring Boot    │  │  Spring Boot    │
                    │   Pod 1         │  │   Pod 2         │  │   Pod 3         │
                    │  :8080          │  │  :8080          │  │  :8080          │
                    └──────┬──────────┘  └────────┬────────┘  └──────┬──────────┘
                           │                      │                  │
                           └──────────────────────┼──────────────────┘
                                                  │
                                    ┌─────────────▼──────────────┐
                                    │         REDIS               │
                                    │   (Shared Rate Counters)    │
                                    │   Atomic Lua Scripts        │
                                    │   Circuit Breaker           │
                                    └─────────────┬──────────────┘
                                                  │
                                    ┌─────────────▼──────────────┐
                                    │         MYSQL               │
                                    │   Rate Limit Rules          │
                                    │   Request Logs              │
                                    │   Tier Configs              │
                                    └────────────────────────────┘
```

---

## 🔄 Request Flow

```
Incoming Request
      │
      ▼
 Is IP Blacklisted? ──── YES ──► 403 Forbidden
      │ NO
      ▼
 Is IP Whitelisted? ──── YES ──► ✅ Allow (skip rate limit)
      │ NO
      ▼
 Find Matching Rule (Redis Cache → MySQL fallback)
      │
      ▼
 Execute Rate Limit Algorithm (Atomic Lua Script in Redis)
      │
      ├── ALLOWED ──► Set Response Headers ──► ✅ 200 OK
      │
      └── BLOCKED ──► Log Decision (Async) ──► ❌ 429 Too Many Requests
                              │
                              ▼
                    Push WebSocket Event
                    (Live Dashboard Update)
```

---

## ✨ Features

### 5 Rate Limiting Algorithms
| Algorithm | Best For | Key Characteristic |
|---|---|---|
| **Fixed Window** | Simple APIs, brute force protection | Fast, minimal memory |
| **Sliding Window Log** | Precise per-user limits | Exact but higher memory |
| **Sliding Window Counter** | High traffic APIs | Memory efficient, approximate |
| **Token Bucket** | APIs allowing bursting | Smooth with burst allowance |
| **Leaky Bucket** | Streaming, uploads | Constant rate, no bursting |

### Per User Tier Rate Limiting
| Tier | Limit | Algorithm |
|---|---|---|
| FREE | 10 req/min | Fixed Window |
| PRO | 100 req/min | Sliding Window Counter |
| ENTERPRISE | 1000 req/min | Token Bucket |
| INTERNAL | Unlimited | — |

### Other Features
- **Custom `@RateLimit` Annotation** — Apply rate limiting directly on any method via AOP
- **Redis Circuit Breaker** — Resilience4j circuit breaker with CLOSED/OPEN/HALF_OPEN states. Fails open when Redis is down
- **Dynamic Rule Caching** — Rules cached in Redis (5 min TTL) with instant invalidation on update
- **Real Time WebSocket Dashboard** — Live traffic monitoring with Chart.js
- **Whitelist / Blacklist** — IP based access control checked before rate limiting
- **Async Logging** — Rate limit decisions logged asynchronously, never slows main request
- **Swagger UI** — Full API documentation with Try It Out

---

## 🚀 Quick Start

### Prerequisites
- Java 17
- Docker + Docker Compose
- Maven 3.9+

### Run with Docker Compose (Recommended)

```bash
# Clone the repo
git clone https://github.com/ParasJain12/RateSentinel.git
cd RateSentinel

# Build the JAR
mvn clean package -DskipTests

# Start all services (MySQL + Redis + 3 App instances + Nginx)
docker-compose up -d

# Check all containers are running
docker-compose ps
```

Access the application —
| URL | Description |
|---|---|
| `http://localhost:8080` | Main app (via Nginx load balancer) |
| `http://localhost:8080/dashboard` | Live monitoring dashboard |
| `http://localhost:8080/swagger-ui.html` | API documentation |
| `http://localhost:8080/api/health` | System health check |

### Run Locally

```bash
# Start Redis
docker start redis-local

# Start MySQL and create database
mysql -u root -p -e "CREATE DATABASE rate_sentinel_db;"

# Run the application
mvn spring-boot:run
```

---

## ☸️ Kubernetes Deployment

```bash
# Start Minikube
minikube start

# Build and load Docker image
docker build -t ratesentinel:latest .
minikube image load ratesentinel:latest

# Deploy all resources
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/mysql-deployment.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

# Verify all pods running
kubectl get all -n ratesentinel

# Access the app
minikube service ratesentinel-service -n ratesentinel
```

---

## 🧪 Testing Rate Limiting

### 1. Test via Postman

Add rule with limit 5 —
```json
POST /api/rules
{
    "ruleName": "test-rule",
    "endpointPattern": "/api/test",
    "httpMethod": "GET",
    "limitCount": 5,
    "windowSeconds": 60,
    "algorithm": "FIXED_WINDOW",
    "identifierType": "IP_ADDRESS",
    "isActive": true
}
```

Hit `GET /api/test` 6 times. First 5 return `200 OK`. 6th returns `429 Too Many Requests`.

### 2. Test User Tier Limiting

```
GET /api/free-endpoint
X-User-Tier: FREE
X-User-Id: user-001
```

FREE users blocked after 10 requests. PRO users get 100. They have completely independent counters.

### 3. Test @RateLimit Annotation

```
POST /api/login   ← limit 3 per minute (brute force protection)
POST /api/send-otp ← limit 2 per minute (OTP spam protection)
GET  /api/profile  ← limit 10 per minute per USER_ID
```

### 4. Prove Distributed Rate Limiting (The Killer Demo)

With 3 pods running and a rule set to limit 9 —

```bash
for i in {1..10}; do
  echo "Request $i: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/test)"
done
```

Output —
```
Request 1: 200  ← went to pod 1
Request 2: 200  ← went to pod 2
Request 3: 200  ← went to pod 3
Request 4: 200  ← went to pod 1
...
Request 9: 200
Request 10: 429 ← ALL pods share same Redis counter
```

Without shared Redis, each pod would allow 9 = 27 total. Shared Redis enforces exactly 9 globally.

---

## 📡 API Reference

### Rate Limit Rules
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/rules` | Get all rules |
| POST | `/api/rules` | Create rule |
| PUT | `/api/rules/{id}` | Update rule |
| PATCH | `/api/rules/{id}/toggle` | Enable/disable rule |
| DELETE | `/api/rules/{id}` | Delete rule |
| POST | `/api/rules/whitelist` | Add IP to whitelist |
| POST | `/api/rules/blacklist` | Add IP to blacklist |

### Analytics
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/analytics/dashboard` | Full 24h analytics |
| GET | `/api/analytics/top-blocked` | Top blocked identifiers |
| GET | `/api/analytics/recent` | Recent activity feed |
| GET | `/api/analytics/circuit-breaker` | Circuit breaker state |

### Tier Management
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/tiers` | Get all tier configs |
| PUT | `/api/tiers/{tierName}` | Update tier limit |

### Health
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/health` | Overall system health |
| GET | `/api/health/ping` | Simple ping |
| GET | `/api/health/redis` | Redis status |
| GET | `/api/health/database` | MySQL status + table counts |
| GET | `/api/health/jvm` | JVM memory + uptime |
| GET | `/api/health/circuit-breaker` | Circuit breaker details |
| GET | `/api/health/info` | System info + features |

### Response Headers
Every rate-limited response includes —
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
X-RateLimit-Reset: 1710000060
X-RateLimit-Algorithm: FIXED_WINDOW
Retry-After: 45  (only on 429 responses)
```

---

## 🔧 Configuration

```yaml
# application.yml
rate-sentinel:
  default-limit: 100
  default-window-seconds: 60
  algorithm: SLIDING_WINDOW_COUNTER
  redis-fallback: FAIL_OPEN      # Allow requests if Redis is down
  alert-threshold: 50            # Alert when block rate exceeds 50%

resilience4j:
  circuitbreaker:
    instances:
      redis:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

---

## 🧠 Key Design Decisions

**1. Atomic Lua Scripts**
All Redis operations use Lua scripts executed atomically. This prevents race conditions where two concurrent requests could both read the same counter value before either increments it.

**2. Circuit Breaker Pattern**
Every Redis call goes through a Resilience4j circuit breaker. When Redis goes down the circuit opens and all requests fail open (allowed) instead of crashing the application.

**3. Fail Open Strategy**
When Redis is unavailable, requests are allowed through. This prioritizes availability over strict rate enforcement — the right tradeoff for most production systems.

**4. Two-Layer Rate Limiting**
Every request goes through two independent checks — endpoint rule check and user tier check. Both must pass. This allows fine-grained control at both the API and business tier level.

**5. Async Everything**
Rate limit decisions are logged asynchronously using `@Async`. WebSocket events are pushed asynchronously. This ensures the main request thread is never blocked by observability operations.

---

## 🧪 Test Coverage

```
AlgorithmTest      — Unit tests for all 5 algorithms
ConcurrencyTest    — 7 tests, 100 concurrent threads
                     Proves zero race conditions
                     Tests each algorithm under load
                     Tests per-user isolation
```

Run tests —
```bash
# Unit tests only
mvn test -Dtest=AlgorithmTest

# Concurrency tests (requires Redis running)
mvn test -Dtest=ConcurrencyTest
```

---

## 📁 Project Structure

```
com.ratesentinel/
├── algorithm/          ← 5 rate limiting algorithms + factory
├── annotation/         ← @RateLimit annotation + AOP aspect
├── config/             ← Redis, Security, WebSocket, Swagger config
├── controller/         ← REST controllers
├── dto/                ← Request/Response DTOs
├── exception/          ← Global exception handler
├── interceptor/        ← HTTP interceptor (applies rules to all requests)
├── model/              ← JPA entities
├── repository/         ← Spring Data JPA repositories
├── service/            ← Business logic
└── websocket/          ← WebSocket event service

k8s/                    ← Kubernetes manifests
nginx/                  ← Nginx load balancer config
src/main/resources/
├── db/changelog/       ← Liquibase migrations
├── scripts/            ← Redis Lua scripts
└── static/             ← Dashboard HTML
```

---

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 3.2.3 | Application framework |
| Redis + Redisson | Distributed counters, rule caching |
| MySQL 8.0 | Rule storage, request logs |
| Liquibase | Database migrations |
| Resilience4j | Circuit breaker for Redis |
| Spring AOP | @RateLimit annotation |
| Spring WebSocket + STOMP | Real time dashboard |
| Springdoc OpenAPI | Swagger documentation |
| Docker + Docker Compose | Containerization |
| Kubernetes + Minikube | Orchestration + autoscaling |
| Nginx | Load balancing across pods |
| JUnit 5 + Awaitility | Concurrency testing |

---

## 👨‍💻 Author

**Paras Jain**
B.Tech CSE 2025 | Associate Software Engineer at WebKorps

[![GitHub](https://img.shields.io/badge/GitHub-ParasJain12-181717?style=flat-square&logo=github)](https://github.com/ParasJain12)

---

## 📄 License

MIT License — free to use for learning and portfolio purposes.
