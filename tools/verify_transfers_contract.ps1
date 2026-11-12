# Verificacion manual de las transferencias de efectivo contra la API en ejecucion.
# Uso:  powershell -File tools/verify_transfers_contract.ps1
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$results = @()

function Invoke-Case {
    param(
        [string]$Name,
        [string]$Body,
        [int]$ExpectedStatus,
        [string]$ExpectedStatusField = ""
    )

    $headers = @{
        "X-AccountNumber" = $Global:account
        "Idempotency-Key"  = "verify-$([guid]::NewGuid())"
    }

    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/orders" -Method POST -Headers $headers `
            -ContentType "application/json" -Body $Body -UseBasicParsing -ErrorAction Stop
        $status = [int]$response.StatusCode
        $payload = $response.Content
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $payload = $reader.ReadToEnd()
    }

    $ok = $status -eq $ExpectedStatus
    if ($ok -and $ExpectedStatusField) {
        $parsed = $payload | ConvertFrom-Json
        $ok = $parsed.status -eq $ExpectedStatusField
    }

    $script:results += [PSCustomObject]@{
        Caso      = $Name
        Esperado  = if ($ExpectedStatusField) { "$ExpectedStatus / $ExpectedStatusField" } else { "$ExpectedStatus" }
        Obtenido  = $status
        Resultado = if ($ok) { "OK" } else { "FALLO" }
        Respuesta = $payload
    }
}

# --- Ingreso de efectivo ---
$Global:account = "10001"
Invoke-Case "Ingreso de efectivo" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":25000.00}' 201 "FILLED"
Invoke-Case "Ingreso de efectivo sin precio" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":25000.00,"price":1}' 400
Invoke-Case "Ingreso con importe en vez de size" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","amount":25000.00}' 400
Invoke-Case "Ingreso de tipo LIMIT" '{"ticker":"ARS","side":"CASH_IN","type":"LIMIT","size":25000.00}' 400
Invoke-Case "Ingreso sobre instrumento no efectivo" '{"ticker":"YPFD","side":"CASH_IN","type":"MARKET","size":25000.00}' 400
Invoke-Case "Ingreso con importe cero" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":0}' 400

# --- Retiro de efectivo ---
$Global:account = "10001"
Invoke-Case "Retiro con saldo suficiente" '{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":5000.00}' 201 "FILLED"

$Global:account = "10004"
Invoke-Case "Retiro sin saldo" '{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":5000.00}' 201 "REJECTED"

# --- Verificacion de que el efectivo reservado no puede retirarse ---
$Global:account = "10003"
Invoke-Case "Fondo para prueba de reserva" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":100000.00}' 201 "FILLED"
Invoke-Case "Compra limite que reserva efectivo" '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":90000.00}' 201 "NEW"
Invoke-Case "Retiro de efectivo reservado" '{"ticker":"ARS","side":"CASH_OUT","type":"MARKET","size":50000.00}' 201 "REJECTED"

$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Respuesta)" }
    exit 1
}
