# Location Architecture Audit Report

> **Generated:** 2026-05-24
> **Scope:** driver-service, matching-service, ride-service, notification-service, booking-service, api-gateway
> **Purpose:** Audit before refactoring 2 location types (Driver Availability vs Ride Tracking)
> **Risk Level: 🔴 HIGH** — Critical architectural conflicts found

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Location Endpoints/Socket Inventory](#2-location-endpointssocket-inventory)
3. [Redis Keys Currently Used](#3-redis-keys-currently-used)
4. [Kafka Topics Related to Location](#4-kafka-topics-related-to-location)
5. [Driver Availability Flow (Current)](#5-driver-availability-flow-current)
6. [Ride Tracking Flow (Current)](#6-ride-tracking-flow-current)
7. [Bugs & Security Holes](#7-bugs--security-holes)
8. [Conflicts Between 2 Location Types](#8-conflicts-between-2-location-types)
9. [Proposed Architecture](#9-proposed-architecture)
10. [Changes Required Per Service](#10-changes-required-per-service)
11. [Risk Assessment](#11-risk-assessment)

---

## 1. Executive Summary

### Critical Finding: Availability Location is BROKEN

The system has a **fundamental architectural gap**: when a driver goes ONLINE (no active ride), their location is saved to **PostgreSQL only** (`DriverProfile.currentLatitude/currentLongitude`) but is **NEVER written to Redis GEO** (`driver:locations`). The matching-service queries `driver:locations` via `GEOSEARCH` to find nearby drivers, but no driver ever appears there from the availability flow.

**The only code that writes to `driver:locations` is [`RideLocationService.updateRedisGeo()``](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:154)** — which runs during an **active ride** (ride tracking), not during driver availability.

This means: **matching-service can only find drivers who are currently on a ride** — the exact opposite of the intended behavior.

---

## 2. Location Endpoints/Socket Inventory

### 2.1 Driver Availability Location (driver-service)

| # | Endpoint/Socket | Service | File | Line | Auth | Notes |
|---|----------------|---------|------|------|------|-------|
| 1 | `PATCH /api/drivers/me/availability` | driver-service | [`DriverProfileController.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/controller/DriverProfileController.java:54) | 54 | JWT DRIVER | Accepts `availabilityStatus`, `currentLatitude`, `currentLongitude` |
| 2 | `PATCH /api/drivers/me/rides/current` | driver-service | [`DriverProfileController.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/controller/DriverProfileController.java:104) | 104 | JWT DRIVER | Updates ride status, optionally accepts `currentLatitude`, `currentLongitude` |
| 3 | `GET /internal/drivers/{driverId}/availability` | driver-service | [`InternalDriverStatusController.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/controller/InternalDriverStatusController.java:21) | 21 | Internal | Checks availability for other services |

### 2.2 Ride Tracking Location (ride-service)

| # | Endpoint/Socket | Service | File | Line | Auth | Notes |
|---|----------------|---------|------|------|------|-------|
| 4 | `POST /api/v1/rides/location` | ride-service | [`LocationController.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/controller/LocationController.java:50) | 50 | `@PreAuthorize("hasRole('DRIVER')")` | REST fallback, requires `rideId` |
| 5 | Socket.IO `driver.location.update` | ride-service | [`RideSocketEventHandler.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/socket/RideSocketEventHandler.java:102) | 102 | JWT via Socket handshake | Primary realtime channel |
| 6 | Socket.IO `join_ride` | ride-service | [`RideSocketEventHandler.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/socket/RideSocketEventHandler.java:67) | 67 | JWT | Customer joins ride room for tracking |
| 7 | Socket.IO `leave_ride` | ride-service | [`RideSocketEventHandler.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/socket/RideSocketEventHandler.java:90) | 90 | JWT | Customer leaves ride room |

### 2.3 Notification Socket (notification-service)

| # | Endpoint/Socket | Service | File | Line | Auth | Notes |
|---|----------------|---------|------|------|------|-------|
| 8 | Socket.IO `join_room` | notification-service | [`SocketIOService.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/SocketIOService.java:43) | 43 | **userId from URL param** ⚠️ | Joins booking room by bookingId |
| 9 | Socket.IO `leave_room` | notification-service | [`SocketIOService.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/SocketIOService.java:61) | 61 | userId from URL param | Leaves booking room |
| 10 | Socket.IO `send_message` | notification-service | [`SocketIOService.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/SocketIOService.java:52) | 52 | userId from URL param | Chat within booking room |

---

## 3. Redis Keys Currently Used

### 3.1 driver-service Redis Keys

| Key Pattern | Type | Written By | Read By | Purpose |
|-------------|------|-----------|---------|---------|
| `driver:status:{driverId}` | STRING | [`DriverStatusService.writeDriverStatus()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverStatusService.java:25) | matching-service | Values: `OFFLINE`, `AVAILABLE`, `ASSIGNED`, `BUSY` |
| `driver:vehicleType:{driverId}` | STRING | [`DriverStatusService.writeDriverVehicleTypeMetadata()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverStatusService.java:63) | matching-service | Vehicle type for filtering |
| `driver:profile:{driverId}` | HASH | [`DriverStatusService.writeDriverVehicleTypeMetadata()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverStatusService.java:71) | — | Profile metadata |
| `driver:{driverId}:pending-ride` | HASH | [`DriverRideCommandService.writePendingRide()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverRideCommandService.java:353) | driver-service | Pending assignment data, TTL=30s |

### 3.2 matching-service Redis Keys

| Key Pattern | Type | Written By | Read By | Purpose |
|-------------|------|-----------|---------|---------|
| **`driver:locations`** | **GEO** | **ride-service** ⚠️ | matching-service | **Nearby driver search — CRITICAL CONFLICT** |
| `driver:lock:{driverId}` | STRING | matching-service | matching-service | Per-driver lock during assignment, TTL=60s |
| `matching:lock:{rideId}` | STRING | matching-service | matching-service | Per-ride matching lock, TTL=60s |
| `matching:request:{rideId}` | STRING | matching-service | matching-service | Cached matching request for retry |
| `matching:assigned:{rideId}` | STRING | matching-service | matching-service | Flag: ride already assigned |
| `matching:driver:{rideId}` | STRING | matching-service | matching-service | Assigned driver ID |
| `matching:failed:{rideId}` | STRING | matching-service | matching-service | Matching failure reason |
| `matching:attempt:{rideId}` | STRING | matching-service | matching-service | Current attempt number |
| `ride:{rideId}:rejected-drivers` | SET | matching-service | matching-service | Drivers who rejected this ride |
| `booking:cancelled:{rideId}` | STRING | matching-service | matching-service | Cancelled ride flag |

### 3.3 ride-service Redis Keys

| Key Pattern | Type | Written By | Read By | Purpose |
|-------------|------|-----------|---------|---------|
| **`driver:locations`** | **GEO** | [`RideLocationService.updateRedisGeo()`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:154) | matching-service | **GEOADD during active ride — CONFLICT** |

### 3.4 booking-service Redis Keys

| Key Pattern | Type | Written By | Read By | Purpose |
|-------------|------|-----------|---------|---------|
| `booking:processed-event:{eventId}` | STRING | [`BookingLifecycleEventListener`](cab_booking/booking-service/src/main/java/com/cab/booking/core/listener/BookingLifecycleEventListener.java:34) | booking-service | Deduplication, TTL=6h |
| `booking:timeout:queue` | ZSET | [`BookingLifecycleEventListener`](cab_booking/booking-service/src/main/java/com/cab/booking/core/listener/BookingLifecycleEventListener.java:205) | booking-service | Timeout scheduling |

---

## 4. Kafka Topics Related to Location

### 4.1 Topic → Producer → Consumer Map

| Topic | Producer | Consumer(s) | Purpose |
|-------|----------|-------------|---------|
| `ride.created` | booking-service | matching-service, notification-service, ride-service | Triggers matching |
| `ride.assigned` | matching-service | driver-service, booking-service, notification-service | Driver assigned |
| `ride.accepted` | driver-service | booking-service, notification-service | Driver accepted |
| `ride.rejected` | driver-service | matching-service, booking-service, notification-service | Driver rejected → rematch |
| `ride.cancelled` | booking-service, matching-service | driver-service, matching-service, booking-service, notification-service | Ride cancelled |
| `ride.arrived` | ride-service | booking-service, notification-service | Driver at pickup |
| `ride.started` | ride-service | booking-service, notification-service | Ride in progress |
| `ride.completed` | ride-service | driver-service, booking-service, notification-service, payment-service, review-service | Ride finished |
| `matching.retry.requested` | matching-service | matching-service | Retry matching |
| `matching.failed` | matching-service | — | Matching failure |
| **`driver.location.updated`** | **ride-service** | **⚠️ NONE** | **Published but never consumed** |

### 4.2 `driver.location.updated` — Producer Details

**File:** [`RideLocationService.java:33`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:33)

```java
private static final String KAFKA_LOCATION_TOPIC = "driver.location.updated";
```

**Topic config:** [`KafkaTopicConfig.java:18`](cab_booking/ride-service/src/main/java/com/cab/ride/config/KafkaTopicConfig.java:18)

**Payload** ([`DriverLocationEvent.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/dto/event/DriverLocationEvent.java)):

```json
{
  "eventId": "uuid",
  "eventType": "DRIVER_LOCATION_UPDATED",
  "rideId": "ride-uuid",
  "bookingId": "booking-uuid",
  "driverId": "driver-user-id",
  "lat": 10.8221,
  "lng": 106.6885,
  "heading": 45.0,
  "speed": 30.0,
  "timestamp": "2026-05-24T08:00:00Z"
}
```

**Consumer:** **NONE** — No service has a `@KafkaListener` for `driver.location.updated`. The FE_INTEGRATION_CONTRACT.md line 941 confirms: *"driver.location.updated realtime event — currently not emitted by any service. FE cannot show real-time driver position on map via Socket.IO"*

---

## 5. Driver Availability Flow (Current)

### 5.1 Flow Diagram

```
Driver App
  │
  ├─ PATCH /api/drivers/me/availability
  │    body: { availabilityStatus: "ONLINE", currentLatitude: 10.82, currentLongitude: 106.68 }
  │    │
  │    ▼
  │  DriverProfileController.updateAvailability()          [line 54]
  │    │
  │    ▼
  │  DriverProfileService.updateAvailability()              [line 78]
  │    │  1. Parse status (OFFLINE/ONLINE/ON_TRIP)
  │    │  2. Validate: ONLINE requires APPROVED verification
  │    │  3. Set profile.availabilityStatus = ONLINE
  │    │  4. Set profile.currentLatitude = lat              ← PostgreSQL ONLY
  │    │  5. Set profile.currentLongitude = lng             ← PostgreSQL ONLY
  │    │  6. Set profile.lastOnlineAt = now
  │    │
  │    ▼
  │  DriverStatusService.writeDriverStatus(profile)         [line 25]
  │    │  Maps to Redis status:
  │    │    ONLINE + no currentRide → "AVAILABLE"
  │    │    ONLINE + currentRide    → "BUSY" (should not happen at this point)
  │    │    OFFLINE                 → "OFFLINE"
  │    │
  │    │  Writes:
  │    │    Redis SET driver:status:{driverId} = "AVAILABLE"
  │    │    Redis SET driver:vehicleType:{driverId} = "SEDAN"
  │    │    Redis HASH driver:profile:{driverId} = {vehicleType: "SEDAN"}
  │    │
  │    │  ❌ DOES NOT write to driver:locations (GEO)
  │    │
  │    ▼
  │  Returns DriverAvailabilityResponse (status, lat, lng)
  │
  ▼
  Driver is now "AVAILABLE" in Redis status
  but NOT in Redis GEO → matching-service CANNOT find them
```

### 5.2 Answer to Audit Questions

| # | Question | Answer |
|---|----------|--------|
| Q1 | Endpoint for ONLINE driver location? | [`PATCH /api/drivers/me/availability`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/controller/DriverProfileController.java:54) |
| Q2 | Which service? | driver-service |
| Q3 | Requires JWT DRIVER? | Yes — uses `currentUserFacade.getCurrentUserId()` from JWT |
| Q4 | driverId from body or JWT? | **JWT** — `currentUserFacade.getCurrentUserId()` |
| Q5 | Redis key for location? | **❌ NONE for GEO** — only `driver:status:{driverId}` = "AVAILABLE" is written |
| Q6 | matching-service queries which key? | [`driver:locations`](cab_booking/matching-service/src/main/java/com/cab/matching/service/MatchingService.java:40) via `opsForGeo().search()` |
| Q7 | How does matching filter ONLINE/AVAILABLE? | After GEO search, batch-gets `driver:status:{driverId}` and filters for `== "AVAILABLE"` ([line 251](cab_booking/matching-service/src/main/java/com/cab/matching/service/MatchingService.java:251)) |
| Q8 | ride.assigned removes from available pool? | Yes — [`DriverRideCommandService.handleRideAssigned()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverRideCommandService.java:101) sets `availabilityStatus = ON_TRIP`, writes `driver:status = "BUSY"` |
| Q9 | ride.completed/cancelled restores available? | Yes — [`cleanupRide()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverRideCommandService.java:233) sets `availabilityStatus = ONLINE`, writes `driver:status = "AVAILABLE"` |
| Q10 | Kafka events for availability location? | **None** — no Kafka event published when driver goes ONLINE with location |

### 5.3 🔴 BUG: Availability Location Never Reaches Redis GEO

**Impact:** Matching-service can NEVER find available drivers through normal flow. The only drivers appearing in `driver:locations` are those currently on a ride (from ride-service tracking).

**Root cause:** [`DriverProfileService.updateAvailability()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverProfileService.java:93) writes lat/lng to PostgreSQL but never calls Redis `GEOADD`.

---

## 6. Ride Tracking Flow (Current)

### 6.1 Flow Diagram (Socket.IO Primary)

```
Driver App (during active ride)
  │
  ├─ Connect: ws://ride-service:9095
  │    auth: { token: "<JWT>" }
  │    │
  │    ▼
  │  RideSocketEventHandler → RideSocketAuthService.authenticate()
  │    Extracts userId from JWT subject, roles from JWT claims
  │    Stores as client attributes
  │
  ├─ Event: "driver.location.update"
  │    payload: { rideId: "uuid", lat: 10.82, lng: 106.68, heading: 45, speed: 30 }
  │    │
  │    ▼
  │  RideSocketEventHandler.handleDriverLocationUpdate()    [line 111]
  │    │  1. Get driverId from JWT (client attribute)        ✅ Secure
  │    │  2. Validate DRIVER role                            ✅ Secure
  │    │  3. Validate rideId present                         ✅
  │    │
  │    ▼
  │  RideLocationService.updateLocation()                    [line 69]
  │    │  1. Validate coordinates (lat: -90..90, lng: -180..180)
  │    │  2. Find ride by ID → validate exists
  │    │  3. Validate ride.driverId == JWT subject            ✅ Ownership check
  │    │  4. Validate ride.status ∈ {ACCEPTED, PICKUP, IN_PROGRESS}
  │    │  5. Redis GEOADD driver:locations (lng, lat, driverId)  ← ride-service writes GEO
  │    │  6. Build DriverLocationEvent
  │    │  7. Publish Kafka: driver.location.updated            ← nobody consumes
  │    │  8. Return DriverLocationUpdatedResponse
  │    │
  │    ▼
  │  RideSocketEventHandler broadcasts to ride room
  │    server.getRoomOperations("ride:{rideId}")
  │      .sendEvent("driver.location.updated", response)
  │
  ▼
  Customer App (if joined ride room via "join_ride")
    Receives "driver.location.updated" event
```

### 6.2 Flow Diagram (REST Fallback)

```
Driver App
  │
  ├─ POST /api/v1/rides/location
  │    Header: Authorization: Bearer <JWT>
  │    body: { rideId: "uuid", lat: 10.82, lng: 106.68, heading: 45, speed: 30 }
  │    │
  │    ▼
  │  LocationController.updateLocation()                     [line 52]
  │    @PreAuthorize("hasRole('DRIVER')")
  │    driverId = jwt.getSubject()                            ✅ From JWT
  │    rideId required → 400 if missing                       ✅
  │    │
  │    ▼
  │  Same RideLocationService.updateLocation() flow as above
  │    ❌ Does NOT broadcast to Socket.IO room (REST has no server reference)
```

### 6.3 Answer to Audit Questions

| # | Question | Answer |
|---|----------|--------|
| Q1 | REST, Socket.IO or both? | **Both** — Socket.IO (primary) on port 9095, REST fallback at `POST /api/v1/rides/location` |
| Q2 | Endpoint/event specifics? | Socket: `driver.location.update` → [`RideSocketEventHandler`](cab_booking/ride-service/src/main/java/com/cab/ride/core/socket/RideSocketEventHandler.java:37); REST: [`POST /api/v1/rides/location`](cab_booking/ride-service/src/main/java/com/cab/ride/core/controller/LocationController.java:50) |
| Q3 | GPS requires rideId? | **Yes** — both REST and Socket.IO require `rideId` (REST returns 400 if missing) |
| Q4 | driverId from JWT or body? | **JWT** — REST: `jwt.getSubject()`; Socket: `authService.getUserId(client)` |
| Q5 | Validates ride.driverId == JWT? | **Yes** — [`RideLocationService:80`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:80) |
| Q6 | Allowed RideStatus? | `ACCEPTED`, `PICKUP`, `IN_PROGRESS` — [`RideLocationService:39`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:39) |
| Q7 | Redis key updated? | `driver:locations` via `GEOADD` — [`RideLocationService:156`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:156) |
| Q8 | Kafka topic published? | `driver.location.updated` — [`RideLocationService:33`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:33) |
| Q9 | Payload fields? | `eventId`, `eventType`, `rideId`, `bookingId`, `driverId`, `lat`, `lng`, `heading`, `speed`, `timestamp` |
| Q10 | Who consumes `driver.location.updated`? | **⚠️ NOBODY** — Published but never consumed |
| Q11 | Customer receives GPS how? | Via ride-service Socket.IO `driver.location.updated` event broadcast to `ride:{rideId}` room — BUT only if customer connects to ride-service socket (port 9095), which is **not documented in FE contract** |

---

## 7. Bugs & Security Holes

### 7.1 🔴 CRITICAL: Availability Location Never Written to Redis GEO

- **Location:** [`DriverProfileService.updateAvailability()`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverProfileService.java:93)
- **Impact:** Matching-service queries `driver:locations` (GEO) but drivers are never added when they go ONLINE
- **Workaround in production:** None — matching literally cannot find available drivers
- **Fix:** driver-service must `GEOADD driver:locations` when driver goes ONLINE with coordinates

### 7.2 🔴 CRITICAL: Same Redis GEO Key Used for Both Purposes

- **Key:** `driver:locations`
- **Written by:** ride-service [`RideLocationService:156`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:156) (during active ride)
- **Read by:** matching-service [`MatchingService:230`](cab_booking/matching-service/src/main/java/com/cab/matching/service/MatchingService.java:230) (for finding available drivers)
- **Conflict:** Ride tracking overwrites availability location. If we fix Bug #1, a driver on a ride would have their tracking location treated as availability location.

### 7.3 🟡 MEDIUM: `driver.location.updated` Kafka Event Published But Never Consumed

- **Producer:** ride-service [`RideLocationService:111`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:111)
- **Consumer:** None
- **Impact:** Wasted Kafka resources; FE_INTEGRATION_CONTRACT.md line 941 acknowledges this as a blocker

### 7.4 🟡 MEDIUM: notification-service Does Not Consume `driver.location.updated`

- **File:** [`RideEventConsumer.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/consumer/RideEventConsumer.java:21)
- **Topics listened:** `ride.created`, `ride.assigned`, `ride.accepted`, `ride.rejected`, `ride.completed`, `ride.arrived`, `ride.started`, `booking-events`, `booking.timeout`, `payment.completed`, `pricing.surge.updated`
- **Missing:** `driver.location.updated`
- **Impact:** Customer FE cannot receive real-time driver position via notification socket

### 7.5 🟡 MEDIUM: Two Separate Socket.IO Servers — FE Must Connect to Both

| Socket Server | Port | Purpose | Auth Method |
|--------------|------|---------|-------------|
| notification-service | 9093 | Lifecycle notifications, chat | `?userId={userId}` URL param ⚠️ |
| ride-service | 9095 | Ride GPS tracking | JWT token (auth object/query/header) |

- **FE contract** ([`FE_INTEGRATION_CONTRACT.md:786`](cab_booking/docs/FE_INTEGRATION_CONTRACT.md:786)) only documents `ws://localhost:9093`
- **ride-service socket** on port 9095 is **not documented** in the FE contract
- **Impact:** Customer FE likely doesn't connect to ride-service socket → no real-time GPS

### 7.6 🟡 MEDIUM: notification-service Auth is Weak

- **File:** [`SocketIOService.java:26`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/SocketIOService.java:26)
- **Auth:** `userId` extracted from URL query parameter `?userId={userId}` — **no JWT validation**
- **Impact:** Any client can impersonate any user by passing arbitrary userId
- **Compare:** ride-service uses proper JWT decoding via [`RideSocketAuthService`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/socket/RideSocketAuthService.java:52)

### 7.7 🟢 LOW: REST Location Update Missing Socket.IO Broadcast

- **File:** [`LocationController.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/controller/LocationController.java:50)
- **Issue:** REST fallback calls `rideLocationService.updateLocation()` which updates Redis + Kafka, but does NOT broadcast to Socket.IO room (no `SocketIOServer` reference in controller)
- **Impact:** If driver uses REST instead of Socket.IO, customer won't receive realtime update (only Kafka event, which nobody consumes)

### 7.8 🟢 LOW: `updateLocationSimple()` Deprecated But Still Exists

- **File:** [`RideLocationService:135`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:135)
- **Issue:** Deprecated method that bypasses ride validation — could be called accidentally

---

## 8. Conflicts Between 2 Location Types

### 8.1 Conflict Matrix

| # | Conflict | Severity | Details |
|---|----------|----------|---------|
| C1 | **Same Redis GEO key `driver:locations` for both availability and tracking** | 🔴 HIGH | ride-service writes during active ride; matching-service reads for available drivers. If both write, ON_TRIP drivers pollute the available pool. |
| C2 | **Availability location never written to Redis GEO** | 🔴 HIGH | driver-service saves lat/lng to PostgreSQL only. matching-service queries Redis GEO. Complete disconnect. |
| C3 | **No REMOVE from `driver:locations` when driver goes OFFLINE/ON_TRIP** | 🟡 MEDIUM | Even if we add availability GEOADD, there's no `ZREM`/`GEOREMOVE` when status changes. Stale entries persist. |
| C4 | **ride-service tracking overwrites availability coordinates** | 🟡 MEDIUM | During ride, `GEOADD` updates driver position in `driver:locations`. If matching runs during ride, it sees the driver (but status filter should block — see C5). |
| C5 | **Status filter is only defense against ON_TRIP matching** | 🟡 MEDIUM | matching-service checks `driver:status:{driverId} == "AVAILABLE"` after GEO search. This works but is a fragile single-layer defense. If status write fails, ON_TRIP driver gets matched. |
| C6 | **notification socket and ride socket are separate servers** | 🟡 MEDIUM | FE needs 2 socket connections. ride-service socket (9095) not in FE contract. |
| C7 | **Gateway does NOT proxy Socket.IO** | 🟢 LOW | `application.yaml` has no `/socket.io/**` route for either service. FE must connect directly to service ports. |

### 8.2 Detailed Conflict: C1 — Same Redis GEO Key

```
CURRENT STATE:
┌─────────────────────────────────────────────────────────────────┐
│                    Redis GEO: "driver:locations"                │
│                                                                 │
│  Written by:  ride-service (during ACTIVE RIDE only)            │
│  Read by:     matching-service (looking for AVAILABLE drivers)  │
│                                                                 │
│  ❌ Available drivers (ONLINE, no ride) are NEVER in this key   │
│  ❌ On-trip drivers ARE in this key (wrong pool)                │
│  ❌ Status filter is the ONLY protection                        │
└─────────────────────────────────────────────────────────────────┘

DESIRED STATE:
┌──────────────────────────────────────┐  ┌──────────────────────────────────┐
│  Redis GEO: "driver:available"       │  │  Redis GEO: "driver:tracking"    │
│                                      │  │                                  │
│  Written by: driver-service          │  │  Written by: ride-service        │
│  When: ONLINE + no ride              │  │  When: ACTIVE RIDE               │
│  Read by: matching-service           │  │  Read by: customer FE (optional) │
│  Remove when: OFFLINE or ON_TRIP     │  │  Remove when: ride ends          │
└──────────────────────────────────────┘  └──────────────────────────────────┘
```

---

## 9. Proposed Architecture

### 9.1 Two Separate Redis GEO Keys

| Key | Purpose | Writer | Reader | Lifecycle |
|-----|---------|--------|--------|-----------|
| `driver:available:locations` | Find nearby available drivers | driver-service | matching-service | ADD when ONLINE, REMOVE when OFFLINE/ON_TRIP |
| `driver:ride:locations:{rideId}` | Track driver during ride | ride-service | customer FE (via socket) | ADD when ride ACCEPTED, REMOVE when ride COMPLETED/CANCELLED |

### 9.2 Driver Availability Location (Target)

```
Driver App
  │
  ├─ PATCH /api/drivers/me/availability  { status: "ONLINE", lat, lng }
  │    │
  │    ▼
  │  driver-service
  │    ├─ Validate DRIVER JWT ✅
  │    ├─ Validate driver approved ✅
  │    ├─ Save to PostgreSQL (currentLatitude, currentLongitude)
  │    ├─ Redis SET driver:status:{driverId} = "AVAILABLE"
  │    ├─ ✨ NEW: Redis GEOADD driver:available:locations (lng, lat, driverId)
  │    └─ ✨ NEW: Kafka publish driver.availability.changed { driverId, lat, lng, status: "ONLINE" }
  │
  ├─ Periodic heartbeat (every 15-30s while ONLINE)
  │    PATCH /api/drivers/me/location  { lat, lng }     ← NEW ENDPOINT
  │    │
  │    ▼
  │  driver-service
  │    ├─ Validate DRIVER JWT
  │    ├─ Validate status == ONLINE && no currentRide
  │    ├─ Update PostgreSQL
  │    ├─ Redis GEOADD driver:available:locations (lng, lat, driverId)
  │    └─ No Kafka needed (high frequency)
  │
  └─ Status change → driver-service manages Redis GEO lifecycle:
       OFFLINE     → GEOREM driver:available:locations
       ON_TRIP     → GEOREM driver:available:locations
       BACK ONLINE → GEOADD driver:available:locations
```

### 9.3 Ride Tracking Location (Target)

```
Driver App (during active ride)
  │
  ├─ Socket.IO: ws://ride-service:9095
  │    auth: { token: "<JWT>" }
  │
  ├─ Event: "driver.location.update"
  │    payload: { rideId, lat, lng, heading, speed }
  │    │
  │    ▼
  │  ride-service
  │    ├─ Validate DRIVER JWT ✅
  │    ├─ Validate ride.driverId == JWT subject ✅
  │    ├─ Validate ride.status ∈ {ACCEPTED, PICKUP, IN_PROGRESS} ✅
  │    ├─ ✨ CHANGE: Redis GEOADD driver:ride:locations:{rideId} (NOT driver:locations)
  │    ├─ Publish Kafka driver.location.updated ✅ (already exists)
  │    └─ Broadcast to ride room "driver.location.updated" ✅ (already exists)
  │
  └─ Customer App connects to ride-service socket
       join_ride { rideId } → receives "driver.location.updated" events
```

### 9.4 Notification Service Enhancement (Optional)

```
notification-service
  │
  ├─ @KafkaListener(topics = "driver.location.updated")     ← NEW CONSUMER
  │    │
  │    ▼
  │  Extract rideId, driverId, lat, lng from event
  │    │
  │    ▼
  │  Broadcast to booking room via Socket.IO
  │    socketIOService.broadcastToBookingRoom(rideId, "driver.location.updated", payload)
  │
  └─ FE connects to notification socket (port 9093)
       join_room { bookingId } → receives "driver.location.updated"
```

---

## 10. Changes Required Per Service

### 10.1 driver-service — 🔴 HIGH Priority

| # | Change | File | Description |
|---|--------|------|-------------|
| D1 | Add Redis GEO dependency | `pom.xml` | Ensure `spring-boot-starter-data-redis` is present |
| D2 | Add `GEOADD` on availability update | [`DriverProfileService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverProfileService.java) | In `updateAvailability()`, after saving to DB, call `redisTemplate.opsForGeo().add("driver:available:locations", new Point(lng, lat), driverId)` |
| D3 | Add `GEOREM` on OFFLINE/ON_TRIP | [`DriverProfileService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverProfileService.java) | When status changes to OFFLINE or ON_TRIP, call `redisTemplate.opsForGeo().remove("driver:available:locations", driverId)` |
| D4 | Add `GEOADD` on ride cleanup | [`DriverRideCommandService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverRideCommandService.java) | In `cleanupRide()` and `rejectRide()`, when driver returns to ONLINE, re-add to GEO |
| D5 | Add `GEOREM` on ride assigned | [`DriverRideCommandService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverRideCommandService.java) | In `handleRideAssigned()`, remove from availability GEO |
| D6 | Add location heartbeat endpoint | [`DriverProfileController.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/controller/DriverProfileController.java) | New `PATCH /api/drivers/me/location` for periodic GPS updates while ONLINE |
| D7 | Write `driver:status` on heartbeat | [`DriverStatusService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverStatusService.java) | Refresh TTL on heartbeat to prevent stale entries |

### 10.2 matching-service — 🟡 MEDIUM Priority

| # | Change | File | Description |
|---|--------|------|-------------|
| M1 | Change GEO key to `driver:available:locations` | [`MatchingService.java:40`](cab_booking/matching-service/src/main/java/com/cab/matching/service/MatchingService.java:40) | Update `DRIVER_LOCATION_KEY` constant |
| M2 | (Optional) Remove status check redundancy | [`MatchingService.java:251`](cab_booking/matching-service/src/main/java/com/cab/matching/service/MatchingService.java:251) | With separate GEO key, status check becomes defense-in-depth rather than primary filter |

### 10.3 ride-service — 🟡 MEDIUM Priority

| # | Change | File | Description |
|---|--------|------|-------------|
| R1 | Change GEO key to `driver:ride:locations:{rideId}` | [`RideLocationService.java:32`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java:32) | Use per-ride key instead of shared `driver:locations` |
| R2 | Add `GEOREM` on ride completion | [`RideService.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideService.java) | Clean up ride tracking GEO when ride ends |
| R3 | Inject `SocketIOServer` into `LocationController` | [`LocationController.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/controller/LocationController.java) | REST fallback should also broadcast to ride room |

### 10.4 notification-service — 🟡 MEDIUM Priority

| # | Change | File | Description |
|---|--------|------|-------------|
| N1 | Add `driver.location.updated` Kafka consumer | [`RideEventConsumer.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/consumer/RideEventConsumer.java) | Consume location events and broadcast to booking room |
| N2 | Replace URL param auth with JWT | [`SocketIOService.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/SocketIOService.java) | Security fix — use JWT like ride-service |

### 10.5 api-gateway — 🟢 LOW Priority

| # | Change | File | Description |
|---|--------|------|-------------|
| G1 | Add Socket.IO proxy route | [`application.yaml`](cab_booking/api-gateway/src/main/resources/application.yaml) | Add WebSocket route for `/socket.io/**` → notification-service |

### 10.6 FE Contract Update

| # | Change | File | Description |
|---|--------|------|-------------|
| F1 | Document ride-service socket | [`FE_INTEGRATION_CONTRACT.md`](cab_booking/docs/FE_INTEGRATION_CONTRACT.md) | Add ride-service Socket.IO connection details (port 9095, JWT auth, events) |
| F2 | Add location heartbeat instructions | [`FE_INTEGRATION_CONTRACT.md`](cab_booking/docs/FE_INTEGRATION_CONTRACT.md) | Document `PATCH /api/drivers/me/location` for driver app |
| F3 | Update `driver.location.updated` section | [`FE_INTEGRATION_CONTRACT.md`](cab_booking/docs/FE_INTEGRATION_CONTRACT.md) | Document the new flow where notification-service forwards GPS to booking rooms |

---

## 11. Risk Assessment

### Overall Risk Level: 🔴 HIGH

| Risk | Level | Impact | Likelihood |
|------|-------|--------|------------|
| Matching cannot find available drivers (Bug #1) | 🔴 CRITICAL | System non-functional for core use case | Currently happening |
| Same GEO key for both location types (Conflict C1) | 🔴 HIGH | ON_TRIP drivers matched if status write fails | Medium |
| `driver.location.updated` not consumed (Bug #3) | 🟡 MEDIUM | Customer can't see driver on map | Currently happening |
| FE doesn't know about ride-service socket (Bug #5) | 🟡 MEDIUM | No realtime GPS for customer | Currently happening |
| notification-service no JWT auth (Bug #6) | 🟡 MEDIUM | User impersonation possible | Low (internal network) |
| Two socket servers for FE (Conflict C6) | 🟡 MEDIUM | Increased FE complexity | Design issue |
| Stale GEO entries (Conflict C3) | 🟢 LOW | Matching returns offline drivers | Low (status filter catches) |

### Implementation Priority

```
Phase 1 (CRITICAL — fix broken matching):
  D2, D3, D4, D5, D7  →  driver-service writes availability to Redis GEO
  M1                    →  matching-service reads from correct key

Phase 2 (HIGH — enable customer GPS tracking):
  R1, R2               →  ride-service uses separate tracking GEO key
  N1                    →  notification-service consumes driver.location.updated
  F1                    →  FE contract documents ride-service socket

Phase 3 (MEDIUM — harden & optimize):
  D6                    →  driver location heartbeat endpoint
  N2                    →  notification-service JWT auth
  R3                    →  REST fallback broadcasts to socket
  G1                    →  gateway Socket.IO proxy
  F2, F3                →  FE contract updates
```

---

## Appendix: File Reference Index

### driver-service
- [`DriverProfileController.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/controller/DriverProfileController.java) — REST endpoints
- [`DriverProfileService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverProfileService.java) — Business logic
- [`DriverStatusService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverStatusService.java) — Redis status writes
- [`DriverRideCommandService.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/service/DriverRideCommandService.java) — Ride lifecycle handling
- [`DriverRideEventConsumer.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/consumer/DriverRideEventConsumer.java) — Kafka consumer
- [`DriverAvailabilityStatus.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/entity/DriverAvailabilityStatus.java) — Enum: OFFLINE, ONLINE, ON_TRIP
- [`DriverProfile.java`](cab_booking/driver-service/src/main/java/iuh/fit/driverservice/entity/DriverProfile.java) — Entity with currentLatitude/Longitude

### matching-service
- [`MatchingService.java`](cab_booking/matching-service/src/main/java/com/cab/matching/service/MatchingService.java) — Core matching logic
- [`RideCreatedListener.java`](cab_booking/matching-service/src/main/java/com/cab/matching/service/consumer/RideCreatedListener.java) — Kafka consumer
- [`RedisConfig.java`](cab_booking/matching-service/src/main/java/com/cab/matching/config/RedisConfig.java) — Redis config

### ride-service
- [`LocationController.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/controller/LocationController.java) — REST location endpoint
- [`RideLocationService.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/RideLocationService.java) — Shared location logic
- [`RideSocketEventHandler.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/socket/RideSocketEventHandler.java) — Socket.IO event handlers
- [`RideSocketAuthService.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/socket/RideSocketAuthService.java) — JWT auth for socket
- [`RideSocketRoomService.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/service/socket/RideSocketRoomService.java) — Room management
- [`RideSocketServerLifecycle.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/socket/RideSocketServerLifecycle.java) — Server start/stop
- [`DriverLocationEvent.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/dto/event/DriverLocationEvent.java) — Kafka event DTO
- [`RideLocationSocketRequest.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/dto/socket/request/RideLocationSocketRequest.java) — Socket request DTO
- [`DriverLocationUpdatedResponse.java`](cab_booking/ride-service/src/main/java/com/cab/ride/core/dto/socket/response/DriverLocationUpdatedResponse.java) — Socket response DTO

### notification-service
- [`SocketIOConfig.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/config/SocketIOConfig.java) — Socket.IO server config (port 9093)
- [`SocketIOService.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/SocketIOService.java) — Socket.IO service
- [`RideEventConsumer.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/consumer/RideEventConsumer.java) — Kafka consumer
- [`NotificationService.java`](cab_booking/notification-service/src/main/java/iuh/fit/notification_service/service/NotificationService.java) — Notification logic

### booking-service
- [`BookingLifecycleEventListener.java`](cab_booking/booking-service/src/main/java/com/cab/booking/core/listener/BookingLifecycleEventListener.java) — Kafka consumer for lifecycle events

### api-gateway
- [`application.yaml`](cab_booking/api-gateway/src/main/resources/application.yaml) — Route configuration

### docs
- [`FE_INTEGRATION_CONTRACT.md`](cab_booking/docs/FE_INTEGRATION_CONTRACT.md) — FE integration contract
