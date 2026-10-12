package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.RodForcePayload;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;

/** Apply an accepted server force once to native motion. No client force/balance calculation. */
public final class RodForceClient {
    private RodForceClient(){ }
    public static void init(){
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,RodForcePayload.TYPE,RodForcePayload.CODEC,
                (payload,context)->ClientPacketDispatch.queue(context,()->{
                    var client=Minecraft.getInstance();var player=client.player;
                    if(player==null || !player.level().dimension().location().equals(payload.dimension()))return;
                    var hook=player.level().getEntity(payload.hookId());
                    if(!(hook instanceof RodHookAccess access) || access.essenceAscendance$attachment()==0)return;
                    if(player.getId()==payload.ownerId() && !client.options.keyUse.isDown())return;
                    player.setDeltaMovement(player.getDeltaMovement().add(payload.force()));
                }));
    }
}
