# Validacion end-to-end de specs/001-investment-portfolio-api/quickstart.md contra la API en marcha.
# Uso:  powershell -File tools/verify_quickstart.ps1   (tras docker compose up -d)
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$results = @()

function Invoke-Http {
    param([string]$Method, [string]$Uri, [hashtable]$Headers = @{}, [string]$Body = $null)
    $params = @{ Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true; ErrorAction = "Stop" }
    if ($Body) {
        # El Content-Type se pasa por el parametro propio de Invoke-WebRequest:
        # PS 5.1 rechaza setearlo a traves del diccionario de headers.
        $params["Body"] = $Body
        $params["ContentType"] = "application/json"
    }
    try {
        $response = Invoke-WebRequest @params
        return @{ Status = [int]$response.StatusCode; Body = ($response.Content | ConvertFrom-Json) }
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        return @{ Status = $status; Body = ($reader.ReadToEnd() | ConvertFrom-Json) }
    }
}

function Invoke-Order {
    param([string]$Account, [string]$Body)
    Invoke-Http POST "$BaseUrl/api/v1/orders" @{
        "X-AccountNumber" = $Account
        "Idempotency-Key" = "qs-$([guid]::NewGuid())"
    } $Body
}

function Assert-Case {
    param([string]$Name, [bool]$Condition, [string]$Detail)
    $script:results += [PSCustomObject]@{
        Caso      = $Name
        Resultado = if ($Condition) { "OK" } else { "FALLO" }
        Detalle   = $Detail
    }
}

# ============ 5.1 Identificacion de cuenta ============
$r1 = Invoke-Http GET "$BaseUrl/api/v1/portfolio"
Assert-Case "5.1 Sin cabecera X-AccountNumber devuelve 400" ($r1.Status -eq 400) "status=$($r1.Status)"

$r2 = Invoke-Http GET "$BaseUrl/api/v1/portfolio" @{ "X-AccountNumber" = "99999" }
Assert-Case "5.1 Cuenta inexistente devuelve 404" ($r2.Status -eq 404) "status=$($r2.Status)"

$r3 = Invoke-Http GET "$BaseUrl/api/v1/portfolio" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.1 Cuenta valida devuelve 200" ($r3.Status -eq 200) "status=$($r3.Status)"

# ============ 5.2 Instrumentos ============
$r = Invoke-Http GET "$BaseUrl/api/v1/instruments"
$arsCount = @($r.Body | Where-Object { $_.ticker -eq "ARS" }).Count
Assert-Case "5.2 Listado completo incluye ARS una sola vez" ($r.Status -eq 200 -and $arsCount -eq 1) "status=$($r.Status) ars=$arsCount"

$r = Invoke-Http GET "$BaseUrl/api/v1/instruments?query=ypf"
Assert-Case "5.2 Busqueda case-insensitive por ticker" ($r.Status -eq 200 -and @($r.Body | Where-Object { $_.ticker -match "YPFD" }).Count -ge 1) "status=$($r.Status)"

$r = Invoke-Http GET "$BaseUrl/api/v1/instruments?query=zzzzz"
Assert-Case "5.2 Sin coincidencias devuelve lista vacia" ($r.Status -eq 200 -and $r.Body.Count -eq 0) "status=$($r.Status) count=$($r.Body.Count)"

$r = Invoke-Http GET "$BaseUrl/api/v1/instruments?query=%20"
Assert-Case "5.2 Query vacio devuelve 400" ($r.Status -eq 400) "status=$($r.Status)"

# ============ 5.3 Ingreso de efectivo y compra ============
$r = Invoke-Order "10001" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":100000.00}'
Assert-Case "5.3 CASH_IN devuelve 201 FILLED sin price" `
    ($r.Status -eq 201 -and $r.Body.status -eq "FILLED" -and $null -eq $r.Body.price) `
    "status=$($r.Status) estado=$($r.Body.status) price=$($r.Body.price)"

$r = Invoke-Order "10001" '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10}'
Assert-Case "5.3 Compra a mercado devuelve 201 FILLED con close" `
    ($r.Status -eq 201 -and $r.Body.status -eq "FILLED" -and $null -ne $r.Body.price) `
    "status=$($r.Status) estado=$($r.Body.status) price=$($r.Body.price)"

# ============ 5.4 Rechazos de negocio ============
$r = Invoke-Order "10002" '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1000}'
Assert-Case "5.4 Compra sin efectivo devuelve 201 REJECTED" `
    ($r.Status -eq 201 -and $r.Body.status -eq "REJECTED" -and $r.Body.rejectionReason) `
    "status=$($r.Status) motivo=$($r.Body.rejectionReason)"

$r = Invoke-Order "10001" '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":10,"amount":100}'
Assert-Case "5.4 Request invalido (size+amount) devuelve 400 sin orden" `
    ($r.Status -eq 400) "status=$($r.Status)"

# ============ 5.5 Idempotencia ============
$key = "qs-idem-$([guid]::NewGuid())"
$body = '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":1000.00}'
$r1 = Invoke-Http POST "$BaseUrl/api/v1/orders" @{ "X-AccountNumber" = "10003"; "Idempotency-Key" = $key } $body
$r2 = Invoke-Http POST "$BaseUrl/api/v1/orders" @{ "X-AccountNumber" = "10003"; "Idempotency-Key" = $key } $body
Assert-Case "5.5 Reintento con misma clave devuelve el mismo id" `
    ($r1.Status -eq 201 -and $r2.Status -eq 201 -and $r1.Body.id -eq $r2.Body.id) `
    "ids=$($r1.Body.id)/$($r2.Body.id)"

$r3 = Invoke-Http POST "$BaseUrl/api/v1/orders" @{ "X-AccountNumber" = "10003"; "Idempotency-Key" = $key } `
    '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":2000.00}'
Assert-Case "5.5 Misma clave con contenido distinto devuelve 409" ($r3.Status -eq 409) "status=$($r3.Status)"

$hist = Invoke-Http GET "$BaseUrl/api/v1/orders?side=CASH_IN" @{ "X-AccountNumber" = "10003" }
Assert-Case "5.5 La orden idempotente aparece una sola vez" `
    ($hist.Status -eq 200 -and $hist.Body.totalElements -eq 1) "total=$($hist.Body.totalElements)"

# ============ 5.6 Orden LIMIT, reserva y cancelacion ============
$r = Invoke-Order "10002" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":120000.00}'
$r = Invoke-Order "10002" '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":5,"price":20000.00}'
Assert-Case "5.6 BUY LIMIT devuelve 201 NEW" ($r.Status -eq 201 -and $r.Body.status -eq "NEW") "status=$($r.Status) estado=$($r.Body.status)"
$limitId = $r.Body.id

$p = Invoke-Http GET "$BaseUrl/api/v1/portfolio" @{ "X-AccountNumber" = "10002" }
Assert-Case "5.6 El portfolio refleja la reserva de efectivo" `
    ($p.Body.reservedCash -eq 100000.00 -and $p.Body.availableCash -eq 20000.00) `
    "reservado=$($p.Body.reservedCash) disponible=$($p.Body.availableCash)"

$r = Invoke-Http DELETE "$BaseUrl/api/v1/orders/$limitId" @{ "X-AccountNumber" = "10002" }
Assert-Case "5.6 Cancelar devuelve 200 CANCELLED" ($r.Status -eq 200 -and $r.Body.status -eq "CANCELLED") "status=$($r.Status)"

$p = Invoke-Http GET "$BaseUrl/api/v1/portfolio" @{ "X-AccountNumber" = "10002" }
Assert-Case "5.6 La reserva se libero al cancelar" `
    ($p.Body.reservedCash -eq 0.00 -and $p.Body.availableCash -eq 120000.00) `
    "reservado=$($p.Body.reservedCash) disponible=$($p.Body.availableCash)"

$r = Invoke-Http DELETE "$BaseUrl/api/v1/orders/$limitId" @{ "X-AccountNumber" = "10002" }
Assert-Case "5.6 Cancelar de nuevo devuelve 409" ($r.Status -eq 409) "status=$($r.Status)"

$r = Invoke-Http DELETE "$BaseUrl/api/v1/orders/99999999" @{ "X-AccountNumber" = "10002" }
Assert-Case "5.6 Orden inexistente devuelve 404" ($r.Status -eq 404) "status=$($r.Status)"

$otherId = (Invoke-Order "10003" '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":10.00}').Body.id
$r = Invoke-Http DELETE "$BaseUrl/api/v1/orders/$otherId" @{ "X-AccountNumber" = "10002" }
Assert-Case "5.6 Orden de otra cuenta devuelve 404" ($r.Status -eq 404) "status=$($r.Status)"

# ============ 5.7 Compra por importe ============
$r = Invoke-Order "10001" '{"ticker":"YPFD","side":"BUY","type":"MARKET","amount":1.00}'
Assert-Case "5.7 Importe insuficiente devuelve 201 REJECTED" `
    ($r.Status -eq 201 -and $r.Body.status -eq "REJECTED") "status=$($r.Status) motivo=$($r.Body.rejectionReason)"

# ============ 5.8 Portfolio de cuenta sin operaciones ============
$r = Invoke-Http GET "$BaseUrl/api/v1/portfolio" @{ "X-AccountNumber" = "10004" }
Assert-Case "5.8 Portfolio limpio devuelve ceros y lista vacia" `
    ($r.Status -eq 200 -and $r.Body.totalCash -eq 0.00 -and $r.Body.availableCash -eq 0.00 `
        -and $r.Body.reservedCash -eq 0.00 -and $r.Body.totalValue -eq 0.00 `
        -and $r.Body.positions.Count -eq 0) `
    "total=$($r.Body.totalCash) posiciones=$($r.Body.positions.Count)"

# ============ 5.9 Historial ============
$h = Invoke-Http GET "$BaseUrl/api/v1/orders?page=0&size=5" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 Paginado devuelve estructura completa" `
    ($h.Status -eq 200 -and $null -ne $h.Body.content -and $null -ne $h.Body.totalElements `
        -and $null -ne $h.Body.page -and $null -ne $h.Body.size) `
    "total=$($h.Body.totalElements) page=$($h.Body.page)"

$h = Invoke-Http GET "$BaseUrl/api/v1/orders?status=FILLED" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 Filtro por estado" ($h.Status -eq 200 -and $h.Body.totalElements -ge 1) "total=$($h.Body.totalElements)"
$h = Invoke-Http GET "$BaseUrl/api/v1/orders?ticker=YPFD" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 Filtro por ticker" ($h.Status -eq 200 -and $h.Body.totalElements -ge 1) "total=$($h.Body.totalElements)"

$r = Invoke-Http GET "$BaseUrl/api/v1/orders?page=-1" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 page negativa devuelve 400" ($r.Status -eq 400) "status=$($r.Status)"
$r = Invoke-Http GET "$BaseUrl/api/v1/orders?size=101" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 size>100 devuelve 400" ($r.Status -eq 400) "status=$($r.Status)"
$r = Invoke-Http GET "$BaseUrl/api/v1/orders?status=INVALID" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 status invalido devuelve 400" ($r.Status -eq 400) "status=$($r.Status)"
$r = Invoke-Http GET "$BaseUrl/api/v1/orders?size=abc" @{ "X-AccountNumber" = "10001" }
Assert-Case "5.9 size no numerico devuelve 400" ($r.Status -eq 400) "status=$($r.Status)"

# ============ 5.10 Formato de error ============
$r = Invoke-Http GET "$BaseUrl/api/v1/portfolio" @{ "X-AccountNumber" = "99999" }
$errorFormat = ($r.Body.status -eq 404 -and $r.Body.error -eq "Not Found" `
    -and $r.Body.message -and $r.Body.path -eq "/api/v1/portfolio" -and $r.Body.timestamp `
    -and $r.Body.timestamp -notmatch "Z$|[+|-]\d{2}:\d{2}$")
Assert-Case "5.10 Estructura de error unificada sin zona horaria" ($errorFormat) `
    "status=$($r.Body.status) timestamp=$($r.Body.timestamp)"

# ============ Resumen ============
$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Escenarios del quickstart: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Detalle)" }
    exit 1
}
