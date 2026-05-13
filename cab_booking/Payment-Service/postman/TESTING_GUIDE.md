# Payment Service - Testing Guide

Hướng dẫn chi tiết cách test Payment Service API với Postman.

---

## 1. Chuẩn bị

### 1.1 Import vào Postman

1. Mở Postman → **Import** → Chọn file:
   - `Payment-Service/postman/Payment_Service_API.postman_environment.json`
   - `Payment-Service/postman/Payment_Service_API.postman_collection.json`
2. Sau khi import, chọn environment **"CAB Booking - Payment Service"** từ dropdown góc phải trên.

### 1.2 Cấu hình Environment

Mở **Environments** → Chọn **"CAB Booking - Payment Service"**, kiểm tra các biến:

| Variable | Value | Mô tả |
|---|---|---|
| `baseUrl` | `http://localhost:8088` | URL Payment Service |
| `ngrokUrl` | `https://your-id.ngrok-free.app` | Ngrok URL (nếu dùng IPN thật từ MoMo) |
| `momoSecretKey` | `SetA5RDnLHvt51AULf51DyauxUo3kDU6` | Secret key sandbox |
| `momoAccessKey` | `MTCKt9W3eU1m39TW` | Access key sandbox |
| `momoPartnerCode` | `MOMOLRJZ20181206` | Partner code sandbox |

### 1.3 Khởi động Service

```bash
cd Payment-Service
mvn spring-boot:run
# Hoặc chạy qua IDE: run PaymentServiceApplication
```

Xác nhận service đang chạy:
```bash
curl http://localhost:8088/actuator/health
```

---

## 2. Test Cases

### TC-01: Tạo thanh toán MoMo (Charge API)

**Mục đích:** Tạo một giao dịch thanh toán MoMo mới.

**Cách thực hiện:**
1. Chọn request **"1. Create MoMo Payment (Charge API)"**
2. Nhấn **Send**

**Expected:**
- HTTP Status: `200`
- Response có `result.transactionId` và `result.bookingId`
- `result.status` = `PENDING` (vì MoMo là async)
- `result.payUrl` chứa link thanh toán MoMo

**Kết quả mẫu:**
```json
{
  "code": 200,
  "message": "MoMo payment initiated, awaiting customer confirmation",
  "result": {
    "transactionId": "txn_a1b2c3d4e5f6",
    "bookingId": "B1746987601234",
    "status": "PENDING",
    "payUrl": "https://test-payment.momo.vn/...",
    "paymentMethod": "MOMO",
    "amount": 50000
  }
}
```

**Ghi chú:** Copy `transactionId` từ response để dùng cho các test tiếp theo. Biến `lastTransactionId` sẽ được auto-set bởi test script.

---

### TC-02: Mở link MoMo để thanh toán

**Mục đích:** Thực hiện thanh toán thật trên sandbox MoMo.

**Cách thực hiện:**
1. Copy giá trị `payUrl` từ response TC-01
2. Dán vào trình duyệt
3. Quét QR hoặc nhấn **"Thanh toán"** trên trang MoMo sandbox

**Lưu ý:** Đây là môi trường test của MoMo, KHÔNG dùng tài khoản MoMo thật.

---

### TC-03: Giả lập MoMo Webhook (IPN Success)

**Mục đích:** Giả lập việc MoMo gọi về webhook báo thanh toán thành công.

> **Giải thích IPN:** Khi khách thanh toán xong, MoMo sẽ gọi API `/api/payments/momo/ipn` để báo kết quả. Vì đang test local, ta cần tự gọi endpoint này để mô phỏng.

**Trước khi gọi:**
1. Lấy `transactionId` từ TC-01 (đã auto-set vào `{{orderId}}` bởi pre-request script)
2. **Tính signature MoMo IPN** (xem phần 3 bên dưới)
3. Thay `signature` trong body bằng giá trị đã tính

**Cách thực hiện:**
1. Chọn request **"2. MoMo Webhook Simulator (IPN Success)"**
2. Trong body, sửa `orderId` = `txn_a1b2c3d4e5f6` (lấy từ TC-01)
3. Sửa `signature` = giá trị HMAC-SHA256 đã tính (xem phần 3)
4. Nhấn **Send**

**Expected:**
```json
{
  "partnerCode": "MOMOLRJZ20181206",
  "orderId": "txn_a1b2c3d4e5f6",
  "requestId": "req_...",
  "resultCode": 0,
  "message": " Successful."
}
```

**Sau đó xác nhận:**
- Gọi **TC-04** để kiểm tra `status` đã chuyển thành `SUCCESS`

---

### TC-04: Kiểm tra trạng thái thanh toán (by Booking ID)

**Mục đích:** Xác nhận giao dịch đã được cập nhật trạng thái thành công.

**Cách thực hiện:**
1. Chọn request **"3. Get Payment by Booking ID"**
2. Biến `{{lastBookingId}}` đã được auto-set từ TC-01
3. Nhấn **Send**

**Expected sau TC-03:**
```json
{
  "code": 200,
  "result": {
    "transactionId": "txn_a1b2c3d4e5f6",
    "bookingId": "B1746987601234",
    "status": "SUCCESS",
    "amount": 50000,
    "gatewayTransactionId": "27082023"
  }
}
```

---

### TC-05: Giả lập MoMo IPN thất bại

**Mục đích:** Xác nhận hệ thống xử lý đúng khi khách từ chối thanh toán.

**Cách thực hiện:**
1. Tạo payment mới (TC-01)
2. Chọn request **"2b. MoMo IPN Simulator - Failure"**
3. Sửa `orderId` và `signature` tương tự TC-03
4. `resultCode: 1006` = khách từ chối
5. Nhấn **Send**

**Expected:**
```json
{
  "resultCode": 1006,
  "message": "Transaction declined by customer."
}
```

**Sau đó xác nhận:**
- Gọi **TC-04** → `status` = `FAILED`

---

### TC-06: Test Idempotency (chống trùng lặp)

**Mục đích:** Xác nhận cùng một `idempotencyKey` không tạo nhiều giao dịch.

**Cách thực hiện:**
1. Tạo payment (TC-01)
2. Gửi lại request **"4. Idempotency Test - Duplicate Charge"** (giữ nguyên `idempotencyKey`)
3. Nhấn **Send**

**Expected:**
- HTTP 200
- `result.transactionId` TRÙNG với transaction từ TC-01
- Không tạo giao dịch mới

---

### TC-07: Test Validation (thiếu trường bắt buộc)

**Mục đích:** Xác nhận API reject request không hợp lệ.

**Cách thực hiện:**
1. Chọn request **"5. Validation Test - Missing Required Fields"**
2. Nhấn **Send**

**Expected:**
- HTTP 400 hoặc 422
- Response chứa thông báo validation lỗi

---

### TC-08: Thanh toán CASH (đồng bộ)

**Mục đích:** Xác nhận thanh toán tiền mặt xử lý đồng bộ (không qua MoMo).

**Cách thực hiện:**
1. Chọn request **"6. Payment with CASH Method"**
2. Nhấn **Send**

**Expected:**
- HTTP 200
- `result.status` = `SUCCESS` **NGAY LẬP TỨC** (không phải `PENDING`)
- Không có `payUrl`

---

## 3. Cách tính MoMo IPN Signature

MoMo yêu cầu signature HMAC-SHA256 (hex output) để xác thực IPN request là thật từ MoMo.

### 3.1 Công thức

```
Raw data = accessKey={accessKey}&amount={amount}&extraData={extraData}&orderId={orderId}&orderInfo={orderInfo}&partnerCode={partnerCode}&requestId={requestId}&resultCode={resultCode}

Signature = HMAC-SHA256(Raw data, SecretKey)
           → Output dạng HEX STRING (không phải Base64)
```

### 3.2 Ví dụ tính tay

**Input:**
```
accessKey    = MTCKt9W3eU1m39TW
amount       = 50000
extraData    = (empty)
orderId      = txn_a1b2c3d4e5f6
orderInfo    = Thanh toan chuyen xe CAB
partnerCode  = MOMOLRJZ20181206
requestId    = req_123456
resultCode   = 0
secretKey    = SetA5RDnLHvt51AULf51DyauxUo3kDU6
```

**Raw data:**
```
MTCKt9W3eU1m39TW&50000&&txn_a1b2c3d4e5f6&Thanh toan chuyen xe CAB&MOMOLRJZ20181206&req_123456&0
```

**Tool online để tính:**
- https://www.labnol.org/code/25927-hmac-sha256-generator
- https://codebeautify.org/hmac-generator

> **Input format:** Chọn `HMAC-SHA256`, nhập secret key, dán raw data → Output là hex string (ví dụ: `a1b2c3d4e5f6...`)

### 3.3 Debug tip

Trong request **"2. MoMo Webhook Simulator"**, tab **Tests** sẽ log raw data ra console. Copy raw data đó sang tool online để verify.

---

## 4. Test với Ngrok (IPN thật từ MoMo)

Nếu muốn MoMo gọi webhook thật (thay vì giả lập):

### 4.1 Khởi tạo Ngrok
```bash
ngrok http 8088
```

### 4.2 Copy forwarding URL
```
https://xxxx.ngrok-free.app
```

### 4.3 Cập nhật application.yaml
```yaml
momo:
  notifyUrl: https://xxxx.ngrok-free.app/api/payments/momo/ipn
```

### 4.4 Restart service và test
1. Tạo payment (TC-01)
2. Mở `payUrl` trong trình duyệt
3. Thanh toán trên MoMo
4. MoMo sẽ tự gọi về `/api/payments/momo/ipn` thông qua Ngrok
5. Kiểm tra trạng thái trong DB

---

## 5. Đọc logs khi test

```bash
# Xem logs Payment Service
cd Payment-Service
mvn spring-boot:run 2>&1 | grep -E "\[Saga\]|\[MoMo\]|\[Consumer\]"

# Hoặc log chi tiết MoMo
mvn spring-boot:run 2>&1 | grep -i momo
```

**Keyword quan trọng cần xem trong log:**

| Log | Ý nghĩa |
|---|---|
| `[Saga] Starting payment saga` | Bắt đầu xử lý payment |
| `[Saga] MoMo payment PENDING` | MoMo order tạo thành công, chờ IPN |
| `[Saga] MoMo payment SUCCESS from IPN` | IPN thành công |
| `[MoMo IPN] Invalid signature` | Signature không đúng |
| `[Producer] payment.completed sent` | Kafka event đã publish |

---

## 6. Troubleshooting

### Lỗi "Invalid signature"
- Kiểm tra lại công thức tính signature (hex, không phải Base64)
- Đảm bảo `secretKey` đúng: `SetA5RDnLHvt51AULf51DyauxUo3kDU6`
- `extraData` trong signature phải khớp với body request

### Lỗi "Connection refused" khi gọi MoMo
- Kiểm tra internet
- MoMo sandbox có thể tạm ngừng → thử lại sau

### Payment vẫn ở trạng thái PENDING
- IPN chưa được gọi → làm TC-03 hoặc mở `payUrl` trên trình duyệt
- Kiểm tra MoMo đã xử lý giao dịch chưa

### Lỗi "Payment not found" khi query
- Booking ID / Transaction ID không đúng
- Dùng đúng giá trị từ response TC-01

---

## 7. Cấu trúc Test Flow hoàn chỉnh

```
┌─────────────────────────────────────────┐
│  TC-01: POST /api/payments/charge       │──► status: PENDING
│  (tạo MoMo payment)                    │    payUrl → mở trình duyệt
└────────────────┬────────────────────────┘
                 │
    ┌────────────┴────────────┐
    │                         │
    ▼                         ▼
┌──────────┐          ┌───────────────────┐
│TC-03     │          │ Mở payUrl trên   │
│IPN mock  │          │ trình duyệt      │
│result=0  │          │ (MoMo gọi IPN)   │
└────┬─────┘          └────────┬──────────┘
     │                          │
     └──────────┬───────────────┘
                ▼
         ┌──────────────┐
         │ TC-04: Query│──► status: SUCCESS
         │ by BookingID │
         └──────────────┘

Fallback flows:
  TC-05: IPN mock (result=1006) → FAILED
  TC-06: Idempotency           → Same txnId
  TC-07: Validation error      → 400/422
  TC-08: CASH payment         → SUCCESS (sync)
```
