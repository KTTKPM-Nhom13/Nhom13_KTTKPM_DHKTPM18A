# Pricing Service API Documentation

## Base URL
```
http://localhost:8083/api/pricing
```

---

## 1. Estimate Fare

Tạo ước tính cước phí cho một chuyến đi.

**Endpoint:** `POST /api/pricing/estimate`

**Request Body:**

```json
{
  "pickupLat": 10.76,
  "pickupLng": 106.70,
  "dropoffLat": 10.78,
  "dropoffLng": 106.73,
  "vehicleType": "ECONOMY",
  "estimatedDurationMinutes": 15
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `pickupLat` | double | Yes | Vĩ độ điểm đón |
| `pickupLng` | double | Yes | Kinh độ điểm đón |
| `dropoffLat` | double | Yes | Vĩ độ điểm trả |
| `dropoffLng` | double | Yes | Kinh độ điểm trả |
| `vehicleType` | string | Yes | Loại xe: `ECONOMY`, `COMFORT`, `PREMIUM` |
| `estimatedDurationMinutes` | int | No | Thời gian ước tính (phút) |

**Example Request:**
```bash
curl -X POST "http://localhost:8083/api/pricing/estimate" \
  -H "Content-Type: application/json" \
  -d '{"pickupLat": 10.76, "pickupLng": 106.70, "dropoffLat": 10.78, "dropoffLng": 106.73, "vehicleType": "ECONOMY", "estimatedDurationMinutes": 15}'
```

**Response:**
```json
{
  "estimateId": "f1365e9d-074c-4b24-90d3-d7762235755d",
  "pickupZone": "Z60206",
  "dropoffZone": "Z60206",
  "vehicleType": "ECONOMY",
  "distanceKm": 3.96,
  "durationMinutes": 15,
  "baseFare": 2.5,
  "distanceFare": 5.94,
  "timeFare": 3.0,
  "surgeMultiplier": 1.0,
  "totalFare": 11.44,
  "currency": "VND",
  "expiresAt": "2026-05-07T01:52:44.672277434",
  "message": "Fare estimate generated successfully"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `estimateId` | string (UUID) | ID duy nhất của ước tính |
| `pickupZone` | string | Zone ID điểm đón |
| `dropoffZone` | string | Zone ID điểm trả |
| `vehicleType` | string | Loại xe đã chọn |
| `distanceKm` | double | Khoảng cách (km) |
| `durationMinutes` | int | Thời gian chuyến đi (phút) |
| `baseFare` | double | Cước phí cơ bản |
| `distanceFare` | double | Cước theo khoảng cách |
| `timeFare` | double | Cước theo thời gian |
| `surgeMultiplier` | double | Hệ số surge (1.0 = bình thường) |
| `totalFare` | double | Tổng cước phí |
| `currency` | string | Đơn vị tiền tệ |
| `expiresAt` | string (ISO datetime) | Thời điểm hết hạn |
| `message` | string | Thông báo trạng thái |

---

## 2. Confirm Estimate

Xác nhận một ước tính để tạo booking.

**Endpoint:** `POST /api/pricing/confirm/{estimateId}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `estimateId` | string (UUID) | Yes | ID của estimate cần confirm |

**Example Request:**
```bash
curl -X POST "http://localhost:8083/api/pricing/confirm/f1365e9d-074c-4b24-90d3-d7762235755d"
```

**Response:**
```json
{
  "bookingId": "BK-550e8400-e29b-41d4-a716-446655440000",
  "estimateId": "f1365e9d-074c-4b24-90d3-d7762235755d",
  "finalFare": 11.44,
  "currency": "VND",
  "status": "CONFIRMED",
  "createdAt": "2026-05-07T08:39:00.000000000",
  "message": "Booking confirmed successfully"
}
```

---

## 3. Get Surge Multiplier

Lấy surge multiplier cho một zone cụ thể.

**Endpoint:** `GET /api/pricing/surge/{zoneId}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `zoneId` | string | Yes | Zone ID cần kiểm tra |

**Example Request:**
```bash
curl "http://localhost:8083/api/pricing/surge/Z60206"
```

**Response:**
```json
{
  "zoneId": "Z60206",
  "surgeMultiplier": 1.2,
  "lastUpdated": "2026-05-07T08:30:00.000000000"
}
```

---

## 4. Get All Surge Multipliers

Lấy tất cả surge multipliers.

**Endpoint:** `GET /api/pricing/surge/all`

**Example Request:**
```bash
curl "http://localhost:8083/api/pricing/surge/all"
```

**Response:**
```json
{
  "surgeMultipliers": [
    {
      "zoneId": "Z60206",
      "surgeMultiplier": 1.2,
      "lastUpdated": "2026-05-07T08:30:00.000000000"
    },
    {
      "zoneId": "Z70101",
      "surgeMultiplier": 1.5,
      "lastUpdated": "2026-05-07T08:25:00.000000000"
    }
  ]
}
```

---

## 5. Update Surge Multiplier (Admin)

Cập nhật surge multiplier cho một zone.

**Endpoint:** `PUT /api/pricing/surge/{zoneId}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `zoneId` | string | Yes | Zone ID cần cập nhật |

**Request Body:**
```json
{
  "multiplier": 1.5
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `multiplier` | number | Yes | Giá trị surge multiplier mới |

**Example Request:**
```bash
curl -X PUT "http://localhost:8083/api/pricing/surge/Z60206" \
  -H "Content-Type: application/json" \
  -d '{"multiplier": 1.5}'
```

**Response:**
```json
{
  "zoneId": "Z60206",
  "surgeMultiplier": 1.5,
  "lastUpdated": "2026-05-07T08:39:00.000000000",
  "message": "Surge multiplier updated successfully"
}
```

---

## 6. Update Demand & Supply Metrics (Admin)

Cập nhật metrics demand/supply để tính surge pricing.

**Endpoint:** `POST /api/pricing/demand-supply`

**Request Body:**
```json
{
  "zoneId": "Z60206",
  "activeDrivers": 10,
  "pendingRides": 30
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `zoneId` | string | Yes | Zone ID cần cập nhật |
| `activeDrivers` | int | Yes | Số driver đang hoạt động |
| `pendingRides` | int | Yes | Số ride đang chờ |

**Example Request:**
```bash
curl -X POST "http://localhost:8083/api/pricing/demand-supply" \
  -H "Content-Type: application/json" \
  -d '{"zoneId": "Z60206", "activeDrivers": 10, "pendingRides": 30}'
```

**Response:**
```json
{
  "zone_id": "Z60206",
  "active_drivers": 10,
  "pending_rides": 30,
  "message": "Demand/supply metrics cached successfully."
}
```

---

## 7. Get Pricing Configuration

Lấy cấu hình giá hiện tại.

**Endpoint:** `GET /api/pricing/config`

**Example Request:**
```bash
curl "http://localhost:8083/api/pricing/config"
```

**Response:**
```json
{
  "calculation": {
    "baseFare": 2.5,
    "perKmRate": 1.5,
    "perMinuteRate": 0.25,
    "minimumFare": 5.0
  },
  "vehicleTypes": {
    "ECONOMY": {
      "multiplier": 1.0,
      "baseFare": 2.5,
      "perKm": 1.5,
      "perMinute": 0.2
    },
    "COMFORT": {
      "multiplier": 1.5,
      "baseFare": 5.0,
      "perKm": 2.5,
      "perMinute": 0.4
    },
    "PREMIUM": {
      "multiplier": 2.0,
      "baseFare": 10.0,
      "perKm": 4.0,
      "perMinute": 0.75
    }
  },
  "surge": {
    "defaultMultiplier": 1.0,
    "minMultiplier": 1.0,
    "maxMultiplier": 3.0,
    "cacheTtlSeconds": 60
  }
}
```

---

## 8. Get Estimate By ID

Lấy thông tin estimate theo ID.

**Endpoint:** `GET /api/pricing/estimate/{estimateId}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `estimateId` | string (UUID) | Yes | ID của estimate cần lấy |

**Example Request:**
```bash
curl "http://localhost:8083/api/pricing/estimate/f1365e9d-074c-4b24-90d3-d7762235755d"
```

**Response (200 OK):**
```json
{
  "estimateId": "f1365e9d-074c-4b24-90d3-d7762235755d",
  "pickupZone": "Z60206",
  "dropoffZone": "Z60206",
  "vehicleType": "ECONOMY",
  "distanceKm": 3.96,
  "durationMinutes": 15,
  "baseFare": 12000,
  "distanceFare": 33660,
  "timeFare": 18000,
  "platformFee": 2000,
  "surgeMultiplier": 1.0,
  "totalFare": 55660,
  "currency": "VND",
  "status": "PENDING",
  "expiresAt": "2026-05-18T20:30:00",
  "message": "Fare estimate generated successfully"
}
```

**Response (404 Not Found):**
```json
{
  "error": "ESTIMATE_NOT_FOUND",
  "message": "Estimate with ID xxx not found",
  "estimateId": "xxx"
}
```

---

## 9. Cancel Estimate

Hủy một estimate đang ở trạng thái PENDING.

**Endpoint:** `DELETE /api/pricing/estimate/{estimateId}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `estimateId` | string (UUID) | Yes | ID của estimate cần hủy |

**Example Request:**
```bash
curl -X DELETE "http://localhost:8083/api/pricing/estimate/f1365e9d-074c-4b24-90d3-d7762235755d"
```

**Response (200 OK):**
```json
{
  "estimateId": "f1365e9d-074c-4b24-90d3-d7762235755d",
  "status": "CANCELLED",
  "message": "Estimate cancelled successfully"
}
```

**Response (400 Bad Request - Estimate không ở trạng thái PENDING):**
```json
{
  "error": "INVALID_STATUS",
  "message": "Estimate is not in PENDING status and cannot be cancelled. Current status: CONFIRMED",
  "estimateId": "f1365e9d-074c-4b24-90d3-d7762235755d",
  "currentStatus": "CONFIRMED"
}
```

**Response (404 Not Found):**
```json
{
  "error": "ESTIMATE_NOT_FOUND",
  "message": "Estimate with ID xxx not found",
  "estimateId": "xxx"
}
```

---

## 10. Compute Surge Multiplier

Trigger tính surge multiplier thủ công cho một zone dựa trên demand/supply metrics hiện tại.

**Endpoint:** `POST /api/pricing/surge/compute/{zoneId}`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `zoneId` | string | Yes | Zone ID cần tính surge |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `badWeather` | boolean | No | false | Có tính thời tiết xấu không |

**Example Request:**
```bash
curl -X POST "http://localhost:8083/api/pricing/surge/compute/Z60206?badWeather=true"
```

**Response (200 OK):**
```json
{
  "zone_id": "Z60206",
  "previous_multiplier": 1.0,
  "predicted_multiplier": 1.5,
  "updated": true,
  "message": "Surge multiplier updated"
}
```

**Response khi surge không thay đổi (dưới threshold):**
```json
{
  "zone_id": "Z60206",
  "previous_multiplier": 1.0,
  "predicted_multiplier": 1.05,
  "updated": false,
  "message": "Surge multiplier unchanged (below threshold)"
}
```

---

## 11. Get Zone Metrics

Lấy thông tin demand/supply metrics của một zone.

**Endpoint:** `GET /api/pricing/zones/{zoneId}/metrics`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `zoneId` | string | Yes | Zone ID cần lấy metrics |

**Example Request:**
```bash
curl "http://localhost:8083/api/pricing/zones/Z60206/metrics"
```

**Response (200 OK):**
```json
{
  "zone_id": "Z60206",
  "active_drivers": 10,
  "pending_rides": 30,
  "updated_at": "2026-05-18T20:25:00Z",
  "demand_ratio": "3.00"
}
```

**Response (404 Not Found - Không có metrics cho zone):**
```json
{
  "zone_id": "Z60206",
  "error": "METRICS_NOT_FOUND",
  "message": "No metrics found for zone: Z60206"
}
```

---

## 12. List Estimates

Liệt kê các estimate với bộ lọc.

**Endpoint:** `GET /api/pricing/estimates`

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `status` | string | No | - | Lọc theo status: PENDING, CONFIRMED, EXPIRED, CANCELLED |
| `vehicleType` | string | No | - | Lọc theo loại xe: ECONOMY, COMFORT, PREMIUM |
| `pickupZone` | string | No | - | Lọc theo pickup zone |
| `limit` | int | No | 50 | Số lượng kết quả tối đa |
| `offset` | int | No | 0 | Vị trí bắt đầu (pagination) |

**Example Request:**
```bash
curl "http://localhost:8083/api/pricing/estimates?status=PENDING&limit=10&offset=0"
```

**Response (200 OK):**
```json
{
  "estimates": [
    {
      "estimateId": "f1365e9d-074c-4b24-90d3-d7762235755d",
      "pickupZone": "Z60206",
      "dropoffZone": "Z60206",
      "vehicleType": "ECONOMY",
      "distanceKm": 3.96,
      "durationMinutes": 15,
      "baseFare": 12000,
      "distanceFare": 33660,
      "timeFare": 18000,
      "platformFee": 2000,
      "surgeMultiplier": 1.0,
      "totalFare": 55660,
      "currency": "VND",
      "status": "PENDING",
      "expiresAt": "2026-05-18T20:30:00",
      "message": "Fare estimate generated successfully"
    }
  ],
  "count": 1,
  "limit": 10,
  "offset": 0
}
```

---

## Error Responses

### 400 Bad Request
```json
{
  "error": "INVALID_VEHICLE_TYPE",
  "message": "Vehicle type must be ECONOMY, COMFORT, or PREMIUM",
  "timestamp": "2026-05-07T08:39:00.000000000"
}
```

### 404 Not Found
```json
{
  "error": "ESTIMATE_NOT_FOUND",
  "message": "Estimate with ID xxx not found or expired",
  "timestamp": "2026-05-07T08:39:00.000000000"
}
```

### 500 Internal Server Error
```json
{
  "error": "PRICING_CALCULATION_FAILED",
  "message": "Failed to calculate fare",
  "timestamp": "2026-05-07T08:39:00.000000000"
}
```

---

## Vehicle Type Comparison

| Vehicle Type | Multiplier | Base Fare | Per KM | Per Minute |
|-------------|------------|-----------|--------|------------|
| ECONOMY | 1.0x | $2.50 | $1.50 | $0.20 |
| COMFORT | 1.5x | $5.00 | $2.50 | $0.40 |
| PREMIUM | 2.0x | $10.00 | $4.00 | $0.75 |

---

## Fare Calculation Formula

```
Total Fare = (Base Fare + Distance Fare + Time Fare) × Surge Multiplier

Distance Fare = Distance (km) × Per KM Rate
Time Fare = Duration (minutes) × Per Minute Rate
```

**Example Calculation (ECONOMY, 3.96km, 15min, no surge):**
```
Distance Fare = 3.96 × $1.50 = $5.94
Time Fare = 15 × $0.20 = $3.00
Total = ($2.50 + $5.94 + $3.00) × 1.0 = $11.44
```
