# Booking Integration Guide - QuoteId + QuotePayloadHash

Tai lieu nay huong dan `Booking-Service` tich hop co che doi soat gia voi `Pricing-Service` de dam bao gia khong bi sua truoc khi tao booking.

## 1. Muc tieu bao mat

`Booking-Service` chi duoc tao booking khi:
- Quote con hop le (`PENDING`, chua het han).
- `quotePayloadHash` do booking luu luc lay quote khop voi hash pricing dang luu.

Neu hash khong khop, `Pricing-Service` tra loi `QUOTE_HASH_MISMATCH` va booking phai chan tao don.

## 2. Du lieu Pricing tra ve khi estimate

API: `POST /api/pricing/estimate`

Response da co them:
- `estimateId`
- `quoteId`
- `quotePayloadHash`
- `quoteHashAlgorithm` (hien tai: `SHA-256`)

Vi du:

```json
{
  "estimateId": "5c8f3e8f-9f16-4c27-9db9-90f0f44aa001",
  "quoteId": "5c8f3e8f-9f16-4c27-9db9-90f0f44aa001",
  "quotePayloadHash": "6ef2c8c1f8e8d1f8c86e4bb2a2df26d9ef67a4d9f96df24d17bd98d46a7d0b48",
  "quoteHashAlgorithm": "SHA-256",
  "totalFare": 128000.00,
  "currency": "VND",
  "expiresAt": "2026-05-21T16:30:00"
}
```

## 3. Booking phai luu gi

Khi user chon gia, `Booking-Service` can luu it nhat:
- `estimateId`
- `quoteId`
- `quotePayloadHash`
- `quoteHashAlgorithm`
- `expiresAt`
- `userId` (hoac principal)

Khuyen nghi: luu trong bang/collection `booking_quote_snapshot` (hoac truong tuong duong trong aggregate booking draft).

## 4. Flow confirm bat buoc

### Buoc 1: Lay quote

`Booking-Service` goi `POST /api/pricing/estimate`, nhan du lieu quote va luu snapshot.

### Buoc 2: Confirm quote truoc khi tao booking

Goi:
- `POST /api/pricing/confirm/{estimateId}`
- Header bat buoc: `X-Quote-Hash: <quotePayloadHash da luu o buoc 1>`

Vi du:

```http
POST /api/pricing/confirm/5c8f3e8f-9f16-4c27-9db9-90f0f44aa001
X-Quote-Hash: 6ef2c8c1f8e8d1f8c86e4bb2a2df26d9ef67a4d9f96df24d17bd98d46a7d0b48
```

### Buoc 3: Chi tao booking khi confirm thanh cong

Neu pricing tra `200 OK`, moi tao booking voi gia da duoc lock.

## 5. Xu ly loi bat buoc phia Booking

### `QUOTE_HASH_MISMATCH`

Y nghia: quote o booking khac quote pricing dang luu (nguy co sua payload/replay sai ngu canh).

Booking phai:
- Khong tao booking.
- Ghi security log muc canh bao.
- Tra loi ro cho client: can lay bao gia moi.

### `ESTIMATE_EXPIRED`

Booking phai:
- Khong tao booking.
- Yeu cau client goi estimate lai.

### `INVALID_STATUS`

Thuong do quote da confirm/cancel truoc do.

Booking phai:
- Khong tao booking.
- Dong bo trang thai UI va yeu cau lay quote moi neu can.

### `ESTIMATE_NOT_FOUND`

Booking phai:
- Khong tao booking.
- Yeu cau tao quote moi.

## 6. Pseudocode tich hop (Booking)

```text
estimate = pricing.estimate(request)
saveSnapshot(estimateId, quoteId, quotePayloadHash, expiresAt, userId)

snapshot = loadSnapshot(estimateId, userId)
confirmResponse = pricing.confirm(
  estimateId = snapshot.estimateId,
  headers = { "X-Quote-Hash": snapshot.quotePayloadHash }
)

if confirmResponse.success:
  createBooking(totalFare = confirmResponse.totalFare, currency = confirmResponse.currency, quoteId = snapshot.quoteId)
else:
  rejectBookingAndReturnBusinessError(confirmResponse.errorCode)
```

## 7. Checklist trien khai nhanh cho Booking team

- [ ] Luu `quotePayloadHash` khi goi estimate.
- [ ] Truyen header `X-Quote-Hash` khi confirm.
- [ ] Chan tao booking neu confirm fail.
- [ ] Mapping day du cac loi: `QUOTE_HASH_MISMATCH`, `ESTIMATE_EXPIRED`, `INVALID_STATUS`, `ESTIMATE_NOT_FOUND`.
- [ ] Ghi audit log co `estimateId`, `quoteId`, `userId`, `errorCode`.

## 8. Luu y quan trong

- Co che hash nay ho tro doi soat va phat hien sai lech du lieu quote.
- De chong gia mao nguon goi service tot hon, nen ket hop them chu ky (HMAC/JWS) va/hoac mTLS giua cac service.
