<#
  Retro Arkáda – nahranie hier z PC na USB kľúč zapojený v Raspberry Pi (Android TV).

  Hry idú cez adb priamo do aplikácie Retro Arkáda a tá ich zapíše na USB kľúč
  (na RPi nemá adb sám prístup na USB). Na TV musí byť Retro Arkáda spustená.

  Použitie (PowerShell):
    .\nahraj-hry.ps1                                  # otvorí okno na výber priečinka s hrami
    .\nahraj-hry.ps1 -Zdroj "E:\Roms"                 # celý priečinok Roms (podpriečinky = konzoly)
    .\nahraj-hry.ps1 -Zdroj "E:\Snes" -Konzola snes   # všetky hry z priečinka ako SNES

  Hry, ktoré už na USB sú, sa preskočia – skript môžeš spúšťať opakovane.
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
if (-not (Test-Path -LiteralPath $Zdroj)) { Chyba "Priečinok $Zdroj neexistuje." }
$Zdroj = (Resolve-Path -LiteralPath $Zdroj).Path.TrimEnd('\')

# Spoj sa s webovým serverom Retro Arkády cez adb (funguje cez kábel aj sieť)
$lokalnyPort = 18080
$api = $null
foreach ($port in 8080, 8088, 8181, 9090) {
    adb forward "tcp:$lokalnyPort" "tcp:$port" | Out-Null
    try {
        $info = Invoke-RestMethod -Uri "http://127.0.0.1:$lokalnyPort/api/info" -TimeoutSec 5
        $api = "http://127.0.0.1:$lokalnyPort"
        break
    } catch { }
}
if (-not $api) { Chyba "Retro Arkáda neodpovedá. Spusti ju na TV a skús znova." }
if (-not $info.usb) {
    Chyba "Appka nevidí USB kľúč. Je zapojený? Má appka povolený prístup (Nastavenia → Prístup k USB a úložisku)?"
}

$maCurl = [bool](Get-Command curl.exe -ErrorAction SilentlyContinue)
$subory = @(Get-ChildItem -LiteralPath $Zdroj -File -Recurse | Where-Object { -not $_.Name.StartsWith(".") })
Write-Host "Nahrávam $($subory.Count) súborov z $Zdroj na USB kľúč v TV" -ForegroundColor Cyan

$nove = 0; $preskocene = 0; $chyby = 0; $i = 0
foreach ($f in $subory) {
    $i++
    # Konzola: parameter, alebo názov priečinka (napr. Roms\snes\hra.sfc -> snes), inak podľa prípony
    $konzola = if ($Konzola) { $Konzola } elseif ($f.DirectoryName -ne $Zdroj) { $f.Directory.Name } else { "auto" }
    $q = "name=$([uri]::EscapeDataString($f.Name))&system=$([uri]::EscapeDataString($konzola))&dest=usb"

    $ma = Invoke-RestMethod -Uri "$api/api/has?$q&size=$($f.Length)" -TimeoutSec 15
    if ($ma.exists) { $preskocene++; continue }

    $mb = [math]::Round($f.Length / 1MB, 1)
    Write-Host "[$i/$($subory.Count)] $($f.Name) ($mb MB)"
    try {
        if ($maCurl) {
            $odpoved = curl.exe -s -S --fail-with-body -T "$($f.FullName)" "$api/api/upload?$q" | ConvertFrom-Json
        } else {
            $odpoved = Invoke-RestMethod -Method Put -InFile $f.FullName -Uri "$api/api/upload?$q"
        }
        if ($odpoved.ok) { Write-Host "   ✓ $($odpoved.message)" -ForegroundColor Green; $nove++ }
        else { Write-Host "   ✗ $($odpoved.message)" -ForegroundColor Yellow; $chyby++ }
    } catch {
        Write-Host "   ✗ $($_.Exception.Message)" -ForegroundColor Yellow; $chyby++
    }
}
adb forward --remove "tcp:$lokalnyPort" | Out-Null

Write-Host ""
Write-Host "✓ Hotovo: nahraných $nove, už bolo na USB $preskocene, chýb $chyby." -ForegroundColor Green
Write-Host "  Hry sa v Retro Arkáde objavia hneď (knižnica sa obnoví sama)."
