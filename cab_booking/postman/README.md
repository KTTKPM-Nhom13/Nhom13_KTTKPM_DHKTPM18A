# Postman

File chinh de import:

- `CAB_Booking_Postman_Collection.json`
- `CAB_DRIVER_ACCEPT_KAFKA_FLOW.postman_collection.json` de test luong Kafka async: `ride.assigned` -> driver accept/reject -> `ride.accept.requested` / `ride.reject.requested` -> booking cap nhat state.
- `CAB_BOUNDARY_MAIN_FLOWS.postman_collection.json` de test boundary refactor moi: Driver ghi status, Ride ghi GPS, Matching doc Redis, Booking update lifecycle.
- `CAB_BOUNDARY_MAIN_FLOWS_TEST_GUIDE.md` la checklist huong dan chay collection boundary moi.

Cap nhat quote hash flow:

- Collection `CAB_BOUNDARY_MAIN_FLOWS.postman_collection.json` da duoc cap nhat theo co che doi soat gia moi.
- Truoc moi request tao booking se co request `Estimate Fare ...` goi `Pricing-Service`.
- Test script cua request estimate tu dong luu `estimateId`, `quoteId`, `quotePayloadHash`, `quoteHashAlgorithm`, `quoteExpiresAt`, `estimatedFare`.
- Request tao booking gui lai cac truong nay de `Booking-Service` goi `POST /api/pricing/confirm/{estimateId}` voi header `X-Quote-Hash`.
- Khong dung lai mot quote da tao booking, vi Pricing confirm se chuyen quote sang `CONFIRMED`.

Variables quan trong:

```text
bookingServiceUrl = http://localhost:8081
pricingServiceUrl = http://localhost:8084
vehicleType       = CAR4
estimatedFare     = tu dong set sau buoc Estimate Fare
```

Payment flow hien tai:

- Online prepaid: Booking publish `payment.requested`, Payment Service tao transaction/payUrl, gateway thanh cong thi publish `payment.completed`, Booking moi publish `ride.created`.
- Cash: Booking publish `ride.created` ngay; Payment Service xu ly CASH sau `ride.completed`.

Collection hien tai da duoc tach lai thanh 2 luong dang ky dung theo code:

- `1. Customer Registration Flow`
- `2. Driver Registration Flow`
- `3. Shared Auth Utilities`

Nhung diem da duoc check lai:

- Gateway auth dung `{{baseUrl}}/api/auth/...` theo `cab-booking-config/api-gateway.yaml`.
- Dang ky hien tai la 1 buoc `POST /api/auth/register`; flow OTP dang ky cu trong `auth-service` dang duoc comment out.
- Luong khach sau dang ky di qua `user-service` voi profile, account, device.
- Luong tai xe sau dang ky di qua `driver-service` voi profile/KYC, roi moi len `ONLINE`.
- `PUT /api/drivers/me/profile` hien tai tu dong dua `verificationStatus` sang `APPROVED`, nen sau buoc nay co the test `PATCH /api/drivers/me/availability`.
- Luong accept Kafka chi dung Gateway cho Auth (`{{gatewayUrl}}/api/auth/...`). Booking, Driver va Pricing goi truc tiep qua service port (`8081`, `8083`, `8084`) vi hien tai chi auth-service duoc gan vao gateway.

Thu tu chay goi y:

1. Chay folder `1. Customer Registration Flow` tu tren xuong duoi.
2. Chay folder `2. Driver Registration Flow` tu tren xuong duoi.
3. Neu can doi mat khau, refresh token, logout, dung folder `3. Shared Auth Utilities`.
