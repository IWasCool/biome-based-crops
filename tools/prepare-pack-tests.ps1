<#
.SYNOPSIS
Build isolated, hash-verified Create New World crop compatibility test fixtures.
.DESCRIPTION
Reads installed instances and the bundled pack-test-fixture-rules.json. Writes only
under this workspace's build/compat. Unexpected mod files are rejected; existing
runtime-generated configs are preserved and hash audited.
Run while the test fixtures are idle; no sibling project or its build cache is needed.
.EXAMPLE
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prepare-pack-tests.ps1
#>
[CmdletBinding()]
param(
    [string]$ClientInstance = (Join-Path $env:USERPROFILE 'curseforge/minecraft/Instances/Create New World  A Locked Progression Experience'),
    [string]$ServerInstance = (Join-Path $env:USERPROFILE 'curseforge/minecraft/Instances/Server Pack v4.1.0+'),
    [string]$AuditRules = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem

$script:Workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:BuildRoot = [System.IO.Path]::GetFullPath((Join-Path $script:Workspace 'build'))
$script:BuildPrefix = $script:BuildRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
$script:FixtureRoot = [System.IO.Path]::GetFullPath((Join-Path $script:BuildRoot 'compat'))
if ([string]::IsNullOrWhiteSpace($AuditRules)) {
    $AuditRules = Join-Path $PSScriptRoot 'pack-test-fixture-rules.json'
}

function Assert-FixturePath([string]$Path) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($script:BuildPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Fixture output must remain inside this workspace's build directory: $fullPath"
    }
    # Reject junctions/symlinks so a valid-looking output cannot redirect writes elsewhere.
    $probe = $fullPath
    while ($probe.StartsWith($script:BuildPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or $probe -eq $script:BuildRoot) {
        if (Test-Path -LiteralPath $probe) {
            $item = Get-Item -LiteralPath $probe -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Fixture output traverses a reparse point: $probe"
            }
        }
        $probe = [System.IO.Path]::GetDirectoryName($probe)
    }
    return $fullPath
}

function New-FixtureDirectory([string]$Path) {
    $checked = Assert-FixturePath $Path
    [System.IO.Directory]::CreateDirectory($checked) | Out-Null
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Get-StreamSha256([System.IO.Stream]$Stream) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha.ComputeHash($Stream)).Replace('-', '')
    } finally {
        $sha.Dispose()
    }
}

function Get-RelativePath([string]$Root, [string]$Path) {
    return $Path.Substring($Root.TrimEnd('\', '/').Length + 1).Replace('\', '/')
}

function Assert-NoExtraFiles([string]$Directory, [string[]]$Expected) {
    $checked = Assert-FixturePath $Directory
    if (-not (Test-Path -LiteralPath $checked)) { return }
    $allowed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($name in $Expected) { [void]$allowed.Add($name.Replace('\', '/')) }
    foreach ($file in @(Get-ChildItem -LiteralPath $checked -Recurse -File -Force)) {
        [void](Assert-FixturePath $file.FullName)
        $relative = Get-RelativePath $checked $file.FullName
        if (-not $allowed.Contains($relative)) {
            throw "Unexpected existing fixture file; nothing will be deleted automatically: $($file.FullName)"
        }
    }
}

function Get-ZipInventory([string]$Path) {
    $inventory = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            $stream = $entry.Open()
            try { $hash = Get-StreamSha256 $stream } finally { $stream.Dispose() }
            $inventory.Add($entry.FullName, [PSCustomObject]@{ length = $entry.Length; sha256 = $hash })
        }
    } finally { $zip.Dispose() }
    return ,$inventory
}

function Read-ZipDataScope($Zip, [string]$Prefix, $Results) {
    foreach ($entry in $Zip.Entries) {
        if ($entry.FullName.StartsWith('data/', [System.StringComparison]::Ordinal) -and -not $entry.FullName.EndsWith('/')) {
            $stream = $entry.Open()
            try { $hash = Get-StreamSha256 $stream } finally { $stream.Dispose() }
            $text = $null
            if ($entry.FullName -eq $script:PackRules.approved_excluded_data_resource.path) {
                $reader = [System.IO.StreamReader]::new($entry.Open())
                try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
            $Results.Add([PSCustomObject]@{ path = $Prefix + $entry.FullName; length = $entry.Length; sha256 = $hash; content = $text })
        }
        if ($entry.FullName.EndsWith('.jar', [System.StringComparison]::OrdinalIgnoreCase)) {
            $memory = [System.IO.MemoryStream]::new()
            $source = $entry.Open()
            try { $source.CopyTo($memory) } finally { $source.Dispose() }
            $memory.Position = 0
            $nested = [System.IO.Compression.ZipArchive]::new($memory, [System.IO.Compression.ZipArchiveMode]::Read, $true)
            try { Read-ZipDataScope $nested ($Prefix + $entry.FullName + '!') $Results }
            finally { $nested.Dispose(); $memory.Dispose() }
        }
    }
}

function Get-ExclusionAudit([string]$Source, $Prior) {
    $hash = Get-Sha256 $Source
    if ($hash -ne $Prior.source_sha256) { throw "Excluded installed JAR differs from its audited version: $Source" }
    $data = [System.Collections.Generic.List[object]]::new()
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Source)
    try { Read-ZipDataScope $zip '' $data } finally { $zip.Dispose() }
    foreach ($resource in $data) {
        if ($resource.path -ne $script:PackRules.approved_excluded_data_resource.path) {
            throw "Excluded client JAR has unapproved data scope; review before omitting it: $Source :: $($resource.path)"
        }
        $json = $resource.content | ConvertFrom-Json
        $approved = $script:PackRules.approved_excluded_data_resource
        if (@($json.values).Count -ne 1 -or $json.values[0] -ne $approved.values[0] -or $json.replace -ne $approved.replace) {
            throw "Excluded zoom tag changed its approved spyglass-only scope: $Source"
        }
    }
    return [PSCustomObject]@{
        jar = [System.IO.Path]::GetFileName($Source)
        source = $Source
        source_sha256 = $hash
        reason = 'Audited presentation/diagnostic-only client JAR omitted for headless gameplay tests'
        data_resources_including_nested_jars = @($data.ToArray())
        data_resource_count = $data.Count
        crops_or_biomes_removed = $false
        data_scope_verified = $true
    }
}

function Write-FixtureZip([string]$Source, [string]$Destination, [string[]]$Remove, [hashtable]$Replacements) {
    [void](Assert-FixturePath $Destination)
    $sourceZip = [System.IO.Compression.ZipFile]::OpenRead($Source)
    $output = [System.IO.File]::Create($Destination)
    $zip = [System.IO.Compression.ZipArchive]::new($output, [System.IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        foreach ($entry in $sourceZip.Entries) {
            if ($Remove -ccontains $entry.FullName) { continue }
            $compression = if ($entry.Length -eq 0) { [System.IO.Compression.CompressionLevel]::NoCompression } else { [System.IO.Compression.CompressionLevel]::Optimal }
            $targetEntry = $zip.CreateEntry($entry.FullName, $compression)
            $targetEntry.LastWriteTime = $entry.LastWriteTime
            $targetEntry.ExternalAttributes = $entry.ExternalAttributes
            $outputStream = $targetEntry.Open()
            try {
                if ($Replacements.ContainsKey($entry.FullName)) {
                    [byte[]]$replacementBytes = $Replacements[$entry.FullName]
                    $outputStream.Write($replacementBytes, 0, $replacementBytes.Length)
                } else {
                    $inputStream = $entry.Open()
                    try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose() }
                }
            } finally { $outputStream.Dispose() }
        }
    } finally { $zip.Dispose(); $output.Dispose(); $sourceZip.Dispose() }
}

function Copy-VerifiedJar([System.IO.FileInfo]$Source, [string]$Directory, $KitchenAudit) {
    $destination = Assert-FixturePath (Join-Path $Directory $Source.Name)
    $sourceHash = Get-Sha256 $Source.FullName
    Copy-Item -LiteralPath $Source.FullName -Destination $destination -Force
    $kitchen = $null
    if ($Source.Name -eq $KitchenAudit.jar) {
        if ($sourceHash -ne $KitchenAudit.source_sha256) { throw 'Central Kitchen source no longer matches the audited version' }
        $sourceEntries = Get-ZipInventory $Source.FullName
        $remove = @($KitchenAudit.removed_test_only_entries)
        if ($remove.Count -ne 10 -or @($remove | Where-Object { $_.EndsWith('.class') }).Count -ne 9) {
            throw 'Expected exactly nine audited Central Kitchen GameTest classes plus their directory'
        }
        foreach ($name in $remove) {
            if (-not $sourceEntries.ContainsKey($name)) { throw "Audited Central Kitchen test entry is missing: $name" }
        }
        # Recreate the isolated ZIP: .NET Framework's Update mode can rewrite empty
        # directory entries with inconsistent compression metadata.
        Write-FixtureZip $Source.FullName $destination $remove @{}
        $fixtureEntries = Get-ZipInventory $destination
        $retained = [System.Collections.Generic.List[object]]::new()
        foreach ($name in $sourceEntries.Keys) {
            if ($remove -ccontains $name) {
                if ($fixtureEntries.ContainsKey($name)) { throw "Central Kitchen test entry was not removed: $name" }
                continue
            }
            if (-not $fixtureEntries.ContainsKey($name) -or $sourceEntries[$name].sha256 -ne $fixtureEntries[$name].sha256 -or
                    $sourceEntries[$name].length -ne $fixtureEntries[$name].length) {
                throw "Central Kitchen retained ZIP bytes changed: $name"
            }
            $retained.Add([PSCustomObject]@{ path = $name; source_sha256 = $sourceEntries[$name].sha256; fixture_sha256 = $fixtureEntries[$name].sha256; exact_match = $true })
        }
        if ($fixtureEntries.Count -ne $sourceEntries.Count - $remove.Count) { throw 'Unexpected added Central Kitchen ZIP entries' }
        $kitchen = [PSCustomObject]@{
            source_entry_count = $sourceEntries.Count
            fixture_entry_count = $fixtureEntries.Count
            removed_test_only_entries = $remove
            retained_entries = @($retained.ToArray() | Sort-Object path)
            all_retained_entries_byte_identical = $true
            all_gameplay_resources_and_classes_preserved = $true
        }
    } elseif ($Source.Name -like 'neruina-*.jar') {
        $rules = @($script:PackRules.neruina_farmland_test_filters | Where-Object { $_.jar -eq $Source.Name })
        if ($rules.Count -ne 1 -or $sourceHash -ne $rules[0].source_sha256) { throw "Neruina source no longer matches its pinned development-test audit: $($Source.Name)" }
        $rule = $rules[0]
        $sourceEntries = Get-ZipInventory $Source.FullName
        $sourceZip = [System.IO.Compression.ZipFile]::OpenRead($Source.FullName)
        try {
            $reader = [System.IO.StreamReader]::new($sourceZip.GetEntry($rule.mixin_config).Open())
            try { $originalJson = $reader.ReadToEnd() } finally { $reader.Dispose() }
            $classStream = $sourceZip.GetEntry($rule.annotated_class).Open()
            $classBytes = [System.IO.MemoryStream]::new()
            try { $classStream.CopyTo($classBytes) } finally { $classStream.Dispose() }
            try { $classText = [System.Text.Encoding]::UTF8.GetString($classBytes.ToArray()) } finally { $classBytes.Dispose() }
            if (-not $classText.Contains($rule.required_annotation_descriptor)) { throw 'Pinned Neruina FarmBlock test mixin lacks its DevOnly annotation' }
        } finally { $sourceZip.Dispose() }
        $pattern = '(?m)^[\t ]*"' + [regex]::Escape($rule.excluded_mixin) + '",\r?\n'
        if ([regex]::Matches($originalJson, $pattern).Count -ne 1) { throw 'Expected exactly one Neruina FarmBlock test mixin JSON line' }
        $filteredJson = [regex]::Replace($originalJson, $pattern, '')
        $originalMixins = @((ConvertFrom-Json $originalJson).mixins)
        $filteredMixins = @((ConvertFrom-Json $filteredJson).mixins)
        if ($filteredMixins.Count -ne $originalMixins.Count - 1 -or $filteredMixins -contains $rule.excluded_mixin) { throw 'Neruina test mixin JSON filtering failed' }
        $replacements = @{}
        $replacements[$rule.mixin_config] = [System.Text.Encoding]::UTF8.GetBytes($filteredJson)
        Write-FixtureZip $Source.FullName $destination @() $replacements
        $fixtureEntries = Get-ZipInventory $destination
        if ($fixtureEntries.Count -ne $sourceEntries.Count) { throw 'Neruina fixture added or removed ZIP entries' }
        $unchanged = [System.Collections.Generic.List[object]]::new()
        foreach ($name in $sourceEntries.Keys) {
            if (-not $fixtureEntries.ContainsKey($name)) { throw "Neruina fixture lost ZIP entry $name" }
            if ($name -ceq $rule.mixin_config) { continue }
            if ($sourceEntries[$name].sha256 -ne $fixtureEntries[$name].sha256 -or $sourceEntries[$name].length -ne $fixtureEntries[$name].length) {
                throw "Neruina non-config ZIP bytes changed: $name"
            }
            $unchanged.Add([PSCustomObject]@{path=$name;source_sha256=$sourceEntries[$name].sha256;fixture_sha256=$fixtureEntries[$name].sha256;exact_match=$true})
        }
        $kitchen = [PSCustomObject]@{
            source_entry_count = $sourceEntries.Count
            fixture_entry_count = $fixtureEntries.Count
            removed_test_only_entries = @()
            changed_entries = @([PSCustomObject]@{path=$rule.mixin_config;source_sha256=$sourceEntries[$rule.mixin_config].sha256;fixture_sha256=$fixtureEntries[$rule.mixin_config].sha256;removed_mixin=$rule.excluded_mixin})
            retained_entries = @($unchanged.ToArray() | Sort-Object path)
            all_other_entries_byte_identical = $true
            all_classfiles_byte_identical = $true
            all_gameplay_resources_and_classes_preserved = $true
            reason = 'Only the DevOnly FarmBlock deliberate crash registration is omitted, matching its production gate for real farmland hydration tests. All production Neruina catchers and classfiles are retained.'
        }
    }
    $fixtureHash = Get-Sha256 $destination
    if ($null -eq $kitchen -and $fixtureHash -ne $sourceHash) { throw "Fixture JAR copy does not match its source: $($Source.Name)" }
    if ((Get-Sha256 $Source.FullName) -ne $sourceHash) { throw "Installed source changed during fixture preparation: $($Source.Name)" }
    return [PSCustomObject]@{
        jar = $Source.Name
        source = $Source.FullName
        fixture = $destination
        source_sha256 = $sourceHash
        fixture_sha256 = $fixtureHash
        exact_complete_jar_match = ($null -eq $kitchen)
        test_only_zip_filter = $kitchen
        installed_source_unchanged = $true
    }
}

# Validate output before any recursive traversal or writes. Production instances are read only.
[void](Assert-FixturePath $script:FixtureRoot)
$ClientInstance = (Resolve-Path -LiteralPath $ClientInstance).Path
$ServerInstance = (Resolve-Path -LiteralPath $ServerInstance).Path
$script:PackRules = Get-Content -LiteralPath $AuditRules -Raw | ConvertFrom-Json
if ($script:PackRules.schema_version -ne 1) { throw 'Unsupported fixture audit rules schema' }
$kitchenPrior = $script:PackRules.central_kitchen
$presentation = @($script:PackRules.excluded_client_presentation_jars)
if ($presentation.Count -ne 26) { throw 'Expected exactly 26 previously audited client presentation exclusions' }
if (@($presentation | Where-Object { $_.jar -like 'create_new_world_core-*' }).Count -ne 0) {
    throw 'Installed Create New World Core must be retained in this crop-mod fixture'
}

$clientMods = @(Get-ChildItem -LiteralPath (Join-Path $ClientInstance 'mods') -File -Filter '*.jar' | Sort-Object Name)
$serverMods = @(Get-ChildItem -LiteralPath (Join-Path $ServerInstance 'mods') -File -Filter '*.jar' | Sort-Object Name)
if ($clientMods.Count -ne $script:PackRules.expected_client_jar_count -or $serverMods.Count -ne $script:PackRules.expected_server_jar_count) {
    throw "Installed inventories changed: client=$($clientMods.Count), server=$($serverMods.Count)"
}
$excludedClient = @($presentation | ForEach-Object { Get-ExclusionAudit (Join-Path $ClientInstance ('mods/' + $_.jar)) $_ })

$sourceConfig = Join-Path $ClientInstance 'config'
$configCandidates = @(
    Get-ChildItem -LiteralPath $sourceConfig -File
    foreach ($subdirectory in @('biomesoplenty', 'toughasnails')) {
        $subpath = Join-Path $sourceConfig $subdirectory
        if (Test-Path -LiteralPath $subpath) { Get-ChildItem -LiteralPath $subpath -Recurse -File }
    }
) | Where-Object { $_.Extension -match '^\.(toml|json|ya?ml)$' }
$configFiles = @($configCandidates | Where-Object { $_.Name -notmatch 'client' -and $_.Name -notlike 'biome_based_crops-*' } | Sort-Object FullName)
$excludedConfigs = @($configCandidates | Where-Object { $_.Name -match 'client' -or $_.Name -like 'biome_based_crops-*' } | ForEach-Object {
    [PSCustomObject]@{ path = Get-RelativePath $sourceConfig $_.FullName; reason = 'Client-specific or installed crop configuration excluded; workspace crop config is preserved' }
})
$configNames = @($configFiles | ForEach-Object { Get-RelativePath $sourceConfig $_.FullName })

foreach ($fixture in @(
        [PSCustomObject]@{ name = 'server'; source = $ServerInstance; mods = $serverMods; presentation = @() },
        [PSCustomObject]@{ name = 'client'; source = $ClientInstance; mods = $clientMods; presentation = $excludedClient })) {
    $directory = Assert-FixturePath (Join-Path $script:FixtureRoot $fixture.name)
    $modsDirectory = Assert-FixturePath (Join-Path $directory 'mods')
    $gameDirectory = Assert-FixturePath (Join-Path $directory 'run')
    $configDirectory = Assert-FixturePath (Join-Path $gameDirectory 'config')
    $omittedOwn = @($fixture.mods | Where-Object { $_.Name -like 'biome_based_crops-*.jar' })
    if ($omittedOwn.Count -ne 1) { throw "Expected one installed crop mod in $($fixture.name)" }
    $exclusionNames = @($fixture.presentation | ForEach-Object { $_.jar })
    $retained = @($fixture.mods | Where-Object { $_.Name -notlike 'biome_based_crops-*.jar' -and $exclusionNames -notcontains $_.Name })
    if ($retained.Count -ne $script:PackRules.expected_retained_jar_count -or
            @($retained | Where-Object { $_.Name -eq $script:PackRules.retained_core_jar }).Count -ne 1) {
        throw "Expected $($script:PackRules.expected_retained_jar_count) retained JARs including $($script:PackRules.retained_core_jar) for $($fixture.name)"
    }
    foreach ($required in @($script:PackRules.required_retained_jars)) {
        $requiredFiles = @($retained | Where-Object { $_.Name -eq $required.jar })
        if ($requiredFiles.Count -ne 1 -or (Get-Sha256 $requiredFiles[0].FullName) -ne $required.source_sha256) {
            throw "Required retained installed JAR differs from its pinned audit: $($required.jar)"
        }
    }
    Assert-NoExtraFiles $modsDirectory @($retained | ForEach-Object { $_.Name })
    # Test runs generate additional config files. Preserve and audit them, including
    # this workspace's fresh crop server config, instead of copying an installed one.
    $preservedConfigs = @()
    if (Test-Path -LiteralPath $configDirectory) {
        $preservedConfigs = @(Get-ChildItem -LiteralPath $configDirectory -Recurse -File -Force | ForEach-Object {
            [void](Assert-FixturePath $_.FullName)
            $relative = Get-RelativePath $configDirectory $_.FullName
            if ($configNames -notcontains $relative) {
                [PSCustomObject]@{ path = $relative; fixture = $_.FullName; sha256 = Get-Sha256 $_.FullName; reason = 'Existing runtime-generated or workspace crop config retained unchanged' }
            }
        })
    }
    New-FixtureDirectory $modsDirectory
    New-FixtureDirectory $configDirectory
    $copies = @($retained | ForEach-Object { Copy-VerifiedJar $_ $modsDirectory $kitchenPrior })
    $configCopies = @($configFiles | ForEach-Object {
        $relative = Get-RelativePath $sourceConfig $_.FullName
        $target = Assert-FixturePath (Join-Path $configDirectory $relative)
        New-FixtureDirectory ([System.IO.Path]::GetDirectoryName($target))
        $sourceHash = Get-Sha256 $_.FullName
        Copy-Item -LiteralPath $_.FullName -Destination $target -Force
        $targetHash = Get-Sha256 $target
        if ($sourceHash -ne $targetHash) { throw "Configuration copy mismatch: $relative" }
        [PSCustomObject]@{ path = $relative; source = $_.FullName; fixture = $target; source_sha256 = $sourceHash; fixture_sha256 = $targetHash; exact_match = $true }
    })
    foreach ($preserved in $preservedConfigs) {
        if ((Get-Sha256 $preserved.fixture) -ne $preserved.sha256) { throw "Preserved runtime config changed during preparation: $($preserved.path)" }
    }
    $manifest = [ordered]@{
        generated_at = [DateTimeOffset]::Now.ToString('o')
        source_instance = $fixture.source
        source_top_level_jar_count = $fixture.mods.Count
        fixture_mods_directory = $modsDirectory
        fixture_game_directory = $gameDirectory
        fixture_jar_count = $copies.Count
        byte_identical_complete_jar_count = @($copies | Where-Object { $_.exact_complete_jar_match }).Count
        test_only_filtered_jar_count = @($copies | Where-Object { -not $_.exact_complete_jar_match }).Count
        source_crop_mod_omitted = @($omittedOwn | ForEach-Object { [PSCustomObject]@{ jar = $_.Name; source_sha256 = Get-Sha256 $_.FullName; reason = 'Current workspace crop sources supplied by Gradle' } })
        installed_core_retained = $true
        presentation_exclusions = @($fixture.presentation)
        excluded_client_data_scope_has_no_crops_or_biomes = $true
        copied_jars = $copies
        config_source_instance = $ClientInstance
        copied_config_count = $configCopies.Count
        copied_configs = $configCopies
        excluded_configs = $excludedConfigs
        preserved_runtime_configs = $preservedConfigs
        preserved_runtime_config_count = $preservedConfigs.Count
        worlds_copied = $false
        installed_files_modified = $false
        fixture_rules_sha256 = Get-Sha256 (Resolve-Path -LiteralPath $AuditRules).Path
        prior_client_audit_sha256 = $script:PackRules.source_audits.client.sha256
        prior_central_kitchen_audit_sha256 = $script:PackRules.source_audits.central_kitchen.sha256
        scope = 'Central Kitchen omits nine audited GameTest classes plus their directory. Neruina changes only neruina.mixins.json to omit its DevOnly deliberate FarmBlock crash mixin registration. Every other retained entry and all complete unmodified JARs are hash verified. No production instance is written.'
    }
    $manifestPath = Assert-FixturePath (Join-Path $directory 'manifest.json')
    [System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 12), [System.Text.UTF8Encoding]::new($false))
    Write-Output "$($fixture.name): $($copies.Count) JARs ($($manifest.byte_identical_complete_jar_count) exact, $($manifest.test_only_filtered_jar_count) test-only filters), $($configCopies.Count) exact config copies; $manifestPath"
}
Write-Output 'Production instances/worlds were read only. No files were deleted. No Gradle tasks were started.'
