# CAB BOOKING SYSTEM - Comprehensive Documentation

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Pricing Service - Complete Analysis](#3-pricing-service---complete-analysis)
4. [API Endpoints Reference](#4-api-endpoints-reference)
5. [Fare Calculation Formulas](#5-fare-calculation-formulas)
6. [Activity Diagram - Complete Booking Flow](#6-activity-diagram---complete-booking-flow)
7. [Sequence Diagram - Service Communication](#7-sequence-diagram---service-communication)
8. [Database Models](#8-database-models)
9. [Configuration Reference](#9-configuration-reference)
10. [Technology Stack](#10-technology-stack)

---

## 1. Project Overview

### 1.1 Project Description

**Cab Booking System** là một hệ thống taxi/ride-hailing vi mô (microservices) được xây dựng bằng Java Spring Boot 3.x và Python, hỗ trợ đặt xe với nhiều loại phương tiện (Bike, Car 4 seats, Car 7 seats), tính giá động (dynamic pricing/surge pricing), ghép cặp tài xế-khách hàng (driver matching), thanh toán đa kênh, đánh giá và hệ thống AI.

### 1.2 Core Business Features

| Feature | Description |
|---------|-------------|
| **Multi-Vehicle Booking** | Hỗ trợ xe máy (BIKE), xe 4 chỗ (CAR4), xe 7 chỗ (CAR7) |
| **Dynamic Pricing** | Tính giá theo khoảng cách, thời gian, zone, surge pricing |
| **Surge Pricing Engine** | Rule-based surge dựa trên demand/supply, thời gian, thời tiết |
| **Driver Matching** | AI-powered driver scoring và matching |
| **Payment Integration** | Tiền mặt (CASH) và thanh toán online (MoMo, VNPay, ZaloPay) |
| **Review System** | Đánh giá sau chuyến đi |
| **AI Chat Agent** | Gemini-powered chatbot hỗ trợ driver/customer/admin |
| **AI Driver Scoring** | Multi-objective optimization để xếp hạng tài xế |

### 1.3 Service Inventory

| Service | Port | Technology | Database | Description |
|---------|------|------------|----------|-------------|
| **api-gateway** | 8080 | Spring Cloud Gateway | - | API Gateway, JWT validation, routing |
| **eureka** | 8761 | Netflix Eureka | - | Service Registry |
| **config-server** | 8888 | Spring Cloud Config | - | Centralized configuration |
| **auth-service** | 8081 | Spring Boot | PostgreSQL | Authentication (JWT RS256) |
| **user-service** | 8082 | Spring Boot | PostgreSQL | User management |
| **driver-service** | 8083 | Spring Boot | PostgreSQL | Driver profiles, availability |
| **booking-service** | 8084 | Spring Boot | PostgreSQL | Core booking logic, state machine |
| **ride-service** | 8085 | Spring Boot | PostgreSQL | Ride lifecycle management |
| **matching-service** | 8086 | Spring Boot | PostgreSQL | Driver-ride matching |
| **Pricing-Service** | 8088 | Spring Boot | MongoDB | Fare calculation, surge pricing |
| **Payment-Service** | 8090 | Spring Boot | PostgreSQL | Payment processing |
| **review-service** | 8091 | Spring Boot | MongoDB | Reviews/ratings |
| **notification-service** | 8092 | Spring Boot | - | Real-time notifications (Socket.IO) |
| **email-service** | 8087 | Spring Boot | - | Email notifications |
| **ai-agent-service** | 8099 | FastAPI/Python | - | AI chatbot (Gemini Flash) |
| **ai-scoring-service** | 8000 | FastAPI/Python | - | AI driver scoring |

### 1.4 Infrastructure

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Kafka** | Apache Kafka | Event-driven communication |
| **Redis** | Redis | Caching, rate limiting, session storage |
| **MongoDB** | MongoDB | Document storage (Pricing, Reviews) |
| **PostgreSQL** | PostgreSQL (6 instances) | Relational data per service |
| **Docker** | Docker Compose | Container orchestration |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                           CLIENTS                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │  Mobile App │  │  Web App    │  │  Admin UI   │                  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                  │
└─────────┼────────────────┼────────────────┼────────────────────────────┘
          │                │                │
          └────────────────┼────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY (8080)                            │
│  JWT Auth │ Rate Limiting │ Routing │ Load Balancing                  │
└──────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      SERVICE MESH (Kafka/REST)                        │
│                                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  Auth    │  │  User    │  │  Driver  │  │ Booking  │            │
│  │ Service  │  │ Service  │  │ Service  │  │ Service  │            │
│  │  (8081)  │  │  (8082)  │  │  (8083)  │  │  (8084)  │            │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
│                                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  Ride    │  │ Matching │  │ Pricing  │  │ Payment  │            │
│  │ Service  │  │ Service  │  │ Service  │  │ Service  │            │
│  │  (8085)  │  │  (8086)  │  │  (8088)  │  │  (8090)  │            │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
│                                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  Review  │  │ Notif.   │  │  Email   │  │   AI     │            │
│  │ Service  │  │ Service  │  │ Service  │  │  Agent   │            │
│  │  (8091)  │  │  (8092)  │  │  (8087)  │  │  (8099)  │            │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
│                                                                       │
│  ┌──────────────────────────────────────────────────────────┐        │
│  │              AI Scoring Service (8000)                     │        │
│  │         Python │ Multi-objective Optimization │ Gemini   │        │
│  └──────────────────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────────────────┐
│                           DATA LAYER                                  │
│                                                                       │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐        │
│  │ PostgreSQL │ │ PostgreSQL │ │ PostgreSQL │ │ PostgreSQL │        │
│  │ Auth DB    │ │ User DB    │ │ Driver DB │ │ Ride DB    │        │
│  │ :5433      │ │ :5434      │ │ :5435     │ │ :5436     │        │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘        │
│                                                                       │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐                       │
│  │ PostgreSQL │ │ PostgreSQL │ │  MongoDB   │                       │
│  │ Booking DB │ │ Payment DB │ │ Pricing DB │                       │
│  │ :5437      │ │ :5438      │ │ :27017     │                       │
│  └────────────┘ └────────────┘ └────────────┘                       │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────┐       │
│  │                         REDIS                                │       │
│  │  Surge Cache │ Route Cache │ Weather Cache │ Session Cache │       │
│  │  Zone Metrics │ Idempotency Keys │ Demand/Supply Metrics   │       │
│  └────────────────────────────────────────────────────────────┘       │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────┐       │
│  │                         KAFKA                               │       │
│  │  ride.created │ driver.status.changed │ payment.completed │       │
│  │  ride.completed │ ride.cancelled │ demand.supply.updated  │       │
│  └────────────────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 Service Dependencies

```
Client
  │
  ▼
API Gateway (8080)
  │
  ├──► Auth Service (8081) ──► Auth DB (PostgreSQL:5433)
  │
  ├──► User Service (8082) ──► User DB (PostgreSQL:5434)
  │
  ├──► Driver Service (8083) ──► Driver DB (PostgreSQL:5435)
  │        │
  │        └──► Kafka (driver.status.changed, driver.location.updated)
  │
  ├──► Booking Service (8084) ──► Booking DB (PostgreSQL:5437)
  │        │
  │        ├──► Pricing Service (8088) ──► Pricing DB (MongoDB:27017)
  │        │        │
  │        │        ├──► Redis (Surge Cache, Route Cache, Weather Cache)
  │        │        ├──► Mapbox API (Distance Matrix)
  │        │        └──► OpenMeteo API (Weather)
  │        │
  │        ├──► Matching Service (8086) ──► Matching DB (PostgreSQL)
  │        │        │
  │        │        └──► AI Scoring Service (8000)
  │        │
  │        └──► Kafka (ride.created, ride.cancelled)
  │
  ├──► Payment Service (8090) ──► Payment DB (PostgreSQL:5438)
  │        │
  │        └──► Kafka (payment.completed, payment.failed)
  │
  ├──► Ride Service (8085) ──► Ride DB (PostgreSQL:5436)
  │
  ├──► Review Service (8091) ──► Review DB (MongoDB)
  │
  ├──► Notification Service (8092) ──► Socket.IO (Real-time)
  │
  ├──► Email Service (8087)
  │
  └──► AI Agent Service (8099) ──► Gemini API
```

---

## 3. Pricing Service - Complete Analysis

### 3.1 Service Overview

**Pricing Service** (Port 8088) là trái tim của hệ thống tính giá, chịu trách nhiệm:

1. **Fare Estimation**: Tính giá ước lượng dựa trên tọa độ, loại xe, thời gian
2. **Quote Locking**: Khóa giá trong 15 phút sau khi confirm
3. **Surge Pricing**: Tính hệ số surge động dựa trên rule-based engine
4. **Revenue Statistics**: Thống kê doanh thu theo ngày, zone, loại xe
5. **Promo Code Management**: Quản lý mã khuyến mãi (FIXED/PERCENTAGE)

### 3.2 Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3.3.x |
| Language | Java 21 |
| Database | MongoDB |
| Cache | Redis |
| API Client | OpenFeign |
| Resilience | Resilience4j (Circuit Breaker, Retry, Rate Limiter) |
| External APIs | Mapbox Distance Matrix, OpenMeteo Weather |
| API Docs | SpringDoc OpenAPI (Swagger) |

### 3.3 Database Collections

#### 3.3.1 `fare_estimates` Collection

```json
{
  "_id": "ObjectId",
  "id": "UUID (estimateId)",
  "rideId": "string (optional - set when ride is created)",
  "pickupZone": "Z006069000106",
  "dropoffZone": "Z006069000107",
  "pickupLat": 10.7624,
  "pickupLng": 106.6597,
  "dropoffLat": 10.8231,
  "dropoffLng": 106.6297,
  "vehicleType": "CAR4",
  "distanceKm": 8.5,
  "durationMinutes": 25,
  "baseFare": 12000.00,
  "distanceFare": 72250.00,
  "timeFare": 30000.00,
  "platformFee": 2000.00,
  "zoneFee": 0.00,
  "airportFee": 0.00,
  "tollFee": 0.00,
  "discountAmount": 0.00,
  "promoCode": "SUMMER2026",
  "surgeMultiplier": 1.50,
  "totalFare": 164250.00,
  "currency": "VND",
  "pricingConfigVersion": "v1",
  "distanceSource": "MAPBOX | HAVERSINE_FALLBACK | MAPBOX_CACHE",
  "weatherCondition": "Rain: Slight, moderate and heavy intensity",
  "weatherSource": "OPEN_METEO | OPEN_METEO_CACHE | FALLBACK_UNAVAILABLE",
  "fallbackUsed": false,
  "status": "PENDING | CONFIRMED | EXPIRED | CANCELLED",
  "createdAt": "2026-05-26T10:00:00",
  "expiresAt": "2026-05-26T10:15:00",
  "schemaVersion": "1.0.0",
  "quoteId": "UUID (same as id)",
  "quotePayloadHash": "sha256 hash",
  "quoteHashAlgorithm": "SHA-256"
}
```

#### 3.3.2 `surge_rules` Collection

```json
{
  "_id": "ObjectId",
  "id": "ObjectId",
  "zoneId": "Z006069000106",
  "zoneName": "District 1 - Ben Thanh",
  "surgeMultiplier": 1.50,
  "latitude": 10.7624,
  "longitude": 106.6597,
  "radiusKm": 2.0,
  "activeDrivers": 15,
  "pendingRides": 45,
  "demandScore": 3.0,
  "minMultiplier": 1.0,
  "maxMultiplier": 3.0,
  "lastUpdated": "2026-05-26T10:00:00",
  "createdAt": "2026-05-01T00:00:00",
  "source": "AUTOMATIC | MANUAL | EVENT_BASED",
  "schemaVersion": "1.0.0"
}
```

#### 3.3.3 `promo_codes` Collection

```json
{
  "_id": "ObjectId",
  "id": "ObjectId",
  "code": "SUMMER2026",
  "description": "Summer promotion 2026",
  "discountType": "PERCENTAGE | FIXED",
  "discountValue": 10.00,
  "maxDiscountAmount": 50000.00,
  "minimumBookingAmount": 50000.00,
  "expiryDate": "2026-08-31T23:59:59",
  "usageLimit": 1000,
  "usedCount": 150,
  "active": true,
  "createdAt": "2026-05-01T00:00:00",
  "updatedAt": "2026-05-26T10:00:00"
}
```

### 3.4 Zone System

Pricing Service sử dụng **Grid-based Zone System** để phân chia khu vực địa lý:

```
Zone ID Format: Z{latZone}{lngZone}

Example:
  Latitude: 10.7624 → Zone: 60762 + 90000 = 150762
  Longitude: 106.6597 → Zone: 60659 + 180000 = 240659
  Zone ID: Z150762240659
```

**Grid Scale**: 1000 (chia latitude/longitude thành các ô ~111m x 111m)

---

## 4. API Endpoints Reference

### 4.1 Pricing API (`/api/v1/pricing`)

#### 4.1.1 Calculate Simple Price (Testing)

```
POST /api/v1/pricing/calculate
```

**Purpose**: Tính giá đơn giản cho testing (không lưu vào DB).

**Request Body**:
```json
{
  "distanceKm": 10.5,
  "demandIndex": 1.5
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `distanceKm` | Double | Yes | Khoảng cách (km) |
| `demandIndex` | Double | Yes | Chỉ số nhu cầu (1.0 = bình thường) |

**Response**:
```json
{
  "distanceKm": 10.5,
  "demandIndex": 1.5,
  "baseFare": 12000.00,
  "distanceFare": 89250.00,
  "surgeMultiplier": 1.50,
  "totalFare": 160875.00,
  "message": "Pricing calculated successfully"
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `distanceKm` | Double | Khoảng cách đã tính |
| `demandIndex` | Double | Chỉ số nhu cầu |
| `baseFare` | BigDecimal | Cước phí cơ bản (VND) |
| `distanceFare` | BigDecimal | Cước theo khoảng cách |
| `surgeMultiplier` | BigDecimal | Hệ số surge |
| `totalFare` | BigDecimal | Tổng cước phí |
| `message` | String | Thông báo trạng thái |

---

#### 4.1.2 Get Fare Estimate

```
POST /api/v1/pricing/estimate
Header: Idempotency-Key: <unique-key> (optional)
```

**Purpose**: Tạo ước tính giá và lưu vào DB với trạng thái PENDING.

**Request Body**:
```json
{
  "pickupLat": 10.7624,
  "pickupLng": 106.6597,
  "dropoffLat": 10.8231,
  "dropoffLng": 106.6297,
  "vehicleType": "CAR4",
  "estimatedDurationMinutes": 25,
  "promoCode": "SUMMER2026"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `pickupLat` | Double | Yes | Vĩ độ điểm đón (-90 to 90) |
| `pickupLng` | Double | Yes | Kinh độ điểm đón (-180 to 180) |
| `dropoffLat` | Double | Yes | Vĩ độ điểm trả (-90 to 90) |
| `dropoffLng` | Double | Yes | Kinh độ điểm trả (-180 to 180) |
| `vehicleType` | String | Yes | Loại xe: `BIKE`, `CAR4`, `CAR7` |
| `estimatedDurationMinutes` | Integer | No | Thời gian ước tính (phút). Nếu không truyền, sẽ lấy từ Mapbox |
| `promoCode` | String | No | Mã khuyến mãi (nếu có) |

**Response**:
```json
{
  "estimateId": "550e8400-e29b-41d4-a716-446655440000",
  "pickupZone": "Z150762240659",
  "dropoffZone": "Z150823240629",
  "vehicleType": "CAR4",
  "distanceKm": 8.5,
  "durationMinutes": 25,
  "baseFare": 12000.00,
  "distanceFare": 72250.00,
  "timeFare": 30000.00,
  "platformFee": 2000.00,
  "zoneFee": 0.00,
  "airportFee": 0.00,
  "tollFee": 0.00,
  "discountAmount": 10642.50,
  "promoCode": "SUMMER2026",
  "surgeMultiplier": 1.50,
  "totalFare": 155607.50,
  "currency": "VND",
  "pricingConfigVersion": "v1",
  "distanceSource": "MAPBOX",
  "weatherCondition": "Clear sky",
  "weatherSource": "OPEN_METEO",
  "fallbackUsed": false,
  "expiresAt": "2026-05-26T10:15:00",
  "quoteId": "550e8400-e29b-41d4-a716-446655440000",
  "quotePayloadHash": "a1b2c3d4e5f6...",
  "quoteHashAlgorithm": "SHA-256",
  "message": "Fare estimate generated successfully"
}
```

**Response Fields Detail**:

| Field | Type | Description |
|-------|------|-------------|
| `estimateId` | String (UUID) | ID duy nhất của ước tính |
| `pickupZone` | String | Zone ID điểm đón (Grid-based) |
| `dropoffZone` | String | Zone ID điểm trả |
| `vehicleType` | String | Loại xe: BIKE, CAR4, CAR7 |
| `distanceKm` | Double | Khoảng cách từ Mapbox (km) |
| `durationMinutes` | Integer | Thời gian di chuyển (phút) |
| `baseFare` | BigDecimal | Cước phí cơ bản (theo loại xe) |
| `distanceFare` | BigDecimal | Cước theo khoảng cách (distance × perKmRate) |
| `timeFare` | BigDecimal | Cước theo thời gian (duration × perMinute) |
| `platformFee` | BigDecimal | Phí nền tảng (fixed: 2000 VND) |
| `zoneFee` | BigDecimal | Phí zone đặc biệt (hiện tại: 0) |
| `airportFee` | BigDecimal | Phí sân bay (hiện tại: 0) |
| `tollFee` | BigDecimal | Phí cầu đường (hiện tại: 0) |
| `discountAmount` | BigDecimal | Số tiền giảm từ promo code |
| `promoCode` | String | Mã khuyến mãi đã áp dụng |
| `surgeMultiplier` | BigDecimal | Hệ số surge (1.0 - 3.0) |
| `totalFare` | BigDecimal | **Tổng cước phí cuối cùng** |
| `currency` | String | Đơn vị tiền tệ (VND) |
| `pricingConfigVersion` | String | Phiên bản cấu hình giá |
| `distanceSource` | String | Nguồn dữ liệu khoảng cách |
| `weatherCondition` | String | Mô tả thời tiết |
| `weatherSource` | String | Nguồn dữ liệu thời tiết |
| `fallbackUsed` | Boolean | True nếu dùng Haversine fallback |
| `expiresAt` | LocalDateTime | Thời điểm hết hạn (15 phút) |
| `quoteId` | String | ID quote (trùng với estimateId) |
| `quotePayloadHash` | String | SHA-256 hash để verify |
| `quoteHashAlgorithm` | String | Thuật toán hash (SHA-256) |
| `message` | String | Thông báo trạng thái |

---

#### 4.1.3 Confirm Fare Estimate

```
POST /api/v1/pricing/confirm/{estimateId}
Header: X-Quote-Hash: <quotePayloadHash> (optional, recommended)
```

**Purpose**: Xác nhận ước tính để khóa giá. Chỉ có estimate ở trạng thái PENDING và chưa hết hạn mới được confirm.

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `estimateId` | String | Yes | ID của estimate cần confirm |

**Request Headers**:
| Header | Required | Description |
|--------|----------|-------------|
| `X-Quote-Hash` | No (recommended) | SHA-256 hash của quote payload |

**Response (200 OK)**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "pickupZone": "Z150762240659",
  "dropoffZone": "Z150823240629",
  "vehicleType": "CAR4",
  "distanceKm": 8.5,
  "durationMinutes": 25,
  "baseFare": 12000.00,
  "distanceFare": 72250.00,
  "timeFare": 30000.00,
  "platformFee": 2000.00,
  "zoneFee": 0.00,
  "airportFee": 0.00,
  "tollFee": 0.00,
  "discountAmount": 10642.50,
  "promoCode": "SUMMER2026",
  "surgeMultiplier": 1.50,
  "totalFare": 155607.50,
  "currency": "VND",
  "pricingConfigVersion": "v1",
  "distanceSource": "MAPBOX",
  "weatherCondition": "Clear sky",
  "weatherSource": "OPEN_METEO",
  "fallbackUsed": false,
  "status": "CONFIRMED",
  "createdAt": "2026-05-26T10:00:00",
  "expiresAt": "2026-05-26T10:15:00",
  "quoteId": "550e8400-e29b-41d4-a716-446655440000",
  "quotePayloadHash": "a1b2c3d4e5f6...",
  "quoteHashAlgorithm": "SHA-256"
}
```

**Error Responses**:
| HTTP Code | Error Code | Description |
|-----------|------------|-------------|
| 404 | `ESTIMATE_NOT_FOUND` | Estimate không tồn tại |
| 400 | `INVALID_STATUS` | Estimate không ở trạng thái PENDING |
| 400 | `ESTIMATE_EXPIRED` | Estimate đã hết hạn |
| 400 | `QUOTE_HASH_MISMATCH` | Hash không khớp (bảo mật) |

---

#### 4.1.4 Get Surge Multiplier

```
GET /api/v1/pricing/surge/{zoneId}
```

**Purpose**: Lấy hệ số surge hiện tại của một zone.

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `zoneId` | String | Yes | Zone ID (format: Zxxxxxx) |

**Response**:
```json
{
  "zone_id": "Z150762240659",
  "surge_multiplier": 1.50,
  "message": "Surge multiplier retrieved successfully"
}
```

---

#### 4.1.5 Update Surge Multiplier (Admin)

```
PUT /api/v1/pricing/surge/{zoneId}
```

**Purpose**: Cập nhật hệ số surge thủ công cho một zone (Admin only).

**Request Body**:
```json
{
  "multiplier": 2.0
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `multiplier` | BigDecimal | Yes | Hệ số surge mới (1.0 - 3.0) |

---

#### 4.1.6 Update Demand/Supply Metrics

```
POST /api/v1/pricing/demand-supply
```

**Purpose**: Cập nhật metrics demand/supply để tính surge pricing. Metrics được cache trong Redis và surge được tính toán bất đồng bộ bởi scheduler.

**Request Body**:
```json
{
  "zoneId": "Z150762240659",
  "activeDrivers": 15,
  "pendingRides": 45
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `zoneId` | String | Yes | Zone ID |
| `activeDrivers` | Integer | Yes | Số tài xế đang hoạt động |
| `pendingRides` | Integer | Yes | Số chuyến đi đang chờ |

**Response**:
```json
{
  "zone_id": "Z150762240659",
  "active_drivers": 15,
  "pending_rides": 45,
  "message": "Demand/supply metrics cached successfully."
}
```

---

#### 4.1.7 Get Zone Metrics

```
GET /api/v1/pricing/zones/{zoneId}/metrics
```

**Purpose**: Lấy metrics demand/supply hiện tại của một zone.

**Response**:
```json
{
  "zone_id": "Z150762240659",
  "active_drivers": 15,
  "pending_rides": 45,
  "updated_at": "2026-05-26T10:00:00",
  "demand_ratio": "3.00"
}
```

---

#### 4.1.8 Get All Surge Multipliers

```
GET /api/v1/pricing/surge/all
```

**Purpose**: Lấy tất cả hệ số surge của các zone đang active.

**Response**:
```json
{
  "Z150762240659": 1.50,
  "Z150823240629": 1.20,
  "Z150800240650": 1.00
}
```

---

#### 4.1.9 Get Pricing Configuration

```
GET /api/v1/pricing/config
```

**Purpose**: Lấy cấu hình giá hiện tại.

**Response**:
```json
{
  "calculation": {
    "baseFare": 12000,
    "perKmRate": 8500,
    "perMinuteRate": 1200,
    "minimumFare": 25000,
    "platformFee": 2000,
    "airportFee": 0,
    "tollFee": 0,
    "currency": "VND",
    "configVersion": "v1"
  },
  "vehicle": {
    "bike": {
      "multiplier": 1.0,
      "baseFare": 8000,
      "perKm": 3500,
      "perMinute": 400
    },
    "car4": {
      "multiplier": 1.0,
      "baseFare": 12000,
      "perKm": 8500,
      "perMinute": 1200
    },
    "car7": {
      "multiplier": 1.5,
      "baseFare": 20000,
      "perKm": 12000,
      "perMinute": 1800
    }
  },
  "surge": {
    "defaultMultiplier": 1.0,
    "minMultiplier": 1.0,
    "maxMultiplier": 3.0,
    "updateThreshold": 0.1,
    "cacheTtlSeconds": 300,
    "metricsTtlSeconds": 600
  },
  "weather": {
    "badWeatherAdjustment": 0.2
  },
  "cache": {
    "weatherTtlSeconds": 1800,
    "routeTtlSeconds": 300
  },
  "eta": {
    "fallbackDurationMinutes": 15
  }
}
```

---

#### 4.1.10 Revenue Statistics

```
GET /api/v1/pricing/revenue/statistics
GET /api/v1/pricing/revenue/daily/{date}
GET /api/v1/pricing/revenue/weekly
GET /api/v1/pricing/revenue/monthly
GET /api/v1/pricing/revenue/zone/{zoneId}
GET /api/v1/pricing/revenue/vehicle/{vehicleType}
```

**Query Parameters** (optional):
| Parameter | Type | Description |
|-----------|------|-------------|
| `startDate` | LocalDateTime | Ngày bắt đầu (ISO format) |
| `endDate` | LocalDateTime | Ngày kết thúc (ISO format) |

**Response**:
```json
{
  "totalRevenue": 150000000.00,
  "totalTrips": 1200,
  "averageFare": 125000.00,
  "dateRange": {
    "startDate": "2026-05-01T00:00:00",
    "endDate": "2026-05-26T23:59:59"
  },
  "dailyRevenueSummaries": [...],
  "vehicleTypeRevenueSummaries": [...],
  "zoneRevenueSummaries": [...]
}
```

---

#### 4.1.11 Estimate Management

```
GET  /api/v1/pricing/estimate/{estimateId}
DELETE /api/v1/pricing/estimate/{estimateId}
GET  /api/v1/pricing/estimates
```

---

#### 4.1.12 Compute Surge

```
POST /api/v1/pricing/surge/compute/{zoneId}
Query: badWeather (boolean, default: false)
```

**Purpose**: Trigger tính surge thủ công cho một zone.

**Response**:
```json
{
  "zone_id": "Z150762240659",
  "previous_multiplier": 1.0,
  "predicted_multiplier": 1.5,
  "updated": true,
  "message": "Surge multiplier updated"
}
```

---

### 4.2 Admin API (`/api/v1/admin`)

#### 4.2.1 Pricing Config Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/pricing-configs` | Tạo cấu hình giá mới |
| GET | `/pricing-configs` | Lấy tất cả cấu hình |
| GET | `/pricing-configs/{id}` | Lấy theo ID |
| GET | `/pricing-configs/vehicle/{vehicleType}` | Lấy theo loại xe |
| PUT | `/pricing-configs/{id}` | Cập nhật |
| DELETE | `/pricing-configs/{id}` | Xóa |
| PATCH | `/pricing-configs/{id}/toggle` | Toggle active/inactive |

#### 4.2.2 Surge Rule Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/surge-rules` | Tạo surge rule mới |
| GET | `/surge-rules` | Lấy tất cả surge rules |
| GET | `/surge-rules/{id}` | Lấy theo ID |
| GET | `/surge-rules/zone/{zoneId}` | Lấy theo zone |
| PUT | `/surge-rules/{id}` | Cập nhật |
| DELETE | `/surge-rules/{id}` | Xóa |
| DELETE | `/surge-rules/zone/{zoneId}` | Xóa theo zone |
| POST | `/surge-rules/bulk` | Tạo nhiều surge rules |
| POST | `/zone-metrics/bulk` | Cập nhật metrics nhiều zone |

#### 4.2.3 Dashboard

```
GET /api/v1/admin/dashboard
```

---

### 4.3 Promo Code Admin API (`/api/v1/admin/promo-codes`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Tạo promo code mới |
| GET | `/` | Lấy tất cả promo codes |
| GET | `/{id}` | Lấy theo ID |
| PUT | `/{id}` | Cập nhật |
| DELETE | `/{id}` | Xóa |

**Promo Code Request**:
```json
{
  "code": "SUMMER2026",
  "description": "Summer promotion 2026",
  "discountType": "PERCENTAGE",
  "discountValue": 10.00,
  "maxDiscountAmount": 50000.00,
  "minimumBookingAmount": 50000.00,
  "expiryDate": "2026-08-31T23:59:59",
  "usageLimit": 1000,
  "active": true
}
```

---

## 5. Fare Calculation Formulas

### 5.1 Main Fare Formula

```
totalFare = max(minimumFare, (subtotal × surgeMultiplier) - discountAmount)

subtotal = baseFare + distanceFare + timeFare + platformFee + zoneFee + airportFee + tollFee
```

### 5.2 Component Formulas

```
distanceFare = distanceKm × perKmRate (theo loại xe)
timeFare = durationMinutes × perMinute (theo loại xe)
```

### 5.3 Surge Multiplier Formula

```
surgeMultiplier = clamp(
  1.0
  + demandAdjustment
  + timeAdjustment
  + weatherAdjustment
  + manualAdjustment
)

demandAdjustment:
  - ratio < highDemandRatio (1.5): 0.0
  - ratio >= highDemandRatio: 0.2
  - ratio >= veryHighDemandRatio (3.0): 0.5
  - ratio = pendingRides / max(activeDrivers, 1)

timeAdjustment:
  - Rush hour (07:00-09:00, 17:00-19:00): +0.2
  - Night (23:00-05:00): +0.1
  - Other: 0.0

weatherAdjustment:
  - Bad weather (Rain/Snow/Thunderstorm/Drizzle/Fog): +0.2
  - Other: 0.0

manualAdjustment:
  - Configured manually by admin: variable

Final clamp: minMultiplier (1.0) ≤ surgeMultiplier ≤ maxMultiplier (3.0)
```

### 5.4 Quote Payload Hash

```
canonical = quoteId | pickupZone | dropoffZone | vehicleType | distanceKm | durationMinutes | totalFare | currency
quotePayloadHash = SHA-256(canonical)
```

### 5.5 Zone Determination

```
latZone = floor(lat × 1000) + 90000
lngZone = floor(lng × 1000) + 180000
zoneId = String.format("Z%06d%06d", latZone, lngZone)
```

---

## 6. Activity Diagram - Complete Booking Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          COMPLETE BOOKING FLOW                               │
└─────────────────────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │   CLIENT    │
    │  (Mobile/   │
    │    Web)     │
    └──────┬──────┘
           │
           │ 1. User enters pickup/dropoff locations
           ▼
    ┌──────────────────────────────────────┐
    │  STEP 1: GET FARE ESTIMATE            │
    │  POST /api/v1/pricing/estimate        │
    │  Body: {                             │
    │    pickupLat, pickupLng,              │
    │    dropoffLat, dropoffLng,            │
    │    vehicleType,                      │
    │    promoCode (optional)               │
    │  }                                    │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  PRICING SERVICE PROCESSING:         │
    │                                       │
    │  1.1 Check Idempotency Key (Redis)   │
    │      → Return cached estimate if exists│
    │                                       │
    │  1.2 Determine Zone (Grid-based)    │
    │      pickupZone = ZoneService         │
    │      dropoffZone = ZoneService        │
    │                                       │
    │  1.3 Get Route Estimate              │
    │      → Check Redis cache first       │
    │      → Call Mapbox Distance Matrix   │
    │         (with Circuit Breaker)       │
    │      → Fallback: Haversine × 1.3     │
    │                                       │
    │  1.4 Get Weather Context              │
    │      → Check Redis cache first       │
    │      → Call OpenMeteo API            │
    │      → Fallback: surcharge disabled   │
    │                                       │
    │  1.5 Compute Surge Multiplier        │
    │      → Get zone metrics (Redis)       │
    │      → RuleBasedSurgeCalculator:     │
    │        demand + time + weather        │
    │      → Clamp: 1.0 ≤ surge ≤ 3.0      │
    │                                       │
    │  1.6 Calculate Fare Breakdown        │
    │      baseFare + distanceFare +       │
    │      timeFare + fees                 │
    │                                       │
    │  1.7 Apply Promo Code (if provided)  │
    │      → PromoCodeService.validate     │
    │      → Calculate discount            │
    │                                       │
    │  1.8 Generate Quote Hash            │
    │      → SHA-256(payload)             │
    │                                       │
    │  1.9 Save FareEstimate (MongoDB)    │
    │      → Status: PENDING               │
    │      → expiresAt: now + 15 min       │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  RESPONSE: FareEstimateResponse      │
    │  {                                   │
    │    estimateId,                       │
    │    totalFare,                        │
    │    quotePayloadHash,                 │
    │    expiresAt,                        │
    │    ...                               │
    │  }                                    │
    └──────────────┬───────────────────────┘
                   │
                   │ 2. User reviews fare
                   │ 3. User confirms booking
                   ▼
    ┌──────────────────────────────────────┐
    │  STEP 2: CONFIRM FARE               │
    │  POST /api/v1/pricing/confirm/{id}   │
    │  Header: X-Quote-Hash: <hash>        │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  PRICING SERVICE:                    │
    │                                       │
    │  2.1 Atomic Confirm (MongoDB)        │
    │      findAndModify:                  │
    │        _id = estimateId             │
    │        status = PENDING             │
    │        expiresAt > now              │
    │      → Set status = CONFIRMED       │
    │                                       │
    │  2.2 Verify Quote Hash (optional)   │
    │      → Compare presented hash       │
    │        with stored hash             │
    │      → Throw QUOTE_HASH_MISMATCH    │
    │        if different                 │
    │                                       │
    │  2.3 Increment Promo Usage          │
    │      (if promo applied)              │
    │                                       │
    │  2.4 Update Estimate Status         │
    └──────────────┬───────────────────────┘
                   │
                   │ 4. Booking Service receives confirmed fare
                   ▼
    ┌──────────────────────────────────────┐
    │  STEP 3: CREATE BOOKING               │
    │  POST /api/v1/bookings                │
    │  (Booking Service)                   │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  BOOKING SERVICE:                    │
    │                                       │
    │  3.1 Idempotency Check               │
    │      → Redis SETNX lock              │
    │      → DB unique constraint          │
    │                                       │
    │  3.2 Verify Fare (confirm quote)    │
    │      → PricingService.confirm        │
    │      → Verify hash mismatch          │
    │                                       │
    │  3.3 Save Booking (PostgreSQL)       │
    │      → Store quotePayloadHash       │
    │      → Store quoteId, estimateId     │
    │                                       │
    │  3.4 Determine Payment Flow:         │
    │      │                               │
    │      ├─► CASH:                       │
    │      │    → Status: MATCHING         │
    │      │    → Publish ride.created     │
    │      │       (Kafka)                 │
    │      │                               │
    │      └─► ONLINE PAYMENT:             │
    │           → Status: PENDING_PAYMENT  │
    │           → Publish payment.requested │
    │              (Kafka)                 │
    │           → Wait for payment.completed│
    │           → Then: MATCHING          │
    │           → Publish ride.created    │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  STEP 4: MATCHING (if CASH or paid)  │
    │  (Matching Service + AI Scoring)     │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  MATCHING SERVICE:                    │
    │                                       │
    │  4.1 Receive ride.created event      │
    │      (Kafka Consumer)                │
    │                                       │
    │  4.2 Query Nearby Drivers            │
    │      → driver-service: drivers       │
    │        in radius (3km default)       │
    │      → Filter: ONLINE, vehicle match │
    │                                       │
    │  4.3 AI Scoring (ai-scoring-service)│
    │      → Get driver features:          │
    │        distance, eta, rating, price  │
    │      → Normalize features            │
    │      → Calculate weighted score      │
    │      → Rank drivers                  │
    │      → Select top N drivers         │
    │                                       │
    │  4.4 Send Ride Requests             │
    │      → notification-service         │
    │      → Socket.IO push to drivers    │
    │      → Set timeout (30 seconds)    │
    │                                       │
    │  4.5 Wait for Response               │
    │      │                               │
    │      ├─► ACCEPT:                     │
    │      │    → Status: ASSIGNED        │
    │      │    → Assign driver           │
    │      │                               │
    │      ├─► REJECT/NO_RESPONSE:        │
    │      │    → Try next driver         │
    │      │    → If all rejected:        │
    │      │       Status: MATCHING       │
    │      │       (rematch after timeout)│
    │      │                               │
    │      └─► TIMEOUT:                    │
    │           → Rematch or cancel       │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  STEP 5: RIDE LIFECYCLE               │
    │  (Ride Service)                       │
    └──────────────┬───────────────────────┘
                   │
           ┌───────┴───────┐
           │               │
           ▼               ▼
    ┌──────────────┐  ┌──────────────┐
    │  ACCEPTED    │  │  CANCELLED  │
    │  (Driver     │  │  (Customer/ │
    │   accepted)  │  │   Driver/   │
    │              │  │   System)   │
    └──────┬───────┘  └──────────────┘
           │
           ▼
    ┌──────────────┐
    │  PICKUP       │
    │  (Driver      │
    │   arrives)    │
    └──────┬───────┘
           │
           ▼
    ┌──────────────────────────────────────┐
    │  IN_PROGRESS (Trip started)          │
    │                                       │
    │  Track GPS location                  │
    │  Calculate actual distance/time      │
    │                                       │
    └──────────────┬───────────────────────┘
           │
           ▼
    ┌──────────────────────────────────────┐
    │  COMPLETED (Trip finished)            │
    │                                       │
    │  5.1 Calculate Final Fare            │
    │      → PricingService.applyFinal    │
    │      → Use actual distance/duration  │
    │      → Apply current surge           │
    │                                       │
    │  5.2 Process Payment                 │
    │      │                               │
    │      ├─► CASH: Done                  │
    │      │                               │
    │      └─► ONLINE:                    │
    │           → PaymentService.process   │
    │           → Kafka: payment.completed │
    │                                       │
    │  5.3 Publish ride.completed (Kafka) │
    │      → Update zone metrics          │
    │      → Update driver stats          │
    │      → Send notifications           │
    │                                       │
    │  5.4 Update Surge Pricing           │
    │      → Decrement pendingRides       │
    │      → Recalculate zone surge       │
    └──────────────┬───────────────────────┘
                   │
                   ▼
    ┌──────────────────────────────────────┐
    │  STEP 6: REVIEW (Optional)            │
    │  POST /api/v1/reviews                │
    └──────────────────────────────────────┘
```

---

## 7. Sequence Diagram - Service Communication

### 7.1 Fare Estimation Sequence

```
┌────────┐    ┌───────────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────────┐
│ Client │    │ Pricing Svc   │    │    Redis     │    │   Mapbox     │    │  OpenMeteo    │
└───┬────┘    └───────┬───────┘    └──────┬───────┘    └──────┬───────┘    └───────┬───────┘
    │                 │                    │                   │                  │
    │ POST /estimate  │                   │                   │                  │
    │────────────────►│                   │                   │                  │
    │                 │                    │                   │                  │
    │                 │ Check Idempotency │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │◄─────────────────────────────────────│                  │
    │                 │   (cached estimate or null)          │                  │
    │                 │                    │                   │                  │
    │                 │ Determine Zone     │                   │                  │
    │                 │ (lat/lng → zoneId)│                   │                  │
    │                 │                    │                   │                  │
    │                 │ Check Route Cache  │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │◄─────────────────────────────────────│                  │
    │                 │   (cached or null)                   │                  │
    │                 │                    │                   │                  │
    │                 │                    │     GET Distance Matrix
    │                 │─────────────────────────────────────────────────────────►
    │                 │◄─────────────────────────────────────────────────────────
    │                 │   (distance/duration)                   │                  │
    │                 │                    │                   │                  │
    │                 │ Cache Route        │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │                    │                   │                  │
    │                 │                    │     GET Weather   │                  │
    │                 │─────────────────────────────────────────────────────────►
    │                 │◄─────────────────────────────────────────────────────────
    │                 │   (weather code)                    │                  │
    │                 │                    │                   │                  │
    │                 │ Cache Weather      │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │                    │                   │                  │
    │                 │ Get Zone Metrics  │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │◄─────────────────────────────────────│                  │
    │                 │   (activeDrivers, pendingRides)       │                  │
    │                 │                    │                   │                  │
    │                 │ Compute Surge     │                   │                  │
    │                 │ (RuleBasedEngine) │                   │                  │
    │                 │                    │                   │                  │
    │                 │ Calculate Fare    │                   │                  │
    │                 │ (base + distance  │                   │                  │
    │                 │  + time + surge)  │                   │                  │
    │                 │                    │                   │                  │
    │                 │ Validate Promo    │                   │                  │
    │                 │ (if provided)     │                   │                  │
    │                 │                    │                   │                  │
    │                 │ Generate Hash     │                   │                  │
    │                 │ (SHA-256)        │                   │                  │
    │                 │                    │                   │                  │
    │                 │ Save FareEstimate │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │   (PENDING, expiresAt +15min)         │                  │
    │                 │                    │                   │                  │
    │                 │ Cache Idempotency │                   │                  │
    │                 │─────────────────────────────────────►│                  │
    │                 │                    │                   │                  │
    │◄────────────────│ FareEstimateResponse                   │                  │
    │   (200 OK)      │                    │                   │                  │
```

### 7.2 Booking Creation Sequence (CASH Payment)

```
┌────────┐   ┌───────────────┐   ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Client │   │ Booking Svc   │   │ Pricing Svc   │   │    Redis      │   │    Kafka      │
└───┬────┘   └───────┬───────┘   └───────┬───────┘   └───────┬───────┘   └───────┬───────┘
    │                │                    │                    │                    │
    │ POST /bookings │                    │                    │                    │
    │───────────────►│                    │                    │                    │
    │                │                    │                    │                    │
    │                │ Acquire Redis Lock │                    │                    │
    │                │ (SETNX idempotency)│                    │                    │
    │                │────────────────────────────────────────►│                    │
    │                │◄────────────────────────────────────────│                    │
    │                │                    │                    │                    │
    │                │ POST /confirm/{id} │                    │                    │
    │                │───────────────────►│                    │                    │
    │                │                    │                    │                    │
    │                │                    │ findAndModify (atomic)                    │
    │                │                    │ (status=PENDING, expiresAt>now)            │
    │                │                    │                    │                    │
    │                │◄───────────────────│ FareEstimate (CONFIRMED)                  │
    │                │                    │                    │                    │
    │                │ Verify Hash        │                    │                    │
    │                │ (security check)   │                    │                    │
    │                │                    │                    │                    │
    │                │ Save Booking (PG)  │                    │                    │
    │                │─────────────────────────────────────────►│                    │
    │                │◄─────────────────────────────────────────│                    │
    │                │                    │                    │                    │
    │                │ Status: MATCHING   │                    │                    │
    │                │                    │                    │                    │
    │                │ Publish ride.created│                    │                    │
    │                │─────────────────────────────────────────────────────────────►
    │                │                    │                    │                    │
    │                │ Add to Timeout Q   │                    │                    │
    │                │─────────────────────────────────────────►│                    │
    │                │                    │                    │                    │
    │◄───────────────│ BookingResponse    │                    │                    │
    │   (201 Created)│                    │                    │                    │
```

### 7.3 Driver Matching Sequence

```
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
│ Booking Svc│  │  Matching  │  │   Driver   │  │ AI Scoring │  │ Notif Svc  │  │   Redis    │
│            │  │   Service  │  │  Service   │  │  Service   │  │            │  │            │
└─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
      │               │               │               │               │               │
      │ ride.created  │               │               │               │               │
      │ (Kafka)       │               │               │               │               │
      │──────────────►│               │               │               │               │
      │               │               │               │               │               │
      │               │ Query drivers │               │               │               │
      │               │ (in radius)  │               │               │               │
      │               │──────────────►│               │               │               │
      │               │◄──────────────│               │               │               │
      │               │ (driver list) │               │               │               │
      │               │               │               │               │               │
      │               │ Score drivers │               │               │               │
      │               │──────────────►│               │               │               │
      │               │◄──────────────│               │               │               │
      │               │ (features)    │               │               │               │
      │               │               │               │               │               │
      │               │ POST /score   │               │               │               │
      │               │ (rank drivers)│               │               │               │
      │               │──────────────►│               │               │               │
      │               │◄──────────────│               │               │               │
      │               │ (ranked list) │               │               │               │
      │               │               │               │               │               │
      │               │ Push to top N │               │               │               │
      │               │ drivers        │               │               │               │
      │               │──────────────►│               │               │               │
      │               │               │               │               │               │
      │               │               │ Send Socket.IO│               │               │
      │               │               │──────────────►│               │               │
      │               │               │               │               │               │
      │               │ Wait 30s...   │               │               │               │
      │               │               │               │               │               │
      │               │ (Driver accepts)│              │               │               │
      │               │◄──────────────│               │               │               │
      │               │               │               │               │               │
      │               │ Update Booking │               │               │               │
      │               │ Status:ASSIGNED│              │               │               │
      │               │──────────────►│               │               │               │
      │               │               │               │               │               │
      │               │               │ Update driver │               │               │
      │               │               │ status: BUSY  │               │               │
      │               │               │──────────────►│               │               │
      │               │               │               │               │               │
      │               │               │ Publish driver.status.changed (Kafka)         │
      │               │               │───────────────────────────────────────────────►
      │               │               │               │               │               │
```

### 7.4 Surge Pricing Update Sequence

```
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
│  Pricing   │  │   Redis   │  │  MongoDB   │  │   Kafka    │
│  Service   │  │           │  │           │  │           │
└─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
      │               │               │               │
      │ (Scheduler    │               │               │
      │  every 60s)   │               │               │
      │               │               │               │
      │ Get active    │               │               │
      │ zones list    │               │               │
      │──────────────►│               │               │
      │◄──────────────│               │               │
      │               │               │               │
      │ For each zone:│               │               │
      │               │               │               │
      │ Get metrics   │               │               │
      │──────────────►│               │               │
      │◄──────────────│               │               │
      │(drivers,rides)│               │               │
      │               │               │               │
      │ Calc surge    │               │               │
      │(demand+time  │               │               │
      │ +weather)     │               │               │
      │               │               │               │
      │ Compare with  │               │               │
      │ current       │               │               │
      │ (threshold)   │               │               │
      │               │               │               │
      │ If updated:   │               │               │
      │ Save to Mongo │               │               │
      │──────────────►│               │               │
      │               │               │               │
      │ Cache surge   │               │               │
      │──────────────►│               │               │
      │               │               │               │
      │ Publish       │               │               │
      │ surge.updated │               │               │
      │ (Kafka)       │               │               │
      │──────────────►│               │               │
      │               │               │               │
```

---

## 8. Database Models

### 8.1 Pricing Service (MongoDB)

#### FareEstimate Document

| Field | Type | Description |
|-------|------|-------------|
| `_id` | ObjectId | MongoDB document ID |
| `id` | String (UUID) | Estimate ID (application-level) |
| `rideId` | String | Liên kết với ride (sau khi booking) |
| `pickupZone` | String | Zone ID điểm đón |
| `dropoffZone` | String | Zone ID điểm trả |
| `pickupLat/Lng` | Double | Tọa độ điểm đón |
| `dropoffLat/Lng` | Double | Tọa độ điểm trả |
| `vehicleType` | String | BIKE, CAR4, CAR7 |
| `distanceKm` | Double | Khoảng cách (km) |
| `durationMinutes` | Integer | Thời gian (phút) |
| `baseFare` | BigDecimal | Cước cơ bản |
| `distanceFare` | BigDecimal | Cước khoảng cách |
| `timeFare` | BigDecimal | Cước thời gian |
| `platformFee` | BigDecimal | Phí nền tảng |
| `zoneFee` | BigDecimal | Phí zone |
| `airportFee` | BigDecimal | Phí sân bay |
| `tollFee` | BigDecimal | Phí cầu đường |
| `discountAmount` | BigDecimal | Số tiền giảm |
| `promoCode` | String | Mã khuyến mãi |
| `surgeMultiplier` | BigDecimal | Hệ số surge (1.0-3.0) |
| `totalFare` | BigDecimal | **Tổng cước** |
| `currency` | String | VND |
| `pricingConfigVersion` | String | Phiên bản config |
| `distanceSource` | String | MAPBOX/HAVERSINE_FALLBACK |
| `weatherCondition` | String | Mô tả thời tiết |
| `weatherSource` | String | Nguồn thời tiết |
| `fallbackUsed` | Boolean | Có dùng fallback không |
| `status` | String | PENDING/CONFIRMED/EXPIRED/CANCELLED |
| `createdAt` | LocalDateTime | Thời điểm tạo |
| `expiresAt` | LocalDateTime | Thời điểm hết hạn |
| `quoteId` | String | Quote ID (trùng id) |
| `quotePayloadHash` | String | SHA-256 hash |
| `quoteHashAlgorithm` | String | SHA-256 |

#### SurgeRule Document

| Field | Type | Description |
|-------|------|-------------|
| `_id` | ObjectId | MongoDB document ID |
| `zoneId` | String (indexed) | Zone ID duy nhất |
| `zoneName` | String | Tên zone |
| `surgeMultiplier` | BigDecimal | Hệ số surge hiện tại |
| `latitude/longitude` | Double | Tọa độ trung tâm zone |
| `radiusKm` | Double | Bán kính zone |
| `activeDrivers` | Integer | Số driver đang active |
| `pendingRides` | Integer | Số ride đang chờ |
| `demandScore` | Double | Điểm nhu cầu |
| `minMultiplier` | BigDecimal | Hệ số tối thiểu |
| `maxMultiplier` | BigDecimal | Hệ số tối đa |
| `lastUpdated` | LocalDateTime | Cập nhật lần cuối |
| `createdAt` | LocalDateTime | Thời điểm tạo |
| `source` | String | AUTOMATIC/MANUAL/EVENT_BASED |

#### PromoCode Document

| Field | Type | Description |
|-------|------|-------------|
| `_id` | ObjectId | MongoDB document ID |
| `code` | String (unique) | Mã khuyến mãi |
| `description` | String | Mô tả |
| `discountType` | Enum | FIXED, PERCENTAGE |
| `discountValue` | BigDecimal | Giá trị giảm |
| `maxDiscountAmount` | BigDecimal | Giảm tối đa (% only) |
| `minimumBookingAmount` | BigDecimal | Đơn tối thiểu |
| `expiryDate` | LocalDateTime | Ngày hết hạn |
| `usageLimit` | Integer | Giới hạn sử dụng |
| `usedCount` | Integer | Đã sử dụng |
| `active` | Boolean | Còn hoạt động không |

### 8.2 Booking Service (PostgreSQL)

#### Bookings Table

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PK | Booking ID |
| `version` | Integer | | JPA optimistic lock |
| `customer_id` | VARCHAR | NOT NULL | User ID |
| `assigned_driver_id` | VARCHAR | | Driver ID |
| `pickup_location` | VARCHAR | NOT NULL | Địa chỉ điểm đón |
| `dropoff_location` | VARCHAR | NOT NULL | Địa chỉ điểm trả |
| `customer_note` | VARCHAR(1000) | | Ghi chú |
| `pickup_lat/lng` | DOUBLE | | Tọa độ điểm đón |
| `dropoff_lat/lng` | DOUBLE | | Tọa độ điểm trả |
| `vehicle_type` | VARCHAR(20) | NOT NULL | BIKE/CAR4/CAR7 |
| `payment_method` | VARCHAR | | CASH/MOMO/VNPAY |
| `estimated_fare` | DECIMAL(12,2) | | Giá ước tính |
| `discount_amount` | DECIMAL | | Số tiền giảm |
| `promo_code` | VARCHAR | | Mã KM |
| `quote_token` | VARCHAR(2000) | | Zero-trust token |
| `estimate_id` | VARCHAR(64) | | Pricing estimate ID |
| `quote_id` | VARCHAR(64) | UNIQUE | Quote ID |
| `quote_payload_hash` | VARCHAR(128) | | SHA-256 hash |
| `quote_hash_algorithm` | VARCHAR(32) | | Thuật toán hash |
| `quote_expires_at` | TIMESTAMP | | Hết hạn quote |
| `idempotency_key` | VARCHAR(64) | UNIQUE | Idempotency key |
| `status` | VARCHAR | NOT NULL | Trạng thái booking |
| `created_at` | TIMESTAMP | | |
| `updated_at` | TIMESTAMP | | |

#### Indexes

```sql
CREATE INDEX idx_customer_id ON bookings(customer_id);
CREATE INDEX idx_status ON bookings(status);
CREATE UNIQUE INDEX idx_idempotency_key ON bookings(idempotency_key);
CREATE UNIQUE INDEX idx_booking_quote_id ON bookings(quote_id);
```

### 8.3 Driver Service (PostgreSQL)

#### driver_profiles Table

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key |
| `external_user_id` | VARCHAR | User account ID |
| `full_name` | VARCHAR | Họ tên |
| `email` | VARCHAR | Email |
| `phone_number` | VARCHAR | SĐT |
| `license_number` | VARCHAR | GPLX |
| `vehicle_type` | VARCHAR | BIKE/CAR4/CAR7 |
| `vehicle_plate` | VARCHAR | Biển số |
| `vehicle_model` | VARCHAR | Dòng xe |
| `availability_status` | VARCHAR | ONLINE/OFFLINE/BUSY |
| `verification_status` | VARCHAR | PENDING/VERIFIED/REJECTED |
| `current_latitude` | DOUBLE | Vĩ độ hiện tại |
| `current_longitude` | DOUBLE | Kinh độ hiện tại |
| `total_completed_rides` | Integer | Số chuyến đã hoàn thành |
| `average_rating` | DECIMAL(3,2) | Điểm đánh giá trung bình |
| `total_reviews` | Integer | Tổng đánh giá |
| `total_earnings` | DECIMAL | Tổng thu nhập |
| `current_ride_id` | UUID | Ride đang thực hiện |
| `current_booking_id` | UUID | Booking đang thực hiện |

---

## 9. Configuration Reference

### 9.1 Pricing Service Configuration

```yaml
# application.yaml

server:
  port: 8088

spring:
  application:
    name: pricing-service
  data:
    mongodb:
      uri: mongodb://mongodb:27017/pricing_db
      auto-index-creation: true
    redis:
      host: cab-redis
      port: 6379
      password: redis123

# Pricing Configuration
pricing:
  calculation:
    base-fare: 12000        # VND - default base fare
    per-km-rate: 8500      # VND/km - default per km
    per-minute-rate: 1200  # VND/minute - default per minute
    minimum-fare: 25000     # VND - minimum fare
    platform-fee: 2000     # VND - fixed platform fee
    airport-fee: 0          # VND - airport surcharge
    toll-fee: 0             # VND - toll fee
    currency: VND
    config-version: v1

  vehicle:
    bike:
      multiplier: 1.0
      base-fare: 8000       # VND
      per-km: 3500          # VND/km
      per-minute: 400       # VND/minute
    car4:
      multiplier: 1.0
      base-fare: 12000
      per-km: 8500
      per-minute: 1200
    car7:
      multiplier: 1.5
      base-fare: 20000
      per-km: 12000
      per-minute: 1800

  surge:
    default-multiplier: 1.0      # Default surge
    min-multiplier: 1.0          # Min surge (no discount)
    max-multiplier: 3.0          # Max surge cap
    update-threshold: 0.1       # Update if diff >= 0.1
    cache-ttl-seconds: 300       # 5 min cache
    metrics-ttl-seconds: 600    # 10 min metrics TTL
    scheduler-fixed-delay-ms: 60000  # 1 min scheduler
    high-demand-ratio: 1.5      # ratio >= 1.5 → high demand
    very-high-demand-ratio: 3.0 # ratio >= 3.0 → very high demand
    high-demand-adjustment: 0.2 # +20% surge
    very-high-demand-adjustment: 0.5  # +50% surge
    rush-hour-adjustment: 0.2    # +20% during rush hours
    night-adjustment: 0.1       # +10% at night
    manual-adjustment: 0.0       # Admin manual adjustment

  weather:
    bad-weather-adjustment: 0.2  # +20% if bad weather

  cache:
    weather-ttl-seconds: 1800    # 30 min weather cache
    route-ttl-seconds: 300      # 5 min route cache

  estimate:
    expiry-scheduler-fixed-delay-ms: 60000  # 1 min expiry check

  eta:
    fallback-duration-minutes: 15  # Default ETA if Mapbox fails

  zone-metrics:
    sync-fixed-delay-ms: 30000     # 30 sec sync
    driver-service-timeout-ms: 5000
    booking-service-timeout-ms: 5000
    default-radius-km: 2.0

# External APIs
mapbox:
  api-key: ${MAPBOX_API_KEY:dummy_mapbox_key}

# Resilience4j Configuration
resilience4j:
  circuitbreaker:
    instances:
      mapbox:
        sliding-window-size: 20
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
      openMeteo:
        sliding-window-size: 20
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
  timelimiter:
    instances:
      mapbox:
        timeout-duration: 3s
      openMeteo:
        timeout-duration: 3s
  retry:
    instances:
      mapbox:
        max-attempts: 2
        wait-duration: 200ms
      openMeteo:
        max-attempts: 2
        wait-duration: 200ms
  ratelimiter:
    instances:
      mapbox:
        limit-for-period: 50
        limit-refresh-period: 1s
      openMeteo:
        limit-for-period: 50
        limit-refresh-period: 1s
```

### 9.2 Redis Keys Reference

| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `pricing:route:{lat1:lng1:lat2:lng2}` | Hash | 300s | Cached route data |
| `pricing:weather:{zoneId}` | Hash | 1800s | Cached weather |
| `pricing:surge:zone:{zoneId}` | String | 300s | Surge multiplier cache |
| `pricing:metrics:zone:{zoneId}` | Hash | 600s | Zone demand/supply |
| `pricing:idempotency:estimate:{key}` | String | 900s | Idempotency mapping |
| `pricing:ride-zone:{rideId}` | String | 600s | Ride→Zone mapping |
| `pricing:driver-zone:{driverId}` | String | 600s | Driver→Zone mapping |
| `pricing:metrics:active-zones` | Set | - | Active zones set |

### 9.3 Kafka Topics

| Topic | Producer | Consumer | Description |
|-------|----------|----------|-------------|
| `ride.created` | Booking Service | Matching Service | New ride needs driver |
| `ride.cancelled` | Booking Service | Matching, Pricing | Ride cancelled |
| `ride.completed` | Ride Service | Pricing, Driver | Ride finished |
| `driver.status.changed` | Driver Service | Pricing, Matching | Driver status update |
| `driver.location.updated` | Driver Service | Pricing, Matching | Driver GPS update |
| `payment.requested` | Booking Service | Payment Service | Payment needed |
| `payment.completed` | Payment Service | Booking Service | Payment confirmed |
| `demand.supply.updated` | Admin/Test | Pricing Service | Manual metrics update |
| `pricing.surge.updated` | Pricing Service | Monitoring | Surge changed |
| `pricing.estimate.created` | Pricing Service | Analytics | New estimate |
| `pricing.estimate.confirmed` | Pricing Service | Analytics | Estimate confirmed |

---

## 10. Technology Stack

### Backend (Java)

| Category | Technology | Version |
|----------|------------|---------|
| Framework | Spring Boot | 3.3.5 |
| Language | Java | 21 |
| Build Tool | Maven | 3.9+ |
| Service Registry | Netflix Eureka | - |
| API Gateway | Spring Cloud Gateway | - |
| Configuration | Spring Cloud Config | - |
| Database | Spring Data MongoDB | - |
| Database | Spring Data JPA / Hibernate | - |
| Database | PostgreSQL | 15+ |
| Cache | Spring Data Redis | - |
| Messaging | Spring Kafka | - |
| Resilience | Resilience4j | 2.x |
| API Client | OpenFeign | - |
| API Docs | SpringDoc OpenAPI | 2.x |
| Security | Spring Security + JWT | - |
| Logging | SLF4J + Logback | - |
| Testing | JUnit 5 + Mockito | - |

### AI Services (Python)

| Category | Technology | Version |
|----------|------------|---------|
| Framework | FastAPI | 0.100+ |
| Language | Python | 3.11+ |
| AI Model | Google Gemini Flash | 2.0 |
| AI Agent | Function Calling | - |
| HTTP Client | httpx | - |
| Validation | Pydantic | 2.x |

### Infrastructure

| Category | Technology |
|----------|------------|
| Container | Docker + Docker Compose |
| Databases | PostgreSQL, MongoDB |
| Cache | Redis |
| Message Broker | Apache Kafka |
| API Gateway | Spring Cloud Gateway |

### Monitoring & Observability

| Category | Technology |
|----------|------------|
| Metrics | Micrometer + Prometheus |
| Tracing | Spring Cloud Sleuth + Zipkin |
| Health Checks | Spring Actuator |
| API Docs | Swagger UI |

---

## Appendix A: Example Calculations

### A.1 Basic Fare Calculation (CAR4, 8.5km, 25min, no surge, no promo)

```
Vehicle: CAR4
Distance: 8.5 km
Duration: 25 minutes
Surge: 1.0 (normal)
Promo: None

baseFare = 12,000 VND
distanceFare = 8.5 × 8,500 = 72,250 VND
timeFare = 25 × 1,200 = 30,000 VND
platformFee = 2,000 VND
zoneFee = 0 VND
airportFee = 0 VND
tollFee = 0 VND

subtotal = 12,000 + 72,250 + 30,000 + 2,000 + 0 + 0 + 0 = 116,250 VND
subtotalWithSurge = 116,250 × 1.0 = 116,250 VND
discountAmount = 0 VND
totalFare = 116,250 - 0 = 116,250 VND
totalFare >= minimumFare (25,000)? YES → 116,250 VND
```

### A.2 Surge Pricing Calculation (High Demand)

```
Zone: Z150762240659
Active Drivers: 10
Pending Rides: 30
Time: 08:30 (rush hour)
Weather: Clear sky (no bad weather)

demandRatio = 30 / 10 = 3.0
demandRatio >= veryHighDemandRatio (3.0)? YES
→ demandAdjustment = 0.5

timeAdjustment:
  08:30 is between 07:00-09:00? YES
  → timeAdjustment = 0.2

weatherAdjustment = 0.0 (clear sky)

manualAdjustment = 0.0 (no manual adjustment)

rawSurge = 1.0 + 0.5 + 0.2 + 0.0 + 0.0 = 1.7
surgeMultiplier = clamp(1.7, min=1.0, max=3.0) = 1.7
```

### A.3 Fare with Surge and Promo

```
subtotal = 116,250 VND
surgeMultiplier = 1.7
subtotalWithSurge = 116,250 × 1.7 = 197,625 VND

Promo: SUMMER2026 (10% off, max 50,000 VND)
discountAmount = 197,625 × 10% = 19,762.50 VND
discountAmount > maxDiscountAmount (50,000)? NO → 19,762.50 VND

totalFare = 197,625 - 19,762.50 = 177,862.50 VND
```

---

## Appendix B: Error Codes

| Error Code | HTTP | Description |
|------------|------|-------------|
| `ESTIMATE_NOT_FOUND` | 404 | Estimate không tồn tại |
| `ESTIMATE_EXPIRED` | 400 | Estimate đã hết hạn |
| `INVALID_STATUS` | 400 | Estimate không ở trạng thái PENDING |
| `QUOTE_HASH_MISMATCH` | 400 | Hash không khớp (bảo mật) |
| `QUOTE_CONFIRMATION_FAILED` | 400 | Confirm thất bại |
| `PRICING_CALCULATION_FAILED` | 500 | Lỗi tính giá |
| `MAPS_API_ERROR` | 500 | Mapbox API lỗi |
| `PROMO_INVALID` | 400 | Mã KM không hợp lệ |
| `PROMO_EXPIRED` | 400 | Mã KM đã hết hạn |
| `PROMO_USAGE_LIMIT` | 400 | Mã KM đã hết lượt sử dụng |

---

## Appendix C: HTTP Status Codes Reference

| Code | Meaning | Usage |
|------|---------|-------|
| 200 | OK | Successful GET/PUT |
| 201 | Created | Successful POST (create) |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Invalid input |
| 401 | Unauthorized | Missing/invalid JWT |
| 403 | Forbidden | No permission |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Duplicate resource |
| 429 | Too Many Requests | Rate limited |
| 500 | Internal Server Error | Server error |
| 503 | Service Unavailable | Circuit breaker open |

---

*Document Version: 1.0*
*Last Updated: 2026-05-26*
*Project: Cab Booking System - Nhom 13 KTTKPM DHKTPM18A*
