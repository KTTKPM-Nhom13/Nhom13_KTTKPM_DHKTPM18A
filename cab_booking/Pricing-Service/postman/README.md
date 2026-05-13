# Pricing Service — Postman Test Collection

## Setup

1. **Import files into Postman**
   - `Pricing_Service_API.postman_collection.json`
   - `Pricing_Service_API.postman_environment.json` (optional)

2. **Prerequisites**
   - Pricing Service must be running at `http://localhost:8087`
   - MongoDB and Redis must be available (configured in `application.yaml`)

## Usage Order

| # | Request | Purpose |
|---|---|---|
| 1 | `1. Fare Estimate` | Get a fare estimate — populates `{{estimateId}}` variable |
| 1b | `1b. Fare Estimate - COMFORT` | Test COMFORT vehicle type |
| 1c | `1c. Fare Estimate - PREMIUM` | Test PREMIUM vehicle type |
| 1d | `1d. Fare Estimate - Validation Error` | Test 400 on missing params |
| 2 | `2. Confirm Fare Estimate` | Confirm estimate using `{{estimateId}}` |
| 2b | `2b. Confirm - Not Found` | Test 400/404 on invalid ID |
| 3 | `3. Get Surge Multiplier` | Get current surge for a zone |
| 4 | `4. Update Surge Multiplier` | Set surge to 1.5x |
| 4b | `4b. Update Surge - 2.5x` | Set surge to 2.5x |
| 4c | `4c. Update Surge - Validation Error` | Test 400 when multiplier < 1.0 |
| 4d | `4d. Update Surge - Validation Error` | Test 400 when multiplier > 3.0 |
| 5 | `5. Get All Surge Multipliers` | List all zone surge values |
| 6 | `6. Update Demand & Supply` | Dynamic surge via ratio (2.5x) |
| 6b | `6b. Demand-Supply - High Demand` | Max surge scenario (3.0x) |
| 6c | `6c. Demand-Supply - Low Demand` | No surge scenario (1.0x) |
| 6d | `6d. Demand-Supply - Validation Error` | Test 400 on missing params |

## Surge Calculation Matrix

| Pending Rides / Active Drivers | Surge Multiplier |
|---|---|
| <= 0.5 | 1.0 |
| 0.5 – 1.0 | 1.25 |
| 1.0 – 1.5 | 1.5 |
| 1.5 – 2.0 | 1.75 |
| 2.0 – 2.5 | 2.0 |
| 2.5 – 3.0 | 2.5 |
| > 3.0 | 3.0 (max) |

## Fare Calculation

```
totalFare = (baseFare + distanceFare + timeFare) × vehicleMultiplier × surgeMultiplier
```

Where:
- `baseFare` = $2.50
- `distanceFare` = $1.50 / km
- `timeFare` = $0.25 / min
- `vehicleMultiplier` = ECONOMY(1.0), COMFORT(1.5), PREMIUM(2.0)
- `surgeMultiplier` = 1.0 – 3.0

## Swagger / OpenAPI

- Swagger UI: http://localhost:8087/swagger-ui.html
- OpenAPI JSON: http://localhost:8087/api-docs
