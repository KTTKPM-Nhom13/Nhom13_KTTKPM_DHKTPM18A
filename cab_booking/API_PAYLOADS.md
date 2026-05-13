# CAB Booking System - API Payloads

Tai lieu nay tong hop cac request mau de team test nhanh qua `API Gateway` tai `http://localhost:8080`.

Phan `Auth Service`, `User Service` va `Driver Service` da duoc test end-to-end qua gateway vao ngay `2026-05-07`.

## 1. Auth Service

Base path qua gateway: `http://localhost:8080/api/auth`

### 1.1 Register
`POST /api/auth/register`

Luong dev hien tai cho phep tao tai khoan truc tiep qua mot endpoint duy nhat, khong can verify email hay OTP.

```json
{
  "fullName": "Nguyen Van A",
  "email": "vana@example.com",
  "password": "12345678",
  "role": "USER",
  "phoneNumber": "0901234567",
  "avatarUrl": "https://example.com/avatars/vana.png",
  "deviceId": "web-chrome-vana-01",
  "platform": "WEB",
  "userAgent": "Mozilla/5.0 Chrome/124.0",
  "appVersion": "1.0.0"
}
```

### 1.2 Register By Email
`POST /api/auth/register/email`

Endpoint nay hien la alias de gui OTP dang ky:

```json
{
  "email": "thib@example.com"
}
```

### 1.3 Login
`POST /api/auth/login`

Payload duoi day da duoc test pass qua gateway:

```json
{
  "email": "tai.demo@cab.local",
  "password": "123456",
  "deviceId": "e2e-web-01",
  "platform": "WEB",
  "userAgent": "Codex-E2E",
  "appVersion": "1.0.0"
}
```

### 1.4 Change Password
`POST /api/auth/password/change`

Can Bearer token:

```text
Authorization: Bearer <access_token>
```

```json
{
  "currentPassword": "12345678",
  "newPassword": "87654321"
}
```

### 1.5 Forgot Password - Request OTP
`POST /api/auth/password/forgot/request-otp`

```json
{
  "email": "vana@example.com"
}
```

### 1.6 Forgot Password - Reset
`POST /api/auth/password/forgot/reset`

```json
{
  "email": "vana@example.com",
  "otpCode": "123456",
  "newPassword": "87654321"
}
```

### 1.7 Refresh Token
`POST /api/auth/refresh`

Payload duoi day da duoc test pass qua gateway sau login:

```json
{
  "refreshToken": "paste_refresh_token_here"
}
```

### 1.8 Logout
`POST /api/auth/logout`

```json
{
  "refreshToken": "paste_refresh_token_here"
}
```

### 1.9 Verify Token
`POST /api/auth/verify`

Payload duoi day da duoc test pass qua gateway sau login:

```json
{
  "token": "paste_access_token_here"
}
```

### 1.12 Debug Token
`GET /api/auth/token`

Co the gui them header:

```text
X-Debug-Trace-Id: test-auth-001
```

### 1.13 Sample OTP Response

```json
{
  "code": 200,
  "message": "Registration OTP sent successfully",
  "result": {
    "email": "vana@example.com",
    "expiresInSeconds": 600
  },
  "timestamp": "2026-05-06T15:20:00"
}
```

### 1.14 Sample Verify OTP Response

```json
{
  "code": 200,
  "message": "Registration OTP verified successfully",
  "result": {
    "email": "vana@example.com",
    "verified": true,
    "canRegister": true,
    "verifiedAt": "2026-05-06T15:22:00"
  },
  "timestamp": "2026-05-06T15:22:00"
}
```

### 1.15 Sample Change Password Response

```json
{
  "code": 200,
  "message": "Password changed successfully",
  "result": {
    "email": "vana@example.com",
    "changedAt": "2026-05-06T15:35:00"
  },
  "timestamp": "2026-05-06T15:35:00"
}
```

### 1.16 Sample Auth Response

```json
{
  "code": 200,
  "message": "Logged in successfully",
  "result": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "9fcb7f68-33de-46d4-b43d-f62a8f0be890",
    "tokenType": "Bearer",
    "expiresInSeconds": 7200,
    "deviceId": "web-chrome-vana-01",
    "platform": "WEB",
    "user": {
      "userId": "f8e3cf6b-6a0a-4f59-a4d2-e3f3e84ac001",
      "email": "vana@example.com",
      "fullName": "Nguyen Van A",
      "avatarUrl": "https://example.com/avatars/vana.png",
      "phoneNumber": "0901234567",
      "role": "USER",
      "emailVerified": true,
      "accountStatus": "ACTIVE",
      "scheduledDeletionAt": null,
      "lastLoginAt": "2026-05-06T14:30:00"
    }
  },
  "timestamp": "2026-05-06T14:30:00"
}
```

## 2. User Service

Tat ca API ben duoi can Bearer token:

```text
Authorization: Bearer <access_token>
```

Base path qua gateway: `http://localhost:8080/api/users`

### 2.1 Get My Profile
`GET /api/users/me/profile`

### 2.2 Upsert My Profile
`PUT /api/users/me/profile`

Payload duoi day da duoc test pass qua gateway:

```json
{
  "fullName": "Tai Demo Rider",
  "phoneNumber": "0901234567",
  "avatarUrl": "https://cdn.example.com/avatars/tai-demo.png",
  "gender": "MALE",
  "dateOfBirth": "1999-09-09"
}
```

### 2.3 Sample Profile Response

```json
{
  "code": 200,
  "message": "Fetched user profile successfully",
  "result": {
    "id": "59dd4f8d-f329-47f6-b252-86d7a4a98f11",
    "externalUserId": "f8e3cf6b-6a0a-4f59-a4d2-e3f3e84ac001",
    "fullName": "Nguyen Van A",
    "email": "vana@example.com",
    "phoneNumber": "0901234567",
    "avatarUrl": "https://example.com/avatars/vana.png",
    "gender": "MALE",
    "dateOfBirth": "1999-10-15",
    "defaultPickupNote": "Cho o cong toa nha Landmark 81",
    "accountStatus": "ACTIVE",
    "deletionRequestedAt": null,
    "scheduledDeletionAt": null,
    "createdAt": "2026-05-06T14:10:00",
    "updatedAt": "2026-05-06T14:32:00"
  },
  "timestamp": "2026-05-06T14:32:00"
}
```

### 2.4 Get My Devices
`GET /api/users/me/devices`

### 2.5 Get My Account
`GET /api/users/me/account`

### 2.6 Request Soft Delete Account
`POST /api/users/me/account/delete-request`

```json
{
  "reason": "Tam thoi khong con nhu cau su dung dich vu"
}
```

Sau khi goi API nay:
- tai khoan vao trang thai `PENDING_DELETION`
- co the khoi phuc trong 30 ngay
- login moi se bi chan

### 2.7 Restore My Account
`POST /api/users/me/account/restore`

Body trong request nay de trong.

### 2.8 Sample Account Response

```json
{
  "code": 200,
  "message": "Fetched user account successfully",
  "result": {
    "profileId": "59dd4f8d-f329-47f6-b252-86d7a4a98f11",
    "externalUserId": "f8e3cf6b-6a0a-4f59-a4d2-e3f3e84ac001",
    "fullName": "Nguyen Van A",
    "email": "vana@example.com",
    "phoneNumber": "0901234567",
    "avatarUrl": "https://example.com/avatars/vana.png",
    "accountStatus": "PENDING_DELETION",
    "deletionRequestedAt": "2026-05-06T16:00:00",
    "scheduledDeletionAt": "2026-06-05T16:00:00",
    "restoreEligible": true,
    "createdAt": "2026-05-06T14:10:00",
    "updatedAt": "2026-05-06T16:00:00"
  },
  "timestamp": "2026-05-06T16:00:00"
}
```

### 2.9 Register Device
`POST /api/users/me/devices`

Payload duoi day da duoc test pass qua gateway:

```json
{
  "deviceIdentifier": "device-web-e2e",
  "deviceName": "Chrome on Windows",
  "deviceType": "WEB",
  "platform": "WEB",
  "osVersion": "Windows 11",
  "appVersion": "1.0.1",
  "pushToken": "push-token-e2e",
  "userAgent": "Codex-E2E Browser",
  "trustedSession": true
}
```

### 2.10 Update Device Session
`PATCH /api/users/me/devices/{deviceId}`

```json
{
  "deviceName": "Tai iPhone 15 PM",
  "deviceType": "MOBILE",
  "platform": "IOS",
  "osVersion": "17.5",
  "appVersion": "1.0.1",
  "pushToken": "new_push_token_here",
  "ipAddress": "10.10.10.21",
  "userAgent": "CABBooking/1.0.1 iOS",
  "trustedSession": true,
  "status": "ACTIVE"
}
```

### 2.11 Device Heartbeat
`POST /api/users/me/devices/{deviceId}/heartbeat`

Body trong request nay de trong.

### 2.12 Revoke Device Session
`POST /api/users/me/devices/{deviceId}/revoke`

Body trong request nay de trong.

### 2.13 Sample Device Response

```json
{
  "code": 200,
  "message": "Registered user device successfully",
  "result": {
    "id": "2d66f509-8a9d-40b1-ae3a-9344f2c7ce90",
    "deviceIdentifier": "iphone-15-pro-max-user-a",
    "deviceName": "Tai iPhone 15 Pro Max",
    "deviceType": "MOBILE",
    "platform": "IOS",
    "osVersion": "17.4.1",
    "appVersion": "1.0.0",
    "pushToken": "expo_push_token_or_fcm_token",
    "ipAddress": "192.168.1.20",
    "userAgent": "CABBooking/1.0 iOS",
    "lastLoginAt": "2026-05-06T14:31:00",
    "lastSeenAt": "2026-05-06T14:31:00",
    "status": "ACTIVE",
    "trustedSession": true,
    "createdAt": "2026-05-06T14:31:00",
    "updatedAt": "2026-05-06T14:31:00"
  },
  "timestamp": "2026-05-06T14:31:00"
}
```

## 3. Driver Service

Tat ca API ben duoi can Bearer token:

```text
Authorization: Bearer <access_token>
```

Khuyen nghi tao tai khoan tai xe bang `POST /api/auth/register` voi:

```json
{
  "fullName": "Tran Van Driver",
  "email": "driver01@example.com",
  "password": "12345678",
  "role": "DRIVER",
  "phoneNumber": "0911222333",
  "avatarUrl": "https://example.com/avatars/driver01.png",
  "deviceId": "driver-iphone-01",
  "platform": "IOS",
  "userAgent": "CABDriver/1.0 iOS",
  "appVersion": "1.0.0"
}
```

Base path qua gateway: `http://localhost:8080/api/drivers`

### 3.1 Get My Driver Profile
`GET /api/drivers/me/profile`

### 3.2 Upsert My Driver Profile / KYC
`PUT /api/drivers/me/profile`

Payload duoi day da duoc test pass qua gateway:

```json
{
  "fullName": "Tai Demo Driver",
  "phoneNumber": "0901234567",
  "avatarUrl": "https://cdn.example.com/avatars/tai-driver.png",
  "licenseNumber": "79A-123456",
  "vehicleType": "SEDAN",
  "vehiclePlate": "51H-12345",
  "vehicleModel": "Toyota Vios",
  "vehicleColor": "Black",
  "serviceArea": "District 1, Ho Chi Minh City"
}
```

### 3.3 Update Availability
`PATCH /api/drivers/me/availability`

Luu y: tai xe phai co profile hop le va `verificationStatus = APPROVED` truoc khi chuyen sang `ONLINE`.

```json
{
  "availabilityStatus": "ONLINE",
  "currentLatitude": 10.7769,
  "currentLongitude": 106.7009
}
```

### 3.4 Get Current Ride
`GET /api/drivers/me/current-ride`

### 3.5 Accept / Reject Incoming Ride
`POST /api/drivers/me/rides/assignment`

Payload duoi day da duoc test pass qua gateway:

```json
{
  "rideId": "RIDE-20260507-001",
  "action": "ACCEPT",
  "pickupAddress": "12 Nguyen Hue, District 1",
  "destinationAddress": "42 Le Loi, District 1"
}
```

### 3.6 Update Current Ride Progress
`PATCH /api/drivers/me/rides/current`

Payload duoi day da duoc test pass qua gateway cho buoc tai xe den diem don:

```json
{
  "rideStatus": "EN_ROUTE_PICKUP",
  "currentLatitude": 10.7771,
  "currentLongitude": 106.7011
}
```

Payload duoi day da duoc test pass qua gateway cho buoc dang chay cuoc:

```json
{
  "rideStatus": "IN_PROGRESS",
  "currentLatitude": 10.7780,
  "currentLongitude": 106.7020
}
```

### 3.7 Complete Current Ride
`POST /api/drivers/me/rides/current/complete`

Payload duoi day da duoc test pass qua gateway:

```json
{
  "fareAmount": 132500,
  "distanceKm": 8.4
}
```

### 3.8 Earnings Summary
`GET /api/drivers/me/earnings/summary`

### 3.9 Internal Driver Availability Check
`GET /internal/drivers/{driverId}/availability`

Endpoint nay danh cho service-to-service call de kiem tra nhanh tai xe dang `ONLINE` hay `OFFLINE`.
Moi lan check se phat lai event Kafka len topic `driver.status.changed` de service khac co the subscribe.

Vi du:

```text
GET http://localhost:8089/internal/drivers/driver-3/availability
```

Sample response:

```json
{
  "code": 200,
  "message": "Checked driver availability successfully",
  "result": {
    "externalUserId": "driver-3",
    "availabilityStatus": "ONLINE",
    "online": true,
    "offline": false,
    "activeForBooking": true,
    "verificationStatus": "APPROVED",
    "currentRideId": null,
    "currentRideStatus": null,
    "currentLatitude": 10.780000,
    "currentLongitude": 106.690000,
    "lastOnlineAt": "2026-05-10T16:34:02"
  },
  "timestamp": "2026-05-10T16:34:02"
}
```

Kafka event duoc phat cung luc:

```json
{
  "eventId": "9d31d4c2-2dd8-4a87-82a0-442f70858bc5",
  "type": "DriverStatusChanged",
  "driverId": "driver-3",
  "availabilityStatus": "ONLINE",
  "activeForBooking": true,
  "rideId": null,
  "rideStatus": null,
  "currentLocation": {
    "lat": 10.780000,
    "lng": 106.690000
  },
  "timestamp": "2026-05-10T09:34:02Z"
}
```

Consumer hien co trong `booking-service`:
- `@KafkaListener(topics = "driver.status.changed", groupId = "booking-service-group")`

### 3.10 Sample Driver Profile Response

```json
{
  "code": 200,
  "message": "Fetched driver profile successfully",
  "result": {
    "id": "e1b638e4-9d2d-4f09-a7d9-c2d7f98c27de",
    "externalUserId": "f8e3cf6b-6a0a-4f59-a4d2-e3f3e84ac001",
    "fullName": "Tran Van Driver",
    "email": "driver01@example.com",
    "phoneNumber": "0911222333",
    "avatarUrl": "https://example.com/avatars/driver01.png",
    "licenseNumber": "79B2-123456",
    "vehicleType": "SEDAN",
    "vehiclePlate": "51H-123.45",
    "vehicleModel": "Toyota Vios 2023",
    "vehicleColor": "White",
    "serviceArea": "Thu Duc - Binh Thanh - District 1",
    "availabilityStatus": "ONLINE",
    "verificationStatus": "APPROVED",
    "currentLatitude": 10.8012,
    "currentLongitude": 106.7145,
    "lastOnlineAt": "2026-05-06T17:10:00",
    "approvedAt": "2026-05-06T12:00:00",
    "totalCompletedRides": 12,
    "averageRating": 4.9,
    "totalEarnings": 2450000,
    "createdAt": "2026-05-05T10:00:00",
    "updatedAt": "2026-05-06T17:10:00"
  },
  "timestamp": "2026-05-06T17:10:00"
}
```

### 3.10 Sample Current Ride Response

```json
{
  "code": 200,
  "message": "Fetched current ride successfully",
  "result": {
    "rideId": "RIDE_1001",
    "rideStatus": "ACCEPTED",
    "pickupAddress": "291 Dien Bien Phu, Binh Thanh",
    "destinationAddress": "12 Ton Dan, District 4",
    "requestedAt": "2026-05-06T17:15:00",
    "driverAvailabilityStatus": "ON_TRIP"
  },
  "timestamp": "2026-05-06T17:16:00"
}
```

### 3.11 Sample Earnings Response

```json
{
  "code": 200,
  "message": "Fetched driver earnings summary successfully",
  "result": {
    "externalUserId": "f8e3cf6b-6a0a-4f59-a4d2-e3f3e84ac001",
    "availabilityStatus": "ONLINE",
    "totalCompletedRides": 12,
    "averageRating": 4.9,
    "totalEarnings": 2635000,
    "currentRideActive": false,
    "lastOnlineAt": "2026-05-06T18:05:00"
  },
  "timestamp": "2026-05-06T18:06:00"
}
```

## 4. Notification Service

Base path qua gateway: `http://localhost:8080/api/notifications`

### 3.1 Get Notifications By User
`GET /api/notifications/user/{userId}`

Vi du:

```text
GET http://localhost:8080/api/notifications/user/USER_123
```

### 3.2 Create Test Notification
`POST /api/notifications/test`

Vi du:

```text
POST http://localhost:8080/api/notifications/test?userId=USER_123&message=Tai xe dang den diem don
```

### 3.3 Sample Notification Response

```json
{
  "id": "6819a7e6c44ce163d2a1f501",
  "userId": "USER_123",
  "title": null,
  "message": "Tai xe dang den diem don",
  "type": null,
  "status": null,
  "createdAt": "2026-05-06T15:00:00"
}
```

## 5. Review Service

Base path qua gateway: `http://localhost:8080/api/reviews`

### 4.1 Create Review
`POST /api/reviews`

```json
{
  "rideId": "RIDE_001",
  "userId": "USER_123",
  "driverId": "DRIVER_456",
  "rating": 5,
  "comment": "Tai xe rat than thien, xe sach se"
}
```

## 6. Internal Email Service

API nay danh cho service-to-service, khong di qua gateway thong thuong.

Base URL noi bo: `http://localhost:8087/internal/emails`

### 5.1 Send Email
`POST /internal/emails/send`

```json
{
  "to": "customer@example.com",
  "subject": "Welcome to CAB Booking",
  "htmlContent": "<h1>Welcome</h1><p>Your account is ready.</p>",
  "textContent": "Welcome. Your account is ready."
}
```

## 7. Quick Test Flow

1. Goi `POST /api/auth/register` de tao tai khoan truc tiep.
2. Hoac dang nhap bang `POST /api/auth/login`.
3. Neu can doi mat khau khi da dang nhap, goi `POST /api/auth/password/change`.
4. Neu quen mat khau, goi `POST /api/auth/password/forgot/request-otp` roi `POST /api/auth/password/forgot/reset`.
5. Lay `accessToken` trong response.
6. Gan header `Authorization: Bearer <accessToken>` khi goi user APIs.
7. Test profile voi `GET/PUT /api/users/me/profile`.
8. Neu can role tai xe, dang ky them tai khoan voi `role = DRIVER`.
9. Test driver profile va availability qua `GET/PUT /api/drivers/me/profile`, `PATCH /api/drivers/me/availability`.
10. Test account lifecycle voi `GET /api/users/me/account`, `POST /api/users/me/account/delete-request`, `POST /api/users/me/account/restore`.
11. Test device session voi `POST /api/users/me/devices`.
12. Test notification bang `POST /api/notifications/test`.
