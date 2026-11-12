# Verificacion manual del endpoint GET /api/v1/portfolio contra la API en ejecucion.
# Uso:  powershell -File tools/verify_portfolio_contract.ps1
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Account = "10001"
)

$ErrorActionPreference = "Stop"
$results = @()

function Invoke-Order {
    param([string]$Body)
    $headers = @{
        "X-AccountNumber" = $Global:account
        "Idempotency-Key"  = "verify-$([guid]::NewGuid())"
    }
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/orders" -Method POST -Headers $headers `
            -ContentType "application/json" -Body $Body -UseBasicParsing -ErrorAction Stop
        return $response.Content
    }
    catch {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.ReadToEnd()
        return $null
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
    param(
        [string]$Name,
        [bool]$Condition,
        [string]$Detail
    )
    $script:results += [PSCustomObject]@{
        Caso      = $Name
        Resultado = if ($Condition) { "OK" } else { "FALLO" }
        Detalle   = $Detail
    }
}

$Global:account = $Account

# --- Portfolio vacio ---
$portfolio = Get-Portfolio
Assert-Case "Portfolio vacio: ceros" `
    ($portfolio.Body.totalCash -eq 0 -and $portfolio.Body.availableCash -eq 0 `
        -and $portfolio.Body.reservedCash -eq 0 -and $portfolio.Body.totalValue -eq 0 `
        -and $portfolio.Body.positions.Count -eq 0) `
    "totalCash=$($portfolio.Body.totalCash) positions=$($portfolio.Body.positions.Count)"

# --- Cabecera ausente -> 400 ---
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/portfolio" -UseBasicParsing -ErrorAction Stop | Out-Null
    $status = 200
} catch { $status = [int]$_.Exception.Response.StatusCode }
Assert-Case "Sin cabecera de cuenta -> 400" ($status -eq 400) "status=$status"

# --- Cuenta inexistente -> 404 ---
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/portfolio" -Headers @{ "X-AccountNumber" = "99999" } `
        -UseBasicParsing -ErrorAction Stop | Out-Null
    $status = 200
} catch { $status = [int]$_.Exception.Response.StatusCode }
Assert-Case "Cuenta inexistente -> 404" ($status -eq 404) "status=$status"

# --- Ingreso de efectivo ---
Invoke-Order '{"ticker":"ARS","side":"CASH_IN","type":"MARKET","size":100000.00}' | Out-Null
$portfolio = Get-Portfolio
Assert-Case "Ingreso refleja el efectivo" `
    ($portfolio.Body.totalCash -eq 100000.00 -and $portfolio.Body.availableCash -eq 100000.00 `
        -and $portfolio.Body.totalValue -eq 100000.00 -and $portfolio.Body.positions.Count -eq 0) `
    "totalCash=$($portfolio.Body.totalCash) positions=$($portfolio.Body.positions.Count)"

# --- Compra a mercado genera posicion ---
Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"MARKET","size":3}' | Out-Null
$portfolio = Get-Portfolio
$position = $portfolio.Body.positions[0]
$expectedMarketValue = $position.totalQuantity * $position.currentPrice
Assert-Case "Compra genera posicion con cantidad y valor de mercado" `
    ($position.ticker -eq "YPFD" -and $position.totalQuantity -eq 3 `
        -and $position.availableQuantity -eq 3 -and $position.reservedQuantity -eq 0 `
        -and [Math]::Abs($position.marketValue - $expectedMarketValue) -lt 0.01) `
    "ticker=$($position.ticker) qty=$($position.totalQuantity) mv=$($position.marketValue)"

Assert-Case "El patrimonio total es el efectivo mas el valor de mercado" `
    ([Math]::Abs($portfolio.Body.totalValue - ($portfolio.Body.totalCash + $position.marketValue)) -lt 0.01) `
    "totalValue=$($portfolio.Body.totalValue)"

# --- Orden pendiente reserva efectivo sin alterar el patrimonio ---
# La compra a mercado previa ya consumio efectivo, por lo que el total actual
# es 100.000 menos el costo de esa compra.
$totalBefore = $portfolio.Body.totalValue
$cashBefore = $portfolio.Body.totalCash
Invoke-Order '{"ticker":"YPFD","side":"BUY","type":"LIMIT","size":1,"price":50000.00}' | Out-Null
$portfolio = Get-Portfolio
Assert-Case "Orden pendiente reserva efectivo" `
    ($portfolio.Body.reservedCash -eq 50000.00 `
        -and [Math]::Abs($portfolio.Body.availableCash - ($cashBefore - 50000.00)) -lt 0.01) `
    "reserved=$($portfolio.Body.reservedCash) available=$($portfolio.Body.availableCash) esperado=$(($cashBefore - 50000.00))"

Assert-Case "Orden pendiente no altera el patrimonio total" `
    ([Math]::Abs($portfolio.Body.totalValue - $totalBefore) -lt 0.01) `
    "totalValue antes=$totalBefore ahora=$($portfolio.Body.totalValue)"

# --- Posicion cerrada no se lista ---
$portfolio = Get-Portfolio
Assert-Case "La posicion sigue listada mientras tenga acciones" `
    ($portfolio.Body.positions.Count -eq 1) "positions=$($portfolio.Body.positions.Count)"

$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Detalle)" }
    exit 1
}
