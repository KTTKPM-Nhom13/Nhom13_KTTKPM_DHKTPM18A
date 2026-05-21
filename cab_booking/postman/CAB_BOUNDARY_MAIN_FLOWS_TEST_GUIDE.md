# CAB Boundary Main Flows - Test Guide

Guide nay dung voi collection:

`CAB_BOUNDARY_MAIN_FLOWS.postman_collection.json`

Muc tieu test:

- Driver Service ghi `driver:status:{driverId}`.
- Ride Service ghi `driver:locations`.
- Matching Service chi doc Redis status + Redis GEO.
- Booking Service la source of truth cho booking lifecycle DB.

## 0. Chuan bi

Can cac container core dang chay:

- `auth-service` 8081
- `api-gateway` 8080
- `driver-service` 8083
- `booking-service` 8084
- `ride-service` 8085
- `matching-service` 8086
- `kafka`, `redis`, cac database

Neu import collection vao Postman, check variables:

```text
gatewayUrl        = http://localhost:8080
bookingServiceUrl = http://localhost:8084
driverServiceUrl  = http://localhost:8083
rideServiceUrl    = http://localhost:8085
```

## 1. Auth

Chay folder `1. Auth` tu tren xuong:

1. `1.1 Register Customer`
2. `1.2 Login Customer`
3. `1.3 Register Driver`
4. `1.4 Login Driver`

Expected:

- Collection variables co `customerAccessToken`, `driverAccessToken`.
- Co `customerUserId`, `driverUserId`.

Neu register tra `409`, chay login tiep la duoc neu email/password cu van dung.

## 2. Driver Online + GPS

Chay folder `2. Prepare Driver Status And GPS`:

1. `2.1 Upsert Driver Profile`
2. `2.2 Set Driver ONLINE -> Redis driver:status AVAILABLE`
3. `2.3 Push Driver GPS To Ride Redis GEO`
4. `2.4 Poll Driver Current Ride`

Expected:

- Driver profile duoc approve.
- Driver availability la `ONLINE`.
- Redis co status va location.

Redis check:

```bash
redis-cli -a redis123 GET driver:status:{driverUserId}
redis-cli -a redis123 GEOPOS driver:locations {driverUserId}
```

Expected:

```text
driver:status:{driverUserId} = AVAILABLE
GEOPOS co toa do
```

Neu `driver:status` null, Matching se skip driver.

## 3. Accept Flow

Chay folder `3. Accept Flow`:

1. `3.1 Create Booking For Accept`
2. Doi 2-5 giay cho Kafka async.
3. `3.2 Poll Driver Current Ride After ride.assigned`
4. `3.3 Driver Accept Ride -> ride.accepted`
5. Doi 1-3 giay.
6. `3.4 Poll Booking Active After Accept`

Expected:

- Booking ban dau `MATCHING`.
- Matching doc Redis `driver:locations` + `driver:status`.
- Matching publish `ride.assigned`.
- Driver consume `ride.assigned`, local ride status `ASSIGNED`.
- Driver accept publish `ride.accepted`.
- Booking consume `ride.accepted`, status thanh `ACCEPTED`.
- Driver status Redis thanh `BUSY`.

Redis check:

```bash
redis-cli -a redis123 GET driver:status:{driverUserId}
redis-cli -a redis123 GET driver:lock:{driverUserId}
```

Expected sau accept:

```text
driver:status:{driverUserId} = BUSY
driver:lock:{driverUserId} co the con hoac het TTL
```

## 4. Cancel Cleanup Flow

Dung sau accept flow.

Chay folder `4. Cancel Cleanup Flow`:

1. `4.1 Cancel Accepted Booking`
2. Doi 1-3 giay.
3. `4.2 Poll Driver Current Ride After Cancel`
4. Neu can, chay `4.3 Set Driver ONLINE Again If Needed`

Expected:

- Booking set `CANCELLED`.
- Booking publish canonical `ride.cancelled`.
- Driver consume `ride.cancelled`.
- Driver clear `currentRideId`.
- Driver availability ve `ONLINE`.
- Redis `driver:status:{driverUserId}` ve `AVAILABLE`.
- Matching retry/assign cho ride da cancel bi chan boi `booking:cancelled:{rideId}`.

Redis check:

```bash
redis-cli -a redis123 GET booking:cancelled:{acceptBookingId}
redis-cli -a redis123 GET driver:status:{driverUserId}
```

Expected:

```text
booking:cancelled:{acceptBookingId} = true
driver:status:{driverUserId} = AVAILABLE
```

## 5. Reject Rematch Flow

Chay folder `5. Reject Rematch Flow`:

1. Dam bao driver dang `ONLINE` va `AVAILABLE`.
2. `5.1 Create Booking For Reject`
3. Doi 2-5 giay.
4. `5.2 Poll Driver Current Ride After Reject Assignment`
5. `5.3 Driver Reject Ride -> ride.rejected`
6. Doi 1-3 giay.
7. `5.4 Poll Booking Active After Reject`

Expected:

- Driver reject publish `ride.rejected`.
- Booking consume `ride.rejected`.
- Booking `ASSIGNED -> MATCHING`.
- Booking publish lai `ride.created` voi:

```text
rematch = true
excludedDriverIds contains rejected driver
```

Neu chi co 1 driver trong Redis, booking se o `MATCHING` sau reject.

## 6. Complete Flow

Chay folder `6. Complete Flow`:

1. Dam bao driver `ONLINE`, `AVAILABLE`, va co GPS.
2. `6.1 Create Booking For Complete`
3. Doi 2-5 giay cho assignment.
4. `6.2 Driver Accept Complete Booking`
5. Doi 1-3 giay.
6. `6.3 Driver Arrive`
7. Doi 1-3 giay.
8. `6.4 Driver Start`
9. Doi 1-3 giay.
10. `6.5 Driver Complete -> ride.completed`
11. `6.6 Poll Booking Active Or History After Complete`

Expected lifecycle:

```text
MATCHING
-> ASSIGNED
-> ACCEPTED
-> PICKUP
-> IN_PROGRESS
-> COMPLETED
```

Expected events:

```text
ride.assigned
ride.accepted
ride.arrived
ride.started
ride.completed
```

Expected Redis after complete:

```text
driver:status:{driverUserId} = AVAILABLE
```

## 7-8. Payment Flows

Cash flow:

```text
Booking Service
-> ride.created
-> ride.completed
-> Payment Service records CASH payment
-> payment.completed
-> driver.earning.settled
```

Online prepaid flow:

```text
Booking Service
-> PENDING_PAYMENT
-> payment.requested
-> Payment Service creates transaction/payUrl
-> customer completes payment
-> payment.completed
-> Booking Service MATCHING
-> ride.created
-> ride.completed
-> Payment Service settles driver earning without charging again
-> driver.earning.settled
```

Postman note for VNPay/MoMo/ZaloPay:

- After create booking, run the payment lookup step and capture `payUrl`/gateway data.
- Complete the payment through the gateway sandbox or callback flow.
- Only after `payment.completed` should you run driver assignment/accept/ride lifecycle steps.

## Debug nhanh

Kafka UI:

```text
http://localhost:8910
```

Topics can xem:

```text
ride.created
ride.assigned
ride.accepted
ride.rejected
ride.arrived
ride.started
ride.completed
ride.cancelled
payment.requested
payment.completed
payment.failed
driver.earning.settled
driver.status.changed
driver.location.updated
```

Redis commands:

```bash
redis-cli -a redis123 KEYS "driver:*"
redis-cli -a redis123 KEYS "booking:*"
redis-cli -a redis123 GET driver:status:{driverUserId}
redis-cli -a redis123 GEOPOS driver:locations {driverUserId}
```

Luu y:

- Matching skip driver neu `driver:status:{driverId}` bi null.
- Ride Service phai nhan GPS truoc thi Matching moi tim thay driver trong GEO.
- Kafka la async, nen cac buoc poll nen chay sau khi doi vai giay.
- Neu default `java` tren may la JRE 1.8, khi build Maven can uu tien JDK 22 path:

```powershell
$env:Path='C:\Program Files\Common Files\Oracle\Java\javapath;' + $env:Path
```
