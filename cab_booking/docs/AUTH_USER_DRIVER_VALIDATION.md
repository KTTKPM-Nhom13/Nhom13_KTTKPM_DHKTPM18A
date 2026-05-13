# Auth, User, Driver Validation

Tai lieu nay ghi lai cach kiem thu `auth-service`, `user-service`, va `driver-service` theo contract hien tai cua repo va theo huong phu hop rubric co dinh trong `final_PROJECT_grading-factor.pdf`.

## Scope

- Khong sua `final_PROJECT_grading-factor.pdf`.
- Bám theo payload va flow trong `API_PAYLOADS.md`.
- Kiem thu qua `API Gateway` tai `http://localhost:8080`.
- Kiem thu them endpoint noi bo cua driver tai `http://localhost:8089/internal/drivers/{driverId}/availability`.

## Preconditions

- Chay stack bang `docker compose up --build`.
- Dam bao cac container `api-gateway`, `eureka-server`, `auth-service`, `user-service`, `driver-service` deu `UP`.
- Dam bao `DRIVER_SERVICE_PORT=8089` trong `.env` de tranh xung dot voi `pricing-service`.

## Automated Validation

Script nguon:

`scripts/validate_auth_user_driver.py`

Chay:

```powershell
python scripts/validate_auth_user_driver.py
```

Script se:

- Tao 1 tai khoan `USER` moi qua `POST /api/auth/register`
- Login, verify token, refresh token cho user
- Kiem thu `GET/PUT profile`, `GET account`, `POST/GET/PATCH/heartbeat/revoke devices`
- Kiem thu `delete-request -> login bi chan -> restore -> login lai`
- Tao 1 tai khoan `DRIVER` moi qua `POST /api/auth/register`
- Login, verify token, refresh token cho driver
- Kiem thu `GET/PUT profile`, `PATCH availability`
- Kiem thu `GET /internal/drivers/{driverId}/availability`
- Kiem thu `assignment -> EN_ROUTE_PICKUP -> IN_PROGRESS -> complete -> earnings`
- Ghi artifact JSON vao `docs/validation-artifacts/`

## Latest Verified Run

Da duoc chay va pass tren stack Docker local vao ngay `2026-05-12`:

- End-to-end smoke flow: `32/32` step pass
- JSON artifact: `docs/validation-artifacts/auth-user-driver-validation-20260512T164234Z.json`
- Discovery check: `AUTH-SERVICE`, `USER-SERVICE`, `DRIVER-SERVICE`, `API-GATEWAY` deu `UP` trong Eureka
- Module tests:
  - `auth-service`: `.\mvnw -q test` pass
  - `user-service`: `.\mvnw -q test` pass
  - `driver-service`: `.\mvnw -q test` pass

Nhung chi tiet quan trong da duoc xac nhan tu runtime:

- `POST /api/auth/register` la public endpoint, khong can Bearer token
- `POST /api/auth/verify` tra `result.status = "success"` va `result.userId`, khong tra field `valid`
- Login khi account dang `PENDING_DELETION` bi chan voi HTTP `403`
- Driver internal check dung host port `8089`, khong phai `8083` hay `8084` tren may nay
- Flow driver pass theo thu tu `ACCEPTED -> EN_ROUTE_PICKUP -> IN_PROGRESS -> complete`

## What This Covers Against The Fixed Rubric

- Happy-path auth flow: register, login, verify, refresh, logout
- Bearer-protected user profile va device flow
- Account lifecycle behavior `ACTIVE -> PENDING_DELETION -> ACTIVE`
- Driver KYC/profile, availability, ride-state progression, earnings
- Gateway routing va discovery readiness cho 3 service

## What This Does Not Claim

- Khong khang dinh full security hardening, zero-trust, mTLS, tracing, alerting
- Khong kiem thu toan bo Kafka consumer side-effects cua `booking-service`
- Khong thay the cho performance/load test

## Reading The Result

- Neu script in ra `passed: true` thi flow co ban cua 3 service da pass tren stack dang chay.
- Neu script fail, doc step loi trong stdout va file JSON duoc ghi trong `docs/validation-artifacts/`.
