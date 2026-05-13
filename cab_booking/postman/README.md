# Postman for Auth, User, Driver

Files:

- `CAB_Auth_User_Driver.postman_collection.json`
- `CAB_Auth_User_Driver.postman_environment.json`

Suggested run order:

1. `1.1 Register Rider` or `1.2 Login Rider`
2. `2.x User Service` requests
3. `1.3 Register Driver` or `1.4 Login Driver`
4. `3.x Driver Service` requests

Notes:

- `Register` and `Login` requests auto-save tokens into collection variables.
- `2.4 Register Device` auto-saves `userDeviceId`.
- `3.10 Internal Driver Availability Check` uses `driverUserId` captured from auth response.
- Current dev flow uses direct `POST /api/auth/register` without registration OTP.
