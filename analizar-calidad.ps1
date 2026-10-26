# =====================================================================
# RestoFlow · Análisis de calidad con SonarQube
#
# Analiza el backend (Java) y el frontend (TypeScript) y envía los resultados al SonarQube local.
# Antes:
#   1. docker compose -f docker-compose.sonar.yml up -d
#   2. En http://localhost:9000 crear un token: My Account → Security → Generate Tokens
#      (tipo "Global Analysis Token"). Ver docs/sonarqube.md
# Uso (desde la carpeta raíz del proyecto):
#   powershell -ExecutionPolicy Bypass -File .\analizar-calidad.ps1
#   powershell -ExecutionPolicy Bypass -File .\analizar-calidad.ps1 -SoloBackend
#   powershell -ExecutionPolicy Bypass -File .\analizar-calidad.ps1 -SoloFrontend
# El token se puede dejar en la variable de entorno SONAR_TOKEN; nunca se guarda en un archivo (factor III).
# =====================================================================
param(
    [switch]$SoloBackend,
    [switch]$SoloFrontend,
    [string]$Servidor = "http://localhost:9000"
)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot
$imagenEscaner = "sonarsource/sonar-scanner-cli:12.2.0.4256_8.1.0"
$redSonar = "restoflow-sonar_sonar-red"

function Paso($texto) { Write-Host "`n==> $texto" -ForegroundColor Cyan }

# ---------- 1. ¿SonarQube está encendido? ----------
Paso "Revisando que SonarQube responda en $Servidor"
try {
    $estado = Invoke-RestMethod "$Servidor/api/system/status" -TimeoutSec 10
} catch {
    Write-Host "SonarQube no responde. Enciéndalo con:" -ForegroundColor Red
    Write-Host "  docker compose -f docker-compose.sonar.yml up -d   (y espere 2-3 minutos)"
    exit 1
}
if ($estado.status -ne "UP") {
    Write-Host "SonarQube está iniciando (estado: $($estado.status)). Espere un minuto y vuelva a intentarlo." -ForegroundColor Yellow
    exit 1
}
Write-Host "SonarQube $($estado.version) listo."

# ---------- 2. Token (de la variable SONAR_TOKEN o escrito ahora, sin mostrarlo) ----------
$token = $env:SONAR_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $seguro = Read-Host "Pegue el token de SonarQube (no se muestra en pantalla)" -AsSecureString
    $token = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "Se necesita un token. Ver docs/sonarqube.md" -ForegroundColor Red
    exit 1
}

# ---------- 3. Backend: pruebas JUnit + cobertura JaCoCo + análisis ----------
if (-not $SoloFrontend) {
    # "clean" borra lo compilado antes (por ejemplo, lo que compila VS Code por su cuenta) para analizar desde cero
    Paso "Backend: pruebas JUnit, cobertura (JaCoCo) y análisis"
    Push-Location (Join-Path $raiz "backend")
    try {
        & .\mvnw.cmd -B clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar "-Dsonar.host.url=$Servidor" "-Dsonar.token=$token"
        if ($LASTEXITCODE -ne 0) { throw "El análisis del backend falló (código $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

# ---------- 4. Frontend: pruebas Jest + cobertura + análisis ----------
if (-not $SoloBackend) {
    Paso "Frontend: pruebas Jest con cobertura"
    $frontend = Join-Path $raiz "frontend"
    Push-Location $frontend
    try {
        & npm run test:jest:cobertura
        if ($LASTEXITCODE -ne 0) { throw "Las pruebas de Jest fallaron" }
    } finally {
        Pop-Location
    }

    # El escáner corre en Linux (Docker): las rutas del informe de cobertura deben ser relativas y con "/"
    $lcov = Join-Path $frontend "coverage\jest\lcov.info"
    $prefijo = $frontend.TrimEnd('\') + '\'
    $lineas = Get-Content $lcov | ForEach-Object {
        if ($_ -like 'SF:*') { 'SF:' + $_.Substring(3).Replace($prefijo, '').Replace('\', '/') } else { $_ }
    }
    [IO.File]::WriteAllLines($lcov, [string[]]$lineas)

    Paso "Frontend: análisis con el escáner de SonarQube (Docker)"
    & docker run --rm --network $redSonar `
        -e SONAR_HOST_URL="http://sonarqube:9000" `
        -e SONAR_TOKEN="$token" `
        -v "${frontend}:/usr/src" `
        $imagenEscaner
    if ($LASTEXITCODE -ne 0) { throw "El análisis del frontend falló (código $LASTEXITCODE)" }
}

Paso "Listo. Resultados en SonarQube:"
if (-not $SoloFrontend) { Write-Host "  Backend:  $Servidor/dashboard?id=restoflow-backend" }
if (-not $SoloBackend)  { Write-Host "  Frontend: $Servidor/dashboard?id=restoflow-frontend" }
