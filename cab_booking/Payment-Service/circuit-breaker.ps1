# =============================================================================
# Circuit Breaker Management Script for Payment Service
# =============================================================================

param(
    [string]$Action = "status",
    [string]$Name = "paymentGateway",
    [string]$State = "CLOSED",
    [string]$BaseUrl = "http://localhost:8088"
)

$ErrorActionPreference = "Stop"

function Get-CircuitBreakerStatus {
    param([string]$Name)
    
    $url = "$BaseUrl/api/admin/circuit-breaker/status/$Name"
    Write-Host "`n=== Circuit Breaker Status: $Name ===" -ForegroundColor Cyan
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method GET -ContentType "application/json"
        Write-Host "State         : " -NoNewline; Write-Host $response.state -ForegroundColor $(if ($response.state -eq "CLOSED") { "Green" } elseif ($response.state -eq "OPEN") { "Red" } else { "Yellow" })
        Write-Host "Failure Rate  : $($response.failureRate)%"
        Write-Host "Buffered Calls: $($response.numberOfBufferedCalls)"
        Write-Host "Failed Calls  : $($response.numberOfFailedCalls)"
        Write-Host "Success Calls : $($response.numberOfSuccessfulCalls)"
        Write-Host "Not Permitted : $($response.numberOfNotPermittedCalls)"
        return $response
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Get-AllCircuitBreakersStatus {
    $url = "$BaseUrl/api/admin/circuit-breaker/status"
    Write-Host "`n=== All Circuit Breakers Status ===" -ForegroundColor Cyan
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method GET -ContentType "application/json"
        $response.circuitBreakers | ForEach-Object {
            $name = $_.Key
            $state = $_.Value
            Write-Host "$name : " -NoNewline
            $color = if ($state -eq "CLOSED") { "Green" } elseif ($state -eq "OPEN") { "Red" } else { "Yellow" }
            Write-Host $state -ForegroundColor $color
        }
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Reset-CircuitBreaker {
    param([string]$Name)
    
    $url = "$BaseUrl/api/admin/circuit-breaker/reset/$Name"
    Write-Host "`n=== Resetting Circuit Breaker: $Name ===" -ForegroundColor Cyan
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method POST -ContentType "application/json"
        if ($response.message -match "successfully") {
            Write-Host "Previous State: $($response.previousState)" -ForegroundColor Yellow
            Write-Host "Current State : " -NoNewline; Write-Host $response.currentState -ForegroundColor Green
            Write-Host "Message       : $($response.message)" -ForegroundColor Green
        } else {
            Write-Host "Error: $($response.error)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Transition-CircuitBreaker {
    param(
        [string]$Name,
        [string]$State
    )
    
    $url = "$BaseUrl/api/admin/circuit-breaker/transition/$Name?state=$State"
    Write-Host "`n=== Transitioning Circuit Breaker: $Name -> $State ===" -ForegroundColor Cyan
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method POST -ContentType "application/json"
        if ($response.message -match "successfully") {
            Write-Host "Previous State: $($response.previousState)" -ForegroundColor Yellow
            Write-Host "Current State : " -NoNewline; Write-Host $response.currentState -ForegroundColor Green
            Write-Host "Message       : $($response.message)" -ForegroundColor Green
        } else {
            Write-Host "Error: $($response.error)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Get-ActuatorHealth {
    $url = "$BaseUrl/actuator/health"
    Write-Host "`n=== Actuator Health ===" -ForegroundColor Cyan
    
    try {
        $response = Invoke-RestMethod -Uri $url -Method GET -ContentType "application/json"
        Write-Host "Status: " -NoNewline
        Write-Host $response.status -ForegroundColor $(if ($response.status -eq "UP") { "Green" } else { "Red" })
        
        if ($response.components) {
            Write-Host "`nComponents:"
            $response.components | ForEach-Object {
                $compName = $_.Key
                $compValue = $_.Value
                $compStatus = $compValue.status
                Write-Host "  $compName : " -NoNewline
                $color = if ($compStatus -eq "UP") { "Green" } elseif ($compStatus -eq "DOWN") { "Red" } else { "Yellow" }
                Write-Host $compStatus -ForegroundColor $color
                
                if ($compValue.details -and $compValue.details.circuitBreakers) {
                    $cbs = $compValue.details.circuitBreakers
                    $cbs.PSObject.Properties | ForEach-Object {
                        $cbName = $_.Name
                        $cbData = $_.Value
                        Write-Host "    Circuit Breaker '$cbName':"
                        Write-Host "      State       : $($cbData.state)"
                        Write-Host "      Failure Rate: $($cbData.failureRate)"
                    }
                }
            }
        }
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Main script logic
switch ($Action.ToLower()) {
    "status" {
        if ($Name) {
            Get-CircuitBreakerStatus -Name $Name
        } else {
            Get-AllCircuitBreakersStatus
        }
    }
    "all" {
        Get-AllCircuitBreakersStatus
        if ($Name) {
            Get-CircuitBreakerStatus -Name $Name
        }
    }
    "reset" {
        Reset-CircuitBreaker -Name $Name
    }
    "transition" {
        Transition-CircuitBreaker -Name $Name -State $State
    }
    "health" {
        Get-ActuatorHealth
    }
    "monitor" {
        Write-Host "=== Monitoring Circuit Breaker '$Name' - Press Ctrl+C to stop ===" -ForegroundColor Yellow
        while ($true) {
            Clear-Host
            Get-CircuitBreakerStatus -Name $Name
            Start-Sleep -Seconds 5
        }
    }
    default {
        Write-Host "Usage: .\circuit-breaker.ps1 -Action <action> [-Name <name>] [-State <state>]" -ForegroundColor Yellow
        Write-Host "`nActions:" -ForegroundColor Cyan
        Write-Host "  status     - Get status of a circuit breaker (default: paymentGateway)"
        Write-Host "  all        - Get status of all circuit breakers"
        Write-Host "  reset      - Reset a circuit breaker to CLOSED state"
        Write-Host "  transition - Transition circuit breaker to specific state"
        Write-Host "  health     - Get actuator health with circuit breaker details"
        Write-Host "  monitor    - Monitor circuit breaker continuously (5s interval)"
        Write-Host "`nExamples:" -ForegroundColor Cyan
        Write-Host "  .\circuit-breaker.ps1 -Action status"
        Write-Host "  .\circuit-breaker.ps1 -Action status -Name paymentGateway"
        Write-Host "  .\circuit-breaker.ps1 -Action reset -Name paymentGateway"
        Write-Host "  .\circuit-breaker.ps1 -Action transition -Name paymentGateway -State HALF_OPEN"
        Write-Host "  .\circuit-breaker.ps1 -Action health"
        Write-Host "  .\circuit-breaker.ps1 -Action monitor -Name paymentGateway"
    }
}
