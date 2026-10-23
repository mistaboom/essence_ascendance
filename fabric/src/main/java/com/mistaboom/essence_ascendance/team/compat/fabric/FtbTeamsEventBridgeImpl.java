package com.mistaboom.essence_ascendance.team.compat.fabric;

//? if >=26.1.2
/*import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;*/
//? if >=26.1.2
/*import dev.ftb.mods.ftbteams.api.fabric.FTBTeamsEvents;*/
//? if >=26.1.2
/*import net.minecraft.server.MinecraftServer;*/
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
/*        FTBTeamsEvents.TEAM_MANAGER.register(data -> invalidate.accept(data.manager().getServer()));*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_CREATED.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_DELETED.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_CHANGED_TEAM.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_JOINED_PARTY_TEAM.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_LEFT_PARTY_TEAM.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_TRANSFERRED_OWNERSHIP.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_PROPERTIES_CHANGED.register(data -> invalidateCurrent(invalidate));*/
//? if >=26.1.2
/*    }*/
//? if >=26.1.2
/*}*/
