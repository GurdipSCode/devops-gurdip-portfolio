import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.projectFeatures.githubConnection
import jetbrains.buildServer.configs.kotlin.projectFeatures.hashiCorpVaultConnection
import jetbrains.buildServer.configs.kotlin.remoteParameters.hashiCorpVaultParameter
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2026.1"

project {

    vcsRoot(HttpsGithubComGurdipSCodeDevopsCiScriptsRefsHeadsMain)

    buildType(Semgrep)
    buildType(Build)

    features {
        githubConnection {
            id = "PROJECT_EXT_2"
            displayName = "GitHub.com"
            clientId = "Ov23liAmrqa9p50bSgoe"
            clientSecret = "credentialsJSON:5138f84e-03c5-4492-ab19-1d365bca3f0e"
        }
        hashiCorpVaultConnection {
            id = "Vault"
            name = "HashiCorp Vault (2)"
            vaultNamespace = "DevOps/Portfolio"
            url = "http://vaultdev.gssira.com:8200"
            authMethod = appRole {
                roleId = "a3076e49-1172-7beb-d487-344ab7cd384c"
                secretId = "credentialsJSON:d614325f-a3dd-438b-a61c-f10b9f78c157"
            }
        }
    }
}

object Build : BuildType({
    name = "Build"

    params {
        param("teamcity.pullRequest.number", "")
        hashiCorpVaultParameter {
            name = "OCTOPUS_KEY"
            query = "kv/data/OCTOPUS_KEY!/key"
            vaultId = "Vault"
            param("buildTypeId", "DevopsGurdip_DevopsGurdipPortfolio_Build")
        }
    }

    vcs {
        root(DslContext.settingsRoot)
        root(HttpsGithubComGurdipSCodeDevopsCiScriptsRefsHeadsMain)
    }

    steps {
        powerShell {
            name = "Create Artifact Dirs"
            id = "Create_Artifact_Dirs"
            scriptMode = script {
                content = """
                    <#
                    .SYNOPSIS
                      Build relative and absolute paths for artifact folders (and optionally create them).
                    
                    .EXAMPLE
                      # Create folders (default)
                      .\mk-artifacts.ps1
                    
                      # Only show path info, don't create
                      .\mk-artifacts.ps1 -Create:${'$'}false
                    #>
                    
                    param(
                        [switch] ${'$'}Create = ${'$'}true
                    )
                    
                    function Get-RepoRoot {
                        # Prefer TeamCity checkout dir if present
                        if ("%teamcity.build.checkoutDir%"-and (Test-Path "%teamcity.build.checkoutDir")) {
                            return (Resolve-Path -Path ${'$'}env:BUILD_CHECKOUTDIR).Path
                        }
                    
                        # If git is available, try to find the git repo root
                        try {
                            ${'$'}git = (Get-Command git -ErrorAction Stop)
                            ${'$'}root = git rev-parse --show-toplevel 2>${'$'}null
                            if (${'$'}LASTEXITCODE -eq 0 -and ${'$'}root) {
                                return (Resolve-Path -Path ${'$'}root.Trim()).Path
                            }
                        } catch {
                            # ignore, fall back next
                        }
                    
                        # Fall back to script directory, or current directory if running interactively
                        if (${'$'}MyInvocation.MyCommand.Path) {
                            return (Resolve-Path -Path (Split-Path -Path ${'$'}MyInvocation.MyCommand.Path -Parent)).Path
                        }
                    
                        return (Resolve-Path -Path .).Path
                    }
                    
                    # main
                    ${'$'}baseDir = Get-RepoRoot
                    Write-Host "Base directory: ${'$'}baseDir"
                    
                    # list of artifact relative paths you asked for
                    ${'$'}relativePaths = @(
                        'artifacts\cyclonedx',
                        'artifacts\gitguardian',
                        'artifacts\snyk',
                        'artifacts\publish',
                        'artifacts\sonar',
                        'artifacts/fraim',
                        'artifacts/semgrep'
                    )
                    
                    ${'$'}result = foreach (${'$'}rel in ${'$'}relativePaths) {
                        # Keep the relative path as-is (relative to ${'$'}baseDir)
                        ${'$'}relNormalized = ${'$'}rel -replace '/','\'
                    
                        # compute absolute path
                        ${'$'}abs = [System.IO.Path]::GetFullPath((Join-Path -Path ${'$'}baseDir -ChildPath ${'$'}relNormalized))
                    
                        # create directory if requested
                        if (${'$'}Create) {
                            if (-not (Test-Path -Path ${'$'}abs)) {
                                New-Item -ItemType Directory -Path ${'$'}abs -Force | Out-Null
                                ${'$'}created = ${'$'}true
                            } else {
                                ${'$'}created = ${'$'}false
                            }
                        } else {
                            ${'$'}created = ${'$'}false
                        }
                    
                        [PSCustomObject]@{
                            Name         = Split-Path -Path ${'$'}relNormalized -Leaf
                            RelativePath = ${'$'}relNormalized
                            AbsolutePath = ${'$'}abs
                            Created      = ${'$'}created
                        }
                    }
                    
                    # show nicely
                    ${'$'}result | Format-Table -AutoSize
                    
                    # export to variables in the current session if the script is dot-sourced:
                    # e.g. `. .\mk-artifacts.ps1` then ${'$'}Artifacts will be available
                    ${'$'}Artifacts = ${'$'}result
                    
                    # also return the array for pipelines
                    return ${'$'}result
                """.trimIndent()
            }
        }
        script {
            name = "NPM Build"
            id = "NPM_Build"
            scriptContent = "npm run build"
        }
        script {
            name = "NPM Install"
            id = "NPM_Build_1"
            scriptContent = "npm i"
        }
        powerShell {
            name = "Compute Version"
            id = "Compute_Version"
            scriptMode = script {
                content = """
                    # Always ensure tags are present locally
                    git fetch --tags --force | Out-Host
                    
                    # Next SemVer computed from commits since last tag (Conventional Commits)
                    ${'$'}next = (& git cliff --config cliff.toml --unreleased --bumped-version).Trim()
                    
                    if ([string]::IsNullOrWhiteSpace(${'$'}next)) {
                      Write-Host "No version bump detected (no unreleased changes)."
                      exit 0
                    }
                    
                    # TeamCity PR builds expose this parameter (if PR build feature is enabled)
                    ${'$'}prNumber = "%teamcity.pullRequest.number%"
                    ${'$'}isPR = ${'$'}prNumber -and ${'$'}prNumber -ne "%teamcity.pullRequest.number%"
                    
                    if (${'$'}isPR) {
                      # PR build: pre-release version, no git tag
                      ${'$'}version = "${'$'}next-pr.${'$'}prNumber"
                    } else {
                      # Main build: release-looking version (we'll actually tag in a later step)
                      ${'$'}version = "v${'$'}next"
                    }
                    
                    Write-Host "Computed version: ${'$'}version"
                    Write-Host "##teamcity[buildNumber '${'$'}version']"
                """.trimIndent()
            }
        }
        script {
            name = "Run Sonar Scan"
            id = "Run_Sonar_Scan"
            scriptContent = "node sonar.js  --kosli_flow=portfolio-flow --kosli_trail=Portfolio-trail-%build.number% --kosli_fingerprint=abc123 --attestation=differ.sonarcloud-scan"
        }
        powerShell {
            name = "Generate Changelog"
            id = "jetbrains_powershell"
            scriptMode = script {
                content = """
                    git fetch --tags --force | Out-Host
                    
                    ${'$'}next = (& git cliff --config cliff.toml --unreleased --bumped-version).Trim()
                    if ([string]::IsNullOrWhiteSpace(${'$'}next)) { exit 0 }
                    
                    # Create release notes using the version label we computed
                    git cliff --config cliff.toml --unreleased --tag "v${'$'}next" -o CHANGELOG.md
                    Write-Host "Generated CHANGELOG.md for v${'$'}next"
                """.trimIndent()
            }
        }
        powerShell {
            name = "Tag Release"
            id = "Tag_Release"
            scriptMode = script {
                content = """
                    git fetch --tags --force | Out-Host
                    
                    ${'$'}next = (& git cliff --config cliff.toml --unreleased --bumped-version).Trim()
                    if ([string]::IsNullOrWhiteSpace(${'$'}next)) { exit 0 }
                    
                    ${'$'}tag = "v${'$'}next"
                    
                    # Safety: don't re-tag if it already exists
                    ${'$'}existing = git tag -l ${'$'}tag
                    if (${'$'}existing) {
                      Write-Host "Tag ${'$'}tag already exists. Skipping."
                      exit 0
                    }
                    
                    git tag ${'$'}tag
                    git push origin ${'$'}tag
                    Write-Host "Created and pushed tag ${'$'}tag"
                """.trimIndent()
            }
        }
        script {
            name = "CSpell"
            id = "CSpell"
            scriptContent = """npx cspell --config cspell.json "**/*.{js,ts,md,txt}""""
        }
        powerShell {
            name = "Copy package.json to dist"
            id = "Copy_package_json_to_dist"
            scriptMode = script {
                content = """Copy-Item -Path "package.json" -Destination "dist/package.json" -Force"""
            }
        }
        powerShell {
            name = "Set Package/Lock versions"
            id = "Set_Package_Lock_versions"
            scriptMode = script {
                content = """
                    param(
                        [Parameter(Mandatory=${'$'}true)]
                        [string]${'$'}Version
                    )
                    
                    Write-Host "Updating package files to version: ${'$'}Version" -ForegroundColor Cyan
                    
                    function Update-JsonVersion {
                        param(
                            [string]${'$'}FilePath,
                            [string]${'$'}Version
                        )
                        
                        if (-not (Test-Path ${'$'}FilePath)) {
                            Write-Warning "${'$'}FilePath not found, skipping..."
                            return ${'$'}false
                        }
                        
                        Write-Host "Updating ${'$'}FilePath..." -ForegroundColor Cyan
                        
                        try {
                            ${'$'}content = Get-Content ${'$'}FilePath -Raw
                            ${'$'}pattern = '("version"\s*:\s*)"[^"]*"'
                            ${'$'}replacement = "`${'$'}1`"${'$'}Version`""
                            
                            if (${'$'}content -match ${'$'}pattern) {
                                ${'$'}newContent = ${'$'}content -replace ${'$'}pattern, ${'$'}replacement
                                Set-Content -Path ${'$'}FilePath -Value ${'$'}newContent -NoNewline -Encoding UTF8
                                Write-Host "Successfully updated ${'$'}FilePath" -ForegroundColor Green
                                return ${'$'}true
                            }
                            else {
                                Write-Warning "Could not find version field in ${'$'}FilePath"
                                return ${'$'}false
                            }
                        }
                        catch {
                            Write-Error "Failed to update ${'$'}{FilePath}: ${'$'}(${'$'}_.Exception.Message)"
                            return ${'$'}false
                        }
                    }
                    
                    # Update all files
                    ${'$'}packageJsonUpdated = Update-JsonVersion -FilePath "package.json" -Version ${'$'}Version
                    ${'$'}packageLockUpdated = Update-JsonVersion -FilePath "package-lock.json" -Version ${'$'}Version
                    ${'$'}distPackageJsonUpdated = Update-JsonVersion -FilePath "dist/package.json" -Version ${'$'}Version
                    
                    # Summary
                    Write-Host "`n========================================" -ForegroundColor Cyan
                    Write-Host "Version Update Summary" -ForegroundColor Cyan
                    Write-Host "========================================" -ForegroundColor Cyan
                    Write-Host "Version: ${'$'}Version" -ForegroundColor Yellow
                    Write-Host "package.json: ${'$'}(if(${'$'}packageJsonUpdated){'Updated'}else{'Failed'})" -ForegroundColor ${'$'}(if(${'$'}packageJsonUpdated){'Green'}else{'Red'})
                    Write-Host "package-lock.json: ${'$'}(if(${'$'}packageLockUpdated){'Updated'}else{'Failed'})" -ForegroundColor ${'$'}(if(${'$'}packageLockUpdated){'Green'}else{'Red'})
                    Write-Host "dist/package.json: ${'$'}(if(${'$'}distPackageJsonUpdated){'Updated'}else{'Failed'})" -ForegroundColor ${'$'}(if(${'$'}distPackageJsonUpdated){'Green'}else{'Red'})
                    Write-Host "========================================`n" -ForegroundColor Cyan
                    
                    # Exit with error if any update failed
                    if (-not ${'$'}packageJsonUpdated -or -not ${'$'}packageLockUpdated -or -not ${'$'}distPackageJsonUpdated) {
                        Write-Error "One or more package files failed to update"
                        exit 1
                    }
                    
                    Write-Host "All package files updated successfully!" -ForegroundColor Green
                    exit 0
                """.trimIndent()
            }
        }
        powerShell {
            name = "Package Dist Folder"
            id = "Package_Dist_Folder"
            scriptMode = script {
                content = """
                    # ===========================
                    # Configuration Variables
                    # ===========================
                    
                    ${'$'}DIST_FOLDER = "dist"
                    ${'$'}OUTPUT_FOLDER = "artifacts/publish"
                    ${'$'}PACKAGE_NAME = "Portfolio"
                    
                    # ===========================
                    # Error Handling
                    # ===========================
                    ${'$'}ErrorActionPreference = "Stop"
                    
                    # ===========================
                    # Functions
                    # ===========================
                    
                    function Write-Step {
                        param([string]${'$'}Message)
                        Write-Host "`n##teamcity[progressMessage '${'$'}Message']"
                        Write-Host "===> ${'$'}Message" -ForegroundColor Cyan
                    }
                    
                    function Write-Success {
                        param([string]${'$'}Message)
                        Write-Host "✓ ${'$'}Message" -ForegroundColor Green
                    }
                    
                    function Write-Error {
                        param([string]${'$'}Message)
                        Write-Host "✗ ${'$'}Message" -ForegroundColor Red
                    }
                    
                    # ===========================
                    # Main Execution
                    # ===========================
                    
                    try {
                        Write-Host "======================================"
                        Write-Host "NPM Package Creation"
                        Write-Host "======================================`n"
                        
                        # Verify dist folder exists
                        if (-not (Test-Path ${'$'}DIST_FOLDER)) {
                            throw "Dist folder not found: ${'$'}DIST_FOLDER"
                        }
                        
                        Write-Success "Dist folder found: ${'$'}DIST_FOLDER"
                        
                        # Create output folder if it doesn't exist
                        Write-Step "Creating output folder"
                        if (-not (Test-Path ${'$'}OUTPUT_FOLDER)) {
                            New-Item -ItemType Directory -Path ${'$'}OUTPUT_FOLDER -Force | Out-Null
                            Write-Success "Created: ${'$'}OUTPUT_FOLDER"
                        } else {
                            Write-Success "Output folder exists: ${'$'}OUTPUT_FOLDER"
                        }
                        
                        # Pack the npm package
                        Write-Step "Packing npm package"
                        
                        Push-Location ${'$'}DIST_FOLDER
                        try {
                            # Read package.json to get version
                            ${'$'}packageJsonPath = "package.json"
                            if (-not (Test-Path ${'$'}packageJsonPath)) {
                                throw "package.json not found in ${'$'}DIST_FOLDER"
                            }
                            
                            ${'$'}packageJson = Get-Content ${'$'}packageJsonPath -Raw | ConvertFrom-Json
                            ${'$'}version = ${'$'}packageJson.version
                            
                            Write-Host "Package version: ${'$'}version"
                            
                            # Pack the package
                            npm pack
                            
                            if (${'$'}LASTEXITCODE -ne 0) {
                                throw "npm pack failed with exit code ${'$'}LASTEXITCODE"
                            }
                            
                            # Find the created tarball
                            ${'$'}tarball = Get-ChildItem -Filter "*.tgz" | Select-Object -First 1
                            
                            if (-not ${'$'}tarball) {
                                throw "No .tgz file found after npm pack"
                            }
                            
                            Write-Success "Package created: ${'$'}(${'$'}tarball.Name)"
                            
                            # Create new filename with version
                            ${'$'}newFileName = "${'$'}PACKAGE_NAME-${'$'}version.tgz"
                            
                            Write-Step "Renaming package to: ${'$'}newFileName"
                            
                            ${'$'}destination = Join-Path (Resolve-Path "..") ${'$'}OUTPUT_FOLDER
                            ${'$'}destinationFile = Join-Path ${'$'}destination ${'$'}newFileName
                            
                            # Remove existing file if present
                            if (Test-Path ${'$'}destinationFile) {
                                Remove-Item ${'$'}destinationFile -Force
                                Write-Host "Removed existing file: ${'$'}newFileName"
                            }
                            
                            # Rename and move
                            Rename-Item ${'$'}tarball.FullName ${'$'}newFileName
                            ${'$'}renamedTarball = Get-Item ${'$'}newFileName
                            Move-Item ${'$'}renamedTarball.FullName ${'$'}destination -Force
                            
                            Write-Success "Moved to: ${'$'}destinationFile"
                            
                            # Verify the file exists in destination
                            if (Test-Path ${'$'}destinationFile) {
                                ${'$'}fileInfo = Get-Item ${'$'}destinationFile
                                Write-Host "`nPackage Details:"
                                Write-Host "  Name: ${'$'}(${'$'}fileInfo.Name)"
                                Write-Host "  Size: ${'$'}([math]::Round(${'$'}fileInfo.Length / 1KB, 2)) KB"
                                Write-Host "  Path: ${'$'}(${'$'}fileInfo.FullName)"
                            }
                            
                        }
                        finally {
                            Pop-Location
                        }
                        
                        Write-Host "`n======================================"
                        Write-Success "Package creation completed successfully!"
                        Write-Host "======================================`n"
                        
                        exit 0
                    }
                    catch {
                        Write-Host "`n======================================"
                        Write-Error "Package creation failed: ${'$'}_"
                        Write-Host "======================================`n"
                        Write-Host ${'$'}_.ScriptStackTrace
                        exit 1
                    }
                """.trimIndent()
            }
        }
        powerShell {
            name = "Sign package with Cosign"
            id = "Sign_package_with_Cosign"
            scriptMode = script {
                content = """
                    #Requires -Version 7.0
                    <#
                    .SYNOPSIS
                        Signs any deliverable (nupkg, zip, exe, etc.) with Cosign and produces
                        a SHA-256 checksum. Can be used standalone or called from publish-to-cloudsmith.ps1.
                    
                    .DESCRIPTION
                        Accepts a path to any file, computes its SHA-256 checksum, signs both
                        the artifact and the checksum with Cosign (key-based or keyless OIDC),
                        and verifies the signature locally before exiting.
                    
                        Outputs a hashtable of produced files so callers can consume them:
                            ${'$'}result.Artifact      – original file (resolved absolute path)
                            ${'$'}result.Checksum      – <artifact>.sha256
                            ${'$'}result.Signature     – <artifact>.sig          (key-based only)
                            ${'$'}result.ChecksumSig   – <artifact>.sha256.sig   (key-based only)
                            ${'$'}result.Bundle        – <artifact>.bundle       (keyless only)
                            ${'$'}result.PublicKey     – resolved path to cosign.pub (key-based only)
                            ${'$'}result.Mode          – "key-based" | "keyless"
                    
                    .PARAMETER DeliverablePath
                        Path to the file to sign. Accepts any file type: .nupkg, .zip, .exe, .msi, etc.
                    
                    .PARAMETER CosignPrivateKeyPath
                        Path to cosign.key. Defaults to ./cosign.key.
                        Ignored when -KeylessSign is used.
                    
                    .PARAMETER CosignPublicKeyPath
                        Path to cosign.pub. Defaults to ./cosign.pub.
                        Used for local verification after signing. Ignored when -KeylessSign is used.
                    
                    .PARAMETER KeylessSign
                        Use Cosign keyless signing via ambient OIDC token (GitHub Actions, GCP, Azure, etc.).
                        No private key file is needed. The Rekor transparency log entry is embedded in the bundle.
                    
                    .PARAMETER GenerateKeys
                        Generate a new Cosign key pair and exit. Run once during initial setup.
                        Reads passphrase from COSIGN_PASSWORD env var, or prompts interactively.
                    
                    .PARAMETER OutputDir
                        Directory to write signing artefacts (.sig, .sha256, .bundle) into.
                        Defaults to the same directory as the deliverable.
                    
                    .EXAMPLE
                        # One-time: generate key pair
                        .\sign-artifact.ps1 -GenerateKeys
                    
                    .EXAMPLE
                        # Sign a nupkg (key-based)
                        ${'$'}env:COSIGN_PASSWORD = "..."
                        .\sign-artifact.ps1 -DeliverablePath "./dist/MyPackage.1.2.3.nupkg"
                    
                    .EXAMPLE
                        # Sign a nupkg (keyless, e.g. inside GitHub Actions)
                        .\sign-artifact.ps1 -DeliverablePath "./dist/MyPackage.1.2.3.nupkg" -KeylessSign
                    
                    .EXAMPLE
                        # Use from another script and capture output paths
                        ${'$'}signed = .\sign-artifact.ps1 -DeliverablePath ${'$'}nupkgPath
                        Write-Host "Signature at: ${'$'}(${'$'}signed.Signature)"
                    #>
                    
                    [CmdletBinding(DefaultParameterSetName = "KeyBased")]
                    param(
                        [Parameter(Mandatory, ParameterSetName = "KeyBased", Position = 0)]
                        [Parameter(Mandatory, ParameterSetName = "Keyless",  Position = 0)]
                        [string] ${'$'}DeliverablePath,
                    
                        [Parameter(ParameterSetName = "KeyBased")]
                        [string] ${'$'}CosignPrivateKeyPath = "./cosign.key",
                    
                        [string] ${'$'}CosignPublicKeyPath  = "./cosign.pub",
                    
                        [Parameter(Mandatory, ParameterSetName = "Keyless")]
                        [switch] ${'$'}KeylessSign,
                    
                        [Parameter(Mandatory, ParameterSetName = "GenerateKeys")]
                        [switch] ${'$'}GenerateKeys,
                    
                        [string] ${'$'}OutputDir = ""
                    )
                    
                    Set-StrictMode -Version Latest
                    ${'$'}ErrorActionPreference = "Stop"
                    
                    # ─────────────────────────────────────────────────────────────
                    # Helpers
                    # ─────────────────────────────────────────────────────────────
                    
                    function Write-Step([string]${'$'}msg) {
                        Write-Host "`n▶  ${'$'}msg" -ForegroundColor Cyan
                    }
                    
                    function Assert-Command([string]${'$'}name) {
                        if (-not (Get-Command ${'$'}name -ErrorAction SilentlyContinue)) {
                            throw "Required tool '${'$'}name' not found. Please install it and ensure it is on PATH."
                        }
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Dependency check
                    # ─────────────────────────────────────────────────────────────
                    
                    Assert-Command "cosign"
                    
                    # ─────────────────────────────────────────────────────────────
                    # MODE: Generate keys (one-time setup)
                    # ─────────────────────────────────────────────────────────────
                    
                    if (${'$'}GenerateKeys) {
                        Write-Step "Generating Cosign key pair"
                    
                        ${'$'}privateKeyPath = Resolve-Path -LiteralPath (Split-Path ${'$'}CosignPrivateKeyPath -Parent) |
                            ForEach-Object { Join-Path ${'$'}_.Path (Split-Path ${'$'}CosignPrivateKeyPath -Leaf) }
                    
                        if (Test-Path ${'$'}privateKeyPath) {
                            Write-Warning "Key '${'$'}privateKeyPath' already exists. Remove it first to regenerate."
                            exit 1
                        }
                    
                        if (-not ${'$'}env:COSIGN_PASSWORD) {
                            ${'$'}secPwd = Read-Host "Enter passphrase for cosign.key" -AsSecureString
                            ${'$'}env:COSIGN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                                [Runtime.InteropServices.Marshal]::SecureStringToBSTR(${'$'}secPwd)
                            )
                        }
                    
                        ${'$'}prefix = [System.IO.Path]::GetFileNameWithoutExtension(${'$'}CosignPrivateKeyPath)
                        cosign generate-key-pair --output-key-prefix ${'$'}prefix
                    
                        Write-Host ""
                        Write-Host "✅  Key pair generated:" -ForegroundColor Green
                        Write-Host "    Private : ${'$'}CosignPrivateKeyPath"
                        Write-Host "              ^ Add to your CI secrets / secrets manager. Never commit." -ForegroundColor Yellow
                        Write-Host "    Public  : ${'$'}CosignPublicKeyPath"
                        Write-Host "              ^ Commit to your repo or publish to a trust store." -ForegroundColor Gray
                        exit 0
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Resolve paths
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Pre-flight checks"
                    
                    if (-not (Test-Path ${'$'}DeliverablePath)) {
                        throw "Deliverable not found: ${'$'}DeliverablePath"
                    }
                    
                    ${'$'}resolvedDeliverable = (Resolve-Path ${'$'}DeliverablePath).Path
                    ${'$'}deliverableName     = Split-Path ${'$'}resolvedDeliverable -Leaf
                    ${'$'}deliverableDir      = Split-Path ${'$'}resolvedDeliverable -Parent
                    
                    # Where to write signing artefacts
                    ${'$'}sigDir = if (${'$'}OutputDir) {
                        if (-not (Test-Path ${'$'}OutputDir)) { New-Item -ItemType Directory -Path ${'$'}OutputDir | Out-Null }
                        (Resolve-Path ${'$'}OutputDir).Path
                    } else {
                        ${'$'}deliverableDir
                    }
                    
                    ${'$'}stem         = Join-Path ${'$'}sigDir ${'$'}deliverableName
                    ${'$'}checksumFile = "${'$'}stem.sha256"
                    ${'$'}sigFile      = "${'$'}stem.sig"
                    ${'$'}checksumSig  = "${'$'}checksumFile.sig"
                    ${'$'}bundleFile   = "${'$'}stem.bundle"
                    
                    if (-not ${'$'}KeylessSign) {
                        if (-not (Test-Path ${'$'}CosignPrivateKeyPath)) {
                            throw "Cosign private key not found: ${'$'}CosignPrivateKeyPath. Run with -GenerateKeys first."
                        }
                        if (-not ${'$'}env:COSIGN_PASSWORD) {
                            throw "COSIGN_PASSWORD environment variable must be set for key-based signing."
                        }
                        ${'$'}resolvedPubKey = (Resolve-Path ${'$'}CosignPublicKeyPath).Path
                    }
                    
                    Write-Host "  Deliverable : ${'$'}resolvedDeliverable"
                    Write-Host "  Output dir  : ${'$'}sigDir"
                    Write-Host "  Sign mode   : ${'$'}(if (${'$'}KeylessSign) { 'keyless (OIDC / Sigstore)' } else { 'key-based' })"
                    
                    # ─────────────────────────────────────────────────────────────
                    # Step 1 – SHA-256 checksum
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Computing SHA-256 checksum"
                    
                    ${'$'}hash = (Get-FileHash -Algorithm SHA256 ${'$'}resolvedDeliverable).Hash.ToLower()
                    "${'$'}hash  ${'$'}deliverableName" | Set-Content ${'$'}checksumFile -Encoding ascii
                    
                    Write-Host "  ${'$'}hash"
                    Write-Host "  Written to: ${'$'}checksumFile"
                    
                    # ─────────────────────────────────────────────────────────────
                    # Step 2 – Sign
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Signing with Cosign"
                    
                    if (${'$'}KeylessSign) {
                        # Keyless: ambient OIDC token (GitHub Actions / GCP / Azure) → Rekor log
                        cosign sign-blob `
                            --yes `
                            --bundle ${'$'}bundleFile `
                            ${'$'}resolvedDeliverable
                    
                        Write-Host "  Bundle (sig + Rekor entry): ${'$'}bundleFile"
                    } else {
                        # Key-based: use --bundle output (cosign v2 modern path — no legacy warning).
                        # The bundle embeds the signature and the public key certificate so consumers
                        # only need cosign.pub to verify; no separate .sig file required.
                        cosign sign-blob `
                            --key                ${'$'}CosignPrivateKeyPath `
                            --bundle             ${'$'}bundleFile `
                            --tlog-upload=false `
                            ${'$'}resolvedDeliverable
                    
                        Write-Host "  Bundle : ${'$'}bundleFile"
                    
                        # Sign the checksum with the same approach
                        cosign sign-blob `
                            --key                ${'$'}CosignPrivateKeyPath `
                            --bundle             "${'$'}checksumFile.bundle" `
                            --tlog-upload=false `
                            ${'$'}checksumFile
                    
                        Write-Host "  Checksum bundle: ${'$'}checksumFile.bundle"
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Step 3 – Local verification (catch key/config mistakes early)
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Verifying signature (local sanity check)"
                    
                    if (${'$'}KeylessSign) {
                        cosign verify-blob `
                            --bundle ${'$'}bundleFile `
                            ${'$'}resolvedDeliverable
                    } else {
                        # --insecure-ignore-tlog because we deliberately skipped the transparency log
                        # (--tlog-upload=false above). This is correct for air-gapped / private CI.
                        cosign verify-blob `
                            --key                 ${'$'}resolvedPubKey `
                            --bundle              ${'$'}bundleFile `
                            --insecure-ignore-tlog `
                            ${'$'}resolvedDeliverable
                    
                        Write-Host "  ✅ Signature verified against ${'$'}resolvedPubKey"
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Return result hashtable (for use when dot-sourced or called
                    # from publish-to-cloudsmith.ps1 via & operator + capture)
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Host ""
                    Write-Host "✅  Signing complete." -ForegroundColor Green
                    
                    ${'$'}result = [ordered]@{
                        Artifact = ${'$'}resolvedDeliverable
                        Checksum = ${'$'}checksumFile
                        Bundle   = ${'$'}bundleFile
                        Mode     = if (${'$'}KeylessSign) { "keyless" } else { "key-based" }
                    }
                    
                    if (-not ${'$'}KeylessSign) {
                        ${'$'}result.ChecksumBundle = "${'$'}checksumFile.bundle"
                        ${'$'}result.PublicKey      = ${'$'}resolvedPubKey
                    }
                    
                    Write-Host ""
                    Write-Host "Files produced:"
                    foreach (${'$'}k in ${'$'}result.Keys) {
                        Write-Host ("  {0,-16} {1}" -f "${'$'}{k}:", ${'$'}result[${'$'}k]) -ForegroundColor Gray
                    }
                    
                    Write-Host ""
                    Write-Host "Verify on another machine with:"
                    if (${'$'}KeylessSign) {
                        Write-Host "  cosign verify-blob --bundle ${'$'}bundleFile ${'$'}resolvedDeliverable"
                    } else {
                        Write-Host "  cosign verify-blob --key cosign.pub --bundle ${'$'}bundleFile --insecure-ignore-tlog ${'$'}resolvedDeliverable"
                    }
                    
                    return ${'$'}result
                """.trimIndent()
            }
        }
        powerShell {
            name = "Kosli Create Flow"
            id = "Kosli_Create_Flow"
            scriptMode = script {
                content = """
                    kosli create flow ${'$'}FlowName `
                      --org ${'$'}KosliOrg `
                      --description ${'$'}FlowDescription `
                      --template-file kosli/flow-template.yml `
                      --api-token %env.KOSLI_KEY%
                """.trimIndent()
            }
        }
        powerShell {
            name = "Kosli Begin Trail"
            id = "jetbrains_powershell_1"
            scriptMode = script {
                content = """
                    # Kosli Begin Trail - TeamCity Build Step
                    Write-Host "Starting Kosli Begin Trail..."
                    ${'$'}ErrorActionPreference = "Stop"
                    
                    # CONFIGURATION
                    ${'$'}KosliOrg        = "gurdipdevops"
                    ${'$'}FlowName        = "portfolio-flow"
                    ${'$'}FlowDescription = "Flow for governance attestation"
                    ${'$'}TemplateFile    = "kosli/flow-template.yml"
                    
                    # Get the git commit SHA to use as the trail name
                    ${'$'}TrailName = git rev-parse HEAD
                    if (${'$'}LASTEXITCODE -ne 0 -or -not ${'$'}TrailName) {
                        throw "Failed to get git commit SHA via 'git rev-parse HEAD'"
                    }
                    Write-Host "Using trail name: ${'$'}TrailName"
                    
                    # Create or update the flow (idempotent)
                    Write-Host "Creating/updating Kosli flow: ${'$'}FlowName..."
                    kosli create flow ${'$'}FlowName `
                      --org ${'$'}KosliOrg `
                      --description ${'$'}FlowDescription `
                      --template-file ${'$'}TemplateFile `
                      --api-token %env.KOSLI_KEY%
                    
                    # Begin the trail
                    kosli begin trail ${'$'}TrailName `
                      --description "Starting Kosli trail for Portfolio build %env.BUILD_NUMBER% (commit ${'$'}TrailName)" `
                      --flow ${'$'}FlowName `
                      --org ${'$'}KosliOrg `
                      --api-token %env.KOSLI_KEY%
                    
                    Write-Host "Kosli Begin Trail completed successfully."
                """.trimIndent()
            }
        }
        powerShell {
            name = "Kosli Attest Artifact"
            id = "Kosli_Attest_Artifact"
            scriptMode = script {
                content = """
                    # Kosli Attest Artifact - TeamCity Build Step
                    Write-Host "Starting Kosli Attest Artifact..."
                    ${'$'}ErrorActionPreference = "Stop"
                    
                    # CONFIGURATION
                    ${'$'}KosliOrg     = "gurdipdevops"
                    ${'$'}FlowName     = "portfolio-flow"
                    ${'$'}ArtifactName = "portfolio"   # must match the artifact name in your flow-template.yml
                    
                    # Path to the packaged artifact produced by the "Package Dist Folder" step
                    # Adjust this to wherever step 15 actually writes its output
                    ${'$'}ArtifactPath = "%teamcity.build.checkoutDir%\dist\portfolio.zip"
                    
                    # Get the git commit SHA (used as the trail name, matches Begin Trail step)
                    ${'$'}TrailName = git rev-parse HEAD
                    if (${'$'}LASTEXITCODE -ne 0 -or -not ${'$'}TrailName) {
                        throw "Failed to get git commit SHA via 'git rev-parse HEAD'"
                    }
                    Write-Host "Trail (commit): ${'$'}TrailName"
                    
                    # Verify the artifact actually exists before attesting
                    if (-not (Test-Path ${'$'}ArtifactPath)) {
                        throw "Artifact not found at: ${'$'}ArtifactPath"
                    }
                    Write-Host "Attesting artifact: ${'$'}ArtifactPath"
                    
                    # Attest the artifact to Kosli
                    # Kosli will calculate the SHA256 fingerprint automatically based on --artifact-type
                    kosli attest artifact ${'$'}ArtifactPath `
                      --artifact-type file `
                      --name ${'$'}ArtifactName `
                      --flow ${'$'}FlowName `
                      --trail ${'$'}TrailName `
                      --org ${'$'}KosliOrg `
                      --commit ${'$'}TrailName `
                      --commit-url "%vcsroot.Portfolio_HttpsGithubComGurdipS5leadOpsShowcaseHubRefsHeadsMain.url%/commit/${'$'}TrailName" `
                      --build-url "%teamcity.serverUrl%/viewLog.html?buildId=%teamcity.build.id%" `
                      --api-token %env.KOSLI_KEY%
                    
                    if (${'$'}LASTEXITCODE -ne 0) {
                        throw "kosli attest artifact failed with exit code ${'$'}LASTEXITCODE"
                    }
                    
                    Write-
                """.trimIndent()
            }
        }
        powerShell {
            name = "Kosli attest sonar"
            id = "Kosli_attest_sonar"
            scriptMode = script {
                content = """
                    kosli attest sonar `
                      --name portfolio.security-scan `
                      --flow ${'$'}FlowName `
                      --trail ${'$'}TrailName `
                      --org ${'$'}KosliOrg `
                      --api-token %env.KOSLI_KEY%
                """.trimIndent()
            }
        }
        powerShell {
            name = "Execute GitGuardian"
            id = "Execut"
            scriptMode = script {
                content = """
                    # Ensure the GitGuardian directory exists
                    if (-not (Test-Path ${'$'}env:GitGuardianDir)) {
                        New-Item -ItemType Directory -Path ${'$'}env:GitGuardianDir | Out-Null
                    }
                    
                    # Run the scan, prettify JSON, and save to SARIF file
                    ggshield secret scan commit-range HEAD~1 --format sarif |
                        ConvertFrom-Json |
                        ConvertTo-Json -Depth 10 |
                        Tee-Object -FilePath "${'$'}env:GitGuardianDir\results.sarif"
                """.trimIndent()
            }
        }
        powerShell {
            name = "Kosli assert artifact (1)"
            id = "Kosli_assert_artifact_1"
            scriptMode = script {
                content = """
                    # Kosli Assert Artifact - TeamCity Build Step
                    # Runs as the final gate before Octopus Deploy
                    Write-Host "Starting Kosli Assert Artifact..."
                    ${'$'}ErrorActionPreference = "Stop"
                    
                    # CONFIGURATION
                    ${'$'}KosliOrg     = "gurdipdevops"
                    ${'$'}FlowName     = "portfolio-flow"
                    
                    # Path to the packaged artifact - must match the path used in Kosli Attest Artifact
                    ${'$'}ArtifactPath = "%teamcity.build.checkoutDir%\dist\portfolio.zip"
                    
                    # Verify the artifact exists
                    if (-not (Test-Path ${'$'}ArtifactPath)) {
                        throw "Artifact not found at: ${'$'}ArtifactPath"
                    }
                    
                    # Compute the artifact's SHA256 fingerprint
                    # This must match the fingerprint Kosli calculated when we attested the artifact
                    Write-Host "Computing fingerprint for: ${'$'}ArtifactPath"
                    ${'$'}Fingerprint = kosli fingerprint ${'$'}ArtifactPath --artifact-type file --api-token %env.KOSLI_KEY%
                    
                    if (${'$'}LASTEXITCODE -ne 0 -or -not ${'$'}Fingerprint) {
                        throw "Failed to compute artifact fingerprint"
                    }
                    ${'$'}Fingerprint = ${'$'}Fingerprint.Trim()
                    Write-Host "Artifact fingerprint: ${'$'}Fingerprint"
                    
                    # Assert the artifact is compliant with the flow's template
                    # Exits non-zero if any required attestation is missing or non-compliant
                    Write-Host "Asserting artifact compliance against flow: ${'$'}FlowName"
                    kosli assert artifact `
                      --fingerprint ${'$'}Fingerprint `
                      --flow ${'$'}FlowName `
                      --org ${'$'}KosliOrg `
                      --api-token %env.KOSLI_KEY%
                    
                    if (${'$'}LASTEXITCODE -ne 0) {
                        throw "Kosli assert artifact FAILED - artifact is not compliant. Deployment blocked."
                    }
                    
                    Write-Host "Kosli Assert Artifact PASSED - artifact is compliant. Proceeding to deploy."
                """.trimIndent()
            }
        }
        powerShell {
            name = "Push to GitHub"
            id = "Push_to_GitHub"
            scriptMode = script {
                content = """
                    #Requires -Version 7.0
                    <#
                    .SYNOPSIS
                        Stages files and pushes to GitHub with a Conventional Commit message.
                    
                    .PARAMETER RepoPath
                        Path to the local git repository. Defaults to the current directory.
                    
                    .PARAMETER Type
                        Conventional commit type: feat, fix, chore, ci, docs, refactor, test, perf, build.
                    
                    .PARAMETER Scope
                        Optional scope in parentheses e.g. "signing" → "chore(signing): …"
                    
                    .PARAMETER Message
                        Short description (the commit subject line).
                    
                    .PARAMETER Body
                        Optional longer description added to the commit body.
                    
                    .PARAMETER BreakingChange
                        Marks the commit as a breaking change (appends ! and BREAKING CHANGE footer).
                    
                    .PARAMETER Files
                        Specific files or globs to stage. Defaults to all changes (git add .).
                    
                    .PARAMETER Branch
                        Branch to push to. Defaults to the current branch.
                    
                    .PARAMETER DryRun
                        Shows what would happen without actually committing or pushing.
                    
                    .EXAMPLE
                        # Stage everything and push
                        .\git-conventional-push.ps1 `
                            -RepoPath "D:\devops-gurdip-portfolio-main\devops-gurdip-portfolio-main" `
                            -Type     "chore" `
                            -Scope    "signing" `
                            -Message  "add cosign signing and cloudsmith publish scripts"
                    
                    .EXAMPLE
                        # Stage specific files only
                        .\git-conventional-push.ps1 `
                            -RepoPath "D:\devops-gurdip-portfolio-main\devops-gurdip-portfolio-main" `
                            -Type     "ci" `
                            -Scope    "release" `
                            -Message  "add tar packaging and cosign pipeline" `
                            -Files    "sign-artifact.ps1","publish-to-cloudsmith.ps1","create-package-tar.ps1"
                    
                    .EXAMPLE
                        # Dry run to preview commit message
                        .\git-conventional-push.ps1 `
                            -RepoPath "D:\devops-gurdip-portfolio-main\devops-gurdip-portfolio-main" `
                            -Type     "feat" `
                            -Message  "add release pipeline" `
                            -DryRun
                    #>
                    
                    param(
                        [string]   ${'$'}RepoPath       = (Get-Location).Path,
                    
                        [ValidateSet("feat","fix","chore","ci","docs","refactor","test","perf","build","style","revert")]
                        [string]   ${'$'}Type           = "chore",
                    
                        [string]   ${'$'}Scope          = "",
                        [string]   ${'$'}Message        = "",
                        [string]   ${'$'}Body           = "",
                        [switch]   ${'$'}BreakingChange,
                        [string[]] ${'$'}Files          = @(),
                        [string]   ${'$'}Branch         = "",
                        [switch]   ${'$'}DryRun
                    )
                    
                    Set-StrictMode -Version Latest
                    ${'$'}ErrorActionPreference = "Stop"
                    
                    function Write-Step([string]${'$'}msg) {
                        Write-Host "`n▶  ${'$'}msg" -ForegroundColor Cyan
                    }
                    
                    function Assert-Command([string]${'$'}name) {
                        if (-not (Get-Command ${'$'}name -ErrorAction SilentlyContinue)) {
                            throw "Required tool '${'$'}name' not found on PATH."
                        }
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Validate
                    # ─────────────────────────────────────────────────────────────
                    
                    Assert-Command "git"
                    
                    if (-not (Test-Path ${'$'}RepoPath)) {
                        throw "Repo path not found: ${'$'}RepoPath"
                    }
                    
                    Push-Location ${'$'}RepoPath
                    
                    try {
                    
                    if (-not (Test-Path ".git")) {
                        throw "${'$'}RepoPath is not a git repository."
                    }
                    
                    if (-not ${'$'}Message) {
                        throw "-Message is required."
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Build conventional commit subject
                    # e.g.  chore(signing)!: add cosign scripts
                    # ─────────────────────────────────────────────────────────────
                    
                    ${'$'}scopePart   = if (${'$'}Scope) { "(${'$'}Scope)" } else { "" }
                    ${'$'}breakPart   = if (${'$'}BreakingChange) { "!" } else { "" }
                    ${'$'}subject     = "${'$'}{Type}${'$'}{scopePart}${'$'}{breakPart}: ${'$'}{Message}"
                    
                    # Full commit message
                    ${'$'}fullMessage = ${'$'}subject
                    if (${'$'}Body) {
                        ${'$'}fullMessage += "`n`n${'$'}Body"
                    }
                    if (${'$'}BreakingChange) {
                        ${'$'}fullMessage += "`n`nBREAKING CHANGE: ${'$'}Message"
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Resolve branch
                    # ─────────────────────────────────────────────────────────────
                    
                    if (-not ${'$'}Branch) {
                        ${'$'}Branch = git rev-parse --abbrev-ref HEAD 2>&1
                        if (${'$'}LASTEXITCODE -ne 0) { throw "Could not determine current branch." }
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Preview
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Conventional commit preview"
                    Write-Host ""
                    Write-Host "  Subject : ${'$'}subject" -ForegroundColor Yellow
                    if (${'$'}Body)           { Write-Host "  Body    : ${'$'}Body" -ForegroundColor Gray }
                    if (${'$'}BreakingChange) { Write-Host "  ⚠️  BREAKING CHANGE" -ForegroundColor Red }
                    Write-Host "  Branch  : ${'$'}Branch" -ForegroundColor Gray
                    Write-Host "  Repo    : ${'$'}RepoPath" -ForegroundColor Gray
                    if (${'$'}Files) {
                        Write-Host "  Files   :" -ForegroundColor Gray
                        ${'$'}Files | ForEach-Object { Write-Host "    ${'$'}_" -ForegroundColor Gray }
                    } else {
                        Write-Host "  Files   : all changes (git add .)" -ForegroundColor Gray
                    }
                    
                    if (${'$'}DryRun) {
                        Write-Host ""
                        Write-Host "  DRY RUN — nothing was committed or pushed." -ForegroundColor Magenta
                        exit 0
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Stage
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Staging files"
                    
                    if (${'$'}Files.Count -gt 0) {
                        foreach (${'$'}f in ${'$'}Files) {
                            git add ${'$'}f
                            if (${'$'}LASTEXITCODE -ne 0) { throw "git add failed for: ${'$'}f" }
                            Write-Host "  + ${'$'}f" -ForegroundColor Gray
                        }
                    } else {
                        git add .
                        if (${'$'}LASTEXITCODE -ne 0) { throw "git add . failed" }
                        Write-Host "  + all changes" -ForegroundColor Gray
                    }
                    
                    # Check there is actually something to commit
                    ${'$'}status = git status --porcelain
                    if (-not ${'$'}status) {
                        Write-Host ""
                        Write-Host "  Nothing to commit — working tree clean." -ForegroundColor Yellow
                        exit 0
                    }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Commit
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Committing"
                    
                    git commit -m ${'$'}fullMessage
                    if (${'$'}LASTEXITCODE -ne 0) { throw "git commit failed." }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Push
                    # ─────────────────────────────────────────────────────────────
                    
                    Write-Step "Pushing to origin/${'$'}Branch"
                    
                    git push origin ${'$'}Branch
                    if (${'$'}LASTEXITCODE -ne 0) { throw "git push failed." }
                    
                    # ─────────────────────────────────────────────────────────────
                    # Done
                    # ─────────────────────────────────────────────────────────────
                    
                    ${'$'}sha = git rev-parse --short HEAD
                    Write-Host ""
                    Write-Host "✅  Pushed ${'$'}sha → origin/${'$'}Branch" -ForegroundColor Green
                    Write-Host "    ${'$'}subject" -ForegroundColor Cyan
                    
                    } finally {
                        Pop-Location
                    }
                """.trimIndent()
            }
        }
    }

    triggers {
        vcs {
            triggerRules = "-:user=teamcity-agent"
            branchFilter = ""
            enableQueueOptimization = false
        }
    }

    features {
        perfmon {
        }
    }

    requirements {
        contains("env.Path", "nodejs")
    }
})

object Semgrep : BuildType({
    name = "Semgrep"

    vcs {
        root(DslContext.settingsRoot)
    }
})

object HttpsGithubComGurdipSCodeDevopsCiScriptsRefsHeadsMain : GitVcsRoot({
    name = "https://github.com/GurdipSCode/devops-ci-scripts#refs/heads/main"
    url = "https://github.com/GurdipSCode/devops-ci-scripts"
    branch = "refs/heads/main"
    authMethod = token {
        userName = "oauth2"
        tokenId = "tc_token_id:CID_d928835e688af7380f1585b923074b93:-1:5b66000b-2ac2-4f6b-8c54-020d9b519620"
    }
})
