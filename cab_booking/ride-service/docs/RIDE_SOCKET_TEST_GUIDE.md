# Ride Service — Socket.IO v4 Realtime Tracking Test Guide

## Overview

This guide explains how to test the Socket.IO realtime GPS tracking feature in `ride-service`.

| Property          | Value                              |
|-------------------|------------------------------------|
| Socket.IO Port    | `9095`                             |
| HTTP Port         | `8085`                             |
| Protocol          | **Socket.IO v4 / Engine.IO v4**    |
| EIO Version       | `EIO=4`                            |
| Transport         | `websocket`                        |
| Auth (v4 style)   | `{ auth: { token: "<JWT>" } }`    |
| Auth (fallback)   | `?token=<JWT>` query param         |
| Library           | `com.corundumstudio.socketio:netty-socketio:2.0.14` |

---

## Connection URLs

### Mobile app / Node.js connection (recommended)

Mobile apps and Node.js clients use the **`socket.io-client@4`** library, which handles the handshake automatically. Provide only the base URL and use the `auth` object:

```js
import { io } from "socket.io-client";

const socket = io("http://localhost:9095", {
  auth: {
    token: jwt
  },
  transports: ["websocket"]
});
```

> **Do NOT** use `ws://localhost:9095/socket.io/?EIO=4&transport=websocket` with `socket.io-client` — the library appends these parameters itself.

### Postman testing URL

Postman's raw WebSocket client does not perform the Socket.IO handshake automatically, so you must include `EIO=4&transport=websocket` manually:

```
ws://localhost:9095/socket.io/?EIO=4&transport=websocket&token=<JWT>
```

### Backward-compatible query parameter

The server also accepts the token as a query parameter for clients that don't support the `auth` object:

```js
const socket = io("http://localhost:9095", {
  query: {
    token: jwt
  },
  transports: ["websocket"]
});
```

> **Note:** `auth.token` takes priority over `query.token` if both are provided.

---

## Prerequisites

1. **ride-service** is running (HTTP on `:8085`, Socket.IO on `:9095`)
2. **PostgreSQL** and **Redis** are running
3. **Kafka** is running (for event publishing)
4. A valid **DRIVER JWT** token (from auth-service)
5. A valid **ride ID** with status `ACCEPTED`, `PICKUP`, or `IN_PROGRESS`

> **Note:** `ASSIGNED` status is **not** allowed for GPS streaming. If the driver has not yet accepted the ride, location updates will be rejected with `INVALID_STATUS`.

---

## Quick Test with Node.js Script

### 1. Install dependency

```bash
cd ride-service
npm install socket.io-client@4
```

### 2. Run the smoke test

```bash
node scripts/socket-test.js <DRIVER_JWT> <RIDE_ID>
```

Example:

```bash
node scripts/socket-test.js "eyJhbGciOiJSUzI1NiJ9..." "550e8400-e29b-41d4-a716-446655440000"
```

### Expected output

```
╔══════════════════════════════════════════════════╗
║  CAB Ride Service — Socket.IO v4 Smoke Test      ║
╚══════════════════════════════════════════════════╝

  Socket URL       : http://localhost:9095
  Protocol         : Socket.IO v4 / Engine.IO v4 / EIO=4
  Ride ID          : 550e8400-e29b-41d4-a716-446655440000
  Token            : eyJhbGciOiJSUzI1NiJ9...

[TEST 1] Socket.IO v4 Connect
  ✅ PASS: Socket connected successfully (EIO=4)
  ✅ PASS: Socket ID assigned: abc123

[TEST 2] join_ride
  Server response: {"rideId":"550e8400...","status":"JOINED"}
  ✅ PASS: rideId matches
  ✅ PASS: status is JOINED

[TEST 3] driver.location.update (ACCEPTED ride)
  Server response: {"eventType":"DRIVER_LOCATION_UPDATED","rideId":"550e8400...",...}
  ✅ PASS: eventType is DRIVER_LOCATION_UPDATED
  ✅ PASS: rideId matches
  ✅ PASS: lat is 10.7769
  ✅ PASS: lng is 106.7009
  ✅ PASS: driverId is present
  ✅ PASS: timestamp is ISO-8601 string

[TEST 4] driver.location.update with ASSIGNED ride
  ⚠️  socket_error: {"code":"INVALID_STATUS","message":"Cannot update location for ride in status: ASSIGNED"}
  ✅ PASS: ASSIGNED rejected with INVALID_STATUS

[TEST 5] leave_ride
  Server response: {"rideId":"550e8400...","status":"LEFT"}
  ✅ PASS: rideId matches
  ✅ PASS: status is LEFT

═══════════════════════════════════════
  Results: 13 passed, 0 failed
═══════════════════════════════════════
```

---

## Test with Postman WebSocket

### Step 1: Connect

1. Open Postman → **New** → **WebSocket Request**
2. URL: `ws://localhost:9095/socket.io/?EIO=4&transport=websocket&token=<YOUR_JWT>`
3. Click **Connect**
4. Expected: Connection established, receive `0{"sid":"..."}` open packet

### Step 2: join_ride

Send this frame:

```
42["join_ride",{"rideId":"<YOUR_RIDE_ID>"}]
```

Expected response:

```
42["joined_ride",{"rideId":"<YOUR_RIDE_ID>","status":"JOINED"}]
```

### Step 3: driver.location.update

Send this frame:

```
42["driver.location.update",{"rideId":"<YOUR_RIDE_ID>","lat":10.7769,"lng":106.7009,"heading":120,"speed":32}]
```

Expected response:

```
42["driver.location.updated",{"eventId":"...","eventType":"DRIVER_LOCATION_UPDATED","rideId":"...","bookingId":"...","driverId":"...","lat":10.7769,"lng":106.7009,"heading":120,"speed":32,"timestamp":"2026-05-23T19:15:00Z"}]
```

> **Note:** `timestamp` is an ISO-8601 UTC string (e.g. `2026-05-23T19:15:00Z`), not a Unix epoch number.

### Step 4: ASSIGNED status rejection test

If you have a ride in `ASSIGNED` status, send a location update for it:

```
42["driver.location.update",{"rideId":"<ASSIGNED_RIDE_ID>","lat":10.7769,"lng":106.7009}]
```

Expected error:

```
42["socket_error",{"code":"INVALID_STATUS","message":"Cannot update location for ride in status: ASSIGNED"}]
```

### Step 5: leave_ride

Send this frame:

```
42["leave_ride",{"rideId":"<YOUR_RIDE_ID>"}]
```

Expected response:

```
42["left_ride",{"rideId":"<YOUR_RIDE_ID>","status":"LEFT"}]
```

### Step 6: Error test

Connect with a customer JWT (not a driver), then send `driver.location.update`:

Expected:

```
42["socket_error",{"code":"FORBIDDEN","message":"Only drivers can send location updates"}]
```

---

## Test with REST Fallback

```bash
curl -X POST http://localhost:8085/api/v1/rides/location \
  -H "Authorization: Bearer <DRIVER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"rideId":"<RIDE_ID>","lat":10.7769,"lng":106.7009,"heading":120,"speed":32}'
```

Expected: `200 OK`

### REST without rideId (deprecated — returns 400)

```bash
curl -X POST http://localhost:8085/api/v1/rides/location \
  -H "Authorization: Bearer <DRIVER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"lat":10.7769,"lng":106.7009}'
```

Expected: `400 BAD_REQUEST` with message `"rideId is required. Legacy mode without rideId is deprecated."`

---

## Event Contract Reference

### Client → Server

| Event                      | Payload                                         | Description                    |
|----------------------------|--------------------------------------------------|--------------------------------|
| `join_ride`                | `{ "rideId": "uuid" }`                          | Join ride tracking room        |
| `leave_ride`               | `{ "rideId": "uuid" }`                          | Leave ride tracking room       |
| `driver.location.update`   | `{ "rideId", "lat", "lng", "heading?", "speed?" }` | Send GPS location (driver only) |

### Server → Client

| Event                        | Payload                                                              | Description                    |
|------------------------------|----------------------------------------------------------------------|--------------------------------|
| `joined_ride`                | `{ "rideId", "status": "JOINED" }`                                  | Room join confirmed            |
| `left_ride`                  | `{ "rideId", "status": "LEFT" }`                                    | Room leave confirmed           |
| `driver.location.updated`    | `{ "eventId", "eventType", "rideId", "bookingId", "driverId", "lat", "lng", "heading?", "speed?", "timestamp" }` | Broadcast to ride room. `timestamp` is ISO-8601 UTC string. |
| `socket_error`               | `{ "code", "message" }`                                              | Error response                 |

---

## Validation Rules

When `driver.location.update` is received:

1. Client must be authenticated (valid JWT)
2. Client must have `DRIVER` role
3. `rideId` must exist in database
4. Driver must be assigned to the ride (`ride.driverId == JWT subject`)
5. Ride status must be one of: `ACCEPTED`, `PICKUP`, `IN_PROGRESS`
   - `ASSIGNED` is **not** allowed — driver has not accepted yet
6. `lat` must be in range [-90, 90]
7. `lng` must be in range [-180, 180]

If any validation fails → `socket_error` event is emitted, no Redis/Kafka update.

---

## Troubleshooting

| Symptom                          | Cause                              | Fix                                      |
|----------------------------------|------------------------------------|------------------------------------------|
| Connection refused               | Server not running on port 9095    | Check `ride-socket.port` config          |
| `UNAUTHORIZED` on connect        | Invalid/expired JWT                | Get fresh token from auth-service        |
| `FORBIDDEN` on join_ride         | User not driver/customer of ride   | Use correct ride owner's JWT             |
| `FORBIDDEN` on location update   | Driver not assigned to this ride   | Use the assigned driver's JWT            |
| `INVALID_STATUS` on location     | Ride not in valid status           | Check ride status is ACCEPTED/PICKUP/IN_PROGRESS (ASSIGNED is rejected) |
| `INVALID_STATUS` on ASSIGNED     | ASSIGNED status not allowed        | Wait for driver to accept (→ ACCEPTED)   |
| No `driver.location.updated`     | Not in ride room                   | Send `join_ride` first                   |
| REST 400 without rideId          | rideId is required                 | Include rideId in request body           |
| 400 Bad Request on connect       | EIO=3 client with EIO=4 server     | Use `socket.io-client@4` (EIO=4)        |

---

## Auth Token Priority

The server extracts the JWT token in this order:

1. **`auth.token`** — Socket.IO v4 handshake auth object (recommended)
2. **`?token=<JWT>`** — query parameter (backward compatible)
3. **`Authorization: Bearer <JWT>`** — HTTP header fallback

### Socket.IO v4 auth (recommended)

```js
const socket = io("http://localhost:9095", {
  auth: { token: jwt },
  transports: ["websocket"]
});
```

### Query parameter (backward compatible)

```js
const socket = io("http://localhost:9095", {
  query: { token: jwt },
  transports: ["websocket"]
});
```
