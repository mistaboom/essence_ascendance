package com.mistaboom.essence_ascendance.team.compat.fabric;

//? if >=26.1.2
/*import com.mistaboom.essence_ascendance.team.compat.FtbTeamsMembershipProvider;*/
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
/*    public static void install(Consumer<MinecraftServer> invalidate) {*/
//? if >=26.1.2
/*        var changed = FtbTeamsMembershipProvider.teamInvalidation(invalidate);*/
//? if >=26.1.2
/*        var managerChanged = FtbTeamsMembershipProvider.managerInvalidation(invalidate);*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_MANAGER.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> managerChanged.accept(data)));*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_CREATED.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), null)));*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_DELETED.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), null)));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_CHANGED_TEAM.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), data.previousTeam())));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_JOINED_PARTY_TEAM.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), data.previousTeam())));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_LEFT_PARTY_TEAM.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), data.playerTeam())));*/
//? if >=26.1.2
/*        FTBTeamsEvents.PLAYER_TRANSFERRED_OWNERSHIP.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), null)));*/
//? if >=26.1.2
/*        FTBTeamsEvents.TEAM_PROPERTIES_CHANGED.register(data -> FtbTeamsMembershipProvider.observeEvent(() -> changed.accept(data.team(), null)));*/
//? if >=26.1.2
/*    }*/
//? if >=26.1.2
/*}*/
