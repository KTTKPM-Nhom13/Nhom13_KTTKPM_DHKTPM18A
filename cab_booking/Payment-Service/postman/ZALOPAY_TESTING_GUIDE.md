# ZaloPay Payment Testing Guide

## Overview

This document provides instructions for testing the ZaloPay payment integration in the CAB Booking system using the Postman collection.

## Prerequisites

### 1. Start Payment Service

Ensure the Payment Service is running:

```bash
cd Payment-Service
./mvnw spring-boot:run
```

Or with Docker:
```bash
docker-compose up -d payment-service
```

### 2. ZaloPay Sandbox Configuration

The following sandbox credentials are pre-configured:

| Parameter | Value |
|-----------|-------|
| App ID | 2553 |
| Key 1 | PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL |
| Key 2 | kLtgPl8HHhfvMuDHPwKfgfsY4Ydm9eIz |
| Endpoint | https://sb-openapi.zalopay.vn/v2 |

### 3. Import Collection into Postman

1. Open Postman
2. Click **Import**
3. Select `ZaloPay_Payment_Test.postman_collection.json`
4. Click **Import**

## Collection Structure

### Folder 1: Create ZaloPay Payment
Main endpoint for creating a ZaloPay payment charge.

**Endpoint:** `POST /api/payments/charge`

**Request Body:**
```json
{
    "bookingId": "BK-123456",
    "customerId": "CUST-001",
    "amount": 50000,
    "paymentMethod": "ZALOPAY",
    "currency": "VND",
    "description": "Test ZaloPay payment"
}
```

**Expected Response:**
```json
{
    "message": "Payment saga initiated",
    "result": {
        "transactionId": "txn_xxx",
        "bookingId": "BK-123456",
        "status": "PENDING",
        "payUrl": "https://...",
        "zaloPayAppTransId": "260514_txn_xxx"
    }
}
```

### Folder 2: Get Payment by Booking ID
Query payment status by booking ID.

**Endpoint:** `GET /api/payments/booking/{bookingId}`

### Folder 3: Get Payment by Transaction ID
Query payment status by transaction ID.

**Endpoint:** `GET /api/payments/txn/{transactionId}`

### Folder 4: ZaloPay Callback (Webhook)
ZaloPay sends payment confirmation to this endpoint after customer completes payment.

**Endpoint:** `POST /api/payments/zalopay/callback`

## Testing Scenarios

### Scenario 1: Happy Path - Create and Check Payment

1. **Create Payment**
   - Run "1. Create ZaloPay Payment"
   - Verify response status is 200
   - Copy the `transactionId` from response

2. **Check by Booking ID**
   - Run "2. Get Payment by Booking ID"
   - Verify booking ID matches

3. **Check by Transaction ID**
   - Run "3. Get Payment by Transaction ID"
   - Verify transaction ID matches

### Scenario 2: Idempotency Test

1. **First Request**
   - Run "First Request - Creates Payment" in Folder 6
   - Note the `transactionId` in response

2. **Duplicate Request**
   - Run "Duplicate Request - Returns Same Response"
   - Use the same idempotency key
   - Verify same `transactionId` is returned

### Scenario 3: Validation Tests

Run tests in Folder 7 to verify:
- Missing Booking ID → 400 Bad Request
- Missing Customer ID → 400 Bad Request
- Missing Payment Method → 400 Bad Request
- Non-existent payment → 404 Not Found

### Scenario 4: Amount Limits

Run tests in Folder 5:
- Minimum amount (1000 VND) → Success
- Large amount (1,000,000 VND) → Success
- Below minimum (500 VND) → 400 Bad Request

### Scenario 5: Full Demo Flow

Run the collection "8. Full Payment Flow Demo" which:
1. Creates a payment
2. Checks status by booking ID
3. Checks status by transaction ID

## Important Notes

### Local Testing with Callbacks

For local development, the Payment Service cannot receive callbacks directly from ZaloPay because:
- ZaloPay servers cannot reach localhost
- You need a public URL

**Solution: Use Ngrok**

1. Install ngrok and sign up
2. Start ngrok:
   ```bash
   ngrok http 8088
   ```
3. Copy the https URL (e.g., `https://abc123.ngrok.io`)
4. Update `.env` file:
   ```
   ZALOPAY_CALLBACK_URL=https://abc123.ngrok.io/api/payments/zalopay/callback
   ```
5. Restart Payment Service

### Callback Testing

In production, ZaloPay automatically calls the callback URL after payment. For testing:

1. **Use ZaloPay Sandbox Portal** (recommended)
   - Go to https://sandbox.zalopay.com
   - Use test credentials
   - Simulate payment

2. **Manual Callback Simulation**
   - Use the "4. ZaloPay Callback (Webhook)" request
   - Note: Signature verification may fail in sandbox mode

## Troubleshooting

### Issue: Connection Refused
- Ensure Payment Service is running on port 8088
- Check `application.yaml` for correct port configuration

### Issue: Invalid Signature
- Callback signature verification uses Key2
- In sandbox mode, you may need to disable signature verification for testing

### Issue: Payment Gateway Error
- Check ZaloPay sandbox status
- Verify endpoint URL is correct
- Check network connectivity

### Issue: 502 Bad Gateway
- Payment gateway is unavailable
- Circuit breaker may be open
- Wait and retry

## API Reference

### POST /api/payments/charge

Create a new payment charge.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| bookingId | string | Yes | Unique booking identifier |
| customerId | string | Yes | Customer identifier |
| amount | number | Yes | Amount in VND (min: 1000) |
| paymentMethod | enum | Yes | ZALOPAY, MOMO, etc. |
| currency | string | No | Currency code (default: VND) |
| description | string | No | Payment description |
| idempotencyKey | string | No | Key to prevent duplicates |

### GET /api/payments/booking/{bookingId}

Retrieve payment by booking ID.

### GET /api/payments/txn/{transactionId}

Retrieve payment by transaction ID.

### POST /api/payments/zalopay/callback

Webhook endpoint for ZaloPay callbacks.

## Variables Reference

| Variable | Description |
|----------|-------------|
| `baseUrl` | Base URL of Payment Service |
| `bookingId` | Current booking ID |
| `customerId` | Current customer ID |
| `transactionId` | Current transaction ID |
| `idempotencyKey` | Idempotency key for requests |
| `zaloPayAppTransId` | ZaloPay transaction ID |
| `zaloPayOrderToken` | ZaloPay order token |

## Test Results

After running tests, check the **Test Results** tab in Postman to see:
- Number of passed/failed tests
- Response times
- Any errors

## Contact

For issues with the Payment Service, check:
1. Application logs: `logs/payment-service.log`
2. Kafka consumer lag
3. Database connectivity
