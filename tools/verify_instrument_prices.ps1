# Verificacion manual de los precios en GET /api/v1/instruments (spec 002)
# contra la API en ejecucion. Uso:
#   powershell -ExecutionPolicy Bypass -File tools/verify_instrument_prices.ps1
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$results = @()

function Get-Instruments {
    param([string]$Query, [switch]$NoQuery)
    $uri = if ($NoQuery) { "$BaseUrl/api/v1/instruments" } else { "$BaseUrl/api/v1/instruments?query=$Query" }
    try {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -ErrorAction Stop
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

$YPFD_PRICE = "7837.50"
$YPFD_PREVIOUS = "7975.05"
$UNQUOTED = @("ARS", "IRCP", "PGR")

# 1. Catalogo completo: status y cantidad
$all = Get-Instruments -NoQuery
Assert-Case "Catalogo completo responde 200 con 66 instrumentos" `
    ($all.Status -eq 200 -and $all.Body.Count -eq 66) `
    "status=$($all.Status) total=$($all.Body.Count)"

# 2. YPFD expone el ultimo cierre y el cierre anterior del seed
$ypfd = @($all.Body | Where-Object { $_.ticker -eq "YPFD" })[0]
Assert-Case "YPFD expone price=$YPFD_PRICE y previousClose=$YPFD_PREVIOUS" `
    ($null -ne $ypfd.price -and "$($ypfd.price)" -eq $YPFD_PRICE -and `
     $null -ne $ypfd.previousClose -and "$($ypfd.previousClose)" -eq $YPFD_PREVIOUS) `
    "price=$($ypfd.price) previousClose=$($ypfd.previousClose)"

# 3. Los instrumentos sin cotizacion no exponen campos de precio
$unquotedOk = $true
$unquotedDetail = @()
foreach ($ticker in $UNQUOTED) {
    $instrument = @($all.Body | Where-Object { $_.ticker -eq $ticker })[0]
    if ($null -ne $instrument -and ($null -ne $instrument.price -or $null -ne $instrument.previousClose)) {
        $unquotedOk = $false
        $unquotedDetail += "$ticker(price=$($instrument.price))"
    }
}
Assert-Case "Los instrumentos sin cotizacion no exponen price ni previousClose" `
    ($unquotedOk -and $unquotedDetail.Count -eq 0) `
    "revisados: $($UNQUOTED -join ',')"

# 4. Todos los demas instrumentos exponen ambos campos
$withQuote = @($all.Body | Where-Object { $UNQUOTED -notcontains $_.ticker })
$missing = @($withQuote | Where-Object { $null -eq $_.price -or $null -eq $_.previousClose })
Assert-Case "Los 63 instrumentos con cotizacion exponen price y previousClose" `
    ($withQuote.Count -eq 63 -and $missing.Count -eq 0) `
    "conCotizacion=$($withQuote.Count) sinPrecio=$($missing.Count)"

# 5. Busqueda por ticker incluye los precios
$byTicker = Get-Instruments "ypf"
$ypfdSearch = @($byTicker.Body | Where-Object { $_.ticker -eq "YPFD" })[0]
Assert-Case "Busqueda por ticker incluye price y previousClose" `
    ($byTicker.Status -eq 200 -and $null -ne $ypfdSearch -and `
     "$($ypfdSearch.price)" -eq $YPFD_PRICE -and "$($ypfdSearch.previousClose)" -eq $YPFD_PREVIOUS) `
    "resultados=$($byTicker.Body.Count) price=$($ypfdSearch.price)"

# 6. Busqueda por nombre incluye los precios
$byName = Get-Instruments "macro"
$bma = @($byName.Body | Where-Object { $_.ticker -eq "BMA" })[0]
Assert-Case "Busqueda por nombre incluye price y previousClose" `
    ($byName.Status -eq 200 -and $null -ne $bma -and $null -ne $bma.price) `
    "resultados=$($byName.Body.Count) price=$($bma.price)"

# 7. Busqueda sin coincidencias sigue devolviendo lista vacia
$none = Get-Instruments "zzzzz"
Assert-Case "Busqueda sin coincidencias devuelve lista vacia" `
    ($none.Status -eq 200 -and $none.Body.Count -eq 0) "status=$($none.Status) total=$($none.Body.Count)"

# 8. La validacion de busqueda vacia se conserva (400)
$blank = Get-Instruments "%20%20%20"
Assert-Case "Busqueda con solo espacios responde 400" ($blank.Status -eq 400) "status=$($blank.Status)"

# 9. El price del catalogo coincide con el price de una orden a mercado
try {
    $key = "qs-" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $order = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/orders" `
        -Headers @{ "X-AccountNumber" = "10001"; "Idempotency-Key" = $key } `
        -ContentType "application/json" `
        -Body '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":1}'
    Assert-Case "El price del catalogo coincide con el de la compra a mercado" `
        ("$($order.price)" -eq $YPFD_PRICE) `
        "estado=$($order.status) priceOrden=$($order.price) priceCatalogo=$($ypfd.price)"
}
catch {
    Assert-Case "El price del catalogo coincide con el de la compra a mercado" $false $_.Exception.Message
}

# 10. El OpenAPI en runtime declara price y previousClose en el schema del catalogo
try {
    $apiDocs = Invoke-RestMethod -Uri "$BaseUrl/v3/api-docs"
    $instrument = $apiDocs.components.schemas.InstrumentResponse
    $hasFields = ($null -ne $instrument.properties.price -and $null -ne $instrument.properties.previousClose)
    $required = @($instrument.required)
    $optional = ($required -notcontains "price" -and $required -notcontains "previousClose")
    Assert-Case "El OpenAPI en runtime declara price y previousClose opcionales" `
        ($hasFields -and $optional) `
        "schemas=$($apiDocs.components.schemas.PSObject.Properties.Name -join ',')"
}
catch {
    Assert-Case "El OpenAPI en runtime declara price y previousClose opcionales" $false $_.Exception.Message
}

$results | Format-Table -AutoSize
$fallos = @($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Detalle)" }
    exit 1
}
