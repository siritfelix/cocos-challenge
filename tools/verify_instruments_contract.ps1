# Verificacion manual del endpoint GET /api/v1/instruments contra la API en ejecucion.
# Uso:  powershell -File tools/verify_instruments_contract.ps1
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$results = @()

function Get-Instruments {
    param([string]$Query, [switch]$NoQuery)
    # -NoQuery omite el parametro por completo; en caso contrario se envia
    # siempre, incluso si esta vacio, para poder verificar el 400 de la
    # busqueda en blanco.
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

# 1. Catalogo completo
$all = Get-Instruments -NoQuery
Assert-Case "Catalogo completo" `
    ($all.Status -eq 200 -and $all.Body.Count -eq 66) `
    "status=$($all.Status) total=$($all.Body.Count)"

# 2. El instrumento de efectivo aparece exactamente una vez
$cashCount = @($all.Body | Where-Object { $_.ticker -eq "ARS" }).Count
Assert-Case "El efectivo ARS aparece exactamente una vez" ($cashCount -eq 1) "count=$cashCount"

# 3. Los campos del contrato estan presentes
$first = $all.Body[0]
Assert-Case "Cada instrumento expone id, ticker, name y type" `
    ($null -ne $first.id -and $first.ticker -and $first.name -and $first.type) `
    "ticker=$($first.ticker) type=$($first.type)"

# 4. Orden determinista por ticker
$tickers = @($all.Body | ForEach-Object { $_.ticker })
$sorted = @($tickers | Sort-Object)
Assert-Case "El catalogo viene ordenado por ticker" `
    (($tickers -join ',') -eq ($sorted -join ',')) "primeros=$($tickers[0]),$($tickers[1])"

# 5. Busqueda por ticker, sin distinguir mayusculas
$byTicker = Get-Instruments "ypf"
Assert-Case "Busqueda por ticker sin distinguir mayusculas" `
    ($byTicker.Status -eq 200 -and $byTicker.Body.Count -ge 1 -and `
        (@($byTicker.Body | Where-Object { $_.ticker -eq "YPFD" }).Count -eq 1)) `
    "resultados=$($byTicker.Body.Count)"

# 6. Busqueda por nombre
$byName = Get-Instruments "macro"
Assert-Case "Busqueda por nombre" `
    ($byName.Status -eq 200 -and @($byName.Body | Where-Object { $_.ticker -eq "BMA" }).Count -eq 1) `
    "resultados=$($byName.Body.Count)"

# 7. Sin duplicados
$dup = Get-Instruments "capex"
$dupTickers = @($dup.Body | ForEach-Object { $_.ticker })
Assert-Case "Sin duplicados" `
    ((@($dupTickers | Select-Object -Unique)).Count -eq $dupTickers.Count) `
    "tickers=$($dupTickers -join ',')"

# 8. Busqueda sin coincidencias
$none = Get-Instruments "zzzzz"
Assert-Case "Busqueda sin coincidencias devuelve lista vacia" `
    ($none.Status -eq 200 -and $none.Body.Count -eq 0) "status=$($none.Status) total=$($none.Body.Count)"

# 9. Busqueda vacia -> 400
$blank = Get-Instruments ""
Assert-Case "Busqueda vacia responde 400" ($blank.Status -eq 400) "status=$($blank.Status)"

# 10. Busqueda con solo espacios -> 400
$spaces = Get-Instruments "%20%20%20"
Assert-Case "Busqueda con solo espacios responde 400" ($spaces.Status -eq 400) "status=$($spaces.Status)"

$results | Format-Table -AutoSize
$fallos = ($results | Where-Object { $_.Resultado -eq "FALLO" }).Count
"Casos: $($results.Count) | Fallos: $fallos"
if ($fallos -gt 0) {
    $results | Where-Object { $_.Resultado -eq "FALLO" } | ForEach-Object { "  $($_.Caso): $($_.Detalle)" }
    exit 1
}
