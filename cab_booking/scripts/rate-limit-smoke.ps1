param(
    [string]$GatewayBase = "http://localhost:8080",
    [int]$AuthBurstCount = 20,
    [int]$EtaBurstCount = 20,
    [switch]$AuthOnly,
    [switch]$EtaOnly
)

$ErrorActionPreference = "Stop"

function Invoke-StatusBurst {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Count,
        [Parameter(Mandatory = $true)][scriptblock]$RequestBlock
    )

    $ok = 0
    $tooMany = 0
    $other = 0
    $statusList = New-Object System.Collections.Generic.List[string]

    for ($i = 1; $i -le $Count; $i++) {
        try {
            $statusCode = & $RequestBlock
        }
        catch {
            $statusCode = "ERR"
        }

        $statusList.Add([string]$statusCode)

        if ($statusCode -eq 200 -or $statusCode -eq "200") {
            $ok++
        }
        elseif ($statusCode -eq 429 -or $statusCode -eq "429") {
            $tooMany++
        }
        else {
            $other++
        }
    }

    $blockedPct = if ($Count -gt 0) { [math]::Round(($tooMany * 100.0) / $Count, 2) } else { 0 }

    [PSCustomObject]@{
        endpoint = $Name
        total = $Count
        status_200 = $ok
        status_429 = $tooMany
        other = $other
        blocked_percent = $blockedPct
        statuses = ($statusList -join ",")
    }
}

function Test-AuthBurst {
    param(
        [string]$Base,
        [int]$Count
    )

    $uri = "$Base/api/auth/token"
    Invoke-StatusBurst -Name "/api/auth/token" -Count $Count -RequestBlock {
        try {
            $resp = Invoke-WebRequest -Uri $uri -Method Get -TimeoutSec 10
            return [int]$resp.StatusCode
        }
        catch {
            if ($_.Exception.Response) {
                return [int]$_.Exception.Response.StatusCode.value__
            }
            return "ERR"
        }
    }
}

function Test-EtaBurst {
    param(
        [string]$Base,
        [int]$Count
    )

    $uri = "$Base/eta/calculate"
    $payloadObj = @{
        pickupLocation = @{ lat = 10.7629; lng = 106.6604 }
        dropoffLocation = @{ lat = 10.8231; lng = 106.6297 }
    }
    $payload = $payloadObj | ConvertTo-Json -Depth 5 -Compress

    Invoke-StatusBurst -Name "/eta/calculate" -Count $Count -RequestBlock {
        try {
            $resp = Invoke-WebRequest -Uri $uri -Method Post -Body $payload -ContentType "application/json" -TimeoutSec 10
            return [int]$resp.StatusCode
        }
        catch {
            if ($_.Exception.Response) {
                return [int]$_.Exception.Response.StatusCode.value__
            }
            return "ERR"
        }
    }
}

Write-Host "=== Rate Limit Smoke Test ===" -ForegroundColor Cyan
Write-Host "Gateway: $GatewayBase" -ForegroundColor DarkCyan

$results = @()

if (-not $EtaOnly) {
    $results += Test-AuthBurst -Base $GatewayBase -Count $AuthBurstCount
}

if (-not $AuthOnly) {
    $results += Test-EtaBurst -Base $GatewayBase -Count $EtaBurstCount
}

$results | Format-Table endpoint, total, status_200, status_429, other, blocked_percent -AutoSize

Write-Host "\nRaw status sequence (for report evidence):" -ForegroundColor Yellow
$results | ForEach-Object {
    Write-Host ("- " + $_.endpoint + ": " + $_.statuses)
}

Write-Host "\nDone." -ForegroundColor Green
