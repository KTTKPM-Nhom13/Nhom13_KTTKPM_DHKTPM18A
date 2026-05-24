#!/usr/bin/env node
/**
 * Socket.IO v4 realtime tracking smoke test for ride-service.
 *
 * Usage:
 *   node scripts/socket-test.js [JWT_TOKEN] [RIDE_ID] [ASSIGNED_RIDE_ID]
 *
 * Requires:
 *   npm install socket.io-client@4
 *
 * Tests:
 *   1. Connect with JWT (Socket.IO v4 auth object)
 *   2. join_ride
 *   3. driver.location.update (ACCEPTED ride)
 *   4. ASSIGNED ride → INVALID_STATUS rejection
 *   5. leave_ride
 *   6. Verify all server responses
 *   7. Verify timestamp is ISO-8601 string
 */

const { io } = require("socket.io-client");

// ── Config ──────────────────────────────────────────────────────────────────
const SOCKET_URL = process.env.SOCKET_URL || "http://localhost:9095";
const TOKEN = process.argv[2] || process.env.DRIVER_JWT || "<paste-driver-jwt-here>";
const RIDE_ID = process.argv[3] || process.env.RIDE_ID || "<paste-ride-id-here>";
const ASSIGNED_RIDE_ID = process.argv[4] || process.env.ASSIGNED_RIDE_ID || null;

// ── Test state ──────────────────────────────────────────────────────────────
let passed = 0;
let failed = 0;

function assert(condition, testName) {
  if (condition) {
    console.log(`  ✅ PASS: ${testName}`);
    passed++;
  } else {
    console.log(`  ❌ FAIL: ${testName}`);
    failed++;
  }
}

function summary() {
  console.log("\n════════════════════════════════════════");
  console.log(`  Results: ${passed} passed, ${failed} failed`);
  console.log("════════════════════════════════════════\n");
  process.exit(failed > 0 ? 1 : 0);
}

// ── Main ────────────────────────────────────────────────────────────────────
console.log("╔══════════════════════════════════════════════════╗");
console.log("║  CAB Ride Service — Socket.IO v4 Smoke Test      ║");
console.log("╚══════════════════════════════════════════════════╝\n");
console.log(`  Socket URL       : ${SOCKET_URL}`);
console.log(`  Protocol         : Socket.IO v4 / Engine.IO v4 / EIO=4`);
console.log(`  Ride ID          : ${RIDE_ID}`);
console.log(`  Assigned Ride ID : ${ASSIGNED_RIDE_ID || "(not provided — ASSIGNED test will be skipped)"}`);
console.log(`  Token            : ${TOKEN.substring(0, 20)}...\n`);

// Socket.IO v4 connection — auth via handshake auth object (v4 style)
// Also sends token as query param for backward compatibility with older servers
const socket = io(SOCKET_URL, {
  auth: { token: TOKEN },
  query: { token: TOKEN },
  transports: ["websocket"],
  reconnection: false,
});

let locationUpdateReceived = false;
let assignedTestDone = !ASSIGNED_RIDE_ID; // skip if no assigned ride ID

// ── Test 1: Connect ─────────────────────────────────────────────────────────
socket.on("connect", () => {
  console.log("\n[TEST 1] Socket.IO v4 Connect");
  assert(socket.connected, "Socket connected successfully (EIO=4)");
  assert(socket.id != null, "Socket ID assigned: " + socket.id);

  // ── Test 2: join_ride ───────────────────────────────────────────────────
  console.log("\n[TEST 2] join_ride");
  socket.emit("join_ride", { rideId: RIDE_ID });
});

socket.on("joined_ride", (data) => {
  console.log(`  Server response: ${JSON.stringify(data)}`);
  assert(data.rideId === RIDE_ID, "rideId matches");
  assert(data.status === "JOINED", "status is JOINED");

  // ── Test 3: driver.location.update ──────────────────────────────────────
  console.log("\n[TEST 3] driver.location.update (ACCEPTED ride)");
  socket.emit("driver.location.update", {
    rideId: RIDE_ID,
    lat: 10.7769,
    lng: 106.7009,
    heading: 120,
    speed: 32,
  });
});

// ── Test 3 response: driver.location.updated ─────────────────────────────────
socket.on("driver.location.updated", (data) => {
  console.log(`  Server response: ${JSON.stringify(data)}`);
  assert(data.eventType === "DRIVER_LOCATION_UPDATED", "eventType is DRIVER_LOCATION_UPDATED");
  assert(data.rideId === RIDE_ID, "rideId matches");
  assert(data.lat === 10.7769, "lat is 10.7769");
  assert(data.lng === 106.7009, "lng is 106.7009");
  assert(data.driverId != null, "driverId is present");

  // Verify timestamp is ISO-8601 string (not a Unix epoch number)
  assert(typeof data.timestamp === "string", "timestamp is a string (not number)");
  assert(
    typeof data.timestamp === "string" && /^\d{4}-\d{2}-\d{2}T/.test(data.timestamp),
    "timestamp is ISO-8601 format (starts with YYYY-MM-DDTHH)"
  );

  locationUpdateReceived = true;

  // ── Test 4: ASSIGNED ride → INVALID_STATUS ─────────────────────────────
  if (ASSIGNED_RIDE_ID) {
    console.log("\n[TEST 4] ASSIGNED ride → INVALID_STATUS rejection");
    socket.emit("join_ride", { rideId: ASSIGNED_RIDE_ID });
    // After joining, emit location — the socket_error handler will verify
  } else {
    console.log("\n[TEST 4] ASSIGNED ride test SKIPPED (no ASSIGNED_RIDE_ID provided)");
    assignedTestDone = true;
    // ── Test 5: leave_ride ─────────────────────────────────────────────────
    console.log("\n[TEST 5] leave_ride");
    socket.emit("leave_ride", { rideId: RIDE_ID });
  }
});

// Handle joined_ride for ASSIGNED ride (second join)
let assignedJoined = false;
const originalJoinedRide = socket.listeners("joined_ride")[0];
socket.off("joined_ride");
socket.on("joined_ride", (data) => {
  if (!assignedJoined && ASSIGNED_RIDE_ID && data.rideId === ASSIGNED_RIDE_ID) {
    // This is the join for the ASSIGNED ride
    assignedJoined = true;
    console.log(`  Joined ASSIGNED ride room: ${JSON.stringify(data)}`);
    socket.emit("driver.location.update", {
      rideId: ASSIGNED_RIDE_ID,
      lat: 10.7769,
      lng: 106.7009,
    });
  } else {
    // Original handler for ACCEPTED ride
    originalJoinedRide(data);
  }
});

// ── Error handling ──────────────────────────────────────────────────────────
socket.on("socket_error", (data) => {
  console.log(`  ⚠️  socket_error: ${JSON.stringify(data)}`);

  // Check if this is the ASSIGNED INVALID_STATUS error
  if (data.code === "INVALID_STATUS" && ASSIGNED_RIDE_ID) {
    assert(data.code === "INVALID_STATUS", "ASSIGNED ride rejected with INVALID_STATUS");
    assignedTestDone = true;

    // ── Test 5: leave_ride ─────────────────────────────────────────────────
    console.log("\n[TEST 5] leave_ride");
    socket.emit("leave_ride", { rideId: RIDE_ID });
    return;
  }

  if (data.code === "UNAUTHORIZED") {
    assert(false, "Authentication failed — check your JWT token");
    socket.disconnect();
    summary();
  }
});

socket.on("left_ride", (data) => {
  console.log(`  Server response: ${JSON.stringify(data)}`);
  assert(data.rideId === RIDE_ID, "rideId matches");
  assert(data.status === "LEFT", "status is LEFT");

  // Done — disconnect and print summary
  socket.disconnect();
  summary();
});

socket.on("connect_error", (err) => {
  console.error(`\n  ❌ Connection error: ${err.message}`);
  assert(false, "Socket connection failed — verify server supports EIO=4");
  summary();
});

socket.on("disconnect", (reason) => {
  console.log(`\n  Disconnected: ${reason}`);
});

// ── Timeout safety ──────────────────────────────────────────────────────────
setTimeout(() => {
  if (!locationUpdateReceived) {
    console.log("\n  ⏰ Timeout: did not receive driver.location.updated within 15s");
    assert(false, "Location update response timeout");
    if (socket.connected) socket.disconnect();
    summary();
  }
}, 15000);
