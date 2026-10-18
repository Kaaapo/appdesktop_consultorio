# Empaqueta la app con jpackage.
#   .\packaging\build-installer.ps1            -> carpeta portable dist\Consultorio SV\ (no requiere WiX)
#   .\packaging\build-installer.ps1 -Type exe  -> instalador .exe (requiere WiX Toolset 3.x en el PATH)
#   .\packaging\build-installer.ps1 -Type msi  -> instalador .msi (requiere WiX Toolset 3.x en el PATH)
param(
    [ValidateSet('app-image', 'exe', 'msi')]
    [string]$Type = 'app-image'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$name = 'Consultorio SV'
$version = '1.0.0'
$dist = Join-Path $root 'dist'

$jpackage = 'jpackage'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) {
    $jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
}

Write-Host '1/3 Compilando y probando...'
mvn -q clean install
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o los tests.' }

$icon = Join-Path $PSScriptRoot 'icon.ico'
if (-not (Test-Path $icon)) {
    & (Join-Path $PSScriptRoot 'make-icon.ps1')
}

Write-Host "2/3 Empaquetando ($Type)..."
if (Test-Path (Join-Path $dist $name)) { Remove-Item (Join-Path $dist $name) -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$common = @(
    '--type', $Type,
    '--name', $name,
    '--app-version', $version,
    '--vendor', 'Stephany Valencia',
    '--description', 'Control de pacientes, citas y avisos por WhatsApp',
    '--input', 'app\target\lib',
    '--main-jar', 'consultorio.jar',
    '--main-class', 'co.sv.consultorio.app.Launcher',
    '--icon', $icon,
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '-Duser.language=es',
    '--java-options', '-Duser.country=CO',
    '--java-options', '-Duser.timezone=America/Bogota',
    '--java-options', '-Dprism.lcdtext=false',
    '--dest', $dist
)
if ($Type -ne 'app-image') {
    $common += @(
        '--win-menu', '--win-menu-group', $name,
        '--win-shortcut', '--win-dir-chooser',
        '--win-upgrade-uuid', '6f1c2a9e-4d1b-4c55-9a51-5e0c0a7b3d21'
    )
}
& $jpackage @common
if ($LASTEXITCODE -ne 0) { throw 'jpackage falló.' }

Write-Host '3/3 Listo.'
if ($Type -eq 'app-image') {
    Write-Host "Programa portable en: $(Join-Path $dist $name)\$name.exe"
} else {
    Write-Host "Instalador en: $dist"
}
