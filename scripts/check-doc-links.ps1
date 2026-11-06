$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$files = @(
    Get-Item -LiteralPath (Join-Path $repositoryRoot 'AGENTS.md')
    Get-Item -LiteralPath (Join-Path $repositoryRoot 'README.md')
    Get-Item -LiteralPath (Join-Path $repositoryRoot '.github/pull_request_template.md')
    Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'docs') -Filter '*.md' -File -Recurse
)
$linkCount = 0
foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrWhiteSpace($content)) { throw "Empty document: $($file.Name)" }
    foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^(https?://|mailto:|#)') { continue }
        $relativePath = ($target -split '#', 2)[0]
        if (-not (Test-Path -LiteralPath (Join-Path $file.DirectoryName $relativePath))) {
            throw "Broken relative link in $($file.Name): $target"
        }
        $linkCount++
    }
}
Write-Output "PASS: $($files.Count) nonempty documents; $linkCount relative file links resolve. Remote URLs and anchors are not checked."
