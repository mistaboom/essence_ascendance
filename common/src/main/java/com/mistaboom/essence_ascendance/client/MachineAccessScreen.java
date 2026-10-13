package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.team.TeamMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Slot-free access request prompt; no machine inventory or settings are exposed. */
public final class MachineAccessScreen extends InterfaceScreen {
    public MachineAccessScreen(TeamMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
}
