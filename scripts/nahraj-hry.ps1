<#
  Retro Arkáda – nahranie hier z PC na USB kľúč zapojený v Raspberry Pi (Android TV) cez adb.

  Použitie (PowerShell):
    .\nahraj-hry.ps1                         # otvorí okno na výber priečinka s hrami
    .\nahraj-hry.ps1 -Zdroj "E:\Roms"        # nahrá celý priečinok Roms (aj s podpriečinkami konzol)
    .\nahraj-hry.ps1 -Zdroj "E:\Snes" -Konzola snes   # nahrá hry do Roms/snes

  Nahrávajú sa len nové a zmenené súbory, takže skript môžeš spúšťať opakovane.
#>
param(
    [string]$Zdroj = "",
    [string]$Konzola = ""
)

function Chyba($text) { Write-Host "✗ $text" -ForegroundColor Red; exit 1 }

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Chyba "Nenašiel som adb. Nainštaluj Android Platform Tools a pridaj ich do PATH."
}
if (-not ((adb devices) -match "`tdevice$")) {
    Chyba "TV nie je pripojená cez adb. Spusti najprv: adb connect <IP-adresa-TV>"
}

if (-not $Zdroj) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
    $dialog.Description = "Vyber priečinok s hrami (napr. Roms alebo snes)"
    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { exit 0 }
    $Zdroj = $dialog.SelectedPath
}
if (-not (Test-Path $Zdroj)) { Chyba "Priečinok $Zdroj neexistuje." }

# USB kľúče sú v /storage pod názvom ako 1A2B-3C4D (emulated = interná pamäť)
$volumes = @(adb shell ls /storage 2>$null | ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and $_ -notin @("emulated", "self") })

if ($volumes.Count -eq 0) {
    Chyba "Nenašiel som USB kľúč. Je zapojený v RPi? (adb shell ls /storage)"
} elseif ($volumes.Count -eq 1) {
    $usb = $volumes[0]
} else {
    Write-Host "Našiel som viac úložísk:"
    for ($i = 0; $i -lt $volumes.Count; $i++) { Write-Host "  [$($i + 1)] /storage/$($volumes[$i])" }
    $volba = Read-Host "Ktoré použiť (číslo)"
    $usb = $volumes[[int]$volba - 1]
}

$ciel = "/storage/$usb/Roms"
if ($Konzola) { $ciel = "$ciel/$Konzola" }

adb shell "mkdir -p '$ciel' && touch '$ciel/.zapis' && rm '$ciel/.zapis'" 2>$null
if ($LASTEXITCODE -ne 0) {
    Chyba "Na USB kľúč sa nedá zapisovať cez adb. Skopíruj hry na kľúč priamo v PC."
}

Write-Host "Nahrávam $Zdroj  →  $ciel" -ForegroundColor Cyan
$polozky = Get-ChildItem -LiteralPath $Zdroj
$i = 0
foreach ($p in $polozky) {
    $i++
    Write-Host "[$i/$($polozky.Count)] $($p.Name)"
    adb push --sync "$($p.FullName)" "$ciel/" | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "   ✗ nepodarilo sa nahrať" -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "✓ Hotovo. V Retro Arkáde daj Nastavenia → Obnoviť knižnicu (alebo appku zavri a otvor)." -ForegroundColor Green
