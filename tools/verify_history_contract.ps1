# Verificacion manual del endpoint GET /api/v1/orders contra la API en ejecucion.
# Uso:  powershell -File tools/verify_history_contract.ps1
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Account = "10001"
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

function Get-History {
    param([hashtable]$Query = @{})
    $uri = "$BaseUrl/api/v1/orders"
    if ($Query.Count -gt 0) {
        $pairs = @()
        foreach ($key in $Query.Keys) { $pairs += "$key=$($Query[$key])" }
        $uri += "?" + ($pairs -join "&")
    }
    try {
        $response = Invoke-WebRequest -Uri $uri -Headers @{ "X-AccountNumber" = $Global:account } `
            -UseBasicParsing -ErrorAction Stop
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

# --- Datos de prueba ---
Invoke-Order '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":50000.00}' | Out-Null
Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":2}' | Out-Null
Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":100.00}' | Out-Null
Invoke-Order '{"ticker":"GGAL","side":"BUY","type":"MARKET","size":1}' | Out-Null

# 1. Historial completo con metadatos de paginacion
$all = Get-History
Assert-Case "Historial devuelve los metadatos de paginacion" `
    ($all.Status -eq 200 -and $all.Body.totalElements -eq 4 -and $all.Body.page -eq 0 `
        -and $all.Body.size -eq 20 -and $all.Body.content.Count -eq 4) `
    "total=$($all.Body.totalElements) page=$($all.Body.page) size=$($all.Body.size)"

# 2. Orden descendente por fecha
$datetimes = @($all.Body.content | ForEach-Object { [datetime]$_.datetime })
$descending = $true
for ($i = 1; $i -lt $datetimes.Count; $i++) {
    if ($datetimes[$i] -gt $datetimes[$i - 1]) { $descending = $false }
}
Assert-Case "El historial viene ordenado de mas reciente a mas antiguo" $descending `
    ($datetimes -join " > ")

# 3. Filtro por estado
$filled = Get-History @{ status = "FILLED" }
Assert-Case "Filtro por estado FILLED" `
    ($filled.Status -eq 200 -and $filled.Body.totalElements -eq 3 -and `
        (@($filled.Body.content | Where-Object { $_.status -ne "FILLED" }).Count -eq 0)) `
    "total=$($filled.Body.totalElements)"

# 4. Filtro por lado
$buys = Get-History @{ side = "BUY" }
Assert-Case "Filtro por lado BUY" `
    ($buys.Status -eq 200 -and $buys.Body.totalElements -eq 3 -and `
        (@($buys.Body.content | Where-Object { $_.side -ne "BUY" }).Count -eq 0)) `
    "total=$($buys.Body.totalElements)"

# 5. Filtro por ticker
$ypfd = Get-History @{ ticker = "YPFD" }
Assert-Case "Filtro por ticker YPFD" `
    ($ypfd.Status -eq 200 -and $ypfd.Body.totalElements -eq 2 -and `
        (@($ypfd.Body.content | Where-Object { $_.ticker -ne "YPFD" }).Count -eq 0)) `
    "total=$($ypfd.Body.totalElements)"

# 6. Filtros combinados
$combo = Get-History @{ status = "NEW"; side = "BUY"; ticker = "YPFD" }
Assert-Case "Filtros combinados" `
    ($combo.Status -eq 200 -and $combo.Body.totalElements -eq 1 -and `
        $combo.Body.content[0].status -eq "NEW" -and $combo.Body.content[0].ticker -eq "YPFD") `
    "total=$($combo.Body.totalElements)"

# 7. Estado invalido -> 400
$badStatus = Get-History @{ status = "INVALID" }
Assert-Case "Estado invalido responde 400" ($badStatus.Status -eq 400) "status=$($badStatus.Status)"

# 8. Lado invalido -> 400
$badSide = Get-History @{ side = "INVALID" }
Assert-Case "Lado invalido responde 400" ($badSide.Status -eq 400) "status=$($badSide.Status)"

# 9. Pagina negativa -> 400
$negPage = Get-History @{ page = "-1" }
Assert-Case "Pagina negativa responde 400" ($negPage.Status -eq 400) "status=$($negPage.Status)"

# 10. Tamano fuera de rango -> 400
$badSize = Get-History @{ size = "101" }
Assert-Case "Tamano fuera de rango responde 400" ($badSize.Status -eq 400) "status=$($badSize.Status)"

# 11. Tamano no numerico -> 400
$nanSize = Get-History @{ size = "abc" }
Assert-Case "Tamano no numerico responde 400" ($nanSize.Status -eq 400) "status=$($nanSize.Status)"

# 12. Paginacion parte el historial
$page0 = Get-History @{ page = "0"; size = "2" }
$page1 = Get-History @{ page = "1"; size = "2" }
$page2 = Get-History @{ page = "2"; size = "2" }
Assert-Case "La paginacion divide el historial" `
    ($page0.Body.content.Count -eq 2 -and $page1.Body.content.Count -eq 2 `
        -and $page2.Body.content.Count -eq 0 -and $page0.Body.totalElements -eq 4 `
        -and $page0.Body.totalPages -eq 2) `
    "paginas: $($page0.Body.content.Count)/$($page1.Body.content.Count)/$($page2.Body.content.Count) totalPages=$($page0.Body.totalPages)"

# 13. Pagina inexistente -> 200 con contenido vacio
$empty = Get-History @{ page = "99" }
Assert-Case "Pagina inexistente responde 200 con contenido vacio" `
    ($empty.Status -eq 200 -and $empty.Body.content.Count -eq 0 -and $empty.Body.totalElements -eq 4) `
    "status=$($empty.Status) content=$($empty.Body.content.Count)"

# 14. Aislamiento entre cuentas
Invoke-Order '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":1000.00}' "10002" | Out-Null
$other = Get-History
Assert-Case "El historial no incluye ordenes de otra cuenta" `
    ($other.Body.totalElements -eq 4) "total=$($other.Body.totalElements)"

$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Detalle)" }
    exit 1
}
