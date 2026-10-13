package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.team.TeamMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Native team membership only. */
public final class TeamScreen extends InterfaceScreen {
    public TeamScreen(TeamMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
}
