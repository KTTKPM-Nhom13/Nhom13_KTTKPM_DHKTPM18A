$headers = @{
    "Content-Type" = "application/json"
}

$timestamp = (Get-Date -Format "yyyyMMddHHmmss")

Write-Host "=== Test 1: POST /api/payments/charge (MoMo) ==="
$idemKey1 = "momo-test-$timestamp"
$headers["X-Idempotency-Key"] = $idemKey1
$body1 = @{
    bookingId = "B_MOMO_$timestamp"
    customerId = "C001"
    amount = 50000
    currency = "VND"
    paymentMethod = "MOMO"
    description = "Test thanh toan MoMo CAB Booking"
    idempotencyKey = $idemKey1
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/charge" -Method Post -Headers $headers -Body $body1
    $response | ConvertTo-Json -Depth 5

    $bookingId = $response.result.bookingId
    $transactionId = $response.result.transactionId
    $payUrl = $response.result.payUrl
    $status = $response.result.status

    if ($payUrl) {
        Write-Host "`n=== MoMo Pay URL (copy to browser) ==="
        Write-Host $payUrl
    }

    if ($status -eq "PENDING") {
        Write-Host "`n=== Test 2: GET /api/payments/booking/$bookingId ==="
        $bookingResponse = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/booking/$bookingId" -Method Get
        $bookingResponse | ConvertTo-Json -Depth 5
    }
} catch {
    $ex = $_.Exception.Response
    Write-Host "Error Status: $([int]$ex.StatusCode)"
    $stream = $ex.GetResponseStream()
    $sr = New-Object System.IO.StreamReader($stream)
    Write-Host $sr.ReadToEnd()
}

Write-Host "`n=== Test 3: POST /api/payments/charge (CASH) ==="
$idemKey2 = "cash-test-$timestamp"
$headers["X-Idempotency-Key"] = $idemKey2
$cashBody = @{
    bookingId = "B_CASH_$timestamp"
    customerId = "C001"
    amount = 30000
    currency = "VND"
    paymentMethod = "CASH"
    description = "Thanh toan tien mat"
    idempotencyKey = $idemKey2
} | ConvertTo-Json

try {
    $cashResponse = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/charge" -Method Post -Headers $headers -Body $cashBody
    $cashResponse | ConvertTo-Json -Depth 5
} catch {
    $ex = $_.Exception.Response
    Write-Host "Error Status: $([int]$ex.StatusCode)"
    $stream = $ex.GetResponseStream()
    $sr = New-Object System.IO.StreamReader($stream)
    Write-Host $sr.ReadToEnd()
}

Write-Host "`n=== Test 4: Validation Test (Missing Fields) ==="
$headers["X-Idempotency-Key"] = "validation-test-$timestamp"
$invalidBody = @{
    bookingId = ""
    amount = 100
} | ConvertTo-Json

try {
    $validationResponse = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/charge" -Method Post -Headers $headers -Body $invalidBody -StatusCodeVariable sc -ErrorAction Stop
    Write-Host "Status: $sc"
    $validationResponse | ConvertTo-Json -Depth 5
} catch {
    $ex = $_.Exception.Response
    if ($null -ne $ex) {
        Write-Host "Validation Status: $([int]$ex.StatusCode)"
        $stream = $ex.GetResponseStream()
        $sr = New-Object System.IO.StreamReader($stream)
        Write-Host $sr.ReadToEnd()
    } else {
        Write-Host "Error: $($_.Exception.Message)"
    }
}

Write-Host "`n=== Test 5: Get Payment by Booking ID ==="
$headers.Remove("X-Idempotency-Key") | Out-Null
$cashBookingId = "B_CASH_$timestamp"
try {
    $getResponse = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/booking/$cashBookingId" -Method Get -Headers $headers
    $getResponse | ConvertTo-Json -Depth 5
} catch {
    $ex = $_.Exception.Response
    if ($null -ne $ex) {
        Write-Host "Status: $([int]$ex.StatusCode)"
        $stream = $ex.GetResponseStream()
        $sr = New-Object System.IO.StreamReader($stream)
        Write-Host $sr.ReadToEnd()
    }
}

Write-Host "`n=== Test 6: Idempotency Test ==="
$idemKey3 = "idem-test-$timestamp"
$headers["X-Idempotency-Key"] = $idemKey3
$idempBody1 = @{
    bookingId = "B_IDEM_$timestamp"
    customerId = "C001"
    amount = 20000
    currency = "VND"
    paymentMethod = "CASH"
    description = "Idempotency test"
    idempotencyKey = $idemKey3
} | ConvertTo-Json

try {
    $resp1 = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/charge" -Method Post -Headers $headers -Body $idempBody1 -ErrorAction Stop
    Write-Host "First call:"
    $resp1.result | ConvertTo-Json -Depth 3
    $txnId1 = $resp1.result.transactionId

    $resp2 = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/charge" -Method Post -Headers $headers -Body $idempBody1 -ErrorAction Stop
    Write-Host "`nSecond call (same idempotencyKey):"
    $resp2.result | ConvertTo-Json -Depth 3
    $txnId2 = $resp2.result.transactionId

    if ($txnId1 -eq $txnId2) {
        Write-Host "`n=== Idempotency Test PASSED: Same transactionId returned ===" -ForegroundColor Green
    } else {
        Write-Host "`n=== Idempotency Test FAILED: Different transactionId ===" -ForegroundColor Red
    }
} catch {
    Write-Host "Error: $($_.Exception.Message)"
}
