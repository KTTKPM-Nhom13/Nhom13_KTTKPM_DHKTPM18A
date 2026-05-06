# CAB Booking System - API Payloads

Tai lieu nay tong hop cac request mau de team test nhanh qua `API Gateway` tai `http://localhost:8080`.

## 1. Auth Service

Base path qua gateway: `http://localhost:8080/api/auth`

### 1.1 Register
`POST /api/auth/register`

```json
{
  "fullName": "Nguyen Van A",
  "email": "vana@example.com",
  "password": "12345678",
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

```json
{
  "fullName": "Tran Thi B",
  "email": "thib@example.com",
  "password": "12345678",
  "phoneNumber": "0912345678",
  "avatarUrl": "https://example.com/avatars/thib.png",
  "deviceId": "ios-thib-iphone15",
  "platform": "IOS",
  "userAgent": "CABBooking/1.0 iOS",
  "appVersion": "1.0.0"
}
```

### 1.3 Login
`POST /api/auth/login`

```json
{
  "email": "vana@example.com",
  "password": "12345678",
  "deviceId": "web-chrome-vana-01",
  "platform": "WEB",
  "userAgent": "Mozilla/5.0 Chrome/124.0",
  "appVersion": "1.0.0"
}
```

### 1.4 Refresh Token
`POST /api/auth/refresh`

```json
{
  "refreshToken": "paste_refresh_token_here"
}
```

### 1.5 Logout
`POST /api/auth/logout`

```json
{
  "refreshToken": "paste_refresh_token_here"
}
```

### 1.6 Verify Token
`POST /api/auth/verify`

```json
{
  "token": "paste_access_token_here"
}
```

### 1.7 Debug Token
`GET /api/auth/token`

Co the gui them header:

```text
X-Debug-Trace-Id: test-auth-001
```

### 1.8 Sample Auth Response

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
      "role": "CUSTOMER",
      "emailVerified": true,
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

```json
{
  "fullName": "Nguyen Van A",
  "email": "vana@example.com",
  "phoneNumber": "0901234567",
  "avatarUrl": "https://example.com/avatars/vana.png",
  "gender": "MALE",
  "dateOfBirth": "1999-10-15",
  "defaultPickupNote": "Cho o cong toa nha Landmark 81"
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
    "createdAt": "2026-05-06T14:10:00",
    "updatedAt": "2026-05-06T14:32:00"
  },
  "timestamp": "2026-05-06T14:32:00"
}
```

### 2.4 Get My Devices
`GET /api/users/me/devices`

### 2.5 Register Device
`POST /api/users/me/devices`

```json
{
  "deviceIdentifier": "iphone-15-pro-max-user-a",
  "deviceName": "Tai iPhone 15 Pro Max",
  "deviceType": "MOBILE",
  "platform": "IOS",
  "osVersion": "17.4.1",
  "appVersion": "1.0.0",
  "pushToken": "expo_push_token_or_fcm_token",
  "ipAddress": "192.168.1.20",
  "userAgent": "CABBooking/1.0 iOS",
  "trustedSession": true
}
```

### 2.6 Update Device Session
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

### 2.7 Device Heartbeat
`POST /api/users/me/devices/{deviceId}/heartbeat`

Body trong request nay de trong.

### 2.8 Revoke Device Session
`POST /api/users/me/devices/{deviceId}/revoke`

Body trong request nay de trong.

### 2.9 Sample Device Response

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

## 3. Notification Service

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

## 4. Review Service

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

## 5. Internal Email Service

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

## 6. Quick Test Flow

1. Goi `POST /api/auth/register` hoac `POST /api/auth/login`.
2. Lay `accessToken` trong response.
3. Gan header `Authorization: Bearer <accessToken>` khi goi user APIs.
4. Test profile voi `GET/PUT /api/users/me/profile`.
5. Test device session voi `POST /api/users/me/devices`.
6. Test notification bang `POST /api/notifications/test`.
