# Pricing Service - ETA Testing Guide

## Overview

This folder contains Postman test cases for the Pricing Service ETA functionality. The tests verify that the ETA calculation service returns valid values based on distance and traffic conditions.

## Files

- `Pricing_Service_ETA_Test.postman_collection.json` - Test cases collection
- `Pricing_Service_ETA_Test.postman_environment.json` - Environment configuration

## Prerequisites

1. **Pricing Service must be running** on port `8088` (docker exposed port)
2. **Redis must be available** for ETA caching (configured in docker-compose)
3. **MongoDB must be running** for fare persistence
4. **Optional**: Google Maps API key configured for accurate ETA (falls back to Haversine formula if not configured)

## Test Cases

### 1. ETA Service - 5km Distance (Normal Traffic)

**Purpose**: Verify ETA calculation for approximately 5km distance

**Expected Results**:
- HTTP 200 response
- `durationMinutes` > 0
- `durationMinutes` < 60 minutes (reasonable for 5km)
- `distanceKm` > 0

### 2. ETA Service - Short Distance (1km)

**Purpose**: Verify ETA calculation for short distance

**Expected Results**:
- HTTP 200 response
- `durationMinutes` > 0
- `durationMinutes` < 15 minutes (reasonable for 1km)

### 3. ETA Service - Long Distance (20km)

**Purpose**: Verify ETA calculation for long distance

**Expected Results**:
- HTTP 200 response
- `durationMinutes` > 0
- `durationMinutes` < 120 minutes (reasonable for 20km)

### 4. ETA Service - Premium Vehicle Type

**Purpose**: Verify ETA with different vehicle types

**Expected Results**:
- HTTP 200 response
- `durationMinutes` > 0
- `vehicleType` = "PREMIUM"
- `totalFare` > 0

## API Endpoint

```
GET /api/pricing/estimate
```

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| pickupLat | double | Yes | Pickup latitude (-90 to 90) |
| pickupLng | double | Yes | Pickup longitude (-180 to 180) |
| dropoffLat | double | Yes | Dropoff latitude (-90 to 90) |
| dropoffLng | double | Yes | Dropoff longitude (-180 to 180) |
| vehicleType | string | Yes | Vehicle type: ECONOMY, COMFORT, PREMIUM |
| estimatedDurationMinutes | int | No | Pre-estimated duration (optional) |

### Example Request

```bash
curl "http://localhost:8088/api/pricing/estimate?pickupLat=10.7629&pickupLng=106.6602&dropoffLat=10.8231&dropoffLng=106.6873&vehicleType=ECONOMY"
```

### Example Response

```json
{
  "estimateId": "EST-1715678901234-ABC123",
  "pickupZone": "District 3",
  "dropoffZone": "Binh Thanh",
  "vehicleType": "ECONOMY",
  "distanceKm": 5.23,
  "durationMinutes": 12,
  "baseFare": 2.50,
  "distanceFare": 7.85,
  "timeFare": 3.00,
  "surgeMultiplier": 1.0,
  "totalFare": 13.35,
  "currency": "VND",
  "expiresAt": "2026-05-14T10:00:00",
  "message": "Fare estimate generated successfully"
}
```

## Validation Rules

The test cases validate:

1. **HTTP Status**: Response must be 200 OK
2. **ETA Value**: `durationMinutes` must be > 0
3. **Reasonable ETA**: ETA should be proportional to distance
   - 5km → < 60 minutes
   - 1km → < 15 minutes
   - 20km → < 120 minutes
4. **Valid Structure**: Response must contain `distanceKm`, `durationMinutes`, `vehicleType`

## ETA Calculation

The ETA is calculated using:

1. **Primary**: Google Maps Distance Matrix API (if API key is configured)
2. **Fallback**: Haversine formula with default speed of 30 km/h (if no Google Maps key)

### Formula (Fallback)

```
ETA (minutes) = (distance_km / 30) * 60
```

For 5km distance:
```
ETA = (5 / 30) * 60 = 10 minutes
```

## Running Tests

1. Import the collection file `Pricing_Service_ETA_Test.postman_collection.json` into Postman
2. Import the environment file `Pricing_Service_ETA_Test.postman_environment.json`
3. Select the "Pricing Service Environment"
4. Click "Run Collection" or run individual test cases

## Troubleshooting

### Service Not Starting

Check docker-compose logs:
```bash
docker-compose logs pricing-service
```

### Redis Connection Error

ETA caching requires Redis. Verify Redis is running:
```bash
docker-compose ps redis
```

### Invalid Coordinates Error

Ensure coordinates are within valid ranges:
- Latitude: -90 to 90
- Longitude: -180 to 180
