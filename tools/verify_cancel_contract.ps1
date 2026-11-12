# Verificacion manual del endpoint DELETE /api/v1/orders/{orderId} contra la API en ejecucion.
# Uso:  powershell -File tools/verify_cancel_contract.ps1
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Account = "10001",
    [string]$OtherAccount = "10002"
)

$ErrorActionPreference = "Stop"
$results = @()

function Invoke-Order {
    param([string]$Body, [string]$AccountNumber = $Global:account)
    $headers = @{
        "X-AccountNumber" = $AccountNumber
        "Idempotency-Key"  = "verify-$([guid]::NewGuid())"
    }
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/orders" -Method POST -Headers $headers `
            -ContentType "application/json" -Body $Body -UseBasicParsing -ErrorAction Stop
        return @{ Status = [int]$response.StatusCode; Body = ($response.Content | ConvertFrom-Json) }
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        return @{ Status = $status; Body = ($reader.ReadToEnd() | ConvertFrom-Json) }
    }
}

function Invoke-Cancel {
    param([long]$OrderId, [string]$AccountNumber = $Global:account, [switch]$NoAccount)
    $headers = @{}
    if (-not $NoAccount) { $headers["X-AccountNumber"] = $AccountNumber }
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/orders/$OrderId" -Method DELETE -Headers $headers `
            -UseBasicParsing -ErrorAction Stop
        return @{ Status = [int]$response.StatusCode; Body = ($response.Content | ConvertFrom-Json) }
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        return @{ Status = $status; Body = ($reader.ReadToEnd() | ConvertFrom-Json) }
    }
}

function Get-Portfolio {
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/portfolio" `
            -Headers @{ "X-AccountNumber" = $Global:account } -UseBasicParsing -ErrorAction Stop
        return @{ Status = [int]$response.StatusCode; Body = ($response.Content | ConvertFrom-Json) }
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        return @{ Status = $status; Body = ($reader.ReadToEnd() | ConvertFrom-Json) }
    }
}

function Assert-Case {
    param([string]$Name, [bool]$Condition, [string]$Detail)
    $script:results += [PSCustomObject]@{
        Caso      = $Name
        Resultado = if ($Condition) { "OK" } else { "FALLO" }
        Detalle   = $Detail
    }
}

$Global:account = $Account

# --- Datos de prueba: efectivo + orden limite pendiente ---
Invoke-Order '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":100000.00}' | Out-Null
$limit = Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":10000.00}'
$limitId = $limit.Body.id

# 1. Cancelar una orden pendiente devuelve 200 con CANCELLED
$cancel = Invoke-Cancel $limitId
Assert-Case "Cancelar una orden pendiente devuelve 200 con CANCELLED" `
    ($cancel.Status -eq 200 -and $cancel.Body.status -eq "CANCELLED" -and $cancel.Body.id -eq $limitId) `
    "status=$($cancel.Status) estado=$($cancel.Body.status)"

# 2. Cancelar de nuevo la misma orden devuelve 409
$again = Invoke-Cancel $limitId
Assert-Case "Cancelar una orden ya cancelada devuelve 409" ($again.Status -eq 409) "status=$($again.Status)"

# 3. Cancelar una orden inexistente devuelve 404
$unknown = Invoke-Cancel 999999
Assert-Case "Cancelar una orden inexistente devuelve 404" ($unknown.Status -eq 404) "status=$($unknown.Status)"

# 4. Cancelar una orden de otra cuenta devuelve 404
$other = Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}' $OtherAccount
$otherCancel = Invoke-Cancel $other.Body.id $Account
Assert-Case "Cancelar una orden de otra cuenta devuelve 404" `
    ($otherCancel.Status -eq 404) "status=$($otherCancel.Status)"

# 5. Sin cabecera de cuenta devuelve 400
$noHeader = Invoke-Cancel $limitId -NoAccount
Assert-Case "Sin cabecera de cuenta devuelve 400" ($noHeader.Status -eq 400) "status=$($noHeader.Status)"

# 6. Cancelar una orden ejecutada devuelve 409
$filled = Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}'
$filledCancel = Invoke-Cancel $filled.Body.id
Assert-Case "Cancelar una orden ejecutada devuelve 409" ($filledCancel.Status -eq 409) "status=$($filledCancel.Status)"

# 7. Cancelar una transferencia devuelve 409
$transfer = Invoke-Order '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":1000.00}'
$transferCancel = Invoke-Cancel $transfer.Body.id
Assert-Case "Cancelar una transferencia devuelve 409" ($transferCancel.Status -eq 409) "status=$($transferCancel.Status)"

# 8. La cancelacion libera el efectivo reservado
$before = Get-Portfolio
Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":30000.00}' | Out-Null
$reserved = Get-Portfolio
$pendingId = (Invoke-RestMethod -Uri "$BaseUrl/api/v1/orders" -Headers @{ "X-AccountNumber" = $Account } `
    -Method Get).content[0].id
Invoke-Cancel $pendingId | Out-Null
$after = Get-Portfolio

Assert-Case "Cancelar una compra limite libera el efectivo reservado" `
    ($reserved.Body.reservedCash -eq 30000.00 -and $after.Body.reservedCash -eq 0.00 `
        -and [Math]::Abs($after.Body.availableCash - $before.Body.availableCash) -lt 0.01) `
    "reservado antes=$($reserved.Body.reservedCash) despues=$($after.Body.reservedCash) disponible=$($after.Body.availableCash)"

# 9. La orden cancelada aparece como CANCELLED en el historial
$history = Invoke-RestMethod -Uri "$BaseUrl/api/v1/orders?status=CANCELLED" `
    -Headers @{ "X-AccountNumber" = $Account } -Method Get
Assert-Case "La orden cancelada aparece como CANCELLED en el historial" `
    ($history.totalElements -ge 2) "total=$($history.totalElements)"

$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Detalle)" }
    exit 1
}
