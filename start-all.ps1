# Starts the whole system: RabbitMQ (docker compose, via WSL or Docker Desktop), Eureka, 2x payment, review and order.
# Logs go to ./logs/<service>.log. Stop everything with stop-all.bat.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$logs = Join-Path $root 'logs'
$pidFile = Join-Path $root '.pids'
New-Item -ItemType Directory -Force $logs | Out-Null
Remove-Item $pidFile -ErrorAction SilentlyContinue

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', $port)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-Port([string]$name, [int]$port, [int]$timeoutSec, $process) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $port) {
            Write-Host ("  [OK]  {0} is up on port {1}" -f $name, $port) -ForegroundColor Green
            return
        }
        if ($process -and $process.HasExited) {
            Write-Host "  [ERR] $name exited early. Last log lines:" -ForegroundColor Red
            Get-Content (Join-Path $logs "$name.log") -Tail 15 -ErrorAction SilentlyContinue
            Get-Content (Join-Path $logs "$name.err.log") -Tail 15 -ErrorAction SilentlyContinue
            throw "$name failed to start"
        }
        Start-Sleep -Seconds 2
    }
    throw "$name did not open port $port within $timeoutSec s (see logs\$name.log)"
}

function Start-Delivery([string]$name, [string]$gradleArgs) {
    Write-Host "Starting $name ..."
    $p = Start-Process -FilePath (Join-Path $root 'gradlew.bat') -ArgumentList $gradleArgs `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logs "$name.log") `
        -RedirectStandardError (Join-Path $logs "$name.err.log")
    Add-Content $pidFile $p.Id
    return $p
}

# 0. Compile once (downloads dependencies / the Java 25 toolchain on the first run) so the services don't race.
Write-Host "Compiling projects (first run may take a few minutes) ..."
& (Join-Path $root 'gradlew.bat') classes --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

# 1. RabbitMQ. A hidden WSL session is kept open so the distro (and Docker inside it) does not shut down.
if (Test-Port 5672) {
    Write-Host "RabbitMQ already running on 5672."
} else {
    Write-Host "Starting RabbitMQ ..."
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $p = Start-Process docker -ArgumentList 'compose up' -WorkingDirectory $root -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logs 'rabbitmq.log') -RedirectStandardError (Join-Path $logs 'rabbitmq.err.log')
    } else {
        $p = Start-Process wsl -ArgumentList @('--cd', "`"$root`"", '-e', 'bash', '-lc', '"docker compose up"') -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logs 'rabbitmq.log') -RedirectStandardError (Join-Path $logs 'rabbitmq.err.log')
    }
    Add-Content $pidFile $p.Id
    Wait-Port 'rabbitmq' 5672 180 $p
}

# 2. Eureka must be up before the other services register.
$eureka = Start-Delivery 'eureka-server' ':eureka-server:bootRun --console=plain'
Wait-Port 'eureka-server' 8761 240 $eureka

# 3. Remaining services in parallel.
$pay1 = Start-Delivery 'payment-service-8081' ':payment-service:bootRun --console=plain'
$pay2 = Start-Delivery 'payment-service-8082' ':payment-service:bootRun --console=plain --args="--server.port=8082"'
$review = Start-Delivery 'review-service' ':review-service:bootRun --console=plain'
$order = Start-Delivery 'order-service' ':order-service:bootRun --console=plain'
Wait-Port 'payment-service-8081' 8081 240 $pay1
Wait-Port 'payment-service-8082' 8082 240 $pay2
Wait-Port 'review-service' 8083 240 $review
Wait-Port 'order-service' 8080 240 $order

# Let the order-service learn about both payment instances (Eureka fetch + load balancer cache) before declaring success.
Write-Host "Waiting for the order-service to discover the payment instances ..."
$deadline = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    try {
        $apps = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps/PAYMENT-SERVICE" -Headers @{ Accept = "application/json" } -UseBasicParsing
        if (@($apps.application.instance).Count -ge 2) { break }
    } catch { }
    Start-Sleep -Seconds 2
}
Start-Sleep -Seconds 8

Write-Host ""
Write-Host "All services are up:" -ForegroundColor Green
Write-Host "  Eureka:    http://localhost:8761"
Write-Host "  RabbitMQ:  http://localhost:15672 (guest/guest)"
Write-Host "  Orders:    http://localhost:8080/dishes"
Write-Host "  Ranking:   http://localhost:8083/reviews/ranking"
Write-Host "Logs in $logs. Stop everything with stop-all.bat"
