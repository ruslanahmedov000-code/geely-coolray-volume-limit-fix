#!/system/bin/sh
# Starts the loudness keeper after boot and restarts it if it ever exits.
MODDIR=${0%/*}
DATA=/data/adb/ramauto_volume
mkdir -p $DATA
[ -f $DATA/gain_db ] || echo 8 > $DATA/gain_db

i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $i -lt 180 ]; do sleep 1; i=$((i + 1)); done
sleep 20   # let audioserver and the car audio stack settle

(
  quick=0
  while true; do
    [ -f $MODDIR/disable ] && break
    t0=$(date +%s)
    CLASSPATH=$MODDIR/keeper.dex app_process /system/bin ru.ramauto.VolumeKeeper $DATA/gain_db $DATA/keeper.log >> $DATA/keeper.out 2>&1
    rc=$?
    [ "$(wc -c < $DATA/keeper.out)" -gt 65536 ] && tail -c 32768 $DATA/keeper.out > $DATA/keeper.out.tmp && mv $DATA/keeper.out.tmp $DATA/keeper.out
    if [ $(( $(date +%s) - t0 )) -lt 60 ]; then quick=$((quick + 1)); else quick=0; fi
    if [ $quick -ge 5 ]; then wait_s=120; else wait_s=10; fi
    echo "$(date '+%F %T') keeper exited (code $rc), restarting in $wait_s s" >> $DATA/keeper.log
    sleep $wait_s
  done
) &
exit 0
