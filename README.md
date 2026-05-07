# Finance Tracker — Backend

REST API cho ứng dụng quản lý tài chính cá nhân,
hỗ trợ hệ thống 3 gói (Free/Plus/Premium).

**CI backend:** [![CI]](https://github.com/diepAu13122001/finance-tracker-be/blob/main/.github/workflows/ci.yml)

**Live API:** https://finance-tracker-be.up.railway.app/swagger-ui/index.html

---

## Tech Stack & Lý Do Chọn

| Công nghệ | Vai trò | Tại sao chọn |
|---|---|---|
| Spring Boot 3.3 + Java 21 | REST API | Mature ecosystem, Virtual Threads |
| Spring Security + JWT | Authentication | Stateless, scalable |
| Spring AOP | Plan gating | Separation of concerns |
| PostgreSQL 16 | Database | JSONB support, reliable |
| Flyway | DB Migration | Version control cho schema |
| Spring Cache | In-memory cache | Giảm DB queries cho summary |

---

## Kiến Trúc Quan Trọng

### Plan Gating Với AOP
Vấn đề: 20+ endpoints cần check plan → code lặp lại
Giải pháp: Custom annotation + AOP Aspect
``` java
// Chỉ cần 1 annotation trên method
@PostMapping("/categories")
@RequiresPlan("PLUS")
public ResponseEntity<?> createCategory(...) {
// Không có check plan ở đây
// AOP tự chặn nếu user không đủ plan
}
```
### JWT Claims Chứa planId
```
JWT payload: { email, planId, iat, exp }
→ Không cần query DB để check plan mỗi request
→ JwtAuthFilter đọc planId → set vào SecurityContext
→ PlanGateAspect đọc từ SecurityContext
```
---

## Database Schema
```
users ──────────── user_subscriptions ──── subscription_plans
│                                              (FREE/PLUS/PREMIUM)
└──── transactions
└──── payment_history
```
---

## Setup Local

### Yêu Cầu
- Java 21+
- PostgreSQL 15+
- Maven 3.9+

### Bước 1: Tạo Database

```bash
psql -U postgres
CREATE DATABASE finance_tracker;
CREATE USER ft_user WITH PASSWORD 'ft_password';
GRANT ALL PRIVILEGES ON DATABASE finance_tracker TO ft_user;
GRANT ALL ON SCHEMA public TO ft_user;
\q
```

### Bước 2: Config

```bash
# application.yml đã có fallback values
# Không cần tạo thêm file cho local development cơ bản
```

### Bước 3: Chạy

```bash
./mvnw spring-boot:run
# API: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

---

## API Endpoints
```
Auth (public):
POST /api/auth/register
POST /api/auth/login
Transactions (cần JWT):
GET    /api/transactions?page=0&size=20&type=EXPENSE
POST   /api/transactions
PUT    /api/transactions/:id
DELETE /api/transactions/:id
GET    /api/transactions/summary?year=2026&month=5
GET    /api/transactions/chart/daily?year=2026&month=5
GET    /api/transactions/chart/monthly?year=2026
Users (cần JWT):
GET /api/users/me
PUT /api/users/me
PUT /api/users/me/password
Health (public):
GET /actuator/health
```
---

## Tests

```bash
./mvnw test

# Test files:
# AuthServiceTest.java     — 8 test cases
# SubscriptionServiceTest.java — 7 test cases
```

---

## Deploy

App deploy tự động lên Railway khi push lên branch `main`.

Environment variables cần thiết:
```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
APP_JWT_SECRET
APP_CORS_ORIGINS
SPRING_PROFILES_ACTIVE=prod
```

---

## 👨‍💻 Tác Giả

**Diệp Âu**
[GitHub](https://github.com/diepau13122001) ·
[Email](mailto:diepau1312@gmail.com)
