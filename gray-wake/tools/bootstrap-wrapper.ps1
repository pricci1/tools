$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Jar = Join-Path $Root 'gradle\wrapper\gradle-wrapper.jar'
$Expected = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
$Url = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar'
if (Test-Path $Jar) {
    if ((Get-FileHash -Algorithm SHA256 $Jar).Hash.ToLowerInvariant() -ne $Expected) {
        throw "Refusing an unverified wrapper: $Jar. Remove it and retry."
    }
    exit 0
}
if ($env:GRAY_WAKE_NO_DOWNLOAD -eq '1') { throw 'Gradle wrapper is not cached, and downloads are disabled.' }
New-Item -ItemType Directory -Force (Split-Path $Jar) | Out-Null
$Temp = "$Jar.$([Guid]::NewGuid().ToString('N')).part"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Write-Host 'Downloading the official Gradle wrapper (checksum pinned)...'
    Invoke-WebRequest -Uri $Url -OutFile $Temp -UseBasicParsing -TimeoutSec 120
    if ((Get-FileHash -Algorithm SHA256 $Temp).Hash.ToLowerInvariant() -ne $Expected) {
        throw 'Gradle wrapper checksum mismatch. Nothing was executed.'
    }
    Move-Item $Temp $Jar -Force
} finally {
    if (Test-Path $Temp) { Remove-Item $Temp -Force }
}
