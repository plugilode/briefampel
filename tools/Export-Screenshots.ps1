param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path -LiteralPath $ProjectRoot).Path
$referencePath = Join-Path $projectPath 'app/src/screenshotTestDebug/reference'
$outputPath = Join-Path $projectPath 'docs/screenshots'
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$titles = @{
    '01' = 'Start'; '02' = 'Brief vorbereitet'; '03' = 'Brief wird geprüft';
    '04' = 'Ergebnis Rot'; '05' = 'Ergebnis Gelb'; '06' = 'Ergebnis Grün';
    '07' = 'Antwort: Stichworte'; '08' = 'Antwort wird geschrieben'; '09' = 'Antwortentwurf';
    '10' = 'Text einfügen'; '11' = 'Einwilligung'; '12' = 'Einstellungen: Gemini';
    '13' = 'Datenschutz'; '14' = 'Originalbrief'; '15' = 'Verbindungsfehler'; '16' = 'Heller Modus';
    '17' = 'Rot: Details und Aktionen'; '18' = 'Gelb: Details und Aktionen'; '19' = 'Grün: Details und Aktionen';
    '20' = 'Einstellungen: Absender'; '21' = 'Datenschutz: unterer Bereich';
    '22' = 'Einwilligung: bestätigen'; '23' = 'Originalbrief: unterer Bereich'
}
$items = @(Get-ChildItem -LiteralPath $referencePath -Filter '*.png' -Recurse | ForEach-Object {
    if ($_.Name -match '_(\d{2}_.+?)_[0-9a-f]{8}_\d+\.png$') {
        $stem = $Matches[1]
        $number = $stem.Substring(0, 2)
        $filename = "$stem.png"
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $outputPath $filename) -Force
        [pscustomobject]@{ Number = $number; Title = $titles[$number]; File = $filename }
    }
} | Sort-Object Number)
if ($items.Count -ne 23) { throw "Expected 23 native captures, found $($items.Count)." }

# A contact sheet only arranges the original screenshots; it does not synthesize or redraw UI.
Add-Type -AssemblyName System.Drawing
function Write-ContactSheet([object[]]$Captures, [string]$Path, [string]$Subtitle) {
    $columns = 4
    $thumbWidth = 280
    $thumbHeight = 622
    $gutter = 24
    $header = 112
    $cellHeight = 674
    $rows = [int][Math]::Ceiling($Captures.Count / $columns)
    $canvas = [System.Drawing.Bitmap]::new($columns * ($thumbWidth + $gutter) + $gutter, $header + $rows * $cellHeight + $gutter)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    $graphics.Clear([System.Drawing.Color]::FromArgb(236, 238, 241))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $headingFont = [System.Drawing.Font]::new('Segoe UI', 27, [System.Drawing.FontStyle]::Bold)
    $captionFont = [System.Drawing.Font]::new('Segoe UI', 12, [System.Drawing.FontStyle]::Bold)
    $noteFont = [System.Drawing.Font]::new('Segoe UI', 12)
    $ink = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(18, 20, 23))
    try {
        $graphics.DrawString('Briefampel · App-Ansichten', $headingFont, $ink, 24, 15)
        $graphics.DrawString($Subtitle, $noteFont, $ink, 26, 66)
        for ($index = 0; $index -lt $Captures.Count; $index++) {
            $capture = $Captures[$index]
            $x = $gutter + ($index % $columns) * ($thumbWidth + $gutter)
            $y = $header + [int][Math]::Floor($index / $columns) * $cellHeight
            $source = [System.Drawing.Image]::FromFile((Join-Path $outputPath $capture.File))
            try {
                if ($source.Width -ne 1082 -or $source.Height -ne 2402) { throw "Unexpected screenshot size for $($capture.File)." }
                $graphics.DrawImage($source, [System.Drawing.Rectangle]::new($x, $y, $thumbWidth, $thumbHeight))
            } finally { $source.Dispose() }
            $graphics.DrawString("$($capture.Number) · $($capture.Title)", $captionFont, $ink, [System.Drawing.RectangleF]::new($x, $y + $thumbHeight + 8, $thumbWidth, 40))
        }
        $canvas.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose(); $canvas.Dispose(); $headingFont.Dispose(); $captionFont.Dispose(); $noteFont.Dispose(); $ink.Dispose()
    }
}
Write-ContactSheet -Captures $items -Path (Join-Path $outputPath 'alle-ansichten.png') -Subtitle '23 native Compose-Renderings mit Beispieldaten · 27.10.2026'
$main = @($items | Where-Object Number -In @('01', '04', '05', '06', '07', '09', '10', '12'))
Write-ContactSheet -Captures $main -Path (Join-Path $outputPath 'uebersicht.png') -Subtitle 'Auswahl aus 23 Screenshots · Vollauflösung und weitere Ansichten in der Galerie'

$figures = ($items | ForEach-Object {
    $caption = [System.Net.WebUtility]::HtmlEncode("$($_.Number) · $($_.Title)")
    "<figure><a href='$($_.File)' target='_blank' rel='noopener'><img loading='lazy' src='$($_.File)' alt='$caption'></a><figcaption>$caption</figcaption><a class='download' href='$($_.File)' download>PNG herunterladen ↗</a></figure>"
}) -join "`n"
$html = @'
<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Briefampel · Screenshots</title>
<style>*{box-sizing:border-box}body{margin:0;background:#eceef1;color:#121417;font:16px/1.5 system-ui,sans-serif}header,main,footer{max-width:1500px;margin:auto;padding:32px}header{padding-bottom:8px}h1{font-size:clamp(28px,4vw,48px);letter-spacing:-.04em;margin:0 0 8px}p{max-width:900px;color:#4e545c}nav{display:flex;gap:12px;flex-wrap:wrap;margin-top:24px}nav a{background:#121417;color:#f3f0e8;border-radius:30px;padding:12px 18px;text-decoration:none;font-weight:650}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:28px;align-items:start}figure{margin:0;background:#fff;border:1px solid #d3d6db;border-radius:18px;padding:12px}img{display:block;width:100%;height:auto;border-radius:12px;background:#121417}figcaption{font-weight:700;margin-top:12px}.download{display:inline-block;color:#4e545c;margin-top:8px;font-size:14px}footer{padding-top:0;font-size:14px;color:#4e545c}a:focus-visible{outline:3px solid #121417;outline-offset:4px}</style>
<header><h1>Briefampel · alle App-Ansichten</h1><p>23 Screenshots aus der nativen Compose-Oberfläche, gerendert mit Android Layoutlib in 1082 × 2402 Pixeln. Alle Briefdaten und Antworten sind Beispiele. Lange Seiten sind zusätzlich im unteren Scrollbereich abgebildet.</p><p>Dies sind lokale Renderings, keine Aufnahmen eines angeschlossenen Pixels. Dialoginhalte verwenden dieselben App-Komponenten in einem statischen Material-Vorschau-Rahmen. Externe Android-Dateiauswahl und Google-Dokument-Scanner sind nicht enthalten.</p><nav><a href="alle-ansichten.png" target="_blank">Gesamtübersicht öffnen</a><a href="../briefampel-screenshots.zip" download>Alle Screenshots als ZIP</a></nav></header>
<main>{{FIGURES}}</main><footer>Erstellt am 27.10.2026 · Bilder anklicken, um die volle Auflösung zu öffnen. Die Galerie funktioniert vollständig lokal und lädt keine externen Inhalte.</footer></html>
'@
$html.Replace('{{FIGURES}}', $figures) | Set-Content -LiteralPath (Join-Path $outputPath 'index.html') -Encoding utf8
$manifest = [ordered]@{ date = '2026-10-27'; renderer = 'Android Layoutlib / Compose Preview Screenshot Testing'; width = 1082; height = 2402; screenshots = $items }
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputPath 'manifest.json') -Encoding utf8
@'
# Briefampel · Screenshots

Die Galerie in index.html lokal öffnen oder die PNG-Dateien direkt ansehen.

23 native Compose-Renderings mit Beispieldaten, 1082 × 2402 Pixel. Kein angeschlossenes Gerät erforderlich. Dialoginhalte werden mit den tatsächlichen App-Komponenten in einem statischen Material-Vorschau-Rahmen gerendert. Die Android-Dateiauswahl und der Google-Dokument-Scanner gehören zu externen Systemoberflächen und sind nicht enthalten.

Einige Ansichten zeigen den oberen und andere zusätzlich den unteren Scrollbereich. Die beiden Übersichtsdateien ordnen die unveränderten Screenshots als Kontaktbogen an.

Neu erzeugen (im Projektverzeichnis):
  .\gradlew.bat :app:updateDebugScreenshotTest
  .\tools\Export-Screenshots.ps1
'@ | Set-Content -LiteralPath (Join-Path $outputPath 'README.md') -Encoding utf8
Compress-Archive -Path (Join-Path $outputPath '*') -DestinationPath (Join-Path $projectPath 'docs/briefampel-screenshots.zip') -Force
Write-Output "Exported $($items.Count) screenshots to $outputPath"
