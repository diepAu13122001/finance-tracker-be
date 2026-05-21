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
| WebClient (Reactive)      | HTTP client     | Non-blocking, dùng cho Gemini API             |
| PayOS SDK                 | Payment         | Tích hợp thanh toán tiền Việt               |

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

### AI Analyze-Spending Flow (Ngày 97)

```
Tại sao không gọi Gemini trực tiếp từ Frontend:
  - Backend tổng hợp data (summary + category chart) trước khi gửi AI
  - GeminiService chỉ làm 1 việc: call Gemini API (single responsibility)
  - Gemini key vẫn do client cung cấp, không lưu server

Flow:
POST /api/ai/analyze-spending { year, month, geminiApiKey }
  → AIController: lấy summary + top 5 categories từ TransactionService
  → GeminiService.analyzeSpending(income, expense, categories, apiKey)
  → Build prompt có số liệu cụ thể → call Gemini → parse JSON
  → AIAnalyzeResponse { overview, topInsight, suggestion, warnings }
```

### Recurring Transactions (Ngày 98)

```
Thiết kế: Template pattern
  - recurring_transactions lưu template (amount, frequency, wallet...)
  - execute() tạo transaction thực + advance nextExecutionDate
  - Dùng TransactionService.create() để đi qua toàn bộ validation (plan limit, wallet...)

nextExecutionDate calculation:
  DAILY   → +1 day
  WEEKLY  → +7 days (plusWeeks(1))
  MONTHLY → +1 month (auto handle month overflow: Jan 31 → Feb 28)
  YEARLY  → +1 year
```

### Goal Progress: Recalculate vs Increment

```
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
        ├── wallets
        │     └── transactions (wallet_id, nullable)
        │
        ├── transactions
        │
        ├── recurring_transactions (Plus) ── ngày 98
        │
        └── payment_history
```

**Flyway migrations:**

- V2: Schema khởi tạo (users, subscriptions, transactions)
- V3: Performance indexes
- V4: Categories system (Plus)
- V5: Goals system (Plus)
- V6-V16: Wallet, transfer, budget, payment features
- **V17: Recurring transactions (Plus) — ngày 98**

---

## API Endpoints

```
Auth (public):
  POST /api/auth/register
  POST /api/auth/login

Transactions (JWT):
  GET    /api/transactions?page&size&type&categoryId&walletId
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
  GET    /api/categories/top-spending?year&month (Plus)

Wallets (JWT):
  GET    /api/wallets
  POST   /api/wallets
  PUT    /api/wallets/:id
  DELETE /api/wallets/:id
  POST   /api/wallets/:id/reopen
  GET    /api/wallets/count

Recurring Transactions (Plus) — ngày 98:
  GET    /api/recurring
  POST   /api/recurring
  PUT    /api/recurring/:id
  DELETE /api/recurring/:id
  POST   /api/recurring/:id/execute

AI (Plus) — Gemini key do client cung cấp:
  POST /api/ai/parse-transaction   — ngày 96
  POST /api/ai/analyze-spending    — ngày 97

Payment (JWT):
  POST /api/payment/create-link
  GET  /api/payment/history
  POST /api/payment/webhook (public, PayOS callback)

Export (JWT):
  GET /api/export/excel?year&month

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
# AuthServiceTest         — 8 cases
# CategoryServiceTest     — 6 cases
# WalletServiceTest       — 8 cases
# GeminiServiceTest       — 2 cases
# RecurringServiceTest    — 6 cases  (ngày 100)
# PaymentServiceTest      — 5 cases  (ngày 100)
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

# PayOS (ngày 94-95)
PAYOS_CLIENT_ID=...
PAYOS_API_KEY=...
PAYOS_CHECKSUM_KEY=...
PAYOS_RETURN_URL=https://your-frontend.vercel.app/payment/success
PAYOS_CANCEL_URL=https://your-frontend.vercel.app/payment/cancel
```

---

## 👨‍💻 Tác Giả

**Diệp Âu**
[GitHub](https://github.com/diepau13122001) ·
[Email](mailto:diepau1312@gmail.com)
