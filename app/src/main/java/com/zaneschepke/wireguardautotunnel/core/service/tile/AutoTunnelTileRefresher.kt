package com.zaneschepke.wireguardautotunnel.core.service.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

object AutoTunnelTileRefresher : TileRefresher {
    override fun refresh(context: Context) {
        TileService.requestListeningState(
            context,
            ComponentName(context, AutoTunnelControlTile::class.java),
        )
    }
}
