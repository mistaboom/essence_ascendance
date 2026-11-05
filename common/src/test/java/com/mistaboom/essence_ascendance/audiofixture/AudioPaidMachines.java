package com.mistaboom.essence_ascendance.audiofixture;

import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.machine.MachineLink;
import com.mistaboom.essence_ascendance.pylon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.math.BigInteger;
import java.util.*;

/** Disposable real machine setup shared by gameplay and measured native-load fixtures. No client snapshots. */
public final class AudioPaidMachines {
    private static final int[][] PYLONS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private AudioPaidMachines() { }
    public static Scene create(ServerPlayer owner,BlockPos center,int count) {
        if(count<1 || count>32) throw new IllegalArgumentException("Use one to thirty-two real Infusers");
        if(!((Object)net.minecraft.world.item.component.CustomData.EMPTY instanceof CustomDataReader.Access))
            throw new AssertionError("Actual native CustomData must use the shared read-only projection bridge");
        return new Scene(owner,center,count);
    }
    public static ItemStack focus(EssenceFocusTier tier) {
        var stack=new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(stack,tier); return stack;
    }
    /** Client linkage is isolated from the dedicated-server setup owner. */
    public static final class Client {
        private Client() { }
        /** Acknowledged native block-use, bounded against chunk/teleport readiness races. Never opens a server menu directly. */
        public static final class CrucibleMenuRequest {
            private int ticks,attempts,lastAttempt;
            public void tick(net.minecraft.client.Minecraft mc,BlockPos position) {
                ticks++;
                if(mc.player!=null && (mc.player.containerMenu instanceof EssenceCrucibleMenu || mc.player.containerMenu instanceof com.mistaboom.essence_ascendance.team.TeamMenu)) return;
                if(ticks>240 || attempts>=5 && ticks-lastAttempt>=40)
                    throw new AssertionError("Native Crucible menu request unacknowledged: attempts="+attempts+" position="+(mc.player==null?null:mc.player.position())+" target="+position+" screen="+mc.screen+" block="+(mc.level==null?null:mc.level.getBlockState(position)));
                if(ticks<20 || ticks-lastAttempt<20 || attempts>=5 || mc.player==null || mc.level==null || mc.gameMode==null || mc.screen!=null
                        || !(mc.level.getBlockEntity(position) instanceof EssenceCrucibleBlockEntity)) return;
                lastAttempt=ticks; attempts++;
                var result=mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(position),net.minecraft.core.Direction.UP,position,false));
                System.out.println("AudioPaidMachines: native Crucible block-use attempt="+attempts+" result="+result+" player="+mc.player.position()+" target="+position+" container="+mc.player.containerMenu.getClass().getSimpleName());
            }
        }
        public static boolean toggleCurrentInfuser(net.minecraft.client.Minecraft mc) {
            if(!infuserMenuReady(mc) || mc.gameMode==null || !(mc.player.containerMenu instanceof EssenceInfuserMenu menu)) return false;
            mc.gameMode.handleInventoryButtonClick(menu.containerId,EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING); return true;
        }
        /** Native initial ContainerData is ordered: nondefault LOCKED at index42 follows processingEnabled at25. */
        public static boolean infuserMenuReady(net.minecraft.client.Minecraft mc) {
            if(mc.player==null || !(mc.player.containerMenu instanceof EssenceInfuserMenu menu)) return false;
            return menu.linkMode()==MachineLink.Mode.LOCKED && menu.infusionThroughputPerSecond()>0
                    && !menu.getSlot(EssenceInfuserBlockEntity.FOCUS_SLOT).getItem().isEmpty()
                    && (!menu.getSlot(EssenceInfuserBlockEntity.INPUT_SLOT).getItem().isEmpty()
                    || !menu.getSlot(EssenceInfuserBlockEntity.OUTPUT_SLOT).getItem().isEmpty());
        }
        /** Actual initialized screen widget, including its native click sound and production C2S callback. */
        public static boolean pressCurrentCrucible(net.minecraft.client.Minecraft mc,boolean wanted) {
            if(mc.player==null || !(mc.player.containerMenu instanceof EssenceCrucibleMenu menu)
                    || !(mc.screen instanceof com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen)) return false;
            var state=com.mistaboom.essence_ascendance.client.EssenceCrucibleClientState.snapshotFor(menu.containerId);
            if(state==null || state.channelingForViewer()==wanted || !state.allowed() || wanted && !state.channelingAllowed()) return false;
            try {
                var field=mc.screen.getClass().getDeclaredField("channelButton"); field.setAccessible(true);
                var button=(net.minecraft.client.gui.components.Button)field.get(mc.screen); if(button==null || !button.active) return false;
                button.playDownSound(mc.getSoundManager());
                //? if >=26.1.2
                /*button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0));*/
                //? if <26.1.2
                button.onPress();
                System.out.println("AudioPaidMachines: actual Crucible screen channel button desired="+wanted+" container="+menu.containerId);
                return true;
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Native Crucible screen widget unavailable",failure); }
        }
    }
    public static final class Scene {
        private final ServerPlayer owner;
        private final EssenceCrucibleBlockEntity source;
        private final List<EssenceInfuserBlockEntity> infusers=new ArrayList<>();
        private final List<BlockPos> pylonPositions=new ArrayList<>();
        private Map<ResourceLocation,BigInteger> initialPool;
        private Map<ResourceLocation,Long> initialPersonal;
        private Map<ResourceLocation,Long> initialContributions;
        private Map<EssenceInfuserBlockEntity,Long> initialConsumerProgress;
        private Scene(ServerPlayer owner,BlockPos center,int count) {
            this.owner=owner;
            var level=owner.serverLevel();
            level.setBlockAndUpdate(center,EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
            source=(EssenceCrucibleBlockEntity)level.getBlockEntity(center);
            check(source!=null && source.bindOnPlacement(owner),"real Crucible placement establishes funding identity");
            var offsets=new ArrayList<BlockPos>();
            for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) if(Math.max(Math.abs(x),Math.abs(z))>=2) offsets.add(new BlockPos(x,0,z));
            offsets.sort(Comparator.comparingDouble((BlockPos p)->p.distSqr(BlockPos.ZERO)).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
            for(int index=0;index<count;index++) {
                BlockPos position=center.offset(offsets.get(index));
                level.setBlockAndUpdate(position,EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
                var infuser=(EssenceInfuserBlockEntity)level.getBlockEntity(position);
                check(infuser!=null && infuser.bindOnPlacement(owner),"real Infuser placement establishes funding identity");
                infuser.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,focus(EssenceFocusTier.DORMANT));
                infuser.setItem(EssenceInfuserBlockEntity.INPUT_SLOT,focus(EssenceFocusTier.DORMANT));
                check(infuser.setLink(owner,MachineLink.Mode.LOCKED,new MachineLink.Source(level.dimension().location(),center)),"real Infuser locks to authorized source within native reach");
                infusers.add(infuser);
            }
            Map<ResourceLocation,BigInteger> funds=new LinkedHashMap<>();
            for(var type:EssenceTypes.ORDERED) funds.put(type.id(),BigInteger.valueOf(1_000_000_000L).multiply(ReservoirService.SCALE));
            check(saved().reservoirs().administer(owner.getUUID(),funds),"fixture operator funds authoritative reservoir");
            resetLedger();
        }
        private EssenceSavedData saved() { return EssenceSavedData.get(owner.serverLevel().getServer()); }
        public EssenceCrucibleBlockEntity source() { return source; }
        public List<EssenceInfuserBlockEntity> infusers() { return Collections.unmodifiableList(infusers); }
        public int loadedPopulation() { return 1+infusers.size()+pylonPositions.size(); }
        public List<BlockPos> positions() {
            var positions=new ArrayList<BlockPos>(); positions.add(source.getBlockPos());
            infusers.forEach(machine->positions.add(machine.getBlockPos())); positions.addAll(pylonPositions); return List.copyOf(positions);
        }
        public void openMenu(int index) {
            var infuser=infusers.get(index);
            check(infuser.canPlayerConfigure(owner),"real operator can reach and configure Infuser "+index);
            owner.openMenu(infuser);
        }
        public void setPylons(int count) {
            if(count<0 || count>8) throw new IllegalArgumentException("Pylon count must be zero through eight");
            var level=owner.serverLevel();
            while(pylonPositions.size()>count) level.removeBlock(pylonPositions.removeLast(),false);
            while(pylonPositions.size()<count) {
                int[] offset=PYLONS[pylonPositions.size()]; BlockPos position=source.getBlockPos().offset(offset[0],0,offset[1]);
                level.setBlockAndUpdate(position,EssencePylonContent.ESSENCE_PYLON.get().defaultBlockState());
                var pylon=(EssencePylonBlockEntity)level.getBlockEntity(position);
                check(pylon!=null && pylon.bindOnPlacement(owner),"real Pylon binds to current funding identity");
                pylon.setItem(EssencePylonBlockEntity.FOCUS_SLOT,focus(EssenceFocusTier.DORMANT));
                check(pylon.setLink(owner,MachineLink.Mode.LOCKED,new MachineLink.Source(level.dimension().location(),source.getBlockPos())),"real focused Pylon locks to Crucible");
                pylonPositions.add(position);
            }
            check(source.structureSnapshot().activePylons().size()==count,"actual structure evaluates requested focused Pylon count "+count);
        }
        public void resetLedger() {
            initialPool=snapshotPool(); initialPersonal=new LinkedHashMap<>();
            initialContributions=new LinkedHashMap<>(); initialConsumerProgress=new IdentityHashMap<>();
            for(var type:EssenceTypes.ORDERED) {
                initialPersonal.put(type.id(),saved().getPlayerData(owner.getUUID()).getAvailable(type));
                long prior=0;
                for(var infuser:infusers) prior=Math.addExact(prior,infuser.focusInfusionContribution(type));
                initialContributions.put(type.id(),prior);
            }
            for(var infuser:infusers) initialConsumerProgress.put(infuser,infuser.focusInfusionTotalContributed());
        }
        public Map<ResourceLocation,BigInteger> snapshotPool() { return Map.copyOf(saved().reservoirs().read(owner.getUUID()).units()); }
        public Map<ResourceLocation,BigInteger> debits() {
            var remaining=snapshotPool(); var result=new LinkedHashMap<ResourceLocation,BigInteger>();
            for(var type:EssenceTypes.ORDERED) result.put(type.id(),initialPool.getOrDefault(type.id(),BigInteger.ZERO).subtract(remaining.getOrDefault(type.id(),BigInteger.ZERO)));
            return Map.copyOf(result);
        }
        public long personalGranted() {
            long total=0;
            for(var type:EssenceTypes.ORDERED) total=Math.addExact(total,Math.subtractExact(saved().getPlayerData(owner.getUUID()).getAvailable(type),initialPersonal.get(type.id())));
            return total;
        }
        public long verifyFocusPayments(boolean requireEveryConsumer) {
            return verifyPayments(requireEveryConsumer,false);
        }
        public long verifyPlayerPayments() { return verifyPayments(false,true); }
        private long verifyPayments(boolean requireEveryConsumer,boolean playerTransfer) {
            var debits=debits(); long total=0;
            for(var type:EssenceTypes.ORDERED) {
                long contribution=0;
                for(var infuser:infusers) contribution=Math.addExact(contribution,infuser.focusInfusionContribution(type));
                contribution=Math.subtractExact(contribution,initialContributions.get(type.id()));
                long grant=Math.subtractExact(saved().getPlayerData(owner.getUUID()).getAvailable(type),initialPersonal.get(type.id()));
                check(debits.get(type.id()).equals(BigInteger.valueOf(Math.addExact(contribution,grant)).multiply(ReservoirService.SCALE)),"exact paid destination conserves "+type.id());
                if(playerTransfer) check(grant>0 && contribution==0,"actual player transfer grants selected Essence while stopped Focus work remains unchanged: "+type.id());
                else check(grant==0,"machine work cannot grant personal "+type.id());
                total=Math.addExact(total,playerTransfer?grant:contribution);
            }
            for(var infuser:infusers) {
                check(infuser.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty(),"long Focus workpiece remains unfinished during measured window");
                if(playerTransfer) check(!infuser.processingEnabled(),"player-family pass keeps Infuser explicitly stopped");
                if(requireEveryConsumer) check(infuser.focusInfusionTotalContributed()>initialConsumerProgress.get(infuser),"each real Infuser receives paid scheduler work in this measured window");
            }
            return total;
        }
        public void remove() {
            var level=owner.serverLevel();
            for(var infuser:infusers) level.removeBlock(infuser.getBlockPos(),false);
            for(var position:pylonPositions) level.removeBlock(position,false);
            level.removeBlock(source.getBlockPos(),false);
        }
    }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
}
