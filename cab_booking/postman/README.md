# Postman

File chinh de import:

- `CAB_Booking_Postman_Collection.json`
- `CAB_BOOKING_KAFKA_LISTENER_FLOW.postman_collection.json`

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

Thu tu chay goi y:

1. Chay folder `1. Customer Registration Flow` tu tren xuong duoi.
2. Chay folder `2. Driver Registration Flow` tu tren xuong duoi.
3. Neu can doi mat khau, refresh token, logout, dung folder `3. Shared Auth Utilities`.

Collection `CAB_BOOKING_KAFKA_LISTENER_FLOW.postman_collection.json` dung de test luong listener Kafka cua `booking-service` thong qua HTTP:

- `driver-service` phat `ride.assigned` va `driver.status.changed`
- `payment-service` phat `payment.completed` va `payment.failed`
- co them cac buoc direct trong `booking-service` de dua booking sang `COMPLETED` truoc khi ban event payment

Ghi chu:

- Hien tai repo chua co endpoint HTTP cong khai de phat `ride.arrived`, `ride.started`, `ride.finished` tu `ride-service`, nen collection moi khong the chạm cac listener do bang Postman-only.
- Neu can test 3 topic do dung nghia Kafka end-to-end, can them producer endpoint hoac ban event bang Kafka CLI/Kafka UI.
