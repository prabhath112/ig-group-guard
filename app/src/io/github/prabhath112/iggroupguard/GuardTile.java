package io.github.prabhath112.iggroupguard;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/**
 * Quick Settings tile. Some banking apps refuse to open while any accessibility service is on, so
 * this is the fast way to step aside: tap while on = pause (the service turns itself off, exactly
 * like switching it off in Settings); tap while off = open Accessibility settings to turn it back on.
 */
public class GuardTile extends TileService {

    /** Ask the system to redraw the tile (on/off) after the service starts or stops. */
    static void refresh(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, GuardTile.class));
        } catch (RuntimeException e) {
            // tile not added by the user, or system refused; nothing to redraw
        }
    }

    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean on = GroupGuardService.isRunning();
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(getString(on ? R.string.tile_on : R.string.tile_off));
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        if (GroupGuardService.pause()) {
            Toast.makeText(this, R.string.paused_toast, Toast.LENGTH_LONG).show();
            onStartListening();
            return;
        }
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, settings,
                    PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(settings); // ponytail: deprecated on 34+, branch above covers it
        }
    }
}
