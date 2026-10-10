# Stops everything started by start-all.ps1.
$root = $PSScriptRoot
$pidFile = Join-Path $root '.pids'

if (Test-Path $pidFile) {
    Get-Content $pidFile | ForEach-Object {
        if ($_ -match '^\d+$') {
            # /T kills the whole process tree (gradlew -> gradle client -> the Spring Boot JVM).
            & taskkill /PID $_ /T /F 2>$null | Out-Null
        }
    }
    Remove-Item $pidFile -ErrorAction SilentlyContinue
}

# The Gradle client may hand the app over to a JVM outside that tree; stop any leftover Spring Boot service.
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'br\.com\.fiap\.delivery' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Push-Location $root; docker compose down; Pop-Location
} else {
    & wsl --cd "$root" -e bash -lc "docker compose down"
}

& (Join-Path $root 'gradlew.bat') --stop | Out-Null
Write-Host "All services stopped." -ForegroundColor Green
