# RamAuto: уровень усиления громкости (модуль RamAuto Volume Boost)
$ErrorActionPreference = 'Continue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
Write-Host '=== RamAuto: усиление громкости ===' -ForegroundColor Cyan
& $adb start-server | Out-Null
if (-not ((& $adb devices) | Select-String "`tdevice$")) {
    $ip = Read-Host 'Магнитола не найдена по USB. Введите IP:порт (например 192.168.43.1:5555)'
    if ($ip) { & $adb connect $ip | Out-Null; Start-Sleep 2 }
}
# --- выбор магнитолы (если подключено несколько устройств/эмулятор) ---
$__all = @((& $adb devices -l) | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' })
$__list = @($__all | Where-Object { $_ -match '\sdevice(\s|$)' })
$__pick = $__list | Where-Object { $_ -match 'IHU|ecarx|geely' } | Select-Object -First 1
if (-not $__pick) { $__pick = $__list | Where-Object { $_ -notmatch 'emulator|127\.0\.0\.1|localhost' } | Select-Object -First 1 }
if (-not $__pick -and $__list.Count -gt 0) { $__pick = $__list[0] }
if ($__pick) {
    $env:ANDROID_SERIAL = ($__pick.Trim() -split '\s+')[0]
    if ($__all.Count -gt 1) { Write-Host "Найдено устройств: $($__all.Count). Использую: $env:ANDROID_SERIAL" -ForegroundColor Yellow }
}



$st = Join-Path $PSScriptRoot 'volume_status.txt'
Write-Host 'Собираю состояние модуля...'
& $adb shell "su -c 'echo === module; ls -la /data/adb/modules/ramauto_volume /data/adb/modules/ramauto_volume/system/bin; echo === conf; cat /data/adb/ramauto_volume/gain_db; echo === process; ps -A -o PID,USER,NAME,ARGS | grep -i -e app_process -e VolumeKeeper | grep -v grep; echo === log; tail -n 40 /data/adb/ramauto_volume/keeper.log; echo === out; tail -n 60 /data/adb/ramauto_volume/keeper.out; echo === which; which ramauto-volume; echo === effects; dumpsys media.audio_flinger | grep -A12 Effect.Chains; echo === logcat; logcat -d -t 4000 | grep -i -e VolumeKeeper -e ramauto -e AudioEffect -e LoudnessEnhancer -e app_process -e AndroidRuntime -e DEBUG | tail -n 150'" 2>&1 | Out-File -Encoding utf8 $st
Write-Host "Сохранено: $st"
Write-Host 'Сейчас:'
& $adb shell "su -c 'ramauto-volume'" 2>&1 | Out-Host
$v = Read-Host 'Новый уровень усиления в дБ (0 = выкл, обычно 6-10, максимум 15). Enter - не менять'
if ($v -match '^\d+$') {
    & $adb shell "su -c 'ramauto-volume $v'" 2>&1 | Out-Host
    Write-Host 'Применится в течение 5 секунд.' -ForegroundColor Green
}
Read-Host 'Enter - закрыть'
