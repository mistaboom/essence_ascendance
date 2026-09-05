$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$packagePath = 'com/mistaboom/essence_ascendance/valuation'
$parser = Join-Path $root "common/src/main/java/$packagePath/ShadowItemNomenclature.java"
$tests = Join-Path $root 'tools/valuation-tests/ShadowItemNomenclatureTest.java'
$classes = Join-Path ([System.IO.Path]::GetTempPath()) ('ea-nomenclature-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $classes | Out-Null
    & javac --release 21 -d $classes $parser $tests
    if ($LASTEXITCODE -ne 0) { throw 'Nomenclature test compilation failed. Use JDK 21 or newer.' }
    & java -cp $classes com.mistaboom.essence_ascendance.valuation.ShadowItemNomenclatureTest
    if ($LASTEXITCODE -ne 0) { throw 'Nomenclature regression tests failed.' }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}
