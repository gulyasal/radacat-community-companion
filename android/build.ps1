param(
    [string]$SdkDir = $env:ANDROID_HOME,
    [string]$JdkDir = $env:JAVA_HOME,
    [string]$BuildToolsVersion = '32.0.0',
    [string]$Platform = 'android-32',
    [string]$R8Jar = (Join-Path $PSScriptRoot 'tools\r8.jar'),
    [string]$KeyStore = (Join-Path $PSScriptRoot 'development.keystore'),
    [string]$KeyPasswordFile,
    [string]$KeyAlias = 'companion'
)
$ErrorActionPreference = 'Stop'
if (-not $SdkDir -or -not $JdkDir) { throw 'Set ANDROID_HOME and JAVA_HOME, or pass -SdkDir and -JdkDir.' }
$buildTools = Join-Path $SdkDir "build-tools\$BuildToolsVersion"
$androidJar = Join-Path $SdkDir "platforms\$Platform\android.jar"
foreach ($required in @($androidJar,$R8Jar,(Join-Path $JdkDir 'bin\javac.exe'),(Join-Path $buildTools 'aapt.exe'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required build input missing: $required. See android/README.md." }
}
$outputDir = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path $outputDir,(Join-Path $outputDir 'classes'),(Join-Path $outputDir 'dex') | Out-Null
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build step failed: $LASTEXITCODE" } }
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JdkDir\bin\javac.exe" --release 8 -classpath $androidJar -d "$outputDir\classes" @sources
Check-Exit
& "$JdkDir\bin\jar.exe" cf "$outputDir\classes.jar" -C "$outputDir\classes" .
Check-Exit
& "$JdkDir\bin\java.exe" -cp $R8Jar com.android.tools.r8.D8 --release --min-api 26 --lib $androidJar --output "$outputDir\dex" "$outputDir\classes.jar"
Check-Exit
& "$buildTools\aapt.exe" package -f -M "$PSScriptRoot\AndroidManifest.xml" -S "$PSScriptRoot\res" -I $androidJar -F "$outputDir\unsigned.apk"
Check-Exit
Push-Location "$outputDir\dex"
try { & "$buildTools\aapt.exe" add "$outputDir\unsigned.apk" classes.dex; Check-Exit } finally { Pop-Location }
& "$buildTools\zipalign.exe" -f 4 "$outputDir\unsigned.apk" "$outputDir\aligned.apk"
Check-Exit
if ($KeyPasswordFile) {
    if (-not (Test-Path -LiteralPath $KeyStore)) { throw 'Release keystore not found.' }
    $signingPassword = "file:$KeyPasswordFile"
} else {
    # Development only. This key is excluded by .gitignore; never use it for public releases.
    $signingPassword = 'pass:android'
    if (-not (Test-Path -LiteralPath $KeyStore)) {
        & "$JdkDir\bin\keytool.exe" -genkeypair -keystore $KeyStore -storepass android -keypass android -alias $KeyAlias -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=RADACAT Local Development'
        Check-Exit
    }
}
& "$JdkDir\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" sign --ks $KeyStore --ks-key-alias $KeyAlias --ks-pass $signingPassword --out "$outputDir\Radacat-Community-Companion.apk" "$outputDir\aligned.apk"
Check-Exit
& "$JdkDir\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" verify --verbose "$outputDir\Radacat-Community-Companion.apk"
Check-Exit
Write-Output "Built $outputDir\Radacat-Community-Companion.apk"
