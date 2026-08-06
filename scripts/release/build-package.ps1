param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ConfigPath = ".release/publish-config.json",
    [string]$OutputDir = "artifacts",
    [string]$ArtifactPathOutputFile = "",
    [bool]$SkipTests = $false
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Release config '$ConfigPath' was not found."
}

$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$normalizedVersion = (($Version.Trim()) -replace "^v", "")
$artifactName = "$($config.artifactNameTemplate)".Replace("{version}", $normalizedVersion)
$artifactPath = Join-Path $OutputDir $artifactName

if ([string]::IsNullOrWhiteSpace($normalizedVersion)) {
    throw "Version cannot be empty."
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$gradleArgs = @("--no-daemon", "clean", "check", "jar")
if ($SkipTests) {
    $gradleArgs = @("--no-daemon", "clean", "jar")
}

& .\gradlew.bat @gradleArgs
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

$builtArtifact = Join-Path "build\libs" $artifactName
if (-not (Test-Path -LiteralPath $builtArtifact -PathType Leaf)) {
    throw "Expected Gradle artifact '$builtArtifact' was not found."
}

Copy-Item -LiteralPath $builtArtifact -Destination $artifactPath -Force
$resolvedArtifactPath = (Resolve-Path -LiteralPath $artifactPath).Path

if ((Get-Item -LiteralPath $resolvedArtifactPath).Length -le 0) {
    throw "Artifact '$resolvedArtifactPath' is empty."
}

Write-Host "Built artifact: $resolvedArtifactPath"
if (-not [string]::IsNullOrWhiteSpace($ArtifactPathOutputFile)) {
    Set-Content -LiteralPath $ArtifactPathOutputFile -Value $resolvedArtifactPath -Encoding utf8NoBOM
}
if ($env:GITHUB_OUTPUT) {
    "artifact_path=$resolvedArtifactPath" | Out-File -FilePath $env:GITHUB_OUTPUT -Append -Encoding utf8
}

Write-Output $resolvedArtifactPath
