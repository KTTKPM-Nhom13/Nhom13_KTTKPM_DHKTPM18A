$headers = @{
    "Content-Type" = "application/json"
}

$timestamp = (Get-Date -Format "yyyyMMddHHmmss")
$idemKey = "momo-test-$timestamp"

Write-Host "=== Test: Create MoMo Payment ===" -ForegroundColor Cyan

$body = @{
    bookingId = "B_MOMO_$timestamp"
    customerId = "C001"
    amount = 50000
    currency = "VND"
    paymentMethod = "MOMO"
    description = "Test MoMo CAB Booking"
    idempotencyKey = $idemKey
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/charge" `
        -Method Post -Headers $headers -Body $body -ErrorAction Stop

    $response | ConvertTo-Json -Depth 5

    $status = $response.result.status
    $payUrl = $response.result.payUrl
    $momoOrderId = $response.result.momoOrderId
    $bookingId = $response.result.bookingId
    $transactionId = $response.result.transactionId

    Write-Host "`n=== Result Summary ===" -ForegroundColor Yellow
    Write-Host "Status: $status"
    Write-Host "TransactionId: $transactionId"
    Write-Host "BookingId: $bookingId"
    Write-Host "MoMoOrderId: $momoOrderId"

    if ($payUrl) {
        Write-Host "`n=== MoMo Pay URL (open in browser) ===" -ForegroundColor Green
        Write-Host $payUrl
        Write-Host "`n1. Copy URL above and open in browser" -ForegroundColor Cyan
        Write-Host "2. Complete payment on MoMo sandbox" -ForegroundColor Cyan
        Write-Host "3. MoMo will call IPN webhook" -ForegroundColor Cyan
        Write-Host "4. Check payment status with GET /api/payments/booking/$bookingId" -ForegroundColor Cyan

        # Save for IPN test
        $script:LAST_BOOKING_ID = $bookingId
        $script:LAST_MOMO_ORDER_ID = $momoOrderId
        $script:LAST_TRANSACTION_ID = $transactionId
    } else {
        Write-Host "`nNo payUrl returned - Status: $status" -ForegroundColor Yellow
    }

    if ($status -eq "PENDING") {
        Write-Host "`nWaiting 5 seconds before querying payment status..." -ForegroundColor Cyan
        Start-Sleep -Seconds 5

        Write-Host "`n=== Test: Get Payment by Booking ID ===" -ForegroundColor Cyan
        $getResponse = Invoke-RestMethod -Uri "http://localhost:8088/api/payments/booking/$bookingId" -Method Get
        $getResponse | ConvertTo-Json -Depth 3
    }

} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    $ex = $_.Exception.Response
    if ($null -ne $ex) {
        $stream = $ex.GetResponseStream()
        $sr = New-Object System.IO.StreamReader($stream)
        Write-Host $sr.ReadToEnd() -ForegroundColor Red
    }
}

Write-Host "`n=== Manual IPN Test ===" -ForegroundColor Yellow
Write-Host "After paying via MoMo app, you can manually trigger IPN:"
Write-Host "POST https://scratch-heaving-create.ngrok-free.dev/api/payments/momo/ipn"
Write-Host "Body: {`"partnerCode`":`"MOMOLRJZ20181206`", `"orderId`":`"<momoOrderId>`", `"resultCode`":0, ...}"
