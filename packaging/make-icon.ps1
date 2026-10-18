# Genera packaging/icon.ico (monograma blanco sobre negro redondeado) a partir del monograma de la app.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$mono = Join-Path $root 'app/src/main/resources/co/sv/consultorio/app/img/mono.png'
$out = Join-Path $PSScriptRoot 'icon.ico'

function New-IconPng([int]$size, [System.Drawing.Image]$src) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.Clear([System.Drawing.Color]::Transparent)
    $r = [int]($size * 0.18)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90)
    $path.AddArc($size - 2 * $r - 1, 0, 2 * $r, 2 * $r, 270, 90)
    $path.AddArc($size - 2 * $r - 1, $size - 2 * $r - 1, 2 * $r, 2 * $r, 0, 90)
    $path.AddArc(0, $size - 2 * $r - 1, 2 * $r, 2 * $r, 90, 90)
    $path.CloseFigure()
    $g.FillPath((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 11, 11, 12))), $path)

    $h = [int]($size * 0.74)
    $w = [int]($src.Width * $h / $src.Height)
    $x = [int](($size - $w) / 2)
    $y = [int](($size - $h) / 2)
    # Invierte el monograma negro a blanco conservando la transparencia.
    $cm = New-Object System.Drawing.Imaging.ColorMatrix
    $cm.Matrix00 = -1; $cm.Matrix11 = -1; $cm.Matrix22 = -1
    $cm.Matrix40 = 1; $cm.Matrix41 = 1; $cm.Matrix42 = 1
    $cm.Matrix33 = 1; $cm.Matrix44 = 1
    $ia = New-Object System.Drawing.Imaging.ImageAttributes
    $ia.SetColorMatrix($cm)
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle $x, $y, $w, $h), 0, 0, $src.Width, $src.Height, 'Pixel', $ia)
    $g.Dispose()
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return , $ms.ToArray()
}

$src = [System.Drawing.Image]::FromFile($mono)
$sizes = 16, 24, 32, 48, 64, 128, 256
$images = @()
foreach ($s in $sizes) { $images += , (New-IconPng $s $src) }
$src.Dispose()

$fs = [System.IO.File]::Create($out)
$bw = New-Object System.IO.BinaryWriter $fs
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]
    $dim = if ($s -ge 256) { 0 } else { $s }
    $bw.Write([byte]$dim); $bw.Write([byte]$dim); $bw.Write([byte]0); $bw.Write([byte]0)
    $bw.Write([UInt16]1); $bw.Write([UInt16]32)
    $bw.Write([UInt32]$images[$i].Length); $bw.Write([UInt32]$offset)
    $offset += $images[$i].Length
}
foreach ($img in $images) { $bw.Write($img) }
$bw.Close()
Write-Host "Icono creado: $out"
