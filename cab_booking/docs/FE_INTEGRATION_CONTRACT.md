# FE Integration Contract — Booking / Ride / Matching

> Generated: 2026-05-22  
> Scope: `booking-service`, `ride-service`, `matching-service`  
> All requests go through **API Gateway** — never call service ports directly.

---

## Table of Contents

1. [Base URL & Auth](#1-base-url--auth)
2. [Response Wrapper](#2-response-wrapper)
3. [Booking Endpoints (Customer)](#3-booking-endpoints-customer)
4. [Ride Endpoints (Driver)](#4-ride-endpoints-driver)
5. [Admin Booking Endpoints](#5-admin-booking-endpoints)
6. [BookingStatus Enum](#6-bookingstatus-enum)
7. [RideStatus Enum](#7-ridestatus-enum)
8. [Request/Response Bodies](#8-requestresponse-bodies)
9. [Error Responses](#9-error-responses)
10. [Role Access Matrix](#10-role-access-matrix)
11. [WebSocket / Realtime Events](#11-websocket--realtime-events)
12. [E2E Flow Checklists](#12-e2e-flow-checklists)

---

## 1. Base URL & Auth

```
Base URL: {{apiGatewayUrl}} = http://localhost:8080
```

**All endpoints require JWT Bearer token** (except auth endpoints).

```
Authorization: Bearer <access_token>
```

Token is obtained from `POST /auth/login` or `POST /auth/register` through the gateway.

---

## 2. Response Wrapper

### Success

```json
{
  "code": 200,
  "message": "Success",
  "result": { ... },
  "timestamp": "2026-05-22T00:00:00"
}
```

### Error

```json
{
  "code": 403,
  "message": "Forbidden",
  "errorMessage": "Access denied",
  "timestamp": "2026-05-22T00:00:00"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `code` | `int` | HTTP status code |
| `message` | `string` | Human-readable message |
| `errorMessage` | `string` | Detailed error (nullable) |
| `result` | `T` | Response payload (nullable) |
| `timestamp` | `string` | ISO 8601 timestamp |

---

## 3. Booking Endpoints (Customer)

All paths are relative to `{{apiGatewayUrl}}`.

### 3.1 Create Booking

```
POST /api/v1/bookings
```

**Auth:** CUSTOMER role required

**Request Body:**

```json
{
  "pickupLocation": "12 Nguyen Van Bao, Go Vap, HCMC",
  "dropoffLocation": "Tan Son Nhat Airport",
  "customerNote": "Optional note for driver",
  "pickupCoordinates": { "lat": 10.8221, "lng": 106.6885 },
  "dropoffCoordinates": { "lat": 10.8188, "lng": 106.6619 },
  "vehicleType": "SEDAN",
  "paymentMethod": "CASH",
  "estimatedFare": 75000.00,
  "promoCode": "",
  "quoteToken": "<from-pricing-service>",
  "estimateId": "<from-pricing-service>",
  "quoteId": "<from-pricing-service>",
  "quotePayloadHash": "<from-pricing-service>",
  "quoteHashAlgorithm": "SHA-256",
  "quoteExpiresAt": "2026-05-22T12:00:00",
  "idempotencyKey": "<unique-uuid>"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `pickupLocation` | `string` | ✅ | Human-readable pickup address |
| `dropoffLocation` | `string` | ✅ | Human-readable dropoff address |
| `customerNote` | `string` | ❌ | Note for driver |
| `pickupCoordinates` | `object` | ❌ | `{ "lat": double, "lng": double }` |
| `dropoffCoordinates` | `object` | ❌ | `{ "lat": double, "lng": double }` |
| `vehicleType` | `string` | ✅ | `BIKE`, `SEDAN`, `SUV`, `ECONOMY` |
| `paymentMethod` | `string` | ❌ | `CASH`, `MOMO`, `VNPAY`, `CARD` |
| `estimatedFare` | `decimal` | ❌ | Must be positive if provided |
| `promoCode` | `string` | ❌ | Promo code |
| `quoteToken` | `string` | ❌ | Zero Trust quote from Pricing Service |
| `estimateId` | `string` | ❌ | Estimate ID from Pricing Service |
| `quoteId` | `string` | ❌ | Quote ID from Pricing Service |
| `quotePayloadHash` | `string` | ❌ | Hash for quote verification |
| `quoteHashAlgorithm` | `string` | ❌ | Hash algorithm (e.g., `SHA-256`) |
| `quoteExpiresAt` | `datetime` | ❌ | Quote expiration time |
| `idempotencyKey` | `string` | ❌ | UUID to prevent duplicate bookings |

**Response:**

```json
{
  "code": 200,
  "message": "Created booking successfully",
  "result": {
    "id": "uuid",
    "customerId": "user-id",
    "assignedDriverId": null,
    "pickupLocation": "...",
    "dropoffLocation": "...",
    "customerNote": "...",
    "pickupCoordinates": { "lat": 10.8221, "lng": 106.6885 },
    "dropoffCoordinates": { "lat": 10.8188, "lng": 106.6619 },
    "vehicleType": "SEDAN",
    "paymentMethod": "CASH",
    "estimatedFare": 75000.00,
    "promoCode": "",
    "estimateId": "...",
    "quoteId": "...",
    "quoteHashAlgorithm": "SHA-256",
    "status": "MATCHING",
    "createdAt": "2026-05-22T10:00:00",
    "updatedAt": "2026-05-22T10:00:00"
  },
  "timestamp": "2026-05-22T10:00:00"
}
```

**Status transitions on create:**
- `paymentMethod = "CASH"` → status = `MATCHING`
- `paymentMethod = "MOMO"/"VNPAY"/"CARD"` → status = `PENDING_PAYMENT`

---

### 3.2 Get My Bookings

```
GET /api/v1/bookings/me?page=0&size=10
```

**Auth:** CUSTOMER role required

**Query Params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | `int` | 0 | Page number (0-based) |
| `size` | `int` | 10 | Page size |

**Response:** Paginated `BookingResponse` list

```json
{
  "code": 200,
  "message": "Fetched bookings successfully",
  "result": {
    "content": [ { /* BookingResponse */ } ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 10
  }
}
```

---

### 3.3 Get Booking by ID

```
GET /api/v1/bookings/{bookingId}
```

**Auth:** Must be the booking owner (customerId matches JWT subject)

**Response:** Single `BookingResponse`

---

### 3.4 Cancel Booking

```
POST /api/v1/bookings/{bookingId}/cancel?reason=Customer+requested+cancellation
```

**Auth:** Must be the booking owner

**Query Params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `reason` | `string` | "Customer requested cancellation" | Cancellation reason |

**Response:** Updated `BookingResponse` with `status: "CANCELLED"`

---

### 3.5 Get Customer History

```
GET /api/v1/bookings/customer/{customerId}?page=0&size=10
```

**Auth:** Must be the same customer

---

### 3.6 Get Active Booking

```
GET /api/v1/bookings/customer/{customerId}/active
```

**Auth:** Must be the same customer

**Response:** Single `BookingResponse` or 404 if no active booking

---

### 3.7 Get Nearby Bookings

```
GET /api/v1/bookings/nearby?lat=10.8221&lng=106.6885&radius=5.0
```

**Auth:** Required (any role)

**Query Params:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `lat` | `double` | required | Latitude |
| `lng` | `double` | required | Longitude |
| `radius` | `double` | 5.0 | Search radius in km |

**Response:** List of `BookingResponse` with status `MATCHING`

---

## 4. Ride Endpoints (Driver)

### 4.1 Get Ride by ID

```
GET /api/v1/rides/{rideId}
```

**Auth:** CUSTOMER (owner), DRIVER (assigned), or ADMIN

**Response:**

```json
{
  "code": 200,
  "message": "Fetched ride successfully",
  "result": {
    "id": "uuid",
    "bookingId": "booking-uuid",
    "customerId": "customer-id",
    "driverId": "driver-id",
    "pickupAddress": "...",
    "dropoffAddress": "...",
    "pickupLat": 10.8221,
    "pickupLng": 106.6885,
    "dropoffLat": 10.8188,
    "dropoffLng": 106.6619,
    "finalFare": null,
    "paymentMethod": "CASH",
    "status": "ASSIGNED",
    "createdAt": "2026-05-22T10:00:00",
    "updatedAt": "2026-05-22T10:00:00"
  }
}
```

---

### 4.2 Driver Arrive at Pickup

```
POST /api/v1/rides/{rideId}/arrive
```

**Auth:** DRIVER role required

**Response:** `RideResponse` with status `PICKUP`

---

### 4.3 Driver Start Ride

```
POST /api/v1/rides/{rideId}/start
```

**Auth:** DRIVER role required

**Response:** `RideResponse` with status `IN_PROGRESS`

---

### 4.4 Driver Complete Ride

```
POST /api/v1/rides/{rideId}/complete
```

**Auth:** DRIVER role required

**Request Body (optional):**

```json
{
  "finalFare": 75000.00,
  "paymentMethod": "CASH"
}
```

**Response:** `RideResponse` with status `COMPLETED`

---

## 5. Admin Booking Endpoints

All admin endpoints require `ROLE_ADMIN`.

### 5.1 Search Bookings

```
GET /api/admin/bookings?status=MATCHING&customerId=xxx&page=0&size=10
```

**Query Params:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | `BookingStatus` | ❌ | Filter by status |
| `customerId` | `string` | ❌ | Filter by customer |
| `driverId` | `string` | ❌ | Filter by driver |
| `paymentMethod` | `string` | ❌ | Filter by payment method |
| `vehicleType` | `VehicleType` | ❌ | Filter by vehicle type |
| `createdFrom` | `datetime` | ❌ | ISO 8601 start date |
| `createdTo` | `datetime` | ❌ | ISO 8601 end date |
| `page` | `int` | ❌ | Page number (0-based) |
| `size` | `int` | ❌ | Page size |

**Response:** Paginated `AdminBookingSummaryResponse`

```json
{
  "code": 200,
  "message": "Fetched admin booking list",
  "result": {
    "content": [
      {
        "bookingId": "uuid",
        "customerId": "...",
        "driverId": "...",
        "status": "MATCHING",
        "pickupLocation": "...",
        "dropoffLocation": "...",
        "vehicleType": "SEDAN",
        "paymentMethod": "CASH",
        "estimatedFare": 75000.00,
        "createdAt": "2026-05-22T10:00:00"
      }
    ]
  }
}
```

---

### 5.2 Get Booking Detail

```
GET /api/admin/bookings/{bookingId}
```

**Response:** `AdminBookingDetailResponse`

```json
{
  "code": 200,
  "message": "Fetched admin booking detail",
  "result": {
    "bookingId": "uuid",
    "customerId": "...",
    "driverId": "...",
    "status": "MATCHING",
    "pickupLocation": "...",
    "dropoffLocation": "...",
    "customerNote": "...",
    "pickupCoordinates": { "lat": 10.8221, "lng": 106.6885 },
    "dropoffCoordinates": { "lat": 10.8188, "lng": 106.6619 },
    "vehicleType": "SEDAN",
    "paymentMethod": "CASH",
    "estimatedFare": 75000.00,
    "promoCode": "",
    "estimateId": "...",
    "quoteId": "...",
    "quoteExpiresAt": "2026-05-22T12:00:00",
    "createdAt": "2026-05-22T10:00:00",
    "updatedAt": "2026-05-22T10:00:00",
    "customerInfo": { "userId": "...", "fullName": "...", "email": "...", "phone": "..." },
    "driverInfo": { "userId": "...", "fullName": "...", "email": "...", "phone": "..." }
  }
}
```

---

### 5.3 Get Booking Timeline

```
GET /api/admin/bookings/{bookingId}/timeline
```

**Response:** List of `AdminBookingTimelineResponse`

```json
{
  "code": 200,
  "result": [
    {
      "id": "audit-uuid",
      "bookingId": "booking-uuid",
      "adminId": "admin-id",
      "action": "ADMIN_CANCEL",
      "oldStatus": "MATCHING",
      "newStatus": "CANCELLED",
      "reason": "Operational issue",
      "createdAt": "2026-05-22T10:05:00"
    }
  ]
}
```

---

### 5.4 Cancel Booking (Admin)

```
POST /api/admin/bookings/{bookingId}/cancel
```

**Request Body:**

```json
{
  "reason": "Admin cancellation reason (required, max 1000 chars)"
}
```

**Response:** `AdminBookingDetailResponse` with `status: "CANCELLED"`

---

### 5.5 Retry Matching

```
POST /api/admin/bookings/{bookingId}/retry-matching
```

**Allowed when:** status is `MATCHING` or `ASSIGNED`

**Response:** `AdminBookingDetailResponse` with `status: "MATCHING"`

---

### 5.6 Force Update Status

```
PATCH /api/admin/bookings/{bookingId}/status
```

**Request Body:**

```json
{
  "status": "MATCHING",
  "reason": "Operational reason"
}
```

**Restrictions:**
- Cannot update terminal bookings (`COMPLETED`, `CANCELLED`)
- Cannot force to `COMPLETED` or `IN_PROGRESS`

---

## 6. BookingStatus Enum

```
CREATED → PENDING_PAYMENT → MATCHING → ASSIGNED → ACCEPTED → PICKUP → IN_PROGRESS → COMPLETED
                                                                                    ↗
                                                              CANCELLED (any non-terminal)
```

| Status | Description |
|--------|-------------|
| `CREATED` | Booking just created |
| `PENDING_PAYMENT` | Online payment pending (MOMO/VNPAY/CARD only) |
| `MATCHING` | Looking for a driver (Kafka → matching-service) |
| `ASSIGNED` | Driver assigned, waiting for acceptance |
| `ACCEPTED` | Driver accepted the ride |
| `PICKUP` | Driver arrived at pickup location |
| `IN_PROGRESS` | Ride in progress |
| `COMPLETED` | Ride completed |
| `CANCELLED` | Booking cancelled (terminal) |

---

## 7. RideStatus Enum

```
CREATED → MATCHING → ASSIGNED → ACCEPTED → PICKUP → IN_PROGRESS → COMPLETED → PAID
                                                ↗
                          CANCELLED (any step)
```

| Status | Description |
|--------|-------------|
| `CREATED` | Ride record created |
| `MATCHING` | Sending to matching-service |
| `ASSIGNED` | Driver assigned |
| `ACCEPTED` | Driver accepted |
| `PICKUP` | Driver at pickup point |
| `IN_PROGRESS` | Customer on board |
| `COMPLETED` | Ride finished |
| `PAID` | Payment confirmed |
| `CANCELLED` | Ride cancelled |

---

## 8. Request/Response Bodies

### BookingRequest (Create)

```json
{
  "pickupLocation": "string (required)",
  "dropoffLocation": "string (required)",
  "customerNote": "string (optional)",
  "pickupCoordinates": { "lat": 10.82, "lng": 106.68 },
  "dropoffCoordinates": { "lat": 10.81, "lng": 106.66 },
  "vehicleType": "SEDAN | BIKE | SUV | ECONOMY (required)",
  "paymentMethod": "CASH | MOMO | VNPAY | CARD (optional)",
  "estimatedFare": 75000.00,
  "promoCode": "string (optional)",
  "quoteToken": "string (optional)",
  "estimateId": "string (optional)",
  "quoteId": "string (optional)",
  "quotePayloadHash": "string (optional)",
  "quoteHashAlgorithm": "string (optional)",
  "quoteExpiresAt": "ISO 8601 datetime (optional)",
  "idempotencyKey": "UUID string (optional, recommended)"
}
```

### BookingResponse

```json
{
  "id": "UUID",
  "customerId": "string",
  "assignedDriverId": "string | null",
  "pickupLocation": "string",
  "dropoffLocation": "string",
  "customerNote": "string | null",
  "pickupCoordinates": { "lat": 10.82, "lng": 106.68 },
  "dropoffCoordinates": { "lat": 10.81, "lng": 106.66 },
  "vehicleType": "SEDAN",
  "paymentMethod": "CASH",
  "estimatedFare": 75000.00,
  "promoCode": "string | null",
  "estimateId": "string | null",
  "quoteId": "string | null",
  "quoteHashAlgorithm": "string | null",
  "status": "MATCHING",
  "createdAt": "2026-05-22T10:00:00",
  "updatedAt": "2026-05-22T10:00:00"
}
```

### RideResponse

```json
{
  "id": "UUID",
  "bookingId": "string",
  "customerId": "string",
  "driverId": "string",
  "pickupAddress": "string",
  "dropoffAddress": "string",
  "pickupLat": 10.82,
  "pickupLng": 106.68,
  "dropoffLat": 10.81,
  "dropoffLng": 106.66,
  "finalFare": null,
  "paymentMethod": "CASH",
  "status": "ASSIGNED",
  "createdAt": "2026-05-22T10:00:00",
  "updatedAt": "2026-05-22T10:00:00"
}
```

### CompleteRideRequest (Driver completes ride)

```json
{
  "finalFare": 75000.00,
  "paymentMethod": "CASH"
}
```

### AdminCancelBookingRequest

```json
{
  "reason": "string (required, max 1000 chars)"
}
```

### AdminUpdateBookingStatusRequest

```json
{
  "status": "MATCHING",
  "reason": "string (required)"
}
```

---

## 9. Error Responses

### 400 Bad Request

```json
{
  "code": 400,
  "message": "Validation Error",
  "errorMessage": "pickupLocation: Pickup location is required",
  "timestamp": "2026-05-22T10:00:00"
}
```

### 401 Unauthorized

```json
{
  "code": 401,
  "message": "Unauthorized",
  "errorMessage": "Authentication required",
  "timestamp": "2026-05-22T10:00:00"
}
```

### 403 Forbidden

```json
{
  "code": 403,
  "message": "Forbidden",
  "errorMessage": "Access denied",
  "timestamp": "2026-05-22T10:00:00"
}
```

### 404 Not Found

```json
{
  "code": 404,
  "message": "Not Found",
  "errorMessage": "Booking not found",
  "timestamp": "2026-05-22T10:00:00"
}
```

### 409 Conflict

```json
{
  "code": 409,
  "message": "Conflict",
  "errorMessage": "Booking was already completed. Cannot cancel it.",
  "timestamp": "2026-05-22T10:00:00"
}
```

### 429 Too Many Requests

```json
{
  "code": 429,
  "message": "Too Many Requests",
  "errorMessage": "Rate limit exceeded. Please retry after 1 second.",
  "timestamp": "2026-05-22T10:00:00"
}
```

---

## 10. Role Access Matrix

### CUSTOMER

| Endpoint | Method | Allowed |
|----------|--------|---------|
| `/api/v1/bookings` | POST | ✅ |
| `/api/v1/bookings/me` | GET | ✅ |
| `/api/v1/bookings/{id}` | GET | ✅ (own bookings only) |
| `/api/v1/bookings/{id}/cancel` | POST | ✅ (own bookings only) |
| `/api/v1/bookings/customer/{id}` | GET | ✅ (own ID only) |
| `/api/v1/bookings/customer/{id}/active` | GET | ✅ (own ID only) |
| `/api/v1/bookings/nearby` | GET | ✅ |
| `/api/v1/rides/{id}` | GET | ✅ (own rides only) |
| `/api/v1/rides/{id}/arrive` | POST | ❌ 403 |
| `/api/v1/rides/{id}/start` | POST | ❌ 403 |
| `/api/v1/rides/{id}/complete` | POST | ❌ 403 |
| `/api/admin/bookings` | GET | ❌ 403 |

### DRIVER

| Endpoint | Method | Allowed |
|----------|--------|---------|
| `/api/v1/rides/{id}` | GET | ✅ (assigned rides only) |
| `/api/v1/rides/{id}/arrive` | POST | ✅ |
| `/api/v1/rides/{id}/start` | POST | ✅ |
| `/api/v1/rides/{id}/complete` | POST | ✅ |
| `/api/v1/bookings/me` | GET | ❌ 403 |
| `/api/v1/bookings/customer/{id}` | GET | ❌ 403 |
| `/api/admin/bookings` | GET | ❌ 403 |

### ADMIN

| Endpoint | Method | Allowed |
|----------|--------|---------|
| `/api/admin/bookings` | GET | ✅ |
| `/api/admin/bookings/{id}` | GET | ✅ |
| `/api/admin/bookings/{id}/timeline` | GET | ✅ |
| `/api/admin/bookings/{id}/cancel` | POST | ✅ |
| `/api/admin/bookings/{id}/retry-matching` | POST | ✅ |
| `/api/admin/bookings/{id}/status` | PATCH | ✅ |

---

## 11. WebSocket / Realtime Events

**Connection:** Socket.IO via notification-service

```
URL: ws://localhost:9093?userId={userId}
```

Or through API Gateway (if WebSocket route is configured):

```
URL: ws://localhost:8080/socket.io?userId={userId}
```

### Room Management

| Event | Direction | Payload | Description |
|-------|-----------|---------|-------------|
| `join_room` | Client → Server | `"booking-id-string"` | Join a booking/ride room |
| `leave_room` | Client → Server | `"booking-id-string"` | Leave a booking/ride room |
| `joined_room_success` | Server → Client | `{ "bookingId": "...", "status": "success" }` | Confirmation |
| `left_room_success` | Server → Client | `{ "bookingId": "...", "status": "success" }` | Confirmation |

### Ride Lifecycle Events

All events are emitted via `new_notification` Socket.IO event with the following payload:

```json
{
  "userId": "room_rideId or userId",
  "title": "Event Title",
  "message": "Human-readable message",
  "type": "PUSH | ROOM_BROADCAST",
  "status": "SENT | BROADCASTED",
  "createdAt": "2026-05-22T10:00:00"
}
```

### Kafka Topics → Socket.IO Mapping

| Kafka Topic | Event Type | Description | FE Action |
|-------------|------------|-------------|-----------|
| `ride.created` | `ride.created` | New ride created, searching for driver | Show "Finding driver..." |
| `ride.assigned` | `ride.assigned` | Driver assigned to ride | Show driver info |
| `ride.accepted` | `ride.accepted` | Driver accepted the ride | Show "Driver is coming" |
| `ride.rejected` | `ride.rejected` | Driver rejected, re-matching | Show "Finding another driver..." |
| `ride.arrived` | `ride.arrived` | Driver arrived at pickup | Show "Driver has arrived!" |
| `ride.started` | `ride.started` | Ride started | Show "Ride in progress" |
| `ride.completed` | `ride.completed` | Ride completed | Show "Ride completed" |
| `ride.cancelled` | `ride.cancelled` | Ride cancelled | Show cancellation reason |
| `booking-events` | Various | Booking lifecycle events | Process by `type` field |
| `booking.timeout` | `booking.timeout` | Matching timeout | Show "No driver found" |
| `payment.completed` | `payment.completed` | Payment succeeded | Show "Payment successful" |

### Event Payload Fields (Kafka JSON)

```json
{
  "eventType": "ride.assigned",
  "rideId": "ride-uuid",
  "bookingId": "booking-uuid",
  "customerId": "customer-user-id",
  "driverId": "driver-user-id",
  "status": "ASSIGNED",
  "timestamp": "2026-05-22T10:00:00",
  "reason": "optional cancellation reason",
  "pickupLat": 10.8221,
  "pickupLng": 106.6885,
  "dropoffLat": 10.8188,
  "dropoffLng": 106.6619
}
```

### FE Subscription Strategy

1. **On booking creation:** `join_room` with `bookingId`
2. **Listen:** `new_notification` event on Socket.IO
3. **Parse** the notification `type` and `title` to determine UI action
4. **On ride complete/cancel:** `leave_room` with `bookingId`
5. **For driver location:** Subscribe to `driver.location.updated` if available

---

## 12. E2E Flow Checklists

### CASH Flow

| # | Step | API / Event | Expected Status |
|---|------|-------------|-----------------|
| 1 | Customer creates booking (CASH) | `POST /api/v1/bookings` | `MATCHING` |
| 2 | Kafka: `ride.created` emitted | — | — |
| 3 | Matching assigns driver → Kafka: `ride.assigned` | — | `ASSIGNED` |
| 4 | Driver accepts | — | `ACCEPTED` |
| 5 | Kafka: `ride.accepted` | — | — |
| 6 | Driver arrives | `POST /api/v1/rides/{id}/arrive` | `PICKUP` |
| 7 | Kafka: `ride.arrived` | — | — |
| 8 | Driver starts ride | `POST /api/v1/rides/{id}/start` | `IN_PROGRESS` |
| 9 | Kafka: `ride.started` | — | — |
| 10 | Driver completes ride | `POST /api/v1/rides/{id}/complete` | `COMPLETED` |
| 11 | Kafka: `ride.completed` | — | — |
| 12 | Booking status → `COMPLETED` | `GET /api/v1/bookings/{id}` | `COMPLETED` |

### ONLINE Flow

| # | Step | API / Event | Expected Status |
|---|------|-------------|-----------------|
| 1 | Customer creates booking (MOMO/VNPAY/CARD) | `POST /api/v1/bookings` | `PENDING_PAYMENT` |
| 2 | Kafka: `payment.requested` emitted | — | — |
| 3 | Payment completed → Kafka: `payment.completed` | — | — |
| 4 | Booking transitions to `MATCHING` | — | `MATCHING` |
| 5 | Kafka: `ride.created` emitted | — | — |
| 6 | Matching assigns driver → Kafka: `ride.assigned` | — | `ASSIGNED` |
| 7–12 | Same as CASH flow steps 4–12 | — | — |

---

## Gateway Route Reference

| Route Pattern | Service | Notes |
|---------------|---------|-------|
| `/api/v1/bookings/**` | booking-service | Customer booking endpoints |
| `/api/admin/bookings/**` | booking-service | Admin booking endpoints |
| `/api/v1/rides/**` | ride-service | Ride lifecycle + query |
| `/auth/**` | auth-service | Authentication (public) |
| `/api/payments/**` | payment-service | Payment processing |
| `/api/notifications/**` | notification-service | Notifications (public) |
| `/socket.io/**` | notification-service | WebSocket (ws://) |
| `/api/pricing/**` | pricing-service | Pricing (public) |
| `/api/drivers/**` | driver-service | Driver management |
| `/api/users/**` | user-service | User management |
| `/api/reviews/**` | review-service | Reviews (public) |

> **Note:** matching-service has NO public REST API. It operates purely as a Kafka consumer.
