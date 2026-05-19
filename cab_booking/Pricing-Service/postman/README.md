# Pricing Service Postman Guide

File collection:

```text
Pricing-Service.postman_collection.json
```

Collection này dùng để test Pricing Service theo flow rule-based pricing, không dùng AI pricing.

## Biến Cần Cấu Hình

```text
AUTH_URL=http://localhost:8081
BASE_URL=http://localhost:8088
MAPBOX_API_KEY=<your-mapbox-token>
```

Nếu gọi qua API Gateway:

```text
AUTH_URL=http://localhost:8080
BASE_URL=http://localhost:8080
```

## Role Và Token

Collection chia request theo role:

```text
USER: dùng JWT_TOKEN
DRIVER: dùng DRIVER_JWT_TOKEN
ADMIN: dùng ADMIN_JWT_TOKEN
```

Auth Service hiện cho public register `USER` và `DRIVER`. `ADMIN` không đăng ký public được, cần có tài khoản admin seed sẵn trong database rồi login để lấy `ADMIN_JWT_TOKEN`.

## 00 - Auth By Role

### Register USER - sample customer account

Tạo tài khoản hành khách mẫu.

Dùng để lấy token test các API dành cho user như estimate giá, confirm quote, xem config/surge.

### Login USER - store JWT_TOKEN

Đăng nhập tài khoản `USER` và lưu access token vào biến:

```text
JWT_TOKEN
```

Các request trong folder `USER Pricing Flow` sẽ dùng token này.

### Register DRIVER - sample driver account

Tạo tài khoản tài xế mẫu.

Pricing Service hiện không có API trực tiếp dành riêng cho driver, nhưng token driver được chuẩn bị để test tích hợp sau này nếu cần.

### Login DRIVER - store DRIVER_JWT_TOKEN

Đăng nhập tài khoản `DRIVER` và lưu token vào:

```text
DRIVER_JWT_TOKEN
```

### Login ADMIN - store ADMIN_JWT_TOKEN

Đăng nhập tài khoản admin đã seed sẵn và lưu token vào:

```text
ADMIN_JWT_TOKEN
```

Token này dùng cho API internal/admin như cập nhật demand/supply hoặc manual surge.

### Debug USER Token - fallback only

Gọi:

```http
GET /auth/token
```

Endpoint này tạo debug token `USER`. Chỉ dùng khi chưa muốn register/login user thật. Token này không có quyền admin nên sẽ bị `403` khi gọi API admin/internal.

## 01 - External API Smoke

### Mapbox Matrix - Distance Duration

Gọi trực tiếp Mapbox Matrix API để kiểm tra:

- API key Mapbox có hợp lệ không.
- Mapbox có trả về distance/duration không.

API này không đi qua Pricing Service.

### Open-Meteo - Current Weather

Gọi trực tiếp Open-Meteo để kiểm tra dữ liệu thời tiết hiện tại:

- `temperature_2m`
- `weather_code`

API này không cần key và không đi qua Pricing Service.

## 02 - USER Pricing Flow

Folder này dùng `JWT_TOKEN`. Đây là các API user app có thể gọi.

### Get Rule-Based Pricing Config

```http
GET /api/pricing/config
```

Lấy cấu hình pricing hiện tại của service.

**Công dụng:**
- Kiểm tra Pricing Service đang chạy đúng config
- Reference cho việc debug pricing
- Xem các rate và threshold đang áp dụng

**Response chứa:**

| Section | Mô tả |
|---------|--------|
| `calculation` | Công thức tính giá cơ bản |
| `vehicle` | Rate theo loại xe (ECONOMY, COMFORT, PREMIUM) |
| `surge` | Cấu hình surge (min, max, threshold) |
| `weather` | Cấu hình weather adjustment |
| `cache` | TTL cho cache route/weather |
| `eta` | Fallback duration |

### Get Surge Multiplier After Metrics

```http
GET /api/pricing/surge/{zoneId}
```

Lấy hệ số surge hiện tại của một zone.

**Công dụng:**
- Kiểm tra surge trước khi estimate
- Debug surge pricing
- Monitor zone health

**Response Fields:**

| Field | Mô tả |
|-------|--------|
| `zone_id` | Zone ID đã truy vấn |
| `surge_multiplier` | Hệ số surge hiện tại |
| `message` | Thông báo trạng thái |

**Surge Multiplier Interpretation:**

| Giá trị | Ý nghĩa |
|----------|----------|
| `1.0` | Bình thường |
| `1.0 - 1.5` | Có nhu cầu cao nhẹ |
| `1.5 - 2.0` | Nhu cầu cao |
| `2.0 - 3.0` | Rất cao (peak hours, events) |

User có thể xem kết quả surge qua giá estimate; endpoint này chủ yếu để test/debug.

### Estimate Fare And Store Quote Token

```http
POST /api/pricing/estimate
```

Tạo fare estimate cho chuyến đi.

**Công dụng:**
- User muốn biết giá trước khi đặt xe
- Tính toán cước phí dựa trên tọa độ, loại xe, surge hiện tại
- Tạo quote có thời hạn (15 phút)

**Request Body:**
```json
{
  "pickupLat": 10.8231,
  "pickupLng": 106.6297,
  "dropoffLat": 10.7626,
  "dropoffLng": 106.6601,
  "vehicleType": "ECONOMY",
  "estimatedDurationMinutes": 15
}
```

**Request Fields:**

| Field | Type | Required | Mô tả |
|-------|------|----------|--------|
| `pickupLat` | double | Yes | Vĩ độ điểm đón (-90 đến 90) |
| `pickupLng` | double | Yes | Kinh độ điểm đón (-180 đến 180) |
| `dropoffLat` | double | Yes | Vĩ độ điểm trả (-90 đến 90) |
| `dropoffLng` | double | Yes | Kinh độ điểm trả (-180 đến 180) |
| `vehicleType` | string | Yes | Loại xe: ECONOMY, COMFORT, PREMIUM |
| `estimatedDurationMinutes` | int | No | Thời gian ước tính (phút) |

**Headers tùy chọn:**

| Header | Mô tả |
|--------|--------|
| `Idempotency-Key: <uuid>` | Đảm bảo không tạo duplicate estimate |

**Idempotency Key là gì?**
- Nếu user gọi API nhiều lần với cùng key, kết quả sẽ được trả về từ cache
- Tránh trường hợp user click nhiều lần tạo nhiều estimate
- Key có hiệu lực trong 15 phút (thời gian hết hạn của estimate)

**Response Fields:**

| Field | Type | Mô tả |
|-------|------|--------|
| `estimateId` | string | UUID duy nhất của estimate |
| `pickupZone` | string | Zone ID điểm đón (được xác định tự động) |
| `dropoffZone` | string | Zone ID điểm trả |
| `vehicleType` | string | Loại xe đã chọn |
| `distanceKm` | double | Khoảng cách chuyến đi (km) |
| `durationMinutes` | int | Thời gian ước tính (phút) |
| `baseFare` | decimal | Cước phí cơ bản - phí khởi đầu |
| `distanceFare` | decimal | Cước tính theo khoảng cách = distanceKm × perKmRate |
| `timeFare` | decimal | Cước tính theo thời gian = durationMinutes × perMinuteRate |
| `platformFee` | decimal | Phí nền tảng (cố định) |
| `surgeMultiplier` | decimal | Hệ số surge - nhân vào tổng giá |
| `totalFare` | decimal | Tổng cước phí = (base + distance + time + fees) × surge |
| `currency` | string | Đơn vị tiền tệ (VND) |
| `pricingConfigVersion` | string | Phiên bản config đã dùng để tính |
| `distanceSource` | string | Nguồn dữ liệu khoảng cách |
| `weatherCondition` | string | Tình trạng thời tiết tại điểm đón |
| `weatherSource` | string | Nguồn dữ liệu thời tiết |
| `fallbackUsed` | boolean | Có dùng fallback không |
| `expiresAt` | datetime | Thời điểm estimate hết hạn |

**Cách tính totalFare:**

```
subtotal = baseFare + distanceFare + timeFare + platformFee
totalFare = subtotal × surgeMultiplier
```

**Distance Source Values:**

| Value | Ý nghĩa |
|-------|----------|
| `MAPBOX` | Lấy từ Mapbox Matrix API |
| `MAPBOX_CACHE` | Lấy từ cache Redis |
| `HAVERSINE_FALLBACK` | Tính toán cục bộ khi Mapbox lỗi |

**Weather Source Values:**

| Value | Ý nghĩa |
|-------|----------|
| `OPEN_METEO` | Lấy từ Open-Meteo API |
| `OPEN_METEO_CACHE` | Lấy từ cache Redis |
| `FALLBACK_UNAVAILABLE` | Không lấy được thời tiết |

**Collection sẽ lưu:**

```text
estimate_id
estimated_fare
surge_multiplier
```

**Lưu ý tọa độ:**

```text
latitude  ≈ 10.x   (vĩ độ Việt Nam)
longitude ≈ 106.x  (kinh độ Việt Nam)
```

Ví dụ đúng:

```text
pickupLat=10.8231
pickupLng=106.629770
```

Không truyền `106.x` vào `pickupLat`.

### Estimate Same Route Again To Exercise Cache

Gọi estimate cùng route lần nữa để kiểm tra cache route/weather.

`distanceSource` có thể là:

```text
MAPBOX
MAPBOX_CACHE
HAVERSINE_FALLBACK
```

`weatherSource` có thể là:

```text
OPEN_METEO
OPEN_METEO_CACHE
FALLBACK_UNAVAILABLE
```

### Confirm Fare Estimate Atomically

```http
POST /api/pricing/confirm/{estimateId}
```

Xác nhận quote và khóa giá. User đồng ý với giá và tiến hành đặt xe.

**Công dụng:**
- Lock giá không thay đổi trong 15 phút
- Chuyển estimate từ PENDING → CONFIRMED
- Tạo booking record trong hệ thống

**Flow:**

```
1. User gọi POST /api/pricing/estimate → nhận estimateId
2. User xem giá và quyết định đặt
3. User gọi POST /api/pricing/confirm/{estimateId}
4. Nếu estimate còn PENDING và chưa hết hạn → CONFIRMED
5. Pricing Service trả về FareEstimate đã confirmed
```

**Sau khi confirm thành công:**

- `status = CONFIRMED`
- `expiresAt` không còn ý nghĩa
- Giá đã được lock, không thay đổi

**Atomic Update là gì?**
- Dùng MongoDB `findAndModify` để đảm bảo chỉ confirm thành công một lần
- Nếu có 2 request confirm cùng estimateId → request thứ 2 sẽ fail

### Confirm Same Estimate Again Should Fail

Gọi confirm lại cùng `estimateId`.

Kỳ vọng lỗi:

```text
400 INVALID_STATUS
```

Mục đích: chứng minh quote đã được lock và không thể confirm lặp.

### Confirm Missing Estimate Should Fail

Gọi confirm với estimate không tồn tại.

Kỳ vọng lỗi:

```text
400 ESTIMATE_NOT_FOUND
```

### Calculate Simple Price Compatibility API

```http
POST /api/pricing/calculate
```

API test đơn giản, tính giá bằng công thức:

```
totalFare = (baseFare + distanceFare) × surgeMultiplier
```

**Request Body:**
```json
{
  "distanceKm": 5.0,
  "demandIndex": 1.2
}
```

**Request Fields:**

| Field | Type | Required | Mô tả |
|-------|------|----------|--------|
| `distanceKm` | double | Yes | Khoảng cách (km) |
| `demandIndex` | double | Yes | Hệ số demand (tương tự surge) |

**Công dụng:**
- Smoke test pricing formula
- Không phải flow chính của user app
- Không tạo estimate trong DB

### Get All Surge Multipliers

```http
GET /api/pricing/surge/all
```

Lấy toàn bộ surge multiplier đang có.

**Công dụng:**
- Debug trạng thái surge theo zone
- Dashboard vận hành
- Monitor surge changes

### Invalid Coordinates Should Return 400

Test validation tọa độ sai.

Ví dụ:

```text
pickupLat=999
```

Latitude hợp lệ chỉ từ `-90` đến `90`. Longitude hợp lệ từ `-180` đến `180`.

## 03 - ADMIN/Internal Pricing Flow

Folder này dùng `ADMIN_JWT_TOKEN`. Nếu dùng token `USER`, các request này có thể trả:

```text
403 Forbidden
```

### Test Mapbox Connection Through Pricing Service

```http
GET /api/pricing/test-mapbox
```

Endpoint diagnostic để kiểm tra Pricing Service gọi Mapbox được không.

**Công dụng:**
- Verify API key Mapbox có hợp lệ không
- Check kết nối từ Pricing Service → Mapbox
- Debug latency và response

**Response Fields:**

| Field | Type | Mô tả |
|-------|------|--------|
| `success` | boolean | Mapbox có trả lời thành công không |
| `status` | string | Status code từ Mapbox |
| `raw_response` | object | Response đầy đủ từ Mapbox |
| `api_key_used` | string | API key đã dùng (đã mask) |

**Chỉ dành cho admin/debug, không dành cho user.**

### Seed Demand Supply Metrics For Pickup Zone

```http
POST /api/pricing/demand-supply
```

Cập nhật thủ công metrics cung-cầu cho một zone.

**Request Body:**
```json
{
  "zoneId": "Z60206",
  "activeDrivers": 10,
  "pendingRides": 30
}
```

**Request Fields:**

| Field | Type | Required | Mô tả |
|-------|------|----------|--------|
| `zoneId` | string | Yes | Zone ID cần cập nhật |
| `activeDrivers` | int | Yes | Số driver đang hoạt động |
| `pendingRides` | int | Yes | Số ride đang chờ |

**Công dụng:**
- Seed dữ liệu test cho surge calculation
- Admin can manually override metrics khi cần
- Trong production, metrics nên được cập nhật tự động từ Kafka events

**Trong production, metrics được cập nhật từ:**

```text
ride.created         - Ride mới được tạo → pendingRides++
ride.cancelled       - Ride bị hủy → pendingRides--
ride.completed      - Ride hoàn thành → activeDrivers++ (driver trở lại)
driver.status.changed - Driver online/offline → activeDrivers +/--
driver.location.updated - Driver di chuyển
```

### Manual Surge Override

```http
PUT /api/pricing/surge/{zoneId}
```

Override surge multiplier thủ công cho một zone.

**Request Body:**
```json
{
  "multiplier": 1.5
}
```

**Request Fields:**

| Field | Type | Required | Mô tả |
|-------|------|----------|--------|
| `multiplier` | decimal | Yes | Surge multiplier mới |

**Surge Multiplier Range:**
- `1.0` = Bình thường (không surge)
- `> 1.0` = Surge (giá tăng)
- Max theo config: `3.0`

**Công dụng:**
- Event-based surge (concert, football match)
- Emergency surge (bad weather)
- Admin manual control

### Invalid Surge Multiplier Still Clamped By Service

Test xem service có clamp surge multiplier không.

Gửi multiplier rất lớn:

```json
{
  "multiplier": 99
}
```

Service sẽ clamp theo config:

```text
minMultiplier = 1.0
maxMultiplier = 3.0
```

→ Giá trị sẽ được giới hạn ở 3.0

**Mục đích:** Kiểm tra rule bảo vệ giá không vượt ngưỡng.

## 02 - Estimate Management

Folder mới chứa các API quản lý estimate.

### 13 - Get Estimate By ID

```http
GET /api/pricing/estimate/{estimateId}
```

Lấy thông tin chi tiết của một estimate đã tạo.

**Công dụng:**
- User muốn xem lại estimate trước khi confirm
- Kiểm tra trạng thái estimate (PENDING, CONFIRMED, EXPIRED, CANCELLED)
- Xem lại chi tiết giá đã được tính

**Response Fields:**

| Field | Type | Mô tả |
|-------|------|--------|
| `estimateId` | string | UUID duy nhất của estimate |
| `pickupZone` | string | Zone ID của điểm đón |
| `dropoffZone` | string | Zone ID của điểm trả |
| `vehicleType` | string | Loại xe: ECONOMY, COMFORT, PREMIUM |
| `distanceKm` | double | Khoảng cách chuyến đi (km) |
| `durationMinutes` | int | Thời gian ước tính (phút) |
| `baseFare` | decimal | Cước phí cơ bản (VND) |
| `distanceFare` | decimal | Cước tính theo khoảng cách (VND) |
| `timeFare` | decimal | Cước tính theo thời gian (VND) |
| `platformFee` | decimal | Phí nền tảng (VND) |
| `surgeMultiplier` | decimal | Hệ số surge (1.0 = bình thường) |
| `totalFare` | decimal | Tổng cước phí (VND) |
| `currency` | string | Đơn vị tiền tệ |
| `status` | string | Trạng thái: PENDING, CONFIRMED, EXPIRED, CANCELLED |
| `expiresAt` | datetime | Thời điểm estimate hết hạn |
| `pricingConfigVersion` | string | Phiên bản config đã dùng |
| `distanceSource` | string | Nguồn dữ liệu khoảng cách |
| `weatherCondition` | string | Tình trạng thời tiết |
| `weatherSource` | string | Nguồn dữ liệu thời tiết |
| `fallbackUsed` | boolean | Có dùng fallback không |

### 14 - Cancel Estimate

```http
DELETE /api/pricing/estimate/{estimateId}
```

Hủy một estimate đang ở trạng thái PENDING.

**Công dụng:**
- User không muốn tiếp tục với chuyến đi
- Thay vì đợi estimate hết hạn tự động (15 phút)

**Lưu ý:**
- Chỉ estimate có `status = PENDING` mới có thể hủy
- Estimate đã CONFIRMED không thể hủy

**Response thành công (200):**

```json
{
  "estimateId": "xxx",
  "status": "CANCELLED",
  "message": "Estimate cancelled successfully"
}
```

**Response lỗi (400) - Estimate không ở trạng thái PENDING:**

```json
{
  "error": "INVALID_STATUS",
  "message": "Estimate is not in PENDING status and cannot be cancelled. Current status: CONFIRMED",
  "estimateId": "xxx",
  "currentStatus": "CONFIRMED"
}
```

### 15 - List Estimates with Filters

```http
GET /api/pricing/estimates
```

Liệt kê các estimate với bộ lọc và phân trang.

**Query Parameters:**

| Parameter | Type | Default | Mô tả |
|-----------|------|---------|--------|
| `status` | string | - | Lọc theo trạng thái: PENDING, CONFIRMED, EXPIRED, CANCELLED |
| `vehicleType` | string | - | Lọc theo loại xe: ECONOMY, COMFORT, PREMIUM |
| `pickupZone` | string | - | Lọc theo zone đón |
| `limit` | int | 50 | Số lượng kết quả tối đa |
| `offset` | int | 0 | Vị trí bắt đầu (pagination) |

**Công dụng:**
- Admin theo dõi các estimate đang chờ
- Debug/tra cứu lịch sử estimate
- Dashboard thống kê

**Ví dụ:**

```
GET /api/pricing/estimates?status=PENDING&limit=10&offset=0
GET /api/pricing/estimates?vehicleType=ECONOMY&limit=20
GET /api/pricing/estimates?pickupZone=Z60206
```

## 03 - Surge & Metrics

Folder mới chứa các API về surge pricing và zone metrics.

### 16 - Get Zone Metrics

```http
GET /api/pricing/zones/{zoneId}/metrics
```

Lấy thông tin demand/supply metrics của một zone.

**Công dụng:**
- Monitor tình trạng cung-cầu theo zone
- Debug surge pricing
- Dashboard vận hành

**Response Fields:**

| Field | Type | Mô tả |
|-------|------|--------|
| `zone_id` | string | Zone ID |
| `active_drivers` | int | Số driver đang hoạt động trong zone |
| `pending_rides` | int | Số ride đang chờ trong zone |
| `updated_at` | datetime | Thời điểm metrics được cập nhật |
| `demand_ratio` | string | Tỷ lệ cầu (pendingRides / activeDrivers) |

**Ý nghĩa demand_ratio:**
- `1.0` = Cân bằng: 1 ride cho mỗi driver
- `< 1.0` = Dư driver: nhiều driver hơn ride
- `> 1.0` = Thiếu driver: nhiều ride hơn driver → surge tăng

**Response khi không có metrics (404):**

```json
{
  "zone_id": "UNKNOWN",
  "error": "METRICS_NOT_FOUND",
  "message": "No metrics found for zone: UNKNOWN"
}
```

### 17 - Compute Surge Multiplier

```http
POST /api/pricing/surge/compute/{zoneId}
```

Trigger tính surge multiplier thủ công cho một zone.

**Query Parameters:**

| Parameter | Type | Default | Mô tả |
|-----------|------|---------|--------|
| `badWeather` | boolean | false | Có tính thời tiết xấu không |

**Công dụng:**
- Admin muốn recalculate surge ngay lập tức
- Test rule-based surge calculator
- Không cần đợi scheduler chạy tự động (mỗi 60 giây)

**Response Fields:**

| Field | Type | Mô tả |
|-------|------|--------|
| `zone_id` | string | Zone ID |
| `previous_multiplier` | decimal | Surge trước khi tính |
| `predicted_multiplier` | decimal | Surge mới được tính |
| `updated` | boolean | Surge có được cập nhật không |
| `message` | string | Thông báo trạng thái |

**Khi nào surge được cập nhật?**
- Surge chỉ được cập nhật khi `|predicted - previous| >= threshold` (config: 0.1)
- Nếu thay đổi nhỏ hơn threshold → `updated: false`

### 18 - Get Zone Metrics Not Found

```http
GET /api/pricing/zones/UNKNOWN_ZONE/metrics
```

Test case để verify xử lý khi zone không có metrics.

---

## Thứ Tự Chạy Đề Xuất

1. Chạy `Register USER` nếu chưa có user.
2. Chạy `Login USER - store JWT_TOKEN`.
3. Nếu cần test admin/internal, chuẩn bị admin account trong DB rồi chạy `Login ADMIN`.
4. Chạy `USER Pricing Flow`.
5. Chạy `Estimate Management` để test các API mới.
6. Chạy `Surge & Metrics` để test surge/metrics APIs.
7. Chạy `ADMIN/Internal Pricing Flow` nếu có `ADMIN_JWT_TOKEN`.

## Lỗi Thường Gặp

### 401 Unauthorized

Token thiếu, hết hạn hoặc biến token chưa được set.

Cách xử lý:

```text
Chạy lại Login USER hoặc Login ADMIN.
Kiểm tra JWT_TOKEN / ADMIN_JWT_TOKEN có giá trị chưa.
```

### 403 Forbidden

Token hợp lệ nhưng không đủ quyền.

Ví dụ:

```text
USER gọi /api/pricing/demand-supply
USER gọi /api/pricing/test-mapbox
USER gọi manual surge update
```

Cách xử lý:

```text
Dùng ADMIN_JWT_TOKEN cho folder ADMIN/Internal Pricing Flow.
```

### 404 Not Found

Resource không tìm thấy.

Ví dụ:

```text
GET /api/pricing/estimate/{id} với id không tồn tại
GET /api/pricing/zones/{zoneId}/metrics với zone không có metrics
```

Response:

```json
{
  "error": "ESTIMATE_NOT_FOUND",
  "message": "Estimate with ID xxx not found",
  "estimateId": "xxx"
}
```

### 400 Bad Request

Request không hợp lệ.

Ví dụ:

```text
Estimate không ở trạng thái PENDING khi cancel
Idempotency key đã được sử dụng
```

Response:

```json
{
  "error": "INVALID_STATUS",
  "message": "Estimate is not in PENDING status and cannot be cancelled",
  "estimateId": "xxx",
  "currentStatus": "CONFIRMED"
}
```

### Validation pickupLat <= 90

Bạn truyền nhầm longitude vào latitude.

Đúng:

```text
pickupLat=10.8231
pickupLng=106.629770
```

Sai:

```text
pickupLat=106.629770
```

## API Gateway Port Smoke Collection

Use this collection when you want to verify Pricing Service through API Gateway instead of calling Pricing Service directly:

```text
Pricing-Service-ApiGateway.postman_collection.json
GATEWAY_URL=http://localhost:8080
```

### Request Flow

#### 01 - Auth By Role
Register và login các tài khoản test (USER, DRIVER, ADMIN).

#### 02 - Estimate Management
```http
GET  /api/pricing/estimate/{estimateId}    # Lấy estimate theo ID
DELETE /api/pricing/estimate/{estimateId}    # Hủy estimate
GET  /api/pricing/estimates                # Liệt kê estimates
```

#### 03 - Surge & Metrics
```http
GET  /api/pricing/zones/{zoneId}/metrics    # Lấy zone metrics
POST /api/pricing/surge/compute/{zoneId}    # Trigger surge calculation
```

### Các API qua Gateway

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/gateway/health` | Health check gateway |
| GET | `/api/pricing/config` | Lấy cấu hình pricing |
| POST | `/api/pricing/calculate` | Tính giá đơn giản |
| POST | `/api/pricing/estimate` | Tạo fare estimate |
| GET | `/api/pricing/estimate/{id}` | Lấy estimate theo ID |
| DELETE | `/api/pricing/estimate/{id}` | Hủy estimate |
| GET | `/api/pricing/estimates` | Liệt kê estimates |
| POST | `/api/pricing/confirm/{id}` | Confirm estimate |
| GET | `/api/pricing/surge/{zoneId}` | Lấy surge multiplier |
| PUT | `/api/pricing/surge/{zoneId}` | Cập nhật surge |
| GET | `/api/pricing/surge/all` | Lấy tất cả surge |
| POST | `/api/pricing/surge/compute/{zoneId}` | Trigger surge calculation |
| POST | `/api/pricing/demand-supply` | Cập nhật demand/supply |
| GET | `/api/pricing/zones/{zoneId}/metrics` | Lấy zone metrics |

## Tổng Quan Tất Cả API Pricing Service

### User APIs (JWT_TOKEN)

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/api/pricing/config` | Lấy cấu hình giá |
| POST | `/api/pricing/estimate` | Tạo ước tính cước phí |
| GET | `/api/pricing/estimate/{id}` | Lấy estimate theo ID |
| DELETE | `/api/pricing/estimate/{id}` | Hủy estimate (nếu PENDING) |
| GET | `/api/pricing/estimates` | Liệt kê estimates |
| POST | `/api/pricing/confirm/{id}` | Xác nhận và lock giá |

### Surge & Metrics APIs (JWT_TOKEN / ADMIN)

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| GET | `/api/pricing/surge/{zoneId}` | Lấy surge của zone |
| GET | `/api/pricing/surge/all` | Lấy tất cả surge |
| GET | `/api/pricing/zones/{zoneId}/metrics` | Lấy metrics zone |
| POST | `/api/pricing/surge/compute/{zoneId}` | Trigger tính surge |

### Admin APIs (ADMIN_JWT_TOKEN)

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| POST | `/api/pricing/demand-supply` | Cập nhật demand/supply |
| PUT | `/api/pricing/surge/{zoneId}` | Override surge thủ công |
| GET | `/api/pricing/test-mapbox` | Test kết nối Mapbox |

### Test APIs (Không cần auth)

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| POST | `/api/pricing/calculate` | Tính giá đơn giản (test)
