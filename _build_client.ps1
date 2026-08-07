$wd = 'C:\Users\caohua\Desktop\IDEAproject\test'
Set-Location $wd
$files = Get-ChildItem 'src\*.java' | ForEach-Object { $_.FullName }
& 'C:\Program Files\Java\jdk-26.0.1\bin\javac.exe' -encoding UTF-8 -d 'out' -sourcepath 'src' $files *> compile_err.log
$code = $LASTEXITCODE
if ($code -ne 0) { Write-Host 'COMPILE FAILED' ; Get-Content compile_err.log -Head 30 ; exit 1 }
Write-Host 'COMPILE OK'
& 'C:\Program Files\Java\jdk-26.0.1\bin\jar.exe' cfe 'out\fishgrab.jar' FishGrabbingHome -C 'out' . *> jar_log.txt
$code = $LASTEXITCODE
if ($code -ne 0) { Write-Host 'JAR FAILED' ; Get-Content jar_log.txt -Head 20 ; exit 1 }
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$bak = "C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar.bak_$ts"
Copy-Item 'C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar' $bak
Copy-Item 'out\fishgrab.jar' 'C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar' -Force
$dst = Get-Item 'C:\Users\caohua\Desktop\FishGrabbingAssistant\app\fishgrab.jar'
Write-Host "backup: $bak"
Write-Host ('deployed size={0} mtime={1}' -f $dst.Length, $dst.LastWriteTime)
