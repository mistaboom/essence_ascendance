package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.gameplay.RodOutcomes;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Real rod item/attachment/force, native target identity/range and accepted native fall damage.
 * Trajectory positions and impact distance are controlled fixture inputs, not a free-play physics rehearsal. */
public final class RodAchievementNativeFixture {
    private static int checks;
    private static final int[] IDS={1,2,3,4,5,7};
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private RodAchievementNativeFixture() { }
    public static void run(ServerLevel level,ServerPlayer player,ServerPlayer other) throws Exception {
        checks=0;
        try {
            for (int id:IDS) { check(adv(player,id)!=null,"Native rod advancement loaded "+id); revoke(player,id); }
            prepare(player); other.setPos(250,210,250);
            waterAndGrapple(level,player);
            arrivals(level,player,other);
            mergeProvenance(level,player);
            otherPickupProvenance(level,player,other);
            launchAndFall(level,player,other);
            rejection(level,player);
            for(int id:IDS) check(done(player,id),"All six rod criteria earned from observed operations "+id);
            player.getAdvancements().save();
            for(int id:IDS) revoke(player,id);
            player.getAdvancements().reload(level.getServer().getAdvancements());
            for(int id:IDS) check(done(player,id),"Native rod progress JSON reload "+id);
            System.out.println("RodAchievementNativeFixture: "+checks+" assertions passed; real native rod force/range/fall commits, fixture-controlled trajectory/impact inputs; no client physics rehearsal");
        } finally {
            AscendanceFishingService.forget(player); player.stopUsingItem();
            ENTITIES.forEach(Entity::discard); ENTITIES.clear();
            player.setGameMode(GameType.SURVIVAL); player.setPos(2,120,2); player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(false);
        }
    }
    private static void prepare(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL); player.setHealth(player.getMaxHealth());
        var data=EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        data.setTier(AscendanceTiers.TRANSCENDENT); data.clearAllSkillsForAdmin(); SkillEffectRuntime.reset(player); SkillEffectRuntime.refresh(player);
        var rod=new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get()); EquipmentTierData.setTier(rod,EquipmentTier.TRANSCENDENT);
        player.setItemInHand(InteractionHand.MAIN_HAND,rod); player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setYRot(90); player.setXRot(-45); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(false);
    }
    private static FishingHook hook(ServerLevel level,ServerPlayer player,Entity target,BlockPos anchor) {
        AscendanceFishingService.forget(player); player.stopUsingItem();
        var stack=player.getMainHandItem();
        var hook=new FishingHook(player,level,0,0); ENTITIES.add(hook); check(level.addFreshEntity(hook),"Native bobber registered");
        var access=(RodHookAccess)hook;
        access.essenceAscendance$initializeRod(InteractionHand.MAIN_HAND,stack,EquipmentFishingRodService.context(player,stack));
        if(target!=null) access.essenceAscendance$attachEntity(target);
        else access.essenceAscendance$attachBlock(new BlockHitResult(new Vec3(anchor.getX(),anchor.getY()+.5,anchor.getZ()+.5),Direction.WEST,anchor,false));
        check(!access.essenceAscendance$rodState().release(),"First release only arms attachment");
        stack.getItem().use(level,player,InteractionHand.MAIN_HAND);
        check(access.essenceAscendance$rodState().pulling && player.isUsingItem(),"Native item press starts attached pulling");
        return hook;
    }
    private static void force(FishingHook hook,ServerPlayer player,Entity target) {
        var access=(RodHookAccess)hook; var state=access.essenceAscendance$rodState();
        Vec3 before=target.getDeltaMovement(); float health=target instanceof LivingEntity living ? living.getHealth() : 0;
        var context=EquipmentFishingRodService.context(player,state.castStack);
        Vec3 toward=player.getBoundingBox().getCenter().subtract(target.getBoundingBox().getCenter());
        double resistance=target instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
        Vec3 expected=PersonalizedRodLaunch.force(player,state.castStack,target,toward,before,context,state.poweredTicks,
                EssenceConfigManager.get().fishingRodBalance(),resistance,state.entityViewScale,new RodPullMath.OpeningFrame(toward,before));
        AscendanceFishingService.tickAttachment(hook,access);
        check(target.getDeltaMovement().subtract(before.add(expected)).lengthSqr()<1e-14,"Observation preserves exact existing rod force formula");
        if(target instanceof LivingEntity living) check(living.getHealth()==health,"Observation adds no damage");
    }
    private static void waterAndGrapple(ServerLevel level,ServerPlayer player) {
        player.setPos(200.5,205,200.5); load(level,player.position());
        // Successful fixture worlds are saved and reused. Remove this fixture's old ledge/cover
        // before constructing the same thin test corridor, so it cannot obstruct the next pull.
        for(int x=199;x<=224;x++) for(int y=201;y<=220;y++)
            level.setBlock(new BlockPos(x,y,200),Blocks.AIR.defaultBlockState(),3);
        for(int x=199;x<=224;x++) level.setBlock(new BlockPos(x,200,200),Blocks.WATER.defaultBlockState(),3);
        BlockPos anchor=new BlockPos(230,220,200); level.setBlock(anchor,Blocks.STONE.defaultBlockState(),3);
        var hook=hook(level,player,null,anchor);
        check(RodOutcomes.exposedWaterRoute(player,player.position(),player.position().add(1,0,0)),"High route above exposed water qualifies without skiing band");
        level.setBlock(new BlockPos(201,202,200),Blocks.STONE.defaultBlockState(),3);
        check(!RodOutcomes.exposedWaterRoute(player,player.position(),player.position().add(1,0,0)),"Solid covering column rejects false water sample");
        level.setBlock(new BlockPos(201,202,200),Blocks.AIR.defaultBlockState(),3);
        check(!RodOutcomes.exposedWaterRoute(player,new Vec3(200.5,200.5,200.5),new Vec3(201.5,200.5,200.5)),"Submerged route rejects");
        AscendanceFishingService.tickAttachment(hook,(RodHookAccess)hook);
        check(player.getDeltaMovement().lengthSqr()>0,"Native anchor force succeeded before observation");
        for(int i=1;i<=20;i++) { advance(level,1); player.setPos(200.5+i,205,200.5); held(hook,player); RodOutcomes.tick(player); }
        check(done(player,1),"One anchored exposed-water displacement reaches exact20");
        for(int i=1;i<=10;i++) { advance(level,1); player.setPos(220.5,205+i,200.5); held(hook,player); RodOutcomes.tick(player); }
        check(!done(player,5),"Midair coordinate ten blocks higher is insufficient");
        level.setBlock(new BlockPos(220,214,200),Blocks.WATER.defaultBlockState(),3); player.setOnGround(true);
        check(!RodOutcomes.supportedDry(player),"Water-surface collision cannot substitute supported dry ledge");
        player.setOnGround(false);
        level.setBlock(new BlockPos(220,214,200),Blocks.STONE.defaultBlockState(),3);
        advance(level,1); player.setOnGround(true); RodOutcomes.tick(player);
        check(done(player,5),"Affirmative solid-supported grapple landing exact10");
        AscendanceFishingService.forget(player); player.stopUsingItem(); player.setOnGround(false);
    }
    private static void arrivals(ServerLevel level,ServerPlayer player,ServerPlayer other) {
        prepare(player); player.setPos(260,215,200); load(level,player.position());
        Mob target=zombie(level,270,215,200);
        FishingHook hook=hook(level,player,target,null); force(hook,player,target);
        for(int i=1;i<=8;i++) { advance(level,1); target.setPos(270-i,215,200); held(hook,player); RodOutcomes.tick(player); }
        check(done(player,4),"Same hostile from >=8 reaches live native melee range");
        check(!done(other,4),"Nearby teammate does not receive hostile reel");
        AscendanceFishingService.forget(player); player.stopUsingItem();
        var item=new ItemEntity(level,270,215,200,new ItemStack(Items.DIAMOND)); ENTITIES.add(item); check(level.addFreshEntity(item),"Dropped item registered");
        hook=hook(level,player,item,null); force(hook,player,item);
        for(int i=1;i<=8;i++) { advance(level,1); item.setPos(270-i,215,200); held(hook,player); RodOutcomes.tick(player); }
        advance(level,1); item.setPos(260.9,215,200);
        item.playerTouch(player);
        check(done(player,7),"Native original-player item-touch range confirms pulled same item");
        check(!done(other,7),"Teammate cannot inherit item reel");
        AscendanceFishingService.forget(player); player.stopUsingItem();
    }
    private static void launchAndFall(ServerLevel level,ServerPlayer player,ServerPlayer other) {
        prepare(player); player.setPos(300,215,200); load(level,player.position());
        Mob target=zombie(level,310,215,200);
        FishingHook hook=hook(level,player,target,null); force(hook,player,target);
        player.releaseUsingItem();
        check(hook.isRemoved(),"Native release removes hook but retains bounded launched-target cause");
        for(int i=1;i<=20;i++) { advance(level,1); target.setPos(310-i,216,200); target.setOnGround(false); RodOutcomes.tick(player); }
        check(done(player,3),"Release-origin hostile horizontal displacement exact20");
        target.push(0,0,0);
        advance(level,1);
        check(target.causeFallDamage(100,1,target.damageSources().fall()),"Native fall impact accepts actual damage");
        check(!target.isAlive() && done(player,2),"Confirmed native fall death credited to live original rod actor");
        check(!done(other,2),"Teammate not credited causal fall");
        AscendanceFishingService.forget(player);
    }
    private static void rejection(ServerLevel level,ServerPlayer player) {
        for(String mode:List.of("teleport","unload","expiry","otherDamage","adminFallNonfatal","competingKnockback","competingPush","creative","creativeCast","creativeMidPull","noForce")) {
            prepare(player); player.setPos(350,215,200); load(level,player.position());
            Mob target=zombie(level,360,215,200);
            revoke(player,2); revoke(player,3);
            if(mode.equals("creative") || mode.equals("creativeCast")) player.setGameMode(GameType.CREATIVE);
            if(mode.equals("noForce")) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            FishingHook hook=hook(level,player,target,null);
            if(mode.equals("creativeCast")) player.setGameMode(GameType.SURVIVAL);
            force(hook,player,target);
            if(mode.equals("creativeMidPull")) {
                advance(level,1); RodOutcomes.tick(player);
                player.setGameMode(GameType.CREATIVE); target.setDeltaMovement(Vec3.ZERO);
                force(hook,player,target);
                check(target.getDeltaMovement().lengthSqr()>0,"Creative mid-pull force really changes native motion");
                player.setGameMode(GameType.SURVIVAL);
            }
            player.releaseUsingItem(); player.setGameMode(GameType.SURVIVAL);
            advance(level,1); target.setPos(361,216,200); target.setOnGround(false); RodOutcomes.tick(player);
            switch(mode) {
                case "teleport" -> target.teleportTo(361.25,216,200);
                case "unload" -> target.discard();
                case "expiry" -> advance(level,199);
                case "competingKnockback" -> target.knockback(.4,1,0);
                case "competingPush" -> target.push(.4,0,0);
                case "adminFallNonfatal" -> {
                    float health=target.getHealth(); target.invulnerableTime=0;
                    level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                            "damage "+target.getUUID()+" 1 minecraft:fall");
                    check(target.isAlive() && target.getHealth()<health,"Actual administrator FALL damage weakens the target without killing it");
                }
                case "otherDamage" -> {
                    target.invulnerableTime=0;
                    //? if >=26.1.2
                    /*target.hurtServer(level,target.damageSources().generic(),1);*/
                    //? if <26.1.2
                    target.hurt(target.damageSources().generic(),1);
                }
            }
            target.invulnerableTime=0; target.causeFallDamage(100,1,target.damageSources().fall());
            check(!done(player,2) && !done(player,3),"No fabricated delayed rod completion after "+mode);
            AscendanceFishingService.forget(player); target.discard();
        }
        // Restore earned completions through a fresh legitimate force/trajectory/fall sequence, not native grant.
        launchAndFall(level,player,player.server.getPlayerList().getPlayers().stream().filter(p->p!=player).findFirst().orElseThrow());
    }
    private static void mergeProvenance(ServerLevel level,ServerPlayer player) throws Exception {
        var nativeMerge=ItemEntity.class.getDeclaredMethod("tryToMerge",ItemEntity.class);
        nativeMerge.setAccessible(true);
        for(boolean commits:List.of(true,false)) {
            prepare(player); player.setPos(260,215,230); load(level,player.position()); revoke(player,7);
            var tracked=new ItemEntity(level,270,215,230,new ItemStack(Items.DIAMOND,8));
            var donor=new ItemEntity(level,270.2,215,230,new ItemStack(commits ? Items.DIAMOND : Items.EMERALD,1));
            ENTITIES.add(tracked); ENTITIES.add(donor);
            check(level.addFreshEntity(tracked) && level.addFreshEntity(donor),"Merge fixture native dropped items registered");
            var hook=hook(level,player,tracked,null); force(hook,player,tracked);
            nativeMerge.invoke(tracked,donor);
            if(commits) check(tracked.isAlive() && tracked.getItem().getCount()==9 && donor.isRemoved(),"Real native merge retains hooked receiver UUID while consuming donor");
            else check(tracked.isAlive() && tracked.getItem().getCount()==8 && donor.isAlive() && donor.getItem().getCount()==1,"Native incompatible-item merge performs no transfer");
            for(int i=1;i<=9;i++) {
                advance(level,1); tracked.setPos(270-i,215,230); held(hook,player); RodOutcomes.tick(player);
            }
            tracked.playerTouch(player);
            check(done(player,7)!=commits,commits ? "Surviving receiver UUID cannot inherit attribution through actual item merge"
                    : "Rejected/no-op native merge preserves the genuine hooked-item history");
            AscendanceFishingService.forget(player); player.stopUsingItem();
            tracked.discard(); donor.discard();
        }
    }
    private static void otherPickupProvenance(ServerLevel level,ServerPlayer player,ServerPlayer other) {
        var inventory=other.getInventory();
        var savedItems=new ArrayList<ItemStack>();
        for(int i=0;i<inventory.getContainerSize();i++) savedItems.add(inventory.getItem(i).copy());
        Vec3 previous=other.position();
        try {
            for(boolean partial:List.of(true,false)) {
                prepare(player); player.setPos(260,215,240); load(level,player.position()); revoke(player,7);
                for(int i=0;i<inventory.getContainerSize();i++) inventory.setItem(i,new ItemStack(Items.COBBLESTONE,64));
                if(partial) inventory.setItem(0,new ItemStack(Items.DIAMOND,63));
                var item=new ItemEntity(level,270,215,240,new ItemStack(Items.DIAMOND,8));
                item.setNoPickUpDelay(); ENTITIES.add(item); check(level.addFreshEntity(item),"Other-pickup native dropped item registered");
                var hook=hook(level,player,item,null); force(hook,player,item);
                other.setPos(270,215,240); item.playerTouch(other);
                check(item.isAlive() && item.getItem().getCount()==(partial ? 7 : 8),partial
                        ? "Different player's one-slot inventory actually consumes one item while original entity UUID survives"
                        : "Full different-player inventory performs no pickup and leaves stack unchanged");
                other.setPos(300,215,240);
                for(int i=1;i<=9;i++) {
                    advance(level,1); item.setPos(270-i,215,240); held(hook,player); RodOutcomes.tick(player);
                }
                item.playerTouch(player);
                check(done(player,7)!=partial,partial ? "Partial teammate pickup invalidates surviving hooked-item attribution"
                        : "Unsuccessful teammate touch preserves original actor's genuine item-reel success");
                AscendanceFishingService.forget(player); player.stopUsingItem(); item.discard();
            }
        } finally {
            for(int i=0;i<savedItems.size();i++) inventory.setItem(i,savedItems.get(i));
            other.setPos(previous);
        }
    }
    private static Mob zombie(ServerLevel level,double x,double y,double z) {
        //? if >=26.1.2
        /*Mob target=new net.minecraft.world.entity.monster.zombie.Zombie(EntityType.ZOMBIE,level);*/
        //? if <26.1.2
        Mob target=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,level);
        load(level,new Vec3(x,y,z)); target.setPos(x,y,z); target.setNoAi(true); target.setHealth(20); ENTITIES.add(target);
        check(level.addFreshEntity(target),"Hostile native target registered"); return target;
    }
    private static void load(ServerLevel level,Vec3 at) { for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) level.getChunkAt(BlockPos.containing(at.x+dx*16,at.y,at.z+dz*16)); }
    private static void held(FishingHook hook,ServerPlayer player) {
        AscendanceFishingService.heldInput(player,hook.getId(),true);
        AscendanceFishingService.tickAttachment(hook,(RodHookAccess)hook);
        check(!hook.isRemoved(),"Fixture keeps genuine supported native pull active");
    }
    private static void advance(ServerLevel level,long ticks) { level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+ticks); }
    private static net.minecraft.advancements.AdvancementHolder adv(ServerPlayer player,int id) { return player.server.getAdvancements().get(AchievementService.resource(id)); }
    private static boolean done(ServerPlayer player,int id) { return player.getAdvancements().getOrStartProgress(adv(player,id)).isDone(); }
    private static void revoke(ServerPlayer player,int id) { player.getAdvancements().revoke(adv(player,id),"achieved"); }
    private static void check(boolean result,String description) { checks++; if(!result) throw new AssertionError(description); }
}
