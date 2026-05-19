# Pricing Service Design

## Scope

Pricing Service is responsible for deterministic fare estimation, quote locking, and rule-based surge pricing. It does not use AI pricing or model serving. Distance and duration are retrieved from Mapbox, while weather context is retrieved from OpenMeteo.

## Runtime Inputs

- Pickup and dropoff coordinates.
- Vehicle type.
- Route distance and duration from Mapbox.
- Weather context from OpenMeteo.
- Demand/supply counters from Redis, updated by Kafka events.
- Pricing configuration from application config.

## Fare Formula

```text
subtotal =
  baseFare
  + distanceFare
  + timeFare
  + platformFee
  + zoneFee
  + airportFee
  + tollFee

grossFare = subtotal * surgeMultiplier
totalFare = max(minimumFare, grossFare - discountAmount)
```

```text
distanceFare = distanceKm * perKmRate
timeFare = durationMinutes * perMinuteRate
```

Every fare estimate stores the pricing config version, source of distance data, weather source, and whether a fallback was used.

## Rule-Based Surge Engine

Surge pricing is computed by `RuleBasedSurgeCalculator`.

```text
surgeMultiplier = clamp(
  1.0
  + demandAdjustment
  + timeAdjustment
  + weatherAdjustment
  + manualAdjustment
)
```

Rules:

- Demand pressure uses `pendingRides / max(activeDrivers, 1)`.
- Rush hours add a configured adjustment.
- Night hours add a configured adjustment.
- Bad weather from OpenMeteo adds a configured adjustment.
- Final multiplier is clamped between configured min/max values.

## Quote Locking

The estimate flow creates a `FareEstimate` in `PENDING` status with an `expiresAt` timestamp. Booking confirmation calls `POST /api/pricing/confirm/{estimateId}`. Confirmation is atomic using MongoDB `findAndModify` with the condition:

```text
id = estimateId
status = PENDING
expiresAt > now
```

This prevents double confirmation of a single quote.

Estimate creation supports an optional `Idempotency-Key` header. If a client retries the same estimate request with the same key while the quote is still valid, Pricing Service returns the original estimate instead of creating duplicate pending quotes.

## External API Resilience

Mapbox and OpenMeteo calls are wrapped with Resilience4j circuit breakers, retry, and rate limiter policies.

- Mapbox fallback: Haversine distance multiplied by a road factor and estimated urban speed.
- OpenMeteo fallback: weather surcharge disabled.
- Route cache TTL is short because route depends on specific coordinates.
- Weather cache TTL is longer because weather is zone-level context.
- Retry is intentionally small to avoid adding too much user-facing latency to fare estimation.
- RateLimiter protects external API quotas and triggers the same graceful fallback path when exhausted.

## Event-Driven Demand/Supply

Pricing Service consumes:

- `demand.supply.updated`
- `ride.created`
- `ride.cancelled`
- `ride.completed`
- `driver.status.changed`
- `driver.status.updated` for forward compatibility if the driver event topic is renamed later
- `driver.location.updated`

On `ride.created`, Pricing Service stores `rideId -> pickupZone` in Redis and increments pending rides for that zone. Later ride terminal events can decrement pending rides by using the event `zoneId` or the stored ride-zone mapping.

On `driver.status.changed`, Pricing Service stores the driver's active zone. On `driver.location.updated`, if an active driver moves across zones, the old zone active-driver count is decremented and the new zone active-driver count is incremented.

`demand.supply.updated` is kept as an internal/test/admin topic and API equivalent. The normal production path does not require another service to publish it because Pricing Service can derive demand/supply from ride and driver lifecycle events.

## Pricing Events

Pricing Service publishes:

- `pricing.estimate.created`
- `pricing.estimate.confirmed`
- `pricing.estimate.expired`
- `pricing.surge.updated`
- `pricing.config.updated`

## Observability

The service exposes Actuator and Prometheus metrics and includes Micrometer tracing dependencies for trace propagation through Gateway, Pricing Service, and downstream calls.

Custom metrics include estimate request count, external API latency, fallback count, surge-applied count, and quote confirmation failure count by reason.

## Security

Pricing Service applies JWT validation at the application layer. Public access is limited to actuator and API documentation endpoints. Estimate and confirm APIs require authentication. Internal/admin operations such as manual surge updates and direct demand/supply updates require elevated pricing/admin authorities.

Service-to-service mTLS remains an infrastructure concern and should be enforced through the service mesh in production.
