$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $projectRoot

$testJavaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($testJavaHome) -or
    -not (Test-Path -LiteralPath (Join-Path $testJavaHome 'bin\java.exe'))) {
    $testJavaHome = Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $testJavaHome -or -not (Test-Path -LiteralPath (Join-Path $testJavaHome 'bin\java.exe'))) {
    throw 'JDK 21 ou supérieur introuvable.'
}

$env:JAVA_HOME = $testJavaHome
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user'

$serverEula = Join-Path $projectRoot 'run-server\eula.txt'
if (-not (Test-Path -LiteralPath $serverEula) -or
    (Get-Content -LiteralPath $serverEula -Raw) -notmatch '(?m)^eula=true\s*$') {
    $accept = Read-Host 'Le serveur Minecraft exige EULA=true. Tape OUI pour accepter'
    if ($accept -ne 'OUI') { throw 'EULA non acceptée : lancement annulé.' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $serverEula) | Out-Null
    Set-Content -LiteralPath $serverEula -Value 'eula=true' -NoNewline
}

# This file belongs only to run-server. The production mod never changes a real server's settings.
$serverProperties = Join-Path $projectRoot 'run-server\server.properties'
$properties = if (Test-Path -LiteralPath $serverProperties) {
    Get-Content -LiteralPath $serverProperties -Raw
} else {
    ''
}

$testProperties = [ordered]@{
    'online-mode' = 'false'
    'enforce-secure-profile' = 'false'
    'white-list' = 'false'
    'server-port' = '25566'
    'spawn-protection' = '0'
    'op-permission-level' = '4'
}
foreach ($entry in $testProperties.GetEnumerator()) {
    $escapedKey = [regex]::Escape($entry.Key)
    if ($properties -match "(?m)^$escapedKey=") {
        $properties = $properties -replace "(?m)^$escapedKey=.*$", "$($entry.Key)=$($entry.Value)"
    } else {
        $properties += "`r`n$($entry.Key)=$($entry.Value)"
    }
}
Set-Content -LiteralPath $serverProperties -Value ($properties.Trim() + "`r`n") -NoNewline

Write-Host 'Compilation et tests automatiques...'
& '.\gradlew.bat' clean build --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Les tests automatiques ont échoué : lancement annulé.' }

$serverScript = "`$env:JAVA_HOME = '$testJavaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; " +
    "Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runServer --no-daemon -x compileJava -x processResources -x classes"
$clientScript = "`$env:JAVA_HOME = '$testJavaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; " +
    "Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runClient --no-daemon -x compileJava -x processResources -x classes"

Write-Host 'Serveur local de test : 127.0.0.1:25566'
Write-Host 'online-mode=false ; les comptes Player suivis de chiffres deviennent OP à la connexion.'
Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -WorkingDirectory $projectRoot `
    -ArgumentList '-NoExit', '-NoProfile', '-Command', $serverScript | Out-Null
Start-Sleep -Seconds 6
Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -WorkingDirectory $projectRoot `
    -ArgumentList '-NoExit', '-NoProfile', '-Command', $clientScript | Out-Null

Write-Host 'Dans le client : Multijoueur > Ajouter un serveur > 127.0.0.1:25566'
