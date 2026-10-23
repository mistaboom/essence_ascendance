package com.mistaboom.essence_ascendance.network;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Keeps action-bar and chat delivery distinct across the Player API change. */
public final class PlayerMessageCompat {
    private PlayerMessageCompat() { }

    public static void send(Player player, Component message, boolean overlay) {
        //? if >=26.1.2
        /*if (overlay) player.sendOverlayMessage(message); else player.sendSystemMessage(message);*/
        //? if <26.1.2
        player.displayClientMessage(message, overlay);
    }
}
