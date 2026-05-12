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
    }

    features {
        perfmon {
        }
    }
})
