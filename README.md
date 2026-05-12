# Finance Tracker — Backend

REST API cho ứng dụng quản lý tài chính cá nhân,
hỗ trợ hệ thống 3 gói (Free/Plus/Premium).

[![CI](https://github.com/diepau1312/finance-tracker-be/actions/workflows/ci.yml/badge.svg)](https://github.com/diepau1312/finance-tracker-be/actions)

**Live API:** https://your-backend.railway.app/swagger-ui.html

---

## Tech Stack & Lý Do Chọn

| Công nghệ                 | Vai trò         | Tại sao chọn                                  |
| ------------------------- | --------------- | --------------------------------------------- |
| Spring Boot 3.3 + Java 21 | REST API        | Mature ecosystem, Virtual Threads             |
| Spring Security + JWT     | Authentication  | Stateless, plan claims trong token            |
| Spring AOP                | Plan gating     | Tách cross-cutting concern khỏi business code |
| PostgreSQL 16             | Database        | JSONB, UUID native, partial index             |
| Flyway                    | DB Migration    | Version control cho schema                    |
| Spring Cache              | In-memory cache | Giảm DB queries cho summary                   |

---

## Kiến Trúc Quan Trọng

### Plan Gating Với AOP

```
Vấn đề: 20+ endpoints cần check plan → code lặp lại
Giải pháp: Custom annotation + AOP Aspect

@PostMapping("/categories")
@RequiresPlan("PLUS")           // ← 1 annotation thay vì 10 dòng check
public ResponseEntity<?> create(...) {
    // Business logic thuần túy, không có check plan
}

Flow:
Request → JwtAuthFilter (set PLAN_FREE/PLUS/PREMIUM authority)
        → PlanGateAspect (đọc authority, compare PLAN_LEVELS map)
        → Throw PlanUpgradeRequiredException nếu không đủ quyền
        → GlobalExceptionHandler → 403 response
```

### JWT Claims Chứa planId

```
Payload: { email, planId, iat, exp }
→ Không query DB mỗi request để check plan
→ JwtAuthFilter đọc planId → set vào SecurityContext
→ PlanGateAspect đọc từ SecurityContext
→ CategoryService, GoalService đọc userId từ SecurityContext
```

### Goal Progress: Recalculate vs Increment

```
Tại sao KHÔNG dùng increment:
  transaction.create → goal.current += amount  ← race condition
  transaction.delete → goal.current -= amount  ← inconsistent nếu fail

Tại sao DÙNG recalculate:
  SELECT SUM(amount) FROM transactions WHERE goal_id = ?
  goal.current = kết quả                       ← idempotent, luôn đúng
  Auto-complete khi current >= target
  Auto-revert ACTIVE khi current < target sau delete
```

---

## Database Schema

```
users ──┬── user_subscriptions ──── subscription_plans (FREE/PLUS/PREMIUM)
        │
        ├── categories (Plus)
        │     └── transactions (category_id, nullable)
        │
        ├── goals (Plus)
        │     └── transactions (goal_id, nullable)
        │
        ├── transactions
        │
        └── payment_history
```

**Flyway migrations:**

- V2: Schema khởi tạo (users, subscriptions, transactions)
- V3: Performance indexes
- V4: Categories system (Plus)
- V5: Goals system (Plus)

---

## API Endpoints

```
Auth (public):
  POST /api/auth/register
  POST /api/auth/login

Transactions (JWT):
  GET    /api/transactions?page&size&type&categoryId
  POST   /api/transactions
  PUT    /api/transactions/:id
  DELETE /api/transactions/:id
  GET    /api/transactions/summary?year&month&quarter
  GET    /api/transactions/chart/daily?year&month
  GET    /api/transactions/chart/monthly?year
  GET    /api/transactions/chart/categories?type&year&month (Plus)

Categories (Plus):
  GET    /api/categories?type
  POST   /api/categories
  PUT    /api/categories/:id
  DELETE /api/categories/:id

Goals (Plus):
  GET    /api/goals
  GET    /api/goals/active
  POST   /api/goals
  PUT    /api/goals/:id
  PATCH  /api/goals/:id/cancel
  DELETE /api/goals/:id

Health (public):
  GET /actuator/health
```

---

## Setup Local

### Yêu Cầu

- Java 21+, Maven 3.9+
- PostgreSQL 15+

### Bước 1: Tạo Database

```bash
psql -U postgres
CREATE DATABASE finance_tracker;
CREATE USER ft_user WITH PASSWORD 'ft_password';
GRANT ALL PRIVILEGES ON DATABASE finance_tracker TO ft_user;
GRANT ALL ON SCHEMA public TO ft_user;
\q
```

### Bước 2: Chạy

```bash
./mvnw spring-boot:run
# API:     http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

---

## Tests

```bash
./mvnw test

# Test files:
# AuthServiceTest       — 8 cases
# SubscriptionServiceTest — 7 cases
# CategoryServiceTest   — 6 cases
# GoalServiceTest       — 6 cases
```

---

## Deploy

Auto-deploy lên Railway khi push lên `main`.

```
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=...
APP_JWT_SECRET=...
APP_CORS_ORIGINS=https://your-frontend.vercel.app
SPRING_PROFILES_ACTIVE=prod
```

---

## 👨‍💻 Tác Giả

**Diệp Âu**
[GitHub](https://github.com/diepau13122001) ·
[Email](mailto:diepau1312@gmail.com)
