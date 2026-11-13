param(
    [string[]]$MinecraftVersions = @('1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11'),
    [switch]$CompileOnly
)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    foreach ($minecraftTarget in $MinecraftVersions) {
        if ($minecraftTarget -eq '1.21.0') { $minecraftTarget = '1.21' }
        $buildArguments = @('build', "-PmcVersion=$minecraftTarget", '--project-cache-dir', ".gradle/versions/$minecraftTarget", '-PincludeLocalMods=false', '-PfastBuild=true', '--max-workers=1', '--no-daemon')
        if (-not $CompileOnly) { $buildArguments += '-PcompatSmoke=true' }
        & .\gradlew.bat @buildArguments
        if ($LASTEXITCODE -ne 0) { throw "Build or runtime smoke tests failed for Minecraft $minecraftTarget" }
        $releaseDirectory = Join-Path $PSScriptRoot 'build/releases'
        New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "build/versions/$minecraftTarget/libs") -Filter '*.jar' |
            Copy-Item -Destination $releaseDirectory -Force
    }
} finally {
    Pop-Location
}
