package com.mistaboom.essence_ascendance.team.compat.neoforge;

//? if >=26.1.2
/*import com.mistaboom.essence_ascendance.team.compat.FtbTeamsMembershipProvider;*/
//? if >=26.1.2
/*import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;*/
//? if >=26.1.2
/*import net.neoforged.neoforge.common.NeoForge;*/
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
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamManager event) -> managerChanged.accept(event.getEventData()));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamCreated event) -> changed.accept(event.getEventData().team(), null));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamDeleted event) -> changed.accept(event.getEventData().team(), null));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerChangedTeam event) -> changed.accept(event.getEventData().team(), event.getEventData().previousTeam()));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerJoinedPartyTeam event) -> changed.accept(event.getEventData().team(), event.getEventData().previousTeam()));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerLeftPartyTeam event) -> changed.accept(event.getEventData().team(), event.getEventData().playerTeam()));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.PlayerTransferredOwnership event) -> changed.accept(event.getEventData().team(), null));*/
//? if >=26.1.2
/*        NeoForge.EVENT_BUS.addListener((FTBTeamsEvent.TeamPropertiesChanged event) -> changed.accept(event.getEventData().team(), null));*/
//? if >=26.1.2
/*    }*/
//? if >=26.1.2
/*}*/
