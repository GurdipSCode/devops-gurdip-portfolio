import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.projectFeatures.githubConnection

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

    buildType(Build)

    features {
        githubConnection {
            id = "PROJECT_EXT_2"
            displayName = "GitHub.com"
            clientId = "Ov23liAmrqa9p50bSgoe"
            clientSecret = "credentialsJSON:5138f84e-03c5-4492-ab19-1d365bca3f0e"
        }
    }
}

object Build : BuildType({
    name = "Build"

    vcs {
        root(DslContext.settingsRoot)
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
    }

    features {
        perfmon {
        }
    }
})
