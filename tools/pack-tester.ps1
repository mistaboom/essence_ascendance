# Wrapper bootstrap completes before starting the independent desktop JVM.
# No Gradle JavaExec task holds project locks while the GUI invokes production builds.
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'Set JAVA_HOME to the project JDK 21 installation.' }
$testerArguments = @($args)
Push-Location -LiteralPath $projectPath
try {
    & $javaExecutable '-Dorg.gradle.appname=gradlew' '-classpath' (Join-Path $projectPath 'gradle\wrapper\gradle-wrapper.jar') 'org.gradle.wrapper.GradleWrapperMain' '--console=plain' ':pack-tester:installDist'
    if ($LASTEXITCODE -ne 0) { throw "Pack Tester bootstrap failed (exit $LASTEXITCODE)." }
    & $javaExecutable '-jar' (Join-Path $projectPath 'pack-tester\build\install\pack-tester\lib\pack-tester.jar') @testerArguments
    exit $LASTEXITCODE
} finally { Pop-Location }
