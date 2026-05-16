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

Lấy cấu hình pricing hiện tại:

- Công thức tính giá.
- Rate theo loại xe.
- Cấu hình surge.
- Cấu hình weather adjustment.
- TTL cache route/weather.

Mục đích: kiểm tra Pricing Service đang chạy đúng config VND/rule-based.

### Get Surge Multiplier After Metrics

```http
GET /api/pricing/surge/{zoneId}
```

Lấy hệ số surge hiện tại của một zone.

User có thể xem kết quả gián tiếp qua giá estimate; endpoint này chủ yếu để test/debug.

### Estimate Fare And Store Quote Token

```http
GET /api/pricing/estimate
```

Tạo fare estimate cho chuyến đi.

Input chính:

```text
pickupLat
pickupLng
dropoffLat
dropoffLng
vehicleType
Idempotency-Key
```

Response có:

```text
estimateId
distanceKm
durationMinutes
baseFare
distanceFare
timeFare
platformFee
surgeMultiplier
totalFare
currency
pricingConfigVersion
distanceSource
weatherSource
fallbackUsed
expiresAt
```

Collection sẽ lưu:

```text
estimate_id
estimated_fare
surge_multiplier
```

Lưu ý tọa độ:

```text
latitude  ≈ 10.x
longitude ≈ 106.x
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

Xác nhận quote và khóa giá.

Sau khi confirm thành công:

```text
status=CONFIRMED
```

API này dùng MongoDB atomic update để tránh confirm trùng cùng một `estimateId`.

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

API test đơn giản, tính giá bằng:

```text
distanceKm
demandIndex
```

Không phải flow chính của user app. Giữ lại để smoke test pricing formula.

### Get All Surge Multipliers

```http
GET /api/pricing/surge/all
```

Lấy toàn bộ surge multiplier đang có trong Redis/current metrics.

Mục đích: debug trạng thái surge theo zone.

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

Chỉ dành cho admin/debug, không dành cho user.

### Seed Demand Supply Metrics For Pickup Zone

```http
POST /api/pricing/demand-supply
```

Cập nhật thủ công metrics cung-cầu:

```text
zoneId
activeDrivers
pendingRides
```

API này không dành cho user. Trong production, Pricing Service nên tự cập nhật các metrics này từ Kafka events:

```text
ride.created
ride.cancelled
ride.finished
ride.completed
driver.status.changed
driver.location.updated
```

Trong Postman, request này chỉ dùng để seed dữ liệu test surge.

### Manual Surge Override

```http
PUT /api/pricing/surge/{zoneId}?multiplier=1.5
```

Cập nhật thủ công surge multiplier cho một zone.

Chỉ dùng cho admin/vận hành/debug.

### Invalid Surge Multiplier Still Clamped By Service

Gửi multiplier rất lớn, ví dụ:

```text
multiplier=99
```

Service sẽ clamp theo config:

```text
minMultiplier
maxMultiplier
```

Mục đích: kiểm tra rule bảo vệ giá không vượt ngưỡng.

## Thứ Tự Chạy Đề Xuất

1. Chạy `Register USER` nếu chưa có user.
2. Chạy `Login USER - store JWT_TOKEN`.
3. Nếu cần test admin/internal, chuẩn bị admin account trong DB rồi chạy `Login ADMIN`.
4. Chạy `USER Pricing Flow`.
5. Chạy `ADMIN/Internal Pricing Flow` nếu có `ADMIN_JWT_TOKEN`.

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
