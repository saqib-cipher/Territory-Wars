Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\sddrk\.gemini\antigravity-ide\brain\1590f7d5-bdbb-4496-a839-77e2edca932b\.user_uploaded\media_1787143225087.jpg"
$outDir = "C:\Users\sddrk\Documents\Projects\Territory Wars\android\app\src\main\res\drawable"

if (!(Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

$srcBitmap = New-Object System.Drawing.Bitmap($srcPath)
Write-Host "Source image size: $($srcBitmap.Width) x $($srcBitmap.Height)"

# Helper function to crop and remove dark background with smooth alpha feathering
function Get-AlphaCroppedBitmap {
    param(
        [int]$x,
        [int]$y,
        [int]$w,
        [int]$h,
        [int]$threshold = 14
    )

    if ($x + $w -gt $srcBitmap.Width) { $w = $srcBitmap.Width - $x }
    if ($y + $h -gt $srcBitmap.Height) { $h = $srcBitmap.Height - $y }

    $destBmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($destBmp)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
    $srcRect = New-Object System.Drawing.Rectangle($x, $y, $w, $h)
    $g.DrawImage($srcBitmap, $rect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()

    # Alpha keying for pure black background with glowing edge preservation
    for ($px = 0; $px -lt $w; $px++) {
        for ($py = 0; $py -lt $h; $py++) {
            $col = $destBmp.GetPixel($px, $py)
            $r = $col.R
            $gCol = $col.G
            $b = $col.B
            $brightness = [Math]::Max($r, [Math]::Max($gCol, $b))
            
            if ($brightness -le $threshold) {
                $destBmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            } elseif ($brightness -lt 60) {
                $alpha = [int](($brightness - $threshold) / (60 - $threshold) * 255)
                if ($alpha -gt 255) { $alpha = 255 }
                if ($alpha -lt 0) { $alpha = 0 }
                $destBmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb($alpha, $r, $gCol, $b))
            }
        }
    }
    return $destBmp
}

function Save-AssetPng {
    param([string]$name, [int]$x, [int]$y, [int]$w, [int]$h, [int]$threshold = 14)
    $bmp = Get-AlphaCroppedBitmap $x $y $w $h $threshold
    $destPath = Join-Path $outDir "$name.png"
    $bmp.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Saved PNG: $name.png ($w x $h)"
}

# Helper to create Android 9-patch (.9.png) with 1px black stretch bars
function Save-NinePatchAsset {
    param(
        [string]$name,
        [int]$x,
        [int]$y,
        [int]$w,
        [int]$h,
        [int]$stretchMarginLeft,
        [int]$stretchMarginRight,
        [int]$stretchMarginTop,
        [int]$stretchMarginBottom,
        [int]$threshold = 14
    )

    $contentBmp = Get-AlphaCroppedBitmap $x $y $w $h $threshold

    # 9-patch is (w+2) x (h+2)
    $npW = $w + 2
    $npH = $h + 2
    $npBmp = New-Object System.Drawing.Bitmap($npW, $npH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    # Initialize all transparent
    $g = [System.Drawing.Graphics]::FromImage($npBmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    # Draw content at (1,1)
    $g.DrawImage($contentBmp, 1, 1, $w, $h)
    $g.Dispose()
    $contentBmp.Dispose()

    $black = [System.Drawing.Color]::FromArgb(255, 0, 0, 0)

    # Draw top stretch line (y = 0)
    $stretchXStart = 1 + $stretchMarginLeft
    $stretchXEnd = $npW - 1 - $stretchMarginRight
    for ($px = $stretchXStart; $px -le $stretchXEnd; $px++) {
        $npBmp.SetPixel($px, 0, $black)
    }

    # Draw left stretch line (x = 0)
    $stretchYStart = 1 + $stretchMarginTop
    $stretchYEnd = $npH - 1 - $stretchMarginBottom
    for ($py = $stretchYStart; $py -le $stretchYEnd; $py++) {
        $npBmp.SetPixel(0, $py, $black)
    }

    # Draw bottom content padding line (y = npH - 1)
    for ($px = $stretchXStart; $px -le $stretchXEnd; $px++) {
        $npBmp.SetPixel($px, $npH - 1, $black)
    }

    # Draw right content padding line (x = npW - 1)
    for ($py = $stretchYStart; $py -le $stretchYEnd; $py++) {
        $npBmp.SetPixel($npW - 1, $py, $black)
    }

    $destPath = Join-Path $outDir "$name.9.png"
    $npBmp.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $npBmp.Dispose()
    Write-Host "Saved 9-Patch: $name.9.png"
}

# ── 1. AVATAR FRAMES ───────────────────────────────────────────────────────────
Save-AssetPng "frame_royal_crown" 10 10 225 190 14
Save-AssetPng "frame_star_champion" 245 5 215 195 14
Save-AssetPng "frame_neon_green" 480 30 155 170 14

# ── 2. CIRCULAR ACTION BUTTONS ────────────────────────────────────────────────
Save-AssetPng "ic_hud_mic_on" 920 120 70 70 14
Save-AssetPng "ic_hud_mic_off" 920 195 70 70 14
Save-AssetPng "ic_hud_speaker_on" 920 260 70 70 14
Save-AssetPng "ic_hud_speaker_off" 920 325 70 70 14
Save-AssetPng "ic_hud_add_friend" 820 385 70 70 14
Save-AssetPng "ic_hud_group" 915 385 70 70 14

# ── 3. GLOWING CARD BACKS & SECRET DECK ───────────────────────────────────────
Save-AssetPng "card_glow_blue" 10 210 175 155 14
Save-AssetPng "card_glow_purple" 195 210 175 155 14
Save-AssetPng "card_glow_amber" 380 210 185 155 14
Save-AssetPng "deck_secret_cards_glow" 365 370 200 130 14
Save-AssetPng "badge_champion_wings" 820 445 150 90 14

# ── 4. 9-PATCH STRETCHABLE BUTTONS & BUBBLES ─────────────────────────────────
# Green Pill (+ Add)
Save-NinePatchAsset "btn_pill_green_invite" 600 360 195 65 40 40 15 15 14
# Red Pill (Exit)
Save-NinePatchAsset "btn_pill_red_leave" 600 430 195 65 40 40 15 15 14

# Voice Wave Bubble
Save-NinePatchAsset "bubble_voice_waveform" 590 245 285 95 60 40 20 20 14

# Question Card Banners
Save-NinePatchAsset "banner_question_blue" 10 370 335 65 45 35 15 15 14
Save-NinePatchAsset "banner_question_purple" 10 440 335 65 45 35 15 15 14

# Top Player Banner
Save-NinePatchAsset "banner_player_header" 660 5 320 120 70 40 20 20 14

# Timer Capsule
Save-NinePatchAsset "hud_timer_capsule" 375 515 190 65 45 35 15 15 14

# Question Counter Bar
Save-NinePatchAsset "hud_question_counter" 575 515 195 65 45 35 15 15 14

# Energy Progress Bar
Save-NinePatchAsset "hud_energy_bar" 780 535 210 50 35 35 10 10 14

# Status Badges
Save-NinePatchAsset "badge_status_online" 18 595 95 45 25 20 10 10 14
Save-NinePatchAsset "badge_status_ingame" 120 595 105 45 25 20 10 10 14
Save-NinePatchAsset "badge_status_busy" 230 595 95 45 25 20 10 10 14
Save-NinePatchAsset "badge_status_offline" 335 595 105 45 25 20 10 10 14

# Glowing Stretch Capsules
Save-NinePatchAsset "bg_glow_capsule_blue" 460 590 150 65 35 35 15 15 14
Save-NinePatchAsset "bg_glow_capsule_amber" 625 590 160 65 35 35 15 15 14
Save-NinePatchAsset "bg_glow_capsule_purple" 805 590 180 65 35 35 15 15 14

$srcBitmap.Dispose()
Write-Host "All assets & 9-patches created!"
