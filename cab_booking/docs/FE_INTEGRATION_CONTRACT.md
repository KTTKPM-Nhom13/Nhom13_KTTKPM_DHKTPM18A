# FE Integration Contract — Booking / Ride / Matching / Driver

> Generated: 2026-05-22
> Updated: 2026-05-24 — Added driver-service endpoints, ride-service Socket.IO, hardened error handling
> Scope: `booking-service`, `ride-service`, `matching-service`, `driver-service`
> All requests go through **API Gateway** — never call service ports directly.

---

## Table of Contents

1. [Base URL & Auth](#1-base-url--auth)
2. [Response Wrapper](#2-response-wrapper)
3. [Booking Endpoints (Customer)](#3-booking-endpoints-customer)
4. [Ride Endpoints (Driver)](#4-ride-endpoints-driver)
5. [Admin Booking Endpoints](#5-admin-booking-endpoints)
6. [Driver Service Endpoints](#6-driver-service-endpoints)
7. [BookingStatus Enum](#7-bookingstatus-enum)
8. [RideStatus Enum](#8-ridestatus-enum)
9. [DriverAvailabilityStatus Enum](#9-driveravailabilitystatus-enum)
10. [Request/Response Bodies](#10-requestresponse-bodies)
11. [Error Responses](#11-error-responses)
12. [Role Access Matrix](#12-role-access-matrix)
13. [WebSocket / Realtime Events](#13-websocket--realtime-events)
14. [E2E Flow Checklists](#14-e2e-flow-checklists)

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

## 6. Driver Service Endpoints

All driver endpoints are prefixed with `/api/drivers/me` and require **DRIVER role**.
Driver identity is extracted from JWT — never from request body.

### 6.1 Get Driver Profile

```
GET /api/drivers/me/profile
```

**Response:**

```json
{
  "code": 200,
  "message": "Fetched driver profile successfully",
  "result": {
    "id": "uuid",
    "externalUserId": "driver-user-id",
    "fullName": "Nguyen Van A",
    "email": "driver@example.com",
    "phoneNumber": "0901234567",
    "avatarUrl": "https://...",
    "licenseNumber": "B2-123456",
    "vehicleType": "SEDAN",
    "vehiclePlate": "59A-12345",
    "vehicleModel": "Toyota Vios",
    "vehicleColor": "White",
    "serviceArea": "HCMC",
    "availabilityStatus": "ONLINE",
    "verificationStatus": "APPROVED",
    "accountStatus": "ACTIVE",
    "currentLatitude": 10.8221,
    "currentLongitude": 106.6885,
    "lastOnlineAt": "2026-05-24T10:00:00",
    "approvedAt": "2026-05-01T00:00:00",
    "totalCompletedRides": 42,
    "averageRating": 4.85,
    "totalEarnings": 1500000.00,
    "createdAt": "2026-05-01T00:00:00",
    "updatedAt": "2026-05-24T10:00:00"
  }
}
```

---

### 6.2 Upsert Driver Profile

```
PUT /api/drivers/me/profile
```

**Request Body:**

```json
{
  "fullName": "Nguyen Van A",
  "email": "driver@example.com",
  "phoneNumber": "0901234567",
  "avatarUrl": "https://...",
  "licenseNumber": "B2-123456",
  "vehicleType": "SEDAN",
  "vehiclePlate": "59A-12345",
  "vehicleModel": "Toyota Vios",
  "vehicleColor": "White",
  "serviceArea": "HCMC"
}
```

**Response:** `DriverProfileResponse` (same as 6.1)

---

### 6.3 Update Availability (Go ONLINE / OFFLINE)

```
PATCH /api/drivers/me/availability
```

**Request Body:**

```json
{
  "availabilityStatus": "ONLINE",
  "currentLatitude": 10.8221,
  "currentLongitude": 106.6885
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `availabilityStatus` | `string` | ✅ | `ONLINE`, `OFFLINE`, `ON_TRIP` |
| `currentLatitude` | `decimal` | ❌ | Current latitude |
| `currentLongitude` | `decimal` | ❌ | Current longitude |

**Response:**

```json
{
  "code": 200,
  "message": "Updated driver availability successfully",
  "result": {
    "externalUserId": "driver-user-id",
    "availabilityStatus": "ONLINE",
    "verificationStatus": "APPROVED",
    "currentLatitude": 10.8221,
    "currentLongitude": 106.6885,
    "lastOnlineAt": "2026-05-24T10:00:00"
  }
}
```

**Side effects:**
- `ONLINE` → writes `driver:status:{driverId}=AVAILABLE` + `GEOADD driver:available:locations`
- `OFFLINE` → writes `driver:status:{driverId}=OFFLINE` + `ZREM driver:available:locations`

---

### 6.4 Heartbeat Location (Redis-only)

```
PATCH /api/drivers/me/location
```

**Auth:** DRIVER role, must be ONLINE with no active ride.

**Request Body:**

```json
{
  "lat": 10.8221,
  "lng": 106.6885
}
```

**Response:** `DriverAvailabilityResponse` (same shape as 6.3)

**Side effects:** Redis GEOADD only — no PostgreSQL write. Fire every 10-15s.

---

### 6.5 Get Current Ride

```
GET /api/drivers/me/current-ride
```

**Response:**

```json
{
  "code": 200,
  "message": "Fetched current ride successfully",
  "result": {
    "rideId": "ride-uuid",
    "bookingId": "booking-uuid",
    "customerId": "customer-user-id",
    "rideStatus": "ASSIGNED",
    "pickupAddress": "12 Nguyen Van Bao, Go Vap",
    "destinationAddress": "Tan Son Nhat Airport",
    "pickupLocation": { "lat": 10.8221, "lng": 106.6885 },
    "destinationLocation": { "lat": 10.8188, "lng": 106.6619 },
    "vehicleType": "SEDAN",
    "paymentMethod": "CASH",
    "estimatedFare": 75000.00,
    "requestedAt": "2026-05-24T10:00:00",
    "driverAvailabilityStatus": "ON_TRIP",
    "currentLocation": null
  }
}
```

---

### 6.6 Handle Assignment (Accept/Reject via single endpoint)

```
POST /api/drivers/me/rides/assignment
```

**Request Body:**

```json
{
  "rideId": "ride-uuid",
  "action": "ACCEPT"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `rideId` | `string` | ✅ | Ride ID from `ride.assigned` Kafka event |
| `action` | `string` | ✅ | `ACCEPT` or `REJECT` |

**Response:** `DriverCurrentRideResponse` (same as 6.5)

---

### 6.7 Accept Ride (Direct)

```
POST /api/drivers/me/rides/{rideId}/accept
```

**Response:** `DriverCurrentRideResponse`

**Side effects:** Removes driver from `driver:available:locations` GEO.

---

### 6.8 Reject Ride (Direct)

```
POST /api/drivers/me/rides/{rideId}/reject
```

**Response:** `DriverCurrentRideResponse`

**Side effects:** Driver stays in `driver:available:locations` GEO (never removed on ASSIGNED).

---

### 6.9 Update Ride Status (Lifecycle)

```
PATCH /api/drivers/me/rides/current
```

**Request Body:**

```json
{
  "rideStatus": "EN_ROUTE_PICKUP",
  "currentLatitude": 10.8221,
  "currentLongitude": 106.6885
}
```

| `rideStatus` | Allowed From |
|--------------|-------------|
| `EN_ROUTE_PICKUP` | `ACCEPTED` |
| `ARRIVED_PICKUP` | `EN_ROUTE_PICKUP` or `ACCEPTED` |
| `IN_PROGRESS` | `ACCEPTED`, `EN_ROUTE_PICKUP`, or `ARRIVED_PICKUP` |

**Response:** `DriverCurrentRideResponse`

---

### 6.10 Complete Ride (Driver-side)

```
POST /api/drivers/me/rides/current/complete
```

**Request Body (optional):**

```json
{
  "finalFare": 75000.00,
  "paymentMethod": "CASH"
}
```

**Response:** `DriverCurrentRideResponse`

**Side effects:** Driver re-added to `driver:available:locations` GEO.

---

### 6.11 Get Earnings Summary

```
GET /api/drivers/me/earnings/summary
```

**Response:**

```json
{
  "code": 200,
  "message": "Fetched driver earnings summary successfully",
  "result": {
    "externalUserId": "driver-user-id",
    "availabilityStatus": "ONLINE",
    "totalCompletedRides": 42,
    "averageRating": 4.85,
    "totalEarnings": 1500000.00,
    "totalGrossAmount": 2100000.00,
    "totalDriverAmount": 1500000.00,
    "currentRideActive": false,
    "lastOnlineAt": "2026-05-24T10:00:00"
  }
}
```

---

## 7. BookingStatus Enum

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

## 8. RideStatus Enum

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

## 9. DriverAvailabilityStatus Enum

```
OFFLINE → ONLINE → ON_TRIP
   ↑                  │
   └──────────────────┘ (ride completes/cancels)
```

| Status | Description | Redis State |
|--------|-------------|-------------|
| `OFFLINE` | Driver not accepting rides | Not in GEO, `driver:status:{id}=OFFLINE` |
| `ONLINE` | Driver available for matching | In `driver:available:locations` GEO, `driver:status:{id}=AVAILABLE` |
| `ON_TRIP` | Driver has an active ride | Not in GEO (removed on accept), `driver:status:{id}=ON_TRIP` |

**Transitions:**
- `OFFLINE → ONLINE`: Driver calls `PATCH /api/drivers/me/availability` with `ONLINE`. Added to Redis GEO.
- `ONLINE → ON_TRIP`: Driver accepts a ride (`POST /api/drivers/me/rides/{rideId}/accept`). Removed from Redis GEO.
- `ON_TRIP → ONLINE`: Ride completes or cancels. Re-added to Redis GEO from profile coordinates.
- `ONLINE → OFFLINE`: Driver calls `PATCH /api/drivers/me/availability` with `OFFLINE`. Removed from Redis GEO.
- `ON_TRIP → OFFLINE`: Not allowed while ride is active.

---

## 10. Request/Response Bodies

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

## 11. Error Responses

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

## 12. Role Access Matrix

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
| `/api/drivers/me/**` | ANY | ❌ 403 |
| `/api/admin/bookings` | GET | ❌ 403 |

### DRIVER

| Endpoint | Method | Allowed |
|----------|--------|---------|
| `/api/v1/rides/{id}` | GET | ✅ (assigned rides only) |
| `/api/v1/rides/{id}/arrive` | POST | ✅ |
| `/api/v1/rides/{id}/start` | POST | ✅ |
| `/api/v1/rides/{id}/complete` | POST | ✅ |
| `/api/drivers/me/profile` | GET | ✅ |
| `/api/drivers/me/profile` | PUT | ✅ |
| `/api/drivers/me/availability` | PATCH | ✅ |
| `/api/drivers/me/location` | PATCH | ✅ (heartbeat, ONLINE only) |
| `/api/drivers/me/current-ride` | GET | ✅ |
| `/api/drivers/me/rides/assignment` | POST | ✅ |
| `/api/drivers/me/rides/{rideId}/accept` | POST | ✅ |
| `/api/drivers/me/rides/{rideId}/reject` | POST | ✅ |
| `/api/drivers/me/rides/current` | PATCH | ✅ (lifecycle) |
| `/api/drivers/me/rides/current/complete` | POST | ✅ |
| `/api/drivers/me/earnings/summary` | GET | ✅ |
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

## 13. WebSocket / Realtime Events

There are **two separate Socket.IO servers**. FE must connect to both.

### 13.1 Notification Service Socket.IO (Booking Lifecycle)

**Connection:** Socket.IO via notification-service

```
URL: ws://localhost:9093?userId={userId}
```

Or through API Gateway (if WebSocket route is configured):

```
URL: ws://localhost:8080/socket.io?userId={userId}
```

#### Room Management

| Event | Direction | Payload | Description |
|-------|-----------|---------|-------------|
| `join_room` | Client → Server | `"booking-id-string"` | Join a booking/ride room |
| `leave_room` | Client → Server | `"booking-id-string"` | Leave a booking/ride room |
| `joined_room_success` | Server → Client | `{ "bookingId": "...", "status": "success" }` | Confirmation |
| `left_room_success` | Server → Client | `{ "bookingId": "...", "status": "success" }` | Confirmation |

#### Ride Lifecycle Events

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

#### Kafka Topics → Socket.IO Mapping (notification-service)

| Kafka Topic | Event Type | Description | FE Action |
|-------------|------------|-------------|-----------|
| `ride.created` | `ride.created` | New ride created, searching for driver | Show "Finding driver..." |
| `ride.assigned` | `ride.assigned` | Driver assigned to ride | Show driver info |
| `ride.accepted` | `ride.accepted` | Driver accepted the ride | Show "Driver is coming" |
| `ride.rejected` | `ride.rejected` | Driver rejected, re-matching | Show "Finding another driver..." |
| `ride.arrived` | `ride.arrived` | Driver arrived at pickup | Show "Driver has arrived!" |
| `ride.started` | `ride.started` | Ride started | Show "Ride in progress" |
| `ride.completed` | `ride.completed` | Ride completed | Show "Ride completed" |
| `ride.cancelled` | `ride.cancelled` | Ride cancelled (canonical topic) | Show cancellation reason |
| `booking.timeout` | `booking.timeout` | Matching timeout | Show "No driver found" |
| `payment.completed` | `payment.completed` | Payment succeeded | Show "Payment successful" |

#### Event Payload Fields (Kafka JSON)

**`ride.assigned`** (from [`RideAssignedEvent`](../matching-service/src/main/java/com/cab/matching/core/dto/event/outbound/RideAssignedEvent.java)):

```json
{
  "eventId": "uuid",
  "eventType": "ride.assigned",
  "rideId": "ride-uuid",
  "bookingId": "booking-uuid",
  "driverId": "driver-user-id",
  "customerId": "customer-user-id",
  "pickupAddress": "12 Nguyen Van Bao, Go Vap",
  "dropoffAddress": "Tan Son Nhat Airport",
  "pickup": { "lat": 10.8221, "lng": 106.6885 },
  "dropoff": { "lat": 10.8188, "lng": 106.6619 },
  "vehicleType": "SEDAN",
  "paymentMethod": "CASH",
  "estimatedFare": 75000.00,
  "timestamp": "2026-05-22T10:00:00"
}
```

**Generic ride event payload** (ride.created, ride.accepted, ride.arrived, ride.started, ride.completed, ride.cancelled):

```json
{
  "eventType": "ride.cancelled",
  "aggregateId": "ride-uuid",
  "rideId": "ride-uuid",
  "bookingId": "booking-uuid",
  "customerId": "customer-user-id",
  "driverId": "driver-user-id",
  "status": "CANCELLED",
  "reason": "optional cancellation reason",
  "timestamp": "2026-05-22T10:00:00"
}
```

### 13.2 Ride Service Socket.IO (Realtime GPS Tracking)

**Connection:** Socket.IO via ride-service

```
URL: ws://localhost:9095
```

**Auth:** JWT token sent via Socket.IO `auth` header or `token` query parameter.

```javascript
const socket = io("ws://localhost:9095", {
  auth: { token: "Bearer <access_token>" }
});
```

#### Client → Server Events

| Event | Payload | Description |
|-------|---------|-------------|
| `join_ride` | `{ "rideId": "ride-uuid" }` | Join a ride room to receive location updates. Requires CUSTOMER or DRIVER role with access to the ride. |
| `leave_ride` | `{ "rideId": "ride-uuid" }` | Leave a ride room |
| `driver.location.update` | `{ "rideId": "ride-uuid", "lat": 10.8221, "lng": 106.6885 }` | Driver sends GPS update. Only DRIVER role. `rideId` is optional — server auto-resolves active ride. |

#### Server → Client Events

| Event | Payload | Description |
|-------|---------|-------------|
| `joined_ride` | `{ "rideId": "ride-uuid", "status": "joined" }` | Confirmation of room join |
| `left_ride` | `{ "rideId": "ride-uuid", "status": "left" }` | Confirmation of room leave |
| `driver.location.updated` | `{ "rideId": "ride-uuid", "driverId": "driver-id", "lat": 10.8221, "lng": 106.6885, "timestamp": "..." }` | **Broadcast to all clients in ride room** when driver sends location update. FE should update driver marker on map. |
| `socket_error` | `{ "code": "AUTH_FAILED", "message": "..." }` | Error (auth failure, invalid ride, etc.) |

#### Error Codes

| Code | Description |
|------|-------------|
| `AUTH_FAILED` | JWT missing, invalid, or expired |
| `MISSING_RIDE_ID` | `join_ride` called without `rideId` |
| `RIDE_NOT_FOUND` | Ride ID doesn't exist |
| `ACCESS_DENIED` | User not authorized for this ride |
| `LOCATION_VALIDATION_ERROR` | Invalid lat/lng coordinates |
| `INTERNAL_ERROR` | Server error |

### 13.3 FE Subscription Strategy

1. **On booking creation:** Connect to notification-service (`ws://localhost:9093?userId={userId}`), then `join_room` with `bookingId`
2. **Listen:** `new_notification` event on notification-service Socket.IO
3. **Parse** the notification `type` and `title` to determine UI action
4. **When ride is ASSIGNED/ACCEPTED:** Connect to ride-service (`ws://localhost:9095`) with JWT, then `join_ride` with `rideId`
5. **Listen:** `driver.location.updated` on ride-service Socket.IO → update driver marker on map
6. **On ride complete/cancel:** `leave_room` on notification-service, `leave_ride` on ride-service
7. **Driver app:** On ride ACCEPTED, emit `driver.location.update` every 3-5s with current GPS coordinates

---

## 14. E2E Flow Checklists

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

| Route Pattern | Service | Port | Notes |
|---------------|---------|------|-------|
| `/api/v1/bookings/**` | booking-service | 8082 | Customer booking endpoints |
| `/api/admin/bookings/**` | booking-service | 8082 | Admin booking endpoints |
| `/api/v1/rides/**` | ride-service | 9095 | Ride lifecycle + query (REST) |
| `/api/drivers/**` | driver-service | 8084 | Driver management (`/api/drivers/me/**`) |
| `/auth/**` | auth-service | 8081 | Authentication (public) |
| `/api/payments/**` | payment-service | — | Payment processing |
| `/api/notifications/**` | notification-service | 9093 | Notifications (public) |
| `/socket.io/**` | notification-service | 9093 | Booking lifecycle Socket.IO (ws://) |
| `/api/pricing/**` | pricing-service | 8086 | Pricing (public) |
| `/api/users/**` | user-service | 8085 | User management |
| `/api/reviews/**` | review-service | — | Reviews (public) |

> **Note:** matching-service has NO public REST API. It operates purely as a Kafka consumer.

### Socket.IO Endpoints (Direct Connection — Not via Gateway)

| Socket.IO Server | URL | Purpose |
|------------------|-----|---------|
| notification-service | `ws://localhost:9093?userId={userId}` | Booking lifecycle events (`new_notification`) |
| ride-service | `ws://localhost:9095` | Realtime driver GPS tracking (`driver.location.updated`) |

---

## Known Blockers Before FE Integration

| # | Severity | Blocker | Owner | Impact | Status |
|---|----------|---------|-------|--------|--------|
| 1 | ✅ NONE | Verify `ride.assigned` payload includes `rideId`, `bookingId`, `driverId`, `customerId`, `pickup/dropoff`, `vehicleType`, `estimatedFare` — confirmed in [`RideAssignedEvent.java`](../matching-service/src/main/java/com/cab/matching/core/dto/event/outbound/RideAssignedEvent.java) | matching-service | ✅ VERIFIED — all fields present | RESOLVED |
| 2 | ✅ NONE | `driver.location.updated` realtime event — implemented in [`RideSocketEventHandler`](../ride-service/src/main/java/com/cab/ride/core/socket/RideSocketEventHandler.java) on ride-service Socket.IO (port 9095). Driver emits `driver.location.update`, server broadcasts `driver.location.updated` to all clients in the ride room. | ride-service | ✅ VERIFIED — fully implemented | RESOLVED |
| 3 | 🟡 P2 | Local gateway route for `/socket.io/**` to ride-service (port 9095) not yet in `api-gateway/src/main/resources/application.yaml`. FE must connect directly to `ws://localhost:9095` for ride tracking Socket.IO. Notification-service Socket.IO on port 9093 is separate. | api-gateway config | LOW — direct `ws://localhost:9095` works as fallback | OPEN |
| 4 | 🟡 P2 | booking-service uses `com.cab.booking.common.ApiResponse` (record: `success`, `data`, `errorCode`) for success responses but `iuh.fit.common.dto.response.ApiResponse` (class: `code`, `result`, `errorMessage`) for error responses via GlobalExceptionHandler. FE must handle BOTH shapes for booking-service endpoints. | booking-service | MEDIUM — inconsistent response shape for success vs error | DOCUMENTED |
| 5 | 🟢 P1 | Swagger UI not accessible via API Gateway (`/swagger-ui.html` not routed). Each service has its own Swagger at its direct port. FE devs should use this contract document + Postman collections as the source of truth. | api-gateway config | LOW — Postman collections available | DOCUMENTED |
