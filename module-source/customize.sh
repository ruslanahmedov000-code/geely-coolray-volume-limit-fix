#!/system/bin/sh
SKIPUNZIP=0
ui_print "- RamAuto Volume Boost"
ui_print "- Default boost: +8 dB (change: su -c ramauto-volume 10)"
ui_print "- Disable the 'Volume Booster' app to avoid two boosts fighting"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/system/bin/ramauto-volume" 0 2000 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
