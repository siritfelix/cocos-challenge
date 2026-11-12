# Verificacion manual del contrato HTTP de POST /api/v1/orders contra la API en ejecucion.
# Uso:  powershell -File tools/verify_orders_contract.ps1
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$results = @()

function Invoke-Case {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [string]$Body,
        [int]$ExpectedStatus
    )

    $arguments = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $Headers
        UseBasicParsing = $true
        ErrorAction     = "Stop"
    }
    if ($Body) {
        $arguments["ContentType"] = "application/json"
        $arguments["Body"] = $Body
    }

    try {
        $response = Invoke-WebRequest @arguments
        $status = [int]$response.StatusCode
        $payload = $response.Content
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $payload = $reader.ReadToEnd()
    }

    $ok = $status -eq $ExpectedStatus
    $script:results += [PSCustomObject]@{
        Caso     = $Name
        Esperado = $ExpectedStatus
        Obtenido = $status
        Resultado = if ($ok) { "OK" } else { "FALLO" }
        Respuesta = $payload
    }
}

function New-Key { "verify-$([guid]::NewGuid())" }

$account = @{ "X-AccountNumber" = "10001" }

# 1. Cabecera de cuenta ausente -> 400
Invoke-Case "Sin cabecera de cuenta" "POST" "/api/v1/orders" @{ "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}' 400

# 2. Cabecera de idempotencia ausente -> 400
Invoke-Case "Sin cabecera Idempotency-Key" "POST" "/api/v1/orders" $account `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}' 400

# 3. Cuenta inexistente -> 404
Invoke-Case "Cuenta inexistente" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "99999"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}' 404

# 4. Instrumento inexistente -> 404
Invoke-Case "Instrumento inexistente" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"NOPE","side":"BUY","type":"MARKET","size":1}' 404

# 5. size y amount juntos -> 400
Invoke-Case "size y amount juntos" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1,"amount":100}' 400

# 6. Ni size ni amount -> 400
Invoke-Case "Sin size ni amount" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET"}' 400

# 7. Cantidad fraccionaria -> 400
Invoke-Case "Cantidad fraccionaria" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1.5}' 400

# 8. MARKET con precio -> 400
Invoke-Case "MARKET con precio" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1,"price":100}' 400

# 9. LIMIT sin precio -> 400
Invoke-Case "LIMIT sin precio" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1}' 400

# 10. Compra sin efectivo suficiente -> 201 + REJECTED
Invoke-Case "Compra sin efectivo" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10004"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":100000}' 201

# 11. Compra a mercado sin cotizacion -> 201 + REJECTED
Invoke-Case "Compra sin cotizacion" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"IRCP","side":"BUY","type":"MARKET","size":1}' 201

# 12. Compra por importe insuficiente -> 201 + REJECTED
Invoke-Case "Importe insuficiente" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","amount":1.00}' 201

# 13. Ingreso de efectivo -> 201 + FILLED
Invoke-Case "Ingreso de efectivo" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":50000}' 201

# 14. Transferencia con amount -> 400
Invoke-Case "Transferencia con amount" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","amount":100}' 400

# 15. Transferencia con precio -> 400
Invoke-Case "Transferencia con precio" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":100,"price":1}' 400

# 16. Compra a mercado -> 201 + FILLED
Invoke-Case "Compra a mercado" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = (New-Key) } `
    '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}' 201

# 17. Idempotencia: misma clave y mismo contenido -> mismo id
$key = New-Key
$body = '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}'
Invoke-Case "Idempotencia: primera llamada" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = $key } $body 201
Invoke-Case "Idempotencia: reintento" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = $key } $body 201

# 18. Idempotencia: misma clave, contenido distinto -> 409
Invoke-Case "Idempotencia: contenido distinto" "POST" "/api/v1/orders" `
    @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = $key } `
    '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":2,"price":100.00}' 409

$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Respuesta)" }
    exit 1
}
