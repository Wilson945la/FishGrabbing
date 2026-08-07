$wd = 'C:\Users\caohua\Desktop\IDEAproject\test'
Set-Location $wd
$files = Get-ChildItem 'src\*.java' | ForEach-Object { $_.FullName }
& 'C:\Program Files\Java\jdk-26.0.1\bin\javac.exe' -encoding UTF-8 -d 'out' -sourcepath 'src' $files 2>&1 | Select-Object -First 15
if ($LASTEXITCODE -eq 0) {
    & 'C:\Program Files\Java\jdk-26.0.1\bin\jar.exe' cfe 'out\fishgrab.jar' FishGrabbingHome -C 'out' . 2>&1 | Select-Object -First 3
    $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
    $bak = "C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar.bak_$ts"
    Copy-Item 'C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar' $bak
    Copy-Item 'out\fishgrab.jar' 'C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar' -Force
    $dst = Get-Item 'C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar'
    Write-Host "backup: $bak"
    Write-Host ("deployed size={0} mtime={1}" -f $dst.Length, $dst.LastWriteTime)
} else {
    Write-Host 'COMPILE FAILED'
}
