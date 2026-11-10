<#
=====================================================================
 RestoFlow · Pruebas de carga y estrés con Apache JMeter (RES-56)

 Pruebas (plan restoflow.jmx + un archivo .properties por prueba):
   humo    1 usuario por recorrido, una vuelta (unos 20 s).
           Siempre primero: comprueba que todo funciona.
   carga   Hora pico: 100 usuarios a la vez durante 5 min (~6 min).
   estres  Sube hasta 600 usuarios, se mantiene y se recupera (~14 min).

 Uso (desde la carpeta del proyecto):
   .\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba humo
   .\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba carga -Servidor IP-DEL-SERVIDOR -Puerto 80
   Ver la prueba EN VIVO en la interfaz gráfica de JMeter (botón verde):
   .\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba carga -Gui

 La primera vez descarga JMeter 5.6.3 (unos 90 MB) en la carpeta
 herramientas\ del proyecto (no se sube a Git) y verifica su firma
 SHA-512. Necesita Java 8 o superior (el mismo del backend).

 Resultados en docs\pruebas\jmeter\resultados\:
   <prueba>-<fecha>\index.html   informe HTML de JMeter con gráficas
   <prueba>-<fecha>.md           tabla por operación (para el informe)
   <prueba>-<fecha>.jtl          cada petición (no se sube a Git)

 Datos: crea dos cuentas temporales que quedan DESACTIVADAS al final,
 y pedidos de prueba en las mesas 101 en adelante (quedan cobrados).
 Por eso no se debe ejecutar contra una base con datos reales: contra
 este computador solo con la base de Docker (docker compose -f
 docker-compose.yml up -d), o contra el servidor de AWS.

 Si PowerShell no deja ejecutar scripts:
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
=====================================================================
#>
param(
    [ValidateSet('humo', 'carga', 'estres')]
    [string]$Prueba = 'humo',
    [string]$Servidor = 'localhost',
    [int]$Puerto = 4200,
    [ValidateSet('http', 'https')]
    [string]$Protocolo = 'http',
    [switch]$Gui,
    [string]$VersionJMeter = '5.6.3'
)

$ErrorActionPreference = 'Stop'
$carpeta = $PSScriptRoot
$raiz = (Resolve-Path (Join-Path $carpeta '..\..\..')).Path
$herramientas = Join-Path $raiz 'herramientas'
$jmeter = Join-Path $herramientas "apache-jmeter-$VersionJMeter\bin\jmeter.bat"

# ---------- 1. Java ----------
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host 'No encontré Java. JMeter necesita Java 8 o superior (el mismo que usa el backend).' -ForegroundColor Red
    exit 1
}

# ---------- 2. JMeter (se descarga una sola vez y se verifica su firma) ----------
if (-not (Test-Path $jmeter)) {
    New-Item -ItemType Directory -Force -Path $herramientas | Out-Null
    $zip = Join-Path $herramientas "apache-jmeter-$VersionJMeter.zip"
    $url = "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$VersionJMeter.zip"
    Write-Host "Descargando Apache JMeter $VersionJMeter (solo la primera vez)..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    $esperado = ((Invoke-WebRequest -Uri "$url.sha512" -UseBasicParsing).Content -split '\s+')[0].Trim().ToLower()
    $obtenido = (Get-FileHash -Algorithm SHA512 $zip).Hash.ToLower()
    if ($esperado -ne $obtenido) {
        Remove-Item $zip -Force
        Write-Host 'La firma SHA-512 del archivo descargado no coincide con la de Apache. No se usará.' -ForegroundColor Red
        exit 1
    }
    Write-Host 'Firma SHA-512 verificada. Descomprimiendo...' -ForegroundColor Green
    Expand-Archive -Path $zip -DestinationPath $herramientas -Force
    Remove-Item $zip -Force
}

# ---------- 3. Proteger los datos reales ----------
$local = @('localhost', '127.0.0.1') -contains $Servidor
if ($local) {
    # Si Docker está apagado, "docker ps" escribe un error: no debe detener el guion
    $ErrorActionPreference = 'Continue'
    $bdDocker = docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq 'restoflow-db' }
    $ErrorActionPreference = 'Stop'
    if (-not $bdDocker) {
        Write-Host ''
        Write-Host 'ATENCIÓN: RestoFlow en este computador NO está usando la base de datos de Docker.' -ForegroundColor Yellow
        Write-Host 'La prueba crearía pedidos y ventas de prueba en tu base de datos real.' -ForegroundColor Yellow
        Write-Host 'Para probar sin tocarla:  docker compose down  y luego  docker compose -f docker-compose.yml up -d' -ForegroundColor Yellow
        $respuesta = Read-Host '¿Continuar de todas formas? (escriba SI para continuar)'
        if ($respuesta -ne 'SI') { exit 1 }
    }
}

# ---------- 4. Administrador (variables de entorno: la contraseña no se escribe en archivos ni en la línea de comandos) ----------
function Leer-Env([string]$Clave) {
    $archivo = Join-Path $raiz '.env'
    if (-not (Test-Path $archivo)) { return $null }
    foreach ($linea in Get-Content $archivo) {
        if ($linea -match "^\s*$Clave\s*=\s*(.*)$") { return $Matches[1].Trim().Trim('"').Trim("'") }
    }
    return $null
}
if ($local -and $bdDocker -and -not $env:ADMIN_PASSWORD) {
    # Base de Docker: su administrador es el del archivo .env
    $env:ADMIN_EMAIL = Leer-Env 'ADMIN_EMAIL'
    $env:ADMIN_PASSWORD = Leer-Env 'ADMIN_PASSWORD'
}
if (-not $env:ADMIN_EMAIL) { $env:ADMIN_EMAIL = 'admin@restoflow.com' }
if (-not $env:ADMIN_PASSWORD) {
    Write-Host "Contraseña del administrador $($env:ADMIN_EMAIL) en $($Servidor):" -ForegroundColor Cyan
    $segura = Read-Host -AsSecureString 'Contraseña'
    $env:ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura))
}

# ---------- 5. Ejecutar ----------
# JMeter escribe avisos en la salida de errores: en Windows PowerShell no deben detener el guion
$ErrorActionPreference = 'Continue'
$plan = Join-Path $carpeta 'restoflow.jmx'
$perfil = Join-Path $carpeta "$Prueba.properties"
$informeProps = Join-Path $carpeta 'informe.properties'
$resultados = Join-Path $carpeta 'resultados'
New-Item -ItemType Directory -Force -Path $resultados | Out-Null
$fecha = Get-Date -Format 'yyyy-MM-dd-HH-mm'
$nombre = "$Prueba-$fecha"
$destino = @("-Jservidor=$Servidor", "-Jpuerto=$Puerto", "-Jprotocolo=$Protocolo")

Write-Host ''
Write-Host "RestoFlow · prueba de $Prueba contra $($Protocolo)://$($Servidor):$Puerto/api (administrador: $($env:ADMIN_EMAIL))" -ForegroundColor Cyan

if ($Gui) {
    Write-Host 'Se abre JMeter. Para empezar pulse el botón verde (Iniciar) y mire "Resumen en vivo" y "Gráfico de tiempos de respuesta".' -ForegroundColor Cyan
    Write-Host 'El modo gráfico es para VER la prueba; los resultados oficiales se toman sin -Gui (así lo recomienda Apache).' -ForegroundColor Cyan
    & $jmeter -t $plan -q $informeProps -q $perfil @destino -j (Join-Path $resultados "$nombre-gui.log")
    exit $LASTEXITCODE
}

Write-Host 'Cada 10 segundos aparece un resumen ("summary +"): peticiones, promedio, mínimo, máximo y errores.' -ForegroundColor Cyan
Write-Host ''
$jtl = Join-Path $resultados "$nombre.jtl"
$informe = Join-Path $resultados $nombre
& $jmeter -n -t $plan -q $informeProps -q $perfil @destino -l $jtl -j (Join-Path $resultados "$nombre.log") -e -o $informe
if (-not (Test-Path (Join-Path $informe 'statistics.json'))) {
    Write-Host "JMeter no generó el informe. Revise $nombre.log en docs\pruebas\jmeter\resultados\" -ForegroundColor Red
    exit 1
}

# ---------- 6. Resumen en Markdown (para docs/pruebas/carga-y-estres.md) ----------
$stats = Get-Content (Join-Path $informe 'statistics.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$orden = @('iniciar sesión', 'ver menú', 'crear pedido', 'cocina: en preparación', 'cocina: listo', 'cocina: entregado',
    'cobrar', 'pantalla cocina', 'lista de pedidos', 'mesas ocupadas', 'panel administrador', 'avisos')
$n = { param($v) ([double]$v).ToString('N1', [Globalization.CultureInfo]'es-CO') }
$p = { param($v) ([double]$v).ToString('N2', [Globalization.CultureInfo]'es-CO') }
$filas = @()
$cumple = $true
foreach ($op in $orden) {
    $s = $stats.$op
    if (-not $s) { continue }
    $bien = ([double]$s.pct2ResTime -lt 3000) -and ([double]$s.errorPct -lt 1)
    if (-not $bien) { $cumple = $false }
    $marca = if ($bien) { 'Sí' } else { '**No**' }
    $filas += "| $op | $($s.sampleCount) | $(& $n $s.meanResTime) | $(& $n $s.medianResTime) | $(& $n $s.pct2ResTime) | $(& $n $s.maxResTime) | $(& $p $s.errorPct) % | $marca |"
}
$t = $stats.Total
if ($null -eq $t -or [int]$t.sampleCount -eq 0) {
    Write-Host 'La prueba NO se ejecutó (falló la preparación). Revise el .log.' -ForegroundColor Red
    exit 1
}
$cobros = if ($stats.'cobrar') { $stats.'cobrar'.sampleCount - $stats.'cobrar'.errorCount } else { 0 }
$veredicto = if ($cumple) { '**Cumple:** P95 < 3 s y menos de 1 % de errores en cada operación.' } else { '**No cumple** en las operaciones marcadas con **No**.' }
$md = @"
# Prueba de $Prueba · RestoFlow (Apache JMeter $VersionJMeter)

- **Fecha:** $(Get-Date -Format 'yyyy-MM-dd HH:mm') · **Servidor:** $($Protocolo)://$($Servidor):$Puerto
- **Peticiones:** $($t.sampleCount) ($(& $n $t.throughput) por segundo) · **con error:** $(& $p $t.errorPct) %
- **Ventas registradas** (pedidos completos hasta el cobro): $cobros
- **Tiempo de respuesta global:** promedio $(& $n $t.meanResTime) ms · P95 $(& $n $t.pct2ResTime) ms · máximo $(& $n $t.maxResTime) ms
- **Informe con gráficas:** ``resultados/$nombre/index.html``

| Operación | Peticiones | Promedio (ms) | Mediana (ms) | P95 (ms) | Máximo (ms) | Errores | Cumple 3 s |
|---|---:|---:|---:|---:|---:|---:|:---:|
$($filas -join "`n")

$veredicto
"@
$md | Out-File -Encoding utf8 (Join-Path $resultados "$nombre.md")
Write-Host ''
Write-Host $md
if ($cumple) { Write-Host 'Terminó y CUMPLE los 3 segundos.' -ForegroundColor Green } else { Write-Host 'Terminó, pero NO cumple en alguna operación.' -ForegroundColor Yellow }
Start-Process (Join-Path $informe 'index.html')
