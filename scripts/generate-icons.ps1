Add-Type -AssemblyName System.Drawing

$root = "e:\lmis0822\260625\MyNFCApp"
$srcPath = Join-Path $root "icon.png"
$resRoot = Join-Path $root "platforms\android\app\src\main\res"
$wwwIcon = Join-Path $root "www\resources\icon.png"

if (-not (Test-Path $srcPath)) {
    Write-Error "Source icon not found: $srcPath"
    exit 1
}

function Save-Png($bitmap, $path) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-SquareCrop($image) {
    $side = [Math]::Min($image.Width, $image.Height)
    $x = [int](($image.Width - $side) / 2)
    $y = [int](($image.Height - $side) / 2)
    $bmp = New-Object System.Drawing.Bitmap $side, $side
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($image, 0, 0, (New-Object System.Drawing.Rectangle $x, $y, $side, $side), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return $bmp
}

function Resize-Image($image, $size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.DrawImage($image, 0, 0, $size, $size)
    $g.Dispose()
    return $bmp
}

function New-SolidBackground($size, $color) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($color)
    $g.Dispose()
    return $bmp
}

function New-AdaptiveForeground($sourceSquare, $size) {
    # 完整图标作为前景，略缩至安全区避免裁切文字
    $drawSize = [int]($size * 0.92)
    $offset = [int](($size - $drawSize) / 2)
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($sourceSquare, $offset, $offset, $drawSize, $drawSize)
    $g.Dispose()
    return $bmp
}

$srcImg = [System.Drawing.Image]::FromFile($srcPath)
$square = New-SquareCrop $srcImg
$srcImg.Dispose()

$sampleBmp = New-Object System.Drawing.Bitmap $square
$corner = $sampleBmp.GetPixel([Math]::Min(5, $square.Width - 1), [Math]::Min(5, $square.Height - 1))
$sampleBmp.Dispose()
$bgColor = [System.Drawing.Color]::FromArgb(255, $corner.R, $corner.G, $corner.B)

Write-Host "Source: $srcPath ($($square.Width)x$($square.Height)), bg=$($corner.R),$($corner.G),$($corner.B)"

# Cordova 源图标 512px
$master512 = Resize-Image $square 512
Save-Png $master512 $wwwIcon
$master512.Dispose()
Write-Host "Wrote $wwwIcon"

$densityMap = @{
    "ldpi"    = @{ launcher = 36; adaptive = 108 }
    "mdpi"    = @{ launcher = 48; adaptive = 108 }
    "hdpi"    = @{ launcher = 72; adaptive = 162 }
    "xhdpi"   = @{ launcher = 96; adaptive = 216 }
    "xxhdpi"  = @{ launcher = 144; adaptive = 324 }
    "xxxhdpi" = @{ launcher = 192; adaptive = 432 }
}

foreach ($entry in $densityMap.GetEnumerator()) {
    $density = $entry.Key
    $launcherSize = $entry.Value.launcher
    $adaptiveSize = $entry.Value.adaptive

    $launcher = Resize-Image $square $launcherSize
    Save-Png $launcher (Join-Path $resRoot "mipmap-$density\ic_launcher.png")
    $launcher.Dispose()

    $adBg = New-SolidBackground $adaptiveSize $bgColor
    Save-Png $adBg (Join-Path $resRoot "mipmap-$density-v26\ic_launcher_background.png")
    $adBg.Dispose()

    $adFg = New-AdaptiveForeground $square $adaptiveSize
    Save-Png $adFg (Join-Path $resRoot "mipmap-$density-v26\ic_launcher_foreground.png")
    $adFg.Dispose()

    Write-Host "Generated mipmap-$density ($launcherSize px) and v26 ($adaptiveSize px)"
}

$square.Dispose()
Write-Host "Done."
