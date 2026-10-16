$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-25"
$env:GRADLE_USER_HOME = "C:\Users\HI\AppData\Local\Temp\opencode\gradle-home"
$g = "C:\Users\HI\AppData\Local\Temp\opencode\gradle-9.8.1\bin\gradle.bat"
$proj = "C:\Users\HI\Desktop\ai helper in minecraft\mc-song-mod"
$dist = Join-Path $proj "dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$vers = @('26.1.2','1.21.11','1.21.1','1.21.2','1.21.3','1.21.4','1.21.5','1.21.6','1.21.7','1.21.8',
          '1.21.9','1.21.10','26.1','26.1.1','26.2')

$results = @()
foreach ($v in $vers) {
    Write-Output "==================== MC $v ===================="
    $log = & $g -p $proj build "-Pmcver=$v" --no-daemon --console=plain 2>&1
    $ok = ($log | Select-String -Pattern "BUILD SUCCESSFUL" -Quiet)
    if ($ok) {
        $src = Join-Path $proj "build\libs\Song_1.0.0_$v.jar"
        if (Test-Path $src) {
            Copy-Item $src (Join-Path $dist "Song_1.0.0_$v.jar") -Force
            Write-Output "RESULT $v : OK"
            $results += "$v OK"
        } else {
            Write-Output "RESULT $v : BUILD OK but jar missing ($src)"
            $results += "$v JAR-MISSING"
        }
    } else {
        Write-Output "RESULT $v : FAILED"
        $log | Select-Object -Last 25 | ForEach-Object { Write-Output ("   " + $_) }
        $results += "$v FAILED"
    }
}
Write-Output "==================== SUMMARY ===================="
$results | ForEach-Object { Write-Output $_ }
Write-Output "ALL DONE"
