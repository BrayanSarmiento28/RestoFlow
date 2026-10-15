<#
=====================================================================
 RestoFlow · Prueba de rendimiento (RES-28)
 Requerimiento no funcional: actualizar el estado de un pedido en
 máximo 3 segundos.

 Qué hace:
   1. Inicia sesión como administrador.
   2. Repite N veces el ciclo completo de un pedido, midiendo cada
      petición con un cronómetro:
        crear pedido → En preparación → Listo → Entregado,
        y consulta las comandas de cocina y el pedido como lo vería
        el mesero.
   3. Muestra promedio, mediana, percentil 95 y máximo de cada
      operación, y si cumple el límite de 3000 ms.
   4. Guarda el detalle en resultados-rendimiento.csv.

 Uso (con el backend encendido, por ejemplo con Docker Compose):
   cd docs\pruebas
   .\medir-rendimiento.ps1
   .\medir-rendimiento.ps1 -Repeticiones 50

 Si PowerShell no deja ejecutar scripts:
   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

 Los pedidos de prueba se crean en la mesa 99 con la observación
 "Prueba de rendimiento RES-28" y terminan en estado Entregado.
=====================================================================
#>
param(
    [string]$Api = 'http://localhost:8080/api',
    [string]$Correo = 'admin@restoflow.com',
    [string]$Contrasena = 'Admin2026*',
    [int]$Repeticiones = 20,
    [int]$LimiteMs = 3000
)

$ErrorActionPreference = 'Stop'
$medidas = New-Object System.Collections.Generic.List[object]

# Ejecuta una petición, mide cuánto tarda y guarda la medida
function Medir([string]$Operacion, [string]$Metodo, [string]$Ruta, $Cuerpo, [bool]$Guardar = $true) {
    $parametros = @{ Uri = "$Api$Ruta"; Method = $Metodo; Headers = $script:cabeceras; ContentType = 'application/json; charset=utf-8' }
    if ($null -ne $Cuerpo) { $parametros.Body = [Text.Encoding]::UTF8.GetBytes(($Cuerpo | ConvertTo-Json -Depth 5)) }
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    $respuesta = Invoke-RestMethod @parametros
    $reloj.Stop()
    if ($Guardar) {
        $medidas.Add([pscustomobject]@{ Operacion = $Operacion; Milisegundos = [math]::Round($reloj.Elapsed.TotalMilliseconds, 1) })
    }
    return $respuesta
}

function Percentil([double[]]$Valores, [double]$P) {
    $ordenados = $Valores | Sort-Object
    $indice = [math]::Ceiling($P / 100 * $ordenados.Count) - 1
    return $ordenados[[math]::Max(0, $indice)]
}

Write-Host ''
Write-Host 'RestoFlow · Prueba de rendimiento (RES-28)' -ForegroundColor Cyan
Write-Host "API: $Api   Repeticiones: $Repeticiones   Límite: $LimiteMs ms"
Write-Host ''

# 1. Iniciar sesión
try {
    $script:cabeceras = @{}
    $sesion = Medir 'Iniciar sesión' 'POST' '/auth/login' @{ email = $Correo; password = $Contrasena } $false
} catch {
    Write-Host "No se pudo iniciar sesión en $Api. ¿Está encendido el backend? Detalle: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$script:cabeceras = @{ Authorization = "Bearer $($sesion.token)" }

# 2. Un producto disponible para armar los pedidos
$producto = @(Medir 'Consultar productos' 'GET' '/productos?soloActivos=true' $null $false)[0]
if ($null -eq $producto) { Write-Host 'No hay productos disponibles para crear pedidos.' -ForegroundColor Red; exit 1 }
Write-Host "Producto de prueba: $($producto.nombre)"

# 3. Calentamiento: la primera petición de cada tipo es más lenta (la JVM aún está compilando). No se cuenta.
Write-Host 'Calentando el servidor...'
for ($i = 1; $i -le 2; $i++) {
    $p = Medir 'calentamiento' 'POST' '/pedidos' @{ numeroMesa = 99; lineas = @(@{ productoId = $producto.id; cantidad = 1 }); observaciones = 'Prueba de rendimiento RES-28' } $false
    foreach ($estado in 'EN_PREPARACION', 'LISTO', 'ENTREGADO') {
        $null = Medir 'calentamiento' 'PATCH' "/cocina/pedidos/$($p.id)/estado" @{ estado = $estado } $false
    }
    $null = Medir 'calentamiento' 'GET' '/cocina/comandas' $null $false
}

# 4. Mediciones
for ($i = 1; $i -le $Repeticiones; $i++) {
    Write-Progress -Activity 'Midiendo' -Status "Pedido $i de $Repeticiones" -PercentComplete ($i / $Repeticiones * 100)
    $p = Medir 'Crear pedido (mesero)' 'POST' '/pedidos' @{ numeroMesa = 99; lineas = @(@{ productoId = $producto.id; cantidad = 2; nota = 'prueba' }); observaciones = 'Prueba de rendimiento RES-28' }
    $null = Medir 'Consultar comandas (cocina)' 'GET' '/cocina/comandas' $null
    $null = Medir 'Cambiar a En preparación' 'PATCH' "/cocina/pedidos/$($p.id)/estado" @{ estado = 'EN_PREPARACION' }
    $null = Medir 'Cambiar a Listo' 'PATCH' "/cocina/pedidos/$($p.id)/estado" @{ estado = 'LISTO' }
    $visto = Medir 'Consultar pedido (mesero)' 'GET' "/pedidos/$($p.id)" $null
    if ($visto.estadoCocina -ne 'LISTO') { Write-Host "El pedido $($p.id) no quedó en LISTO" -ForegroundColor Red; exit 1 }
    $null = Medir 'Cambiar a Entregado' 'PATCH' "/cocina/pedidos/$($p.id)/estado" @{ estado = 'ENTREGADO' }
}
Write-Progress -Activity 'Midiendo' -Completed

# 5. Resumen por operación
$resumen = $medidas | Group-Object Operacion | ForEach-Object {
    $valores = [double[]]($_.Group | ForEach-Object Milisegundos)
    $p95 = Percentil $valores 95
    $max = ($valores | Measure-Object -Maximum).Maximum
    [pscustomobject]@{
        'Operación'     = $_.Name
        'Veces'         = $valores.Count
        'Promedio (ms)' = [math]::Round(($valores | Measure-Object -Average).Average, 1)
        'Mediana (ms)'  = Percentil $valores 50
        'P95 (ms)'      = $p95
        'Máximo (ms)'   = $max
        'Cumple'        = $(if ($max -le $LimiteMs) { 'Sí' } else { 'No' })
    }
}
$resumen | Format-Table -AutoSize

$archivo = Join-Path (Get-Location) 'resultados-rendimiento.csv'
$medidas | Export-Csv -Path $archivo -NoTypeInformation -Encoding UTF8
Write-Host "Detalle de cada petición: $archivo"

if (@($resumen | Where-Object { $_.Cumple -eq 'No' }).Count -eq 0) {
    Write-Host "RESULTADO: todas las operaciones respondieron en menos de $LimiteMs ms." -ForegroundColor Green
} else {
    Write-Host "RESULTADO: alguna operación superó $LimiteMs ms." -ForegroundColor Red
    exit 2
}
