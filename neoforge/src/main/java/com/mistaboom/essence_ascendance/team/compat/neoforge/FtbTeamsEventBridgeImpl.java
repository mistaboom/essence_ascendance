package com.mistaboom.essence_ascendance.team.compat.neoforge;

//? if >=26.1.2
/*import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;*/
//? if >=26.1.2
/*import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;*/
//? if >=26.1.2
/*import net.minecraft.server.MinecraftServer;*/
//? if >=26.1.2
/*import net.neoforged.neoforge.common.NeoForge;*/
//? if >=26.1.2
/*import java.util.function.Consumer;*/

//? if >=26.1.2
/*public final class FtbTeamsEventBridgeImpl {*/
//? if >=26.1.2
/*    private FtbTeamsEventBridgeImpl() { }*/
//? if >=26.1.2
/*    private static void invalidateCurrent(Consumer<MinecraftServer> invalidate) { var api = FTBTeamsAPI.api(); if (api != null && api.isManagerLoaded()) invalidate.accept(api.getManager().getServer()); }*/
//? if >=26.1.2
/*    public static void install(Consumer<MinecraftServer> invalidate) {*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamManager event) -> invalidate.accept(event.getEventData().manager().getServer()));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamCreated event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamDeleted event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerChangedTeam event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerJoinedPartyTeam event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerLeftPartyTeam event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerTransferredOwnership event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamPropertiesChanged event) -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*    }*/
//? if >=26.1.2
/*}*/
