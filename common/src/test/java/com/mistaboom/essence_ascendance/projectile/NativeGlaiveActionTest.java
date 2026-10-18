package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionCollision.*;

/** Finite curved geometry plus real native hurt/defense/enchantment/attribution/wear and action lifecycle. No save/world. */
public final class NativeGlaiveActionTest {
    private static int checks;
    public static void run() throws ReflectiveOperationException {
        geometry();
        swipeGeometry();
        var f=new ProjectileNativeInterceptionTest.Fixture(ActionPlayer.class);
        NativeGuardOutcomeTest.registry(f);
        installEnchantments(f);
        try {
            timingAndInput(f);
            personalizedContent();
            aimedGrip(f);
            groundSwipe(f);
            nativeContact(f);
            blockers(f);
            cancellation(f);
            wearAndCritical(f);
            swipeNative(f);
            offenseSnapshot(f);
            chargedSnapshot(f);
            integratedAudit();
            System.out.println("NativeGlaiveActionTest: "+checks+" continuous geometry, lifecycle and native combat checks PASS");
        } finally { WeaponActionRuntime.clear(); f.close(); }
    }
    /** Concurrent native actions and reproducible cost receipts; elapsed time is diagnostic only. */
    private static void integratedAudit() throws ReflectiveOperationException {
        var f=new ProjectileNativeInterceptionTest.Fixture(ActionPlayer.class);
        NativeGuardOutcomeTest.registry(f);
        installEnchantments(f);
        try {
            equip(f,EquipmentTier.TRANSCENDENT);
            var second=f.player(ActionPlayer.class,new Vec3(20,0,0),"glaive_second_attacker");
            var weapon=new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
            EquipmentTierData.setTier(weapon,EquipmentTier.TRANSCENDENT);
            second.setItemInHand(InteractionHand.MAIN_HAND,weapon);
            f.saved.getPlayerData(second.getUUID()).setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT);
            TwoHandedEquipmentService.refresh(second); EquipmentAttributeService.sync(second);
            var observerA=f.player(ActionPlayer.class,new Vec3(0,2,7),"glaive_observer_a");
            var observerB=f.player(ActionPlayer.class,new Vec3(20,2,7),"glaive_observer_b");
            for (int x=-6;x<=26;x++) for (int z=0;z<=30;z++)
                f.level.memoryBlocks.put(new BlockPos(x,-1,z),Blocks.STONE.defaultBlockState());
            for (int offset:new int[]{0,20}) {
                for (int y=0;y<4;y++) {
                    f.level.memoryBlocks.put(new BlockPos(offset-3,y,4),Blocks.OAK_LOG.defaultBlockState());
                    f.level.memoryBlocks.put(new BlockPos(offset+3,y,4),Blocks.OAK_LOG.defaultBlockState());
                }
                for (int x:new int[]{-2,0,2}) {
                    var zombie=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,f.level);
                    zombie.setPos(offset+x,0,7);
                    zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); zombie.setHealth(1000);
                    f.level.entities.add(zombie);
                }
            }
            var attackers=List.of(f.player,second);
            long classifications=TwoHandedEquipmentPolicy.classifications();
            check(attackers.stream().allMatch(p->WeaponActionRuntime.attempt(p,false)),"Independent players accept concurrent swipes");
            var allocations=allocationMeter();
            long allocatedBefore=allocated(allocations),started=System.nanoTime(),bytes=0;
            int largestPacket=0,maxKnots=0,interested=0;
            for (int tick=1;tick<=18;tick++) {
                f.level.tick++;
                for (int i=0;i<attackers.size();i++) {
                    var player=attackers.get(i);
                    player.setPos(i*20+tick*.01,0,0);
                    player.setYRot((float)(Math.sin(tick*.3)*8));
                    player.setXRot((float)(Math.sin(tick*.2)*3));
                    WeaponActionRuntime.observeAim(player);
                    TwoHandedEquipmentService.reconcile(player); WeaponActionRuntime.tick(player);
                    var state=WeaponActionRuntime.snapshot(player);
                    check(state!=null && state.range()==10,"Concurrent action retains its own accepted range");
                    maxKnots=Math.max(maxKnots,state.knots().size());
                    var payload=new com.mistaboom.essence_ascendance.network.WeaponActionStatePayload(
                            f.level.dimension().location(),player.getId(),player.getUUID(),state,EquipmentAppearance.resolve(player,player.getMainHandItem()));
                    var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),f.level.registryAccess());
                    try {
                        com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.encode(buffer,payload);
                        int size=buffer.readableBytes(); largestPacket=Math.max(largestPacket,size); bytes+=size;
                        var decoded=com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.decode(buffer);
                        check(decoded.owner().equals(player.getUUID()) && decoded.state().actionId()==state.actionId(),
                                "Concurrent observer codec preserves owner/action identity");
                    } finally { buffer.release(); }
                    for (var observer:List.of(observerA,observerB))
                        if (com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.near(state,observer.getEyePosition(),8)) interested++;
                }
            }
            check(interested>0,"Multiple nearby observers discover the moving physical weapons");
            check(attackers.stream().allMatch(p->WeaponActionRuntime.snapshot(p).phase()==WeaponActionRuntime.Phase.IDLE),
                    "Concurrent actions independently finish recovery");
            check(TwoHandedEquipmentPolicy.classifications()==classifications,"Concurrent contacts, motion and observer work never classify compatibility");
            System.out.println("Glaive integrated audit: 2 attackers, 2 observers, 6 targets, floor/trunks, 18 ticks; "
                    +((System.nanoTime()-started)/1_000_000.0)+" ms, "+allocationDelta(allocations,allocatedBefore)
                    +" allocated bytes, "+bytes+" encoded bytes total before recipient fanout, max packet "+largestPacket
                    +", max solved knots "+maxKnots+", classifications 0 (memory-world CPU/codec diagnostic)");
            // 16/64 are retained stress inputs, not supported gameplay ranges or restored perks.
            f.level.entities.removeIf(e->e!=f.player);
            for (double range:new double[]{10,16,30,64}) {
                var distant=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,f.level);
                distant.setPos(0,0,range-.5); f.level.entities.add(distant);
                var from=new Pose(new Vec3(0,1.62,0),-7.5,0,range);
                var to=new Pose(new Vec3(.02,1.62,.01),7.5,1.5,range);
                for (int i=0;i<2;i++) auditSweep(f,from,to);
                allocatedBefore=allocated(allocations); started=System.nanoTime();
                long blockReads=f.level.blockStateReads,entityQueries=f.level.entityQueries;
                int knots=0;
                for (int i=0;i<4;i++) {
                    var result=auditSweep(f,from,to); knots=Math.max(knots,result.knots().size());
                    check(result.completed(),"Cost probe completes its accepted collision path");
                }
                System.out.println("Glaive collision audit range="+range+": "
                        +((System.nanoTime()-started)/4_000_000.0)+" ms/segment, "
                        +(allocationDelta(allocations,allocatedBefore)/4)+" allocated bytes/segment, "
                        +((f.level.blockStateReads-blockReads)/4)+" block reads, "+((f.level.entityQueries-entityQueries)/4)
                        +" entity queries/segment, max knots="+knots
                        +(range==16 || range==64 ? " (unsupported-range stress only)":"") );
                f.level.entities.remove(distant);
            }
        } finally { WeaponActionRuntime.clear(); f.close(); }
    }
    private static Sweep auditSweep(ProjectileNativeInterceptionTest.Fixture f,Pose from,Pose to) {
        return to.tipDistance()>10 ? WeaponActionFastCollision.swipe(from,to,f.player,c->true)
                : WeaponActionCollision.swipe(from,to,WeaponActionCollision.world(f.player,true),c->true);
    }
    private static com.sun.management.ThreadMXBean allocationMeter() {
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean meter) || !meter.isThreadAllocatedMemorySupported()) return null;
        if (!meter.isThreadAllocatedMemoryEnabled()) meter.setThreadAllocatedMemoryEnabled(true);
        return meter;
    }
    private static long allocated(com.sun.management.ThreadMXBean meter) {
        return meter==null ? -1:meter.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
    private static long allocationDelta(com.sun.management.ThreadMXBean meter,long before) {
        return meter==null ? -1:allocated(meter)-before;
    }
    private static void personalizedContent() throws ReflectiveOperationException {
        var f = new ProjectileNativeInterceptionTest.Fixture(ActionPlayer.class);
        NativeGuardOutcomeTest.registry(f);
        try {
            var sebastian = com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService.definitions()
                    .stream().filter(p -> p.id().equals("sebastian_long_distance_relationship")).findFirst().orElseThrow();
            UUID old = f.player.getUUID();
            f.player.setUUID(sebastian.playerId());
            f.players.remove(old); f.players.put(f.player.getUUID(), f.player);
            ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.player.Player.class, f.player, "gameProfile",
                    new com.mojang.authlib.GameProfile(sebastian.playerId(), "Sebastian"));
            var stack = equip(f, EquipmentTier.TRANSCENDENT);
            var binding = SoulboundEquipmentData.bind(stack, sebastian.playerId(), "Sebastian");
            var unchanged = stack.copy();
            for (var tier : EquipmentTier.values()) {
                EquipmentTierData.setTier(stack, tier);
                var eligible = com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService.active(f.player, stack);
                check(eligible.isPresent() == (tier == EquipmentTier.TRANSCENDENT), "Actual Sebastian definition uses exact tier eligibility");
                equal(GlaiveTuning.maximumRange(f.player, stack), tier == EquipmentTier.TRANSCENDENT ? 30 : GlaiveTuning.maximumRange(tier),
                        "Personalized range falls back at every wrong tier");
                var appearance = EquipmentAppearance.resolve(f.player, stack);
                check(appearance.baseRgb() == (eligible.isPresent() ? 0x0d0967 : EquipmentTierVisuals.primaryRgb(tier)), "Eligible blade-base tint");
                check(appearance.accentRgb() == (eligible.isPresent() ? 0xb6f800 : EquipmentTierVisuals.armorAccentRgb(tier)), "Eligible shared accent tint");
                if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT)
                    PersonalizedClient.verify(f.player, stack, eligible);
            }
            check(com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService.active(f.player, stack.copy()).isEmpty(),
                    "Detached catalog and dropped copies have no personalized overlay");
            check(EquipmentCatalogStacks.allTiers(stack.getItem()).stream().noneMatch(copy ->
                    com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService.active(f.player, copy).isPresent()),
                    "All creative/catalog tiers remain ordinary");
            check(WeaponActionRuntime.attempt(f.player, true), "Actual Sebastian definition accepts stab");
            equal(WeaponActionRuntime.snapshot(f.player).range(), 30, "Authority snapshots eligible 30-block range");
            for (int i=0;i<4;i++) tick(f);
            equal(WeaponActionRuntime.snapshot(f.player).current().tipDistance(),30,"Eligible weapon extends to 30 in the ordinary four ticks");
            var payload = new com.mistaboom.essence_ascendance.network.WeaponActionStatePayload(f.level.dimension().location(),
                    f.player.getId(), f.player.getUUID(), WeaponActionRuntime.snapshot(f.player), EquipmentAppearance.resolve(f.player, stack));
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), f.level.registryAccess());
            try {
                com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.encode(buffer,payload);
                var remote = com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.decode(buffer);
                check(remote.appearance().baseRgb()==0x0d0967 && remote.appearance().accentRgb()==0xb6f800 && remote.state().range()==30,
                        "Observer wire agrees on actual eligible appearance and range");
            } finally { buffer.release(); }
            FracturedEquipmentData.markFractured(stack); tick(f);
            check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Fracture cancels personalized action");
            equal(GlaiveTuning.maximumRange(f.player,stack),4,"Fracture removes personalized range immediately");
            check(EquipmentAppearance.resolve(f.player,stack).baseRgb()!=0x0d0967,"Fracture removes personalized appearance immediately");
            FracturedEquipmentData.clearFractured(stack); stack.setDamageValue(0);
            equal(GlaiveTuning.maximumRange(f.player,stack),30,"Repair restores eligible range without a stale cache");
            stack.setDamageValue(stack.getMaxDamage());
            equal(GlaiveTuning.maximumRange(f.player,stack),10,"Unusable item loses personalized range");
            stack.setDamageValue(0);
            WeaponActionRuntime.clear();
            ProjectileNativeInterceptionTest.set(LivingEntity.class,f.player,"attackStrengthTicker",1000);
            TwoHandedEquipmentService.refresh(f.player);
            check(WeaponActionRuntime.attempt(f.player,false),"Repaired personalized weapon accepts swipe");
            equal(WeaponActionRuntime.snapshot(f.player).range(),30,"Personalized swipe uses the accepted physical range");
            for (int i=0;i<40;i++) {
                f.player.setYRot(i%2==0 ? 35 : -35);
                WeaponActionRuntime.observeAim(f.player);
            }
            f.player.setYRot(0); WeaponActionRuntime.observeAim(f.player);
            tick(f);
            check(WeaponActionRuntime.snapshot(f.player).knots().size()<=201,
                    "Long-range packet burst has bounded collision and wire work per tick");
            for (int i=1;i<10;i++) {
                tick(f);
                check(WeaponActionRuntime.snapshot(f.player).knots().size()<=50,
                        "Long-range swipe has bounded collision and wire work per tick");
            }
            f.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); tick(f);
            check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Unequipping cancels accepted personalized range");
            var other = f.player(ActionPlayer.class, new Vec3(10,0,0), "ordinary_holder");
            other.setItemInHand(InteractionHand.MAIN_HAND,stack);
            equal(GlaiveTuning.maximumRange(other,stack),10,"Another actual holder receives normal tier range");
            check(EquipmentAppearance.resolve(other,stack).baseRgb()==EquipmentTierVisuals.primaryRgb(EquipmentTier.TRANSCENDENT),
                    "Other holder receives ordinary grey metal");
            if (dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.CLIENT)
                PersonalizedClient.verify(other,stack,java.util.Optional.empty());
            other.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            f.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            check(ItemStack.isSameItemSameComponents(stack,unchanged) && SoulboundEquipmentData.read(stack).orElseThrow().equals(binding),
                    "Flavor, range, fracture repair and transfer preserve components and permanent ownership");
        } finally { WeaponActionRuntime.clear(); f.close(); }
    }

    private static final class PersonalizedClient {
        static void verify(ServerPlayer holder, ItemStack stack,
                java.util.Optional<com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentDefinition> expected)
                throws ReflectiveOperationException {
            var holders = List.of(holder);
            var actual = com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.active(holders,stack);
            check(actual.equals(expected),"Client and authority share exact eligibility");
            int base = com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders,stack,0);
            int accent = com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders,stack,1);
            check(base==(0xFF000000|EquipmentAppearance.resolve(holder,stack).baseRgb())
                    && accent==(0xFF000000|EquipmentAppearance.resolve(holder,stack).accentRgb()),"Compact and extended palette agree");
            check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders,stack,3)==accent
                    && com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders,stack,2)==EquipmentTierVisuals.CLEAR_TINT,
                    "Both separate authored accent parts match and wood is untinted");
            var build = EquipmentTooltipSyncService.class.getDeclaredMethod("build",ServerPlayer.class); build.setAccessible(true);
            var payload = (com.mistaboom.essence_ascendance.network.EquipmentTooltipPayload)build.invoke(null,holder);
            var accept = com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState.class.getDeclaredMethod("accept",
                    com.mistaboom.essence_ascendance.network.EquipmentTooltipPayload.class,Runnable.class); accept.setAccessible(true);
            accept.invoke(null,payload,(Runnable)()->{});
            var apply = com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState.class.getDeclaredMethod("applyToGeneratedTooltip",
                    ItemStack.class,List.class,Item.TooltipContext.class,TooltipFlag.class,java.util.Optional.class); apply.setAccessible(true);
            var tooltip = new ArrayList<net.minecraft.network.chat.Component>(); tooltip.add(net.minecraft.network.chat.Component.literal("Weapon"));
            apply.invoke(null,stack,tooltip,Item.TooltipContext.EMPTY,TooltipFlag.NORMAL,actual);
            var flavor = tooltip.stream().filter(c -> com.mistaboom.essence_ascendance.client.ui.StyledTextLayout.runs(c)
                    .stream().anyMatch(run -> run.style().isItalic())).toList();
            String flavorText = String.join(" ",flavor.stream().map(c -> c.getString().strip()).toList()).replaceAll("\\s+"," ");
            check(flavorText.equals(expected.map(p -> p.flavor()).orElse("")),
                    "Organized tooltip includes eligible flavor exactly once, preserves wrapping and never leaks it");
            var range = tooltip.stream().flatMap(c -> c.getSiblings().stream())
                    .map(c -> c.getContents()).filter(c -> c instanceof net.minecraft.network.chat.contents.TranslatableContents)
                    .map(c -> (net.minecraft.network.chat.contents.TranslatableContents)c)
                    .filter(c -> c.getKey().equals("tooltip.essence_ascendance.equipment.stat.weapon_attack_range")).toList();
            check(range.size()==1 && range.getFirst().getArgs()[0].equals(expected.isPresent()?"30":""+
                    (int)GlaiveTuning.maximumRange(stack)),"Tooltip shows current eligible range exactly once");
            com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState.clear();
        }
    }
    private static void aimedGrip(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        equip(f,EquipmentTier.TRANSCENDENT); f.player.setPos(.5,0,.5);
        var view=GlaiveGrip.frame(f.player.getEyePosition(),0,0);
        check(WeaponActionAim.target(f.player,view,16)==null,"No focus target in empty loaded space");
        var victim=target(f,6); victim.setPos(.5,0,6);
        var focus=WeaponActionAim.target(f.player,view,16);
        check(focus!=null && Math.abs(focus.z-victim.getBoundingBox().minZ)<1e-6,"Crosshair focus hits actual entity boundary");
        f.level.memoryBlocks.put(new BlockPos(0,1,3),Blocks.STONE.defaultBlockState());
        focus=WeaponActionAim.target(f.player,view,16);
        check(focus!=null && Math.abs(focus.z-3)<1e-6,"Nearer terrain shields camera focus from entity behind it");
        for(var main:HumanoidArm.values()) {
            f.player.setMainArm(main);
            var aim=WeaponActionAim.pose(f.player,null,16,16);
            var parallel=GlaiveGrip.rigidAim(view,f.player.yBodyRot,main,f.player.getScale(),null,16,null);
            check(GlaiveGrip.shoulder(aim,main,f.player.getScale()).distanceTo(GlaiveGrip.shoulder(parallel,main,f.player.getScale()))<1e-8,
                    "Focus aiming rotates the main arm about its fixed body-relative shoulder");
            check(aim.pivot().add(aim.forward().scale(aim.pivot().distanceTo(focus))).distanceTo(focus)<1e-8,
                    "Authority converges physical shaft onto blocked camera target");
        }
        f.player.setMainArm(HumanoidArm.RIGHT); f.level.entities.remove(victim);
    }
    private static void groundSwipe(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var tiles=new ArrayList<Candidate>();
        for(int x=-8;x<8;x++) for(int z=-8;z<8;z++) tiles.add(new Candidate(new AABB(x,-1,z,x+1,0,z+1),Kind.BLOCK,null,true));
        var merged=mergeTerrain(tiles);
        check(merged.size()==1 && merged.getFirst().bounds().equals(new AABB(-8,-1,-8,8,0,8)),"Flat ground merges to the exact same solid union");
        tiles.removeIf(c->c.bounds().minX==0 && c.bounds().minZ==0);
        check(mergeTerrain(tiles).stream().noneMatch(c->c.bounds().contains(new Vec3(.5,-.5,.5))),"Terrain union preserves a one-block opening");
        equip(f,EquipmentTier.TRANSCENDENT);
        for(int x=-18;x<=18;x++) for(int z=-18;z<=18;z++)
            f.level.memoryBlocks.put(new BlockPos(x,-1,z),Blocks.STONE.defaultBlockState());
        f.player.setXRot(25);
        check(WeaponActionRuntime.attempt(f.player,false),"Floor-aimed swipe accepts");
        long started=System.nanoTime();
        var times=new ArrayList<Long>();
        for(int i=0;i<5;i++) { long before=System.nanoTime(); tick(f); times.add((System.nanoTime()-before)/1_000_000); }
        var floor=WeaponActionRuntime.snapshot(f.player);
        check(floor.current().tipDistance()<5,"Floor contact shortens the finite blade");
        f.player.setXRot(-20); WeaponActionRuntime.observeAim(f.player); tick(f);
        check(WeaponActionRuntime.snapshot(f.player).current().tipDistance()>9,"Swipe extends immediately after lifting clear of floor");
        System.out.println("Glaive grounded swipe and lift: "+((System.nanoTime()-started)/1_000_000)+" ms for six authority ticks; floor ticks "+times);
        finish(f);
    }
    private static void swipeGeometry() {
        Pose from=new Pose(new Vec3(0,1.62,0),0,0,16,-60),to=from.withSweep(60);
        var left=new Candidate(new AABB(-3,0,4,-2,4,5),Kind.BLOCK,null,true);
        var right=new Candidate(new AABB(2,0,4,3,4,5),Kind.BLOCK,null,true);
        var gap=new Candidate(new AABB(-.3,0,8,.3,1.95,8.6),Kind.ENTITY,null,false);
        var shielded=new Candidate(new AABB(-4.2,0,7.8,-3.8,1.95,8.2),Kind.ENTITY,null,false);
        var shieldedRight=new Candidate(new AABB(3.8,0,7.8,4.2,1.95,8.2),Kind.ENTITY,null,false);
        var contacts=new ArrayList<Contact>();
        var result=swipe(from,to,(p,m)->List.of(shieldedRight,gap,shielded,right,left),c->{ contacts.add(c); return true; });
        check(contacts.stream().anyMatch(c->c.obstacle()==gap),"Exposed zombie-sized body farther back between trees receives finite blade contact");
        check(contacts.stream().noneMatch(c->c.obstacle()==shielded || c.obstacle()==shieldedRight),"Either tree shields the body behind it");
        check(contacts.stream().anyMatch(c->c.obstacle()==left) && contacts.stream().anyMatch(c->c.obstacle()==right),"Both trees shorten the continuing swipe");
        check(result.knots().getFirst().pose().tipDistance()==16 && result.knots().getLast().pose().tipDistance()==16
                && result.knots().stream().anyMatch(k->k.pose().tipDistance()<5),"Long endpoints retain intermediate shortening knots");
        double last=-1; for(var k:result.knots()) { check(k.fraction()>=last,"Solved swipe knots are chronological"); last=k.fraction(); }
        // Center remains behind the left tree, but the body's right side is exposed through the gap.
        var partial=new Candidate(new AABB(-4.2,0,7.8,-3.0,1.95,8.2),Kind.ENTITY,null,false);
        contacts.clear(); swipe(from,to,(p,m)->List.of(left,partial),c->{contacts.add(c); return true;});
        check(contacts.stream().anyMatch(c->c.obstacle()==partial),"Actual body exposure beyond a tree is hit even with its center occluded");
        Pose aimed=new Pose(new Vec3(1,2,3),33,-62,16,0);
        equal(aimed.withSweep(60).forward().dot(aimed.up()),0,"Built-in sweep remains in tilted aimed plane");
        equal(aimed.withSweep(60).forward().dot(aimed.forward()),.5,"Built-in plane angle is sixty degrees at any pitch");
        for(int i=0;i<24;i++) {
            Pose a=new Pose(new Vec3(0,2,0),170+i,-70+i*5,48,-60);
            Pose b=new Pose(new Vec3(2,3,1),195+i,-65+i*5,64,60);
            Pose mid=interpolate(a,b,.731); Vec3 point=mid.blade().center();
            var thin=new Candidate(new AABB(point,point).inflate(.00005),Kind.ENTITY,null,false);
            var found=new ArrayList<Contact>();
            swipe(a,b,(p,m)->List.of(thin),c->{found.add(c); return true;});
            check(found.stream().anyMatch(c->c.obstacle()==thin),"64-block thin curved target with movement, pitch, yaw wrap and extension "+i);
        }
        var first=new Candidate(new AABB(-2,1,6,-1.99,2,6.01),Kind.ENTITY,null,false);
        var second=new Candidate(new AABB(2,1,6,2.01,2,6.01),Kind.ENTITY,null,false);
        contacts.clear(); swipe(from,to,(p,m)->List.of(second,first),c->{contacts.add(c);return true;});
        check(contacts.size()==2 && contacts.getFirst().obstacle()==first && contacts.getLast().obstacle()==second,
                "Physical curved contact chronology wins over candidate iteration order");
        var wall=new Candidate(gap.bounds(),Kind.BLOCK,null,true);
        contacts.clear(); swipe(from,to,(p,m)->List.of(gap,wall),c->{contacts.add(c);return true;});
        check(contacts.stream().noneMatch(c->c.obstacle()==gap),"Terrain wins a truly coincident contact");
        // Distinct first contacts must not be sorted by UUID/list order.
        var early=new Candidate(new AABB(-.8,1,6,-.79,2,6.01),Kind.ENTITY,null,false);
        var late=new Candidate(new AABB(.8,1,6,.81,2,6.01),Kind.ENTITY,null,false);
        contacts.clear(); swipe(from,to,(p,m)->List.of(late,early),c->{contacts.add(c);return true;});
        check(contacts.size()==2 && contacts.get(0).fraction()<contacts.get(1).fraction(),"Separate physical entry roots remain chronologically distinct");
        Pose border=new Pose(new Vec3(29_999_000,1.62,0),0,0,64);
        Pose adjacent=new Pose(border.pivot().add(Math.ulp(border.pivot().x),0,0),0,0,64);
        Vec3 center=border.blade().center();
        var grazing=new Candidate(new AABB(center.x-.00001,center.y-1e-10,center.z-.00001,
                center.x+.00001,center.y+1e-10,center.z+.00001),Kind.ENTITY,null,false);
        contacts.clear(); swipe(border,adjacent,(p,m)->List.of(grazing),c->{contacts.add(c);return true;});
        check(contacts.size()==1,"World-border coordinate ULP terminates conservative event refinement without losing grazing contact");
    }
    private static void geometry() {
        for (double range:new double[]{4,6,8,10,12,16,64}) {
            for (int tick=0;tick<4;tick++) check(GlaiveTuning.stabTipDistance(range,tick)<range,"Extension not prematurely complete at "+range);
            equal(GlaiveTuning.stabTipDistance(range,4),range,"Same four-tick extension for every range");
        }
        Pose a=new Pose(new Vec3(0,1.62,0),-45,0,8), b=new Pose(a.pivot(),45,0,8);
        var thin=new Candidate(new AABB(-.005,1.615,7.65,.005,1.625,7.66),Kind.ENTITY,null,false);
        check(!a.blade().intersects(thin.bounds(),0) && !b.blade().intersects(thin.bounds(),0),"Both endpoint blade tests miss thin curved-path target");
        var trace=trace(a,b,(p,m)->List.of(thin));
        check(trace.contact()!=null && trace.knots().size()>2,"Adaptive rotation finds target between endpoint rays");
        var chord=new Candidate(new AABB(-.005,1.615,5.65,.005,1.625,5.66),Kind.ENTITY,null,false);
        check(trace(a,b,(p,m)->List.of(chord)).contact()==null,"A straight chord cannot damage empty inside of a curved tip path");
        Pose translated=new Pose(new Vec3(2,2.5,1),50,-60,16);
        Pose middle=interpolate(a,translated,.43);
        Vec3 point=middle.blade().center();
        var obstacle=new Candidate(new AABB(point,point).inflate(.001),Kind.BLOCK,null,false);
        check(trace(a,translated,(p,m)->List.of(obstacle)).contact()!=null,"Simultaneous movement, pitch, turn and extension covers finite blade");
        equal(unwrapYaw(179,-179),181,"Yaw wraps through two degrees");
        check(new Pose(Vec3.ZERO,0,-89.99,8).right().dot(new Pose(Vec3.ZERO,0,-90,8).right())>.999,"Vertical aim has stable basis");
        var behind=new Candidate(new AABB(-.02,1.6,3,.02,1.64,3.01),Kind.ENTITY,null,false);
        check(trace(new Pose(a.pivot(),0,0,8),new Pose(a.pivot(),0,0,8),(p,m)->List.of(behind)).contact()==null,"Shaft never deals entity damage");
        var shaftWall=new Candidate(behind.bounds(),Kind.BLOCK,null,true);
        var shortened=trace(new Pose(a.pivot(),0,0,8),new Pose(a.pivot(),0,0,8),(p,m)->List.of(shaftWall));
        check(shortened.contact()!=null && shortened.contact().pose().tipDistance()<3.01,"Terrain inside shaft occludes and preserves shortened pose");
        var near=new Candidate(new AABB(-1,1,3,1,2,3.01),Kind.BLOCK,null,true);
        var far=new Candidate(new AABB(-1,1,4,1,2,4.01),Kind.ENTITY,null,false);
        var ordered=trace(new Pose(a.pivot(),0,0,REST_TIP),new Pose(a.pivot(),0,0,8),(p,m)->List.of(far,near));
        check(ordered.contact().obstacle()==near,"First blocker wins independently of candidate iteration order");
        for (int i=0;i<60;i++) {
            double yaw=-170+i*5.7, pitch=-80+i*2.6;
            Pose from=new Pose(new Vec3(i*.01,2,0),yaw,pitch,2+i),to=new Pose(new Vec3(1,2.1,1),yaw+3,pitch+2,3+i);
            Vec3 onPath=interpolate(from,to,.37).blade().center();
            var tiny=new Candidate(new AABB(onPath,onPath).inflate(.0001),Kind.ENTITY,null,false);
            check(trace(from,to,(p,m)->List.of(tiny)).contact()!=null,"Thin-target property case "+i);
        }
        Pose longFrom=new Pose(new Vec3(0,2,0),-150,-70,64),longTo=new Pose(new Vec3(1,3,1),20,60,64);
        Vec3 longPoint=interpolate(longFrom,longTo,.79).blade().center();
        var longTarget=new Candidate(new AABB(longPoint,longPoint).inflate(.001),Kind.ENTITY,null,false);
        check(trace(longFrom,longTo,(p,m)->List.of(longTarget)).contact()!=null,"Full 64-block curved path is processed through late contact without a sampling cap");
        check(GlaiveTuning.STAB.recoveryTicks(1000)==1 && GlaiveTuning.STAB.outwardTicks()==4
                && GlaiveTuning.STAB.retractionTicks()==2,"Attack-speed saturation retains all phase floors");
    }
    private static ItemStack equip(ProjectileNativeInterceptionTest.Fixture f,EquipmentTier tier) throws ReflectiveOperationException {
        WeaponActionRuntime.clear();
        f.player.setPos(0,0,0); f.player.setYRot(0); f.player.setXRot(0); f.player.setHealth(20);
        f.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var stack=new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get()); EquipmentTierData.setTier(stack,tier);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        ProjectileNativeInterceptionTest.set(LivingEntity.class,f.player,"attackStrengthTicker",1000);
        TwoHandedEquipmentService.refresh(f.player); EquipmentAttributeService.sync(f.player);
        f.level.memoryBlocks=new HashMap<>(); f.level.unloadedMachineChunks=new HashSet<>();
        return stack;
    }
    private static void tick(ProjectileNativeInterceptionTest.Fixture f) {
        f.level.tick++; TwoHandedEquipmentService.reconcile(f.player); WeaponActionRuntime.tick(f.player);
    }
    private static void finish(ProjectileNativeInterceptionTest.Fixture f) {
        for(int i=0;i<100 && WeaponActionRuntime.snapshot(f.player).phase()!=WeaponActionRuntime.Phase.IDLE;i++) tick(f);
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.IDLE,"Action reaches shared ready state");
    }
    private static void timingAndInput(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        for(var tier:EquipmentTier.values()) {
            equip(f,tier);
            check(WeaponActionRuntime.attempt(f.player,true),"Accept ready stab "+tier);
            long classifications=TwoHandedEquipmentPolicy.classifications();
            check(!WeaponActionRuntime.attempt(f.player,true) && !WeaponActionRuntime.attempt(f.player,false),"Duplicate/mode overlap reject");
            check(f.player.getAttackStrengthScale(0)==0,"Native attack indicator starts empty");
            var accepted=WeaponActionRuntime.snapshot(f.player);
            equal(f.player.getCurrentItemAttackStrengthDelay(),accepted.readyAt()-accepted.acceptedAt(),"Native full-ready indicator uses action cadence");
            for(int i=1;i<=4;i++) {
                tick(f); var s=WeaponActionRuntime.snapshot(f.player);
                equal(s.current().tipDistance(),GlaiveTuning.stabTipDistance(GlaiveTuning.maximumRange(tier),i),"Authoritative normalized extension");
                check(s.phase()==(i==4 ? WeaponActionRuntime.Phase.RETRACT : WeaponActionRuntime.Phase.OUTWARD),"Fixed phase timing");
            }
            check(TwoHandedEquipmentPolicy.classifications()==classifications,"Acceptance, updates and collision never classify compatibility");
            finish(f);
            equal(f.player.getAttackStrengthScale(0),1,"Native indicator recovers with shared gate");
        }
        equip(f,EquipmentTier.TRANSCENDENT);
        long revision=TwoHandedEquipmentService.decision(f.player).revision();
        check(!WeaponActionRuntime.input(f.player,true,1,f.level.tick-11,revision),"Old press is rejected");
        check(!WeaponActionRuntime.input(f.player,true,1,f.level.tick,revision),"Duplicate sequence cannot replay a rejected press");
        check(!WeaponActionRuntime.input(f.player,true,2,f.level.tick,revision-1),"Stale equipment intent rejected");
        check(WeaponActionRuntime.input(f.player,true,3,f.level.tick,revision),"Fresh intent accepted");
        f.player.setYRot(60); WeaponActionRuntime.observeAim(f.player);
        f.player.setYRot(0); WeaponActionRuntime.observeAim(f.player);
        tick(f);
        check(WeaponActionRuntime.snapshot(f.player).knots().stream().anyMatch(p->p.pose().yaw()>50),"Observed turn and return survive same-tick endpoint equality");
        var packet=new com.mistaboom.essence_ascendance.network.WeaponActionStatePayload(f.player.level().dimension().location(),
                f.player.getId(),f.player.getUUID(),WeaponActionRuntime.snapshot(f.player));
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),f.level.registryAccess());
        try {
            com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.encode(buffer,packet);
            check(packet.equals(com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.decode(buffer)),"Renderer payload round-trips authoritative knots and timing");
        } finally { buffer.release(); }
        finish(f);
        check(!WeaponActionRuntime.input(f.player,true,4,f.level.tick-1,revision),"A press sent before recovery cannot arrive later as a queued action");
    }
    private static ActionPlayer target(ProjectileNativeInterceptionTest.Fixture f,double z) throws ReflectiveOperationException {
        var p=f.player(ActionPlayer.class,new Vec3(0,0,z),"glaive_contact");
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000);
        return p;
    }
    private static void nativeContact(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var stack=equip(f,EquipmentTier.LATENT); var target=target(f,3);
        double damage=f.player.getAttributeValue(Attributes.ATTACK_DAMAGE)*.5;
        long classifications=TwoHandedEquipmentPolicy.classifications();
        check(WeaponActionRuntime.attempt(f.player,true),"Ready stab accepts native damage action");
        tick(f); tick(f); tick(f);
        var contact=WeaponActionRuntime.snapshot(f.player);
        check(contact.physicalContact() && contact.phase()==WeaponActionRuntime.Phase.RETRACT,"Contact ends damaging phase");
        equal(1000-target.getHealth(),damage,"Real Player.hurt receives configured half native melee damage");
        check(target.getLastHurtByMob()==f.player && target.lastSource.getEntity()==f.player,"Native responsible melee attribution retained");
        check(stack.getDamageValue()==1,"Successful action charges one native wear event");
        int hits=target.calls; finish(f);
        check(target.calls==hits && stack.getDamageValue()==1,"Retraction/recovery never damage or charge wear");
        check(TwoHandedEquipmentPolicy.classifications()==classifications,"Hit/health/proc/wear paths never classify compatibility");
        f.level.entities.remove(target);

        stack=equip(f,EquipmentTier.LATENT); target=target(f,3);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        check(WeaponActionRuntime.attempt(f.player,true),"Armored contact accepted"); tick(f); tick(f); tick(f);
        check(1000-target.getHealth()>0 && 1000-target.getHealth()<damage,"Live native armor mitigates resolved stab");
        f.level.entities.remove(target);

        stack=equip(f,EquipmentTier.LATENT); target=target(f,3);
        target.hurt(target.damageSources().playerAttack(f.player),damage>0 ? (float)(damage+1) : 5);
        float health=target.getHealth();
        check(WeaponActionRuntime.attempt(f.player,true),"Action begins while target has native hurt immunity"); tick(f); tick(f); tick(f);
        check(target.getHealth()==health && stack.getDamageValue()==0 && WeaponActionRuntime.snapshot(f.player).physicalContact(),
                "Immune contact blocks without resetting invulnerableTime, success or wear");
        f.level.entities.remove(target);

        stack=equip(f,EquipmentTier.LATENT); target=target(f,3);
        var ench=f.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        stack.enchant(ench.getHolderOrThrow(Enchantments.SHARPNESS),5);
        stack.enchant(ench.getHolderOrThrow(Enchantments.FIRE_ASPECT),2);
        stack.enchant(ench.getHolderOrThrow(Enchantments.KNOCKBACK),2);
        check(WeaponActionRuntime.attempt(f.player,true),"Enchanted stab accepts"); tick(f); tick(f); tick(f);
        check(1000-target.getHealth()>damage && target.isOnFire() && target.getDeltaMovement().horizontalDistance()>0,
                "Real native Sharpness, Fire Aspect and Knockback hooks apply to custom contact");
        check(target.calls==1,"One contact never falls through to a second native hit or sword sweep");
        f.level.entities.remove(target);
    }
    private static void swipeNative(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var stack=equip(f,EquipmentTier.TRANSCENDENT);
        var first=target(f,5); first.setPos(-3,0,5);
        var second=target(f,7); second.setPos(2,0,7);
        double damage=WeaponActionDamage.snapshot(f.player).damage();
        var order=new ArrayList<ActionPlayer>();
        first.afterHurt=()->order.add(first); second.afterHurt=()->order.add(second);
        check(WeaponActionRuntime.attempt(f.player,false),"Left click accepts authoritative swipe");
        var accepted=WeaponActionRuntime.snapshot(f.player);
        check(accepted.mode()==WeaponActionRuntime.Mode.SWIPE && accepted.phase()==WeaponActionRuntime.Phase.EXTEND,"Swipe starts in explicit extension phase");
        check(!WeaponActionRuntime.attempt(f.player,true),"Alternating buttons cannot bypass accepted swipe");
        tick(f); tick(f);
        check(first.calls==0 && second.calls==0 && WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.SWEEP,
                "Extension is physical and non-damaging before the eight-tick arc");
        for(int i=0;i<8;i++) tick(f);
        equal(1000-first.getHealth(),damage,"First swipe victim receives full resolved pre-defense damage");
        equal(1000-second.getHealth(),damage,"Additional swipe victim receives the same full resolved damage");
        check(first.calls==1 && second.calls==1 && order.equals(List.of(first,second)),"Full swipe contacts once per victim in physical order");
        check(second.lastSource.getEntity()==f.player && second.getLastHurtByMob()==f.player && !second.secondary,
                "Additional swipe victim retains ordinary native melee attribution");
        check(stack.getDamageValue()==1,"Whole successful multi-victim swipe charges wear once");
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.RETRACT,"Swipe enters non-damaging retraction");
        finish(f); check(first.calls==1 && second.calls==1 && stack.getDamageValue()==1,"Retraction/recovery never redamage or rewear");
        f.level.entities.remove(first);f.level.entities.remove(second);

        equip(f,EquipmentTier.TRANSCENDENT);
        // Real native zombies, actual oak collision shapes, and native armor/health writes.
        var exposed=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,f.level); exposed.setPos(0,0,9); f.level.entities.add(exposed);
        var behindLeft=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,f.level); behindLeft.setPos(-4,0,8); f.level.entities.add(behindLeft);
        var behindRight=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,f.level); behindRight.setPos(4,0,8); f.level.entities.add(behindRight);
        for(int y=0;y<4;y++) {
            f.level.memoryBlocks.put(new BlockPos(-3,y,4),Blocks.OAK_LOG.defaultBlockState());
            f.level.memoryBlocks.put(new BlockPos(2,y,4),Blocks.OAK_LOG.defaultBlockState());
        }
        check(WeaponActionRuntime.attempt(f.player,false),"Tree/gap swipe accepts");
        for(int i=0;i<10;i++) tick(f);
        check(exposed.getHealth()<20 && behindLeft.getHealth()==20 && behindRight.getHealth()==20,
                "Exposed real zombie between oak trunks is hit; zombies behind either tree are protected");
        f.level.entities.remove(exposed);f.level.entities.remove(behindLeft);f.level.entities.remove(behindRight);

        equip(f,EquipmentTier.TRANSCENDENT); var immune=target(f,3); var behind=target(f,6);
        immune.getAbilities().invulnerable=true;
        check(WeaponActionRuntime.attempt(f.player,false),"Immune/revisit swipe accepts"); tick(f);tick(f);
        // Cancel built-in progress with deliberate free aim, holding this direction on both bodies.
        for(int i=3;i<=7;i++) {
            f.player.setYRot((float)GlaiveTuning.swipeAngle(i)); tick(f);
        }
        check(immune.calls==1 && behind.calls==0,"Immune body remains nearest physical blocker throughout swipe");
        immune.getAbilities().invulnerable=false; immune.invulnerableTime=0;
        f.player.setYRot(150); WeaponActionRuntime.observeAim(f.player);
        f.player.setYRot((float)GlaiveTuning.swipeAngle(8)); WeaponActionRuntime.observeAim(f.player); tick(f);
        check(immune.calls==1,"Camera turn and revisit cannot retry an initially immune contact");
        check(WeaponActionRuntime.snapshot(f.player).knots().stream().anyMatch(k->k.pose().yaw()>100),"Camera turning widens swipe with intermediate aim history");
        f.level.entities.remove(immune);f.level.entities.remove(behind);

        stack=equip(f,EquipmentTier.TRANSCENDENT); var contacted=target(f,3); var protectedTarget=target(f,6);
        check(WeaponActionRuntime.attempt(f.player,false),"Successful body/revisit swipe accepts");tick(f);tick(f);
        f.player.setYRot((float)GlaiveTuning.swipeAngle(3)); tick(f);
        contacted.invulnerableTime=0;
        for(int i=4;i<=8;i++) { f.player.setYRot((float)GlaiveTuning.swipeAngle(i)); tick(f); }
        check(contacted.calls==1 && protectedTarget.calls==0,"Already-contacted living body remains a blocker after hurt immunity expires");
        long ready=WeaponActionRuntime.snapshot(f.player).readyAt();
        f.player.setItemInHand(InteractionHand.MAIN_HAND,stack.copy());
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED
                && !WeaponActionRuntime.attempt(f.player,true) && WeaponActionRuntime.snapshot(f.player).readyAt()==ready,
                "Copy swap cancels swipe while retaining shared stab/swipe recovery");
        f.level.entities.remove(contacted);f.level.entities.remove(protectedTarget);

        equip(f,EquipmentTier.TRANSCENDENT);
        f.level.memoryBlocks.put(new BlockPos(0,1,4),Blocks.OAK_LOG.defaultBlockState());
        check(WeaponActionRuntime.attempt(f.player,false),"Intermediate tree-pose swipe accepts");tick(f);tick(f);
        f.player.setYRot(-60);WeaponActionRuntime.observeAim(f.player);
        f.player.setYRot(0);WeaponActionRuntime.observeAim(f.player);tick(f);
        var poses=WeaponActionRuntime.snapshot(f.player);
        check(poses.previous().tipDistance()==10 && poses.current().tipDistance()==10
                && poses.knots().stream().anyMatch(k->k.pose().tipDistance()<5),"Observed turn preserves short tree pose between two long endpoints");
        var payload=new com.mistaboom.essence_ascendance.network.WeaponActionStatePayload(f.level.dimension().location(),f.player.getId(),f.player.getUUID(),poses);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),f.level.registryAccess());
        try {
            com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.encode(buffer,payload);
            var decoded=com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.CODEC.decode(buffer);
            check(decoded.state().knots().equals(WeaponActionPresentation.wireKnots(poses.knots())),
                    "Swipe pose codec retains essential chronological collision constraints");
            for (int i=0;i<=1000;i++) check(motionBound(WeaponActionPresentation.sample(poses,i/1000.0),
                    WeaponActionPresentation.sample(decoded.state(),i/1000.0))<1e-6,
                    "Wire simplification preserves tree/gap/tree playback at fraction "+i);
        } finally {buffer.release();}

        stack=equip(f,EquipmentTier.TRANSCENDENT);var lethalFirst=target(f,5);lethalFirst.setPos(-3,0,5);lethalFirst.setHealth(1);
        var lethalSecond=target(f,7);lethalSecond.setPos(2,0,7);lethalSecond.setHealth(1);
        check(WeaponActionRuntime.attempt(f.player,false),"Native lethal multi-victim swipe accepts");
        for(int i=0;i<10;i++)tick(f);
        check(lethalFirst.deaths==1 && lethalSecond.deaths==1 && lethalFirst.deathOwner==f.player && lethalSecond.deathOwner==f.player,
                "Executed swipe preserves ordinary native kill attribution for both contacts");
        check(stack.getDamageValue()==1,"Multi-kill swipe still charges original weapon once");
        f.level.entities.remove(lethalFirst);f.level.entities.remove(lethalSecond);

        equip(f,EquipmentTier.TRANSCENDENT);
        var distant=target(f,28);
        for (int x=-8;x<=8;x++) for (int z=0;z<=30;z++)
            f.level.memoryBlocks.put(new BlockPos(x,0,z),Blocks.STONE.defaultBlockState());
        var from=new Pose(f.player.getEyePosition(),0,0,30,-7.5);
        var to=from.withSweep(7.5);
        var contacts=new ArrayList<Contact>();
        long started=System.nanoTime();
        var longResult=WeaponActionFastCollision.swipe(from,to,f.player,c->{contacts.add(c);return true;});
        long millis=(System.nanoTime()-started)/1_000_000;
        check(longResult.completed() && longResult.knots().size()<=50,
                "30-block long-range swipe bounds collision probes and presentation knots on a dense floor");
        check(contacts.stream().anyMatch(c->c.obstacle().entity()==distant),
                "Long-range swipe still discovers a distant live body");
        check(WeaponActionFastCollision.stab(from.withSweep(0).withDistance(22),
                to.withSweep(0),f.player).contact().obstacle().entity()==distant,
                "Long-range stab still discovers a distant live body");
        f.level.memoryBlocks.put(new BlockPos(-1,1,14),Blocks.OAK_LOG.defaultBlockState());
        f.level.memoryBlocks.put(new BlockPos(0,1,14),Blocks.OAK_LOG.defaultBlockState());
        contacts.clear();
        WeaponActionFastCollision.swipe(from,to,f.player,c->{contacts.add(c);return true;});
        check(contacts.stream().anyMatch(c->c.obstacle().kind()==Kind.BLOCK)
                        && contacts.stream().noneMatch(c->c.obstacle().entity()==distant),
                "Long-range swipe cannot damage a body behind loaded collision terrain");
        check(WeaponActionFastCollision.stab(from.withSweep(0).withDistance(22),
                to.withSweep(0),f.player).contact().obstacle().kind()==Kind.BLOCK,
                "Long-range stab cannot pass through loaded collision terrain");
        f.level.memoryBlocks.remove(new BlockPos(-1,1,14));
        f.level.memoryBlocks.remove(new BlockPos(0,1,14));
        f.level.unloadedMachineChunks.add(net.minecraft.world.level.ChunkPos.asLong(0,1));
        f.level.rejectMissingTerrainReads=true;
        try {
            check(WeaponActionFastCollision.stab(from.withSweep(0).withDistance(22),
                    to.withSweep(0),f.player).contact().obstacle().kind()==Kind.UNKNOWN_TERRAIN,
                    "Long-range probing treats a missing chunk as opaque without loading it");
        } finally {
            f.level.rejectMissingTerrainReads=false;
            f.level.unloadedMachineChunks.clear();
        }
        System.out.println("Glaive 30-block dense-floor swipe probe: "+longResult.knots().size()
                +" pose knots, "+millis+" ms (diagnostic, not a timing assertion)");
        f.level.entities.remove(distant);
    }
    private static void blockers(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        equip(f,EquipmentTier.TRANSCENDENT); var victim=target(f,6);
        f.player.setPos(.5,0,0); victim.setPos(.5,0,6);
        f.level.memoryBlocks.put(new BlockPos(0,1,3),Blocks.IRON_BARS.defaultBlockState());
        check(WeaponActionRuntime.attempt(f.player,true),"Thin-shape blocker action accepts"); tick(f);
        check(WeaponActionRuntime.snapshot(f.player).physicalContact() && victim.getHealth()==1000,"Actual thin collision shape blocks distant damage");
        f.level.entities.remove(victim);
        equip(f,EquipmentTier.TRANSCENDENT); victim=target(f,6);
        f.level.memoryBlocks.put(new BlockPos(0,1,3),Blocks.IRON_BARS.defaultBlockState());
        check(WeaponActionRuntime.attempt(f.player,true),"Gap beside thin collision shape accepts"); tick(f); tick(f);
        check(victim.getHealth()<1000,"Empty space inside a block's unit cube stays open around actual collision shape");
        f.level.entities.remove(victim);
        equip(f,EquipmentTier.TRANSCENDENT); var immune=target(f,3); victim=target(f,5);
        immune.getAbilities().invulnerable=true;
        check(WeaponActionRuntime.attempt(f.player,true),"Invulnerable-first action accepts"); tick(f);
        check(victim.getHealth()==1000 && immune.getHealth()==1000 && WeaponActionRuntime.snapshot(f.player).contactedEntity()==immune.getId(),
                "Invulnerable entity is first physical blocker, never transparent");
        f.level.entities.remove(immune); f.level.entities.remove(victim);
        equip(f,EquipmentTier.TRANSCENDENT); f.player.setPos(0,0,13); victim=target(f,18);
        f.level.unloadedMachineChunks.add(net.minecraft.world.level.ChunkPos.asLong(0,1));
        f.level.rejectMissingTerrainReads=true;
        check(WeaponActionRuntime.attempt(f.player,true),"Loaded-edge action accepts"); tick(f);
        check(WeaponActionRuntime.snapshot(f.player).physicalContact() && victim.getHealth()==1000,"Unknown chunk is opaque without loading terrain");
        f.level.rejectMissingTerrainReads=false;
        f.level.entities.remove(victim);
    }
    private static void cancellation(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var original=equip(f,EquipmentTier.TRANSCENDENT);
        check(WeaponActionRuntime.attempt(f.player,true),"Cancelable action accepts");
        long ready=WeaponActionRuntime.snapshot(f.player).readyAt();
        var replacement=original.copy(); f.player.setItemInHand(InteractionHand.MAIN_HAND,replacement);
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Copy replacement cancels immediately between ticks");
        f.player.setItemInHand(InteractionHand.MAIN_HAND,original);
        check(!WeaponActionRuntime.attempt(f.player,true) && WeaponActionRuntime.snapshot(f.player).readyAt()==ready,"Swap back preserves spent recovery");
        check(replacement.getDamageValue()==0,"Replacement stack never receives old action wear");
        finish(f); check(WeaponActionRuntime.attempt(f.player,true),"New press accepts after shared recovery");
        FracturedEquipmentData.markFractured(original); tick(f);
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Fracture cancels accepted action");
        original=equip(f,EquipmentTier.LATENT); check(WeaponActionRuntime.attempt(f.player,true),"Offhand cancellation action accepts");
        f.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD)); tick(f);
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Incompatible cached hand state cancels action");
        var oldStack=equip(f,EquipmentTier.LATENT); var swapped=oldStack.copy(); var swappingTarget=target(f,3);
        swappingTarget.afterHurt=()->{
            f.player.setItemInHand(InteractionHand.MAIN_HAND,swapped);
            f.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));
        };
        check(WeaponActionRuntime.attempt(f.player,true),"Native callback cancellation action accepts");
        long before=TwoHandedEquipmentPolicy.classifications();
        // The held origin reaches this target earlier than the old eye-origin trace. Stop at the callback,
        // before the NEXT ordinary tick is allowed to reconcile the mutation outside combat.
        for(int i=0;i<4 && WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.OUTWARD;i++) tick(f);
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED
                && oldStack.getDamageValue()==0 && swapped.getDamageValue()==0,"A native callback swap cancels without wearing a replacement or resurrecting retraction");
        check(TwoHandedEquipmentPolicy.classifications()==before,"Combat-time role mutation remains pending without classification inside damage/update paths");
        TwoHandedEquipmentService.reconcile(f.player);
        check(TwoHandedEquipmentPolicy.classifications()>before,"Ordinary reconciliation classifies the completed changed hands outside combat");
        f.level.entities.remove(swappingTarget);
        equip(f,EquipmentTier.LATENT); check(WeaponActionRuntime.attempt(f.player,true),"Dimension cancellation action accepts");
        ProjectileNativeInterceptionTest.set(net.minecraft.world.level.Level.class,f.level,"dimension",net.minecraft.world.level.Level.NETHER);
        tick(f); check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Dimension identity cancels action");
        ProjectileNativeInterceptionTest.set(net.minecraft.world.level.Level.class,f.level,"dimension",net.minecraft.world.level.Level.OVERWORLD);
        equip(f,EquipmentTier.LATENT); check(WeaponActionRuntime.attempt(f.player,true),"Logout cancellation action accepts");
        WeaponActionRuntime.disconnected(f.player); check(!WeaponActionRuntime.attempt(f.player,true),"Logout preserves recovery for reconnect");
        equip(f,EquipmentTier.LATENT); check(WeaponActionRuntime.attempt(f.player,true),"Death cancellation action accepts");
        f.player.setHealth(0); tick(f);
        check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.CANCELLED,"Dead attacker cannot continue accepted action");
        equip(f,EquipmentTier.LATENT);
        var oldTags=new HashMap<net.minecraft.tags.TagKey<Item>,List<Holder<Item>>>();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags().forEach(t->oldTags.put(t.getFirst(),t.getSecond().stream().toList()));
        var tags=new HashMap<>(oldTags); tags.put(TwoHandedEquipmentPolicy.PASSIVE_OFFHAND,List.of(Items.TOTEM_OF_UNDYING.builtInRegistryHolder()));
        net.minecraft.core.registries.BuiltInRegistries.ITEM.bindTags(tags); TwoHandedEquipmentPolicy.rulesChanged();
        try {
            f.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
            check(WeaponActionRuntime.attempt(f.player,true),"Compatible totem action accepts");
            long classifications=TwoHandedEquipmentPolicy.classifications();
            f.player.invulnerableTime=0;
            check(f.player.hurt(f.player.damageSources().generic(),1000) && f.player.getOffhandItem().isEmpty(),"Native lethal hit consumes totem during outward action");
            tick(f);
            check(WeaponActionRuntime.snapshot(f.player).phase()==WeaponActionRuntime.Phase.OUTWARD
                    && TwoHandedEquipmentPolicy.classifications()==classifications,"Compatible-to-empty transition preserves action without classification");
        } finally {
            net.minecraft.core.registries.BuiltInRegistries.ITEM.bindTags(oldTags); TwoHandedEquipmentPolicy.rulesChanged();
            f.player.removeAllEffects(); f.player.setAbsorptionAmount(0); f.player.invulnerableTime=0;
        }
    }
    private static void wearAndCritical(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var stack=equip(f,EquipmentTier.LATENT); var target=target(f,3);
        f.player.fallDistance=1; ProjectileNativeInterceptionTest.set(Entity.class,f.player,"onGround",false);
        var offense=WeaponActionDamage.snapshot(f.player);
        var result=WeaponActionDamage.hit(f.player,target,offense,.5,true,new Vec3(0,0,1));
        equal(result.loss(),offense.damage()*.5*1.5,"Native critical rules and configured scalar compose once");
        f.player.fallDistance=0; ProjectileNativeInterceptionTest.set(Entity.class,f.player,"onGround",true); f.level.entities.remove(target);

        stack=equip(f,EquipmentTier.LATENT); target=target(f,3);
        var ench=f.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        stack.enchant(ench.getHolderOrThrow(Enchantments.SHARPNESS),5);
        check(WeaponActionRuntime.attempt(f.player,true),"Snapshot-enchantment action accepts");
        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        tick(f); tick(f); tick(f);
        check(1000-target.getHealth()>offense.damage()*.5,"Changing live enchantments cannot erase accepted offensive snapshot");
        f.level.entities.remove(target);

        stack=equip(f,EquipmentTier.LATENT);
        for(int i=0;i<100;i++) WeaponActionDamage.wear(f.player,stack,f.player.getInventory().selected);
        int ordinary=stack.getDamageValue();
        var efficiencyStat=com.mistaboom.essence_ascendance.stat.EssenceStats.DURABILITY_EFFICIENCY;
        var playerData=f.saved.getPlayerData(f.player.getUUID());
        playerData.setInvested(efficiencyStat,com.mistaboom.essence_ascendance.config.EssenceConfigManager.get().balanceProfile()
                .getInvestmentCap(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT,efficiencyStat));
        var efficient=new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get()); EquipmentTierData.setTier(efficient,EquipmentTier.DORMANT);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,efficient);
        for(int i=0;i<100;i++) WeaponActionDamage.wear(f.player,efficient,f.player.getInventory().selected);
        check(efficient.getDamageValue()<ordinary,"Glaive wear uses existing channeled durability-efficiency handling");
        playerData.setInvested(efficiencyStat,0);
        var enchanted=new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
        enchanted.enchant(ench.getHolderOrThrow(Enchantments.UNBREAKING),3);
        f.player.setItemInHand(InteractionHand.MAIN_HAND,enchanted);
        for(int i=0;i<100;i++) WeaponActionDamage.wear(f.player,enchanted,f.player.getInventory().selected);
        check(enchanted.getDamageValue()>0 && enchanted.getDamageValue()<ordinary,"Shared native wear honors Unbreaking");
        enchanted.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        enchanted.setDamageValue(enchanted.getMaxDamage()-1);
        WeaponActionDamage.wear(f.player,enchanted,f.player.getInventory().selected);
        check(enchanted.getCount()==1 && FracturedEquipmentData.isFractured(enchanted),"Native successful-action wear retains artifact through fracture");

        stack=equip(f,EquipmentTier.DORMANT); target=target(f,3);
        var data=f.saved.getPlayerData(f.player.getUUID());
        var lootingStat=com.mistaboom.essence_ascendance.stat.EssenceStats.LOOTING;
        data.setInvested(lootingStat,com.mistaboom.essence_ascendance.config.EssenceConfigManager.get().balanceProfile()
                .getInvestmentCap(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT,lootingStat));
        stack.enchant(ench.getHolderOrThrow(Enchantments.LOOTING),2);
        offense=WeaponActionDamage.snapshot(f.player);
        check(offense.looting()>2,"Accepted weapon captures native plus channeled Looting");
        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        data.setInvested(com.mistaboom.essence_ascendance.stat.EssenceStats.LOOTING,0);
        WeaponActionDamage.hit(f.player,target,offense,1,true,new Vec3(0,0,1));
        check(target.observedLooting==offense.looting(),"Native loot-query boundary reads accepted Looting after live offense changes");
        f.level.entities.remove(target);
    }
    private static void offenseSnapshot(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var stack=equip(f,EquipmentTier.LATENT); var first=target(f,3); var second=target(f,4);
        var data=f.saved.getPlayerData(f.player.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.FRENZY)));
        data.setLoadoutSelection(com.mistaboom.essence_ascendance.skill.SkillGroups.OFFENSE_COMBAT_STANCE,SkillIds.FRENZY);
        SkillEffectRuntime.refresh(f.player);
        check(SkillEffectRuntime.context(f.player).isEffective(SkillIds.FRENZY),"Fixture commits Frenzy through real loadout");
        var offense=WeaponActionDamage.snapshot(f.player);
        var a=WeaponActionDamage.hit(f.player,first,offense,1,true,new Vec3(0,0,1));
        var earned=WeaponActionDamage.snapshot(f.player);
        var b=WeaponActionDamage.hit(f.player,second,offense,1,false,new Vec3(0,0,1));
        check(a.accepted() && b.accepted() && a.loss()>0 && b.loss()>0,"Both direct native contacts succeed");
        equal(a.loss(),b.loss(),"Frenzy earned from first victim cannot amplify later victim");
        check(second.lastSource.getEntity()==f.player && second.getLastHurtByMob()==f.player && !second.secondary,"Additional victims retain ordinary direct melee attribution");
        var after=WeaponActionDamage.snapshot(f.player);
        check(after.skills().apply(second,second.lastSource,10,true)>offense.skills().apply(second,second.lastSource,10,true),"New action can receive newly earned Frenzy");
        equal(after.skills().apply(second,second.lastSource,10,true),earned.skills().apply(second,second.lastSource,10,true),"Additional direct contact cannot earn another primary-only Frenzy proc");
        check(!EquipmentDamageService.isPrimarySkillMelee(f.player,second,second.lastSource),"Synchronous native skill scope is closed after contacts");
        f.level.entities.remove(first); f.level.entities.remove(second);
        equip(f,EquipmentTier.LATENT);
        var swipeFirst=target(f,2.6);swipeFirst.setPos(-1.5,0,2.6);
        var swipeSecond=target(f,2.6);swipeSecond.setPos(1.5,0,2.6);
        var swipeImmune=target(f,1.5);swipeImmune.setPos(-2.6,0,1.5);swipeImmune.getAbilities().invulnerable=true;
        WeaponActionDamage.Offense[] beforeSecond={null};
        var beforeSwipe=WeaponActionDamage.snapshot(f.player);
        swipeSecond.afterHurt=()->beforeSecond[0]=WeaponActionDamage.snapshot(f.player);
        check(WeaponActionRuntime.attempt(f.player,false),"Real multi-victim swipe captures accepted Frenzy offense");
        for(int i=0;i<10;i++) tick(f);
        equal(1000-swipeFirst.getHealth(),1000-swipeSecond.getHealth(),"Frenzy earned during swipe cannot amplify later direct victims");
        check(swipeFirst.calls==1 && swipeSecond.calls==1 && beforeSecond[0]!=null,"Both actual swipe victims receive one native hit");
        var afterSwipe=WeaponActionDamage.snapshot(f.player);
        check(swipeImmune.calls==1 && afterSwipe.skills().apply(swipeSecond,swipeSecond.lastSource,10,true)
                >beforeSwipe.skills().apply(swipeSecond,swipeSecond.lastSource,10,true),"Failed first physical attempt preserves primary proc for the first successful swipe contact");
        equal(afterSwipe.skills().apply(swipeSecond,swipeSecond.lastSource,10,true),beforeSecond[0].skills().apply(swipeSecond,swipeSecond.lastSource,10,true),
                "Actual additional swipe contact cannot earn another primary-only Frenzy proc");
        f.level.entities.remove(swipeFirst);f.level.entities.remove(swipeSecond);f.level.entities.remove(swipeImmune);
        equip(f,EquipmentTier.LATENT);
        check(WeaponActionRuntime.attempt(f.player,true),"Whiff test accepts an extending action");
        tick(f);
        check(WeaponActionDamage.snapshot(f.player).skills().apply(second,second.lastSource,10,true)>10,"No premature miss before extension can reach a victim");
        tick(f); tick(f); tick(f);
        equal(WeaponActionDamage.snapshot(f.player).skills().apply(second,second.lastSource,10,true),10,"Whiff bookkeeping occurs when damaging outward phase ends");
        var source=f.player.damageSources().playerAttack(f.player);
        try {
            EquipmentDamageService.runActionMeleeContact(f.player,second,source,after.skills(),true,
                    ()->{ throw new IllegalStateException("fixture contact failure"); });
            throw new AssertionError("Expected contact failure");
        } catch (IllegalStateException expected) {
            check(!EquipmentDamageService.isPrimarySkillMelee(f.player,second,source),"Exception closes native skill and damage scopes");
        }
        first=target(f,3); second=target(f,4); first.setHealth(1); second.setHealth(1);
        offense=WeaponActionDamage.snapshot(f.player);
        WeaponActionDamage.hit(f.player,first,offense,1,true,new Vec3(0,0,1));
        WeaponActionDamage.hit(f.player,second,offense,1,false,new Vec3(0,0,1));
        check(first.deaths==1 && second.deaths==1 && first.deathOwner==f.player && second.deathOwner==f.player,
                "Native lethal health writes keep ordinary kill attribution on primary and additional direct contacts");
        f.level.entities.remove(first); f.level.entities.remove(second);
    }
    private static void installEnchantments(ProjectileNativeInterceptionTest.Fixture f) {
        var registries=new ArrayList<Registry<?>>();
        f.level.memoryRegistries.registries().filter(e->!e.key().equals(Registries.ENCHANTMENT)).forEach(e->registries.add(e.value()));
        var enchantments=new MappedRegistry<Enchantment>(Registries.ENCHANTMENT,com.mojang.serialization.Lifecycle.stable());
        net.minecraft.data.registries.VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(h->enchantments.register(h.key(),h.value(),RegistrationInfo.BUILT_IN));
        enchantments.freeze(); registries.add(enchantments);
        f.level.memoryRegistries=new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
    }
    private static void chargedSnapshot(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        equip(f,EquipmentTier.LATENT);
        var data=f.saved.getPlayerData(f.player.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.STATIC_CHARGE)));
        data.setLoadoutSelection(com.mistaboom.essence_ascendance.skill.SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT,SkillIds.STATIC_CHARGE);
        var context=SkillEffectRuntime.context(f.player);
        check(context.isEffective(SkillIds.STATIC_CHARGE),"Charge fixture uses real committed handler");
        var type=Class.forName("com.mistaboom.essence_ascendance.skill.effect.OffenseElementalImbuementEffects$StaticChargeState");
        var constructor=type.getDeclaredConstructor(); constructor.setAccessible(true);
        var chargeState=(SkillEffectState)constructor.newInstance();
        context.state(SkillIds.STATIC_CHARGE,()->chargeState);
        var charge=type.getDeclaredField("charge"); charge.setAccessible(true);
        double maximum=context.settings().staticCharge().maximumCharge();
        var empty=WeaponActionDamage.snapshot(f.player);
        charge.setDouble(chargeState,maximum);
        var first=target(f,3); var second=target(f,4); var third=target(f,5);
        WeaponActionDamage.hit(f.player,first,empty,1,true,new Vec3(0,0,1));
        equal(charge.getDouble(chargeState),maximum,"Charge earned after acceptance cannot amplify or discharge the accepted action");
        var full=WeaponActionDamage.snapshot(f.player);
        var modifier=full.skills().modifiers().get(SkillIds.STATIC_CHARGE);
        check(modifier.at(second,f.player.damageSources().playerAttack(f.player),true).flat()>0,"Full accepted charge captures its offensive bonus");
        double expected=full.skills().apply(second,f.player.damageSources().playerAttack(f.player),(float)full.damage(),true)*.5;
        var charged=WeaponActionDamage.hit(f.player,second,full,.5,true,new Vec3(0,0,1));
        equal(charged.loss(),expected,"Stab scalar scales the complete resolved direct hit, including captured flat skill damage");
        equal(charge.getDouble(chargeState),0,"First successful accepted charge contact consumes native skill state");
        charge.setDouble(chargeState,maximum);
        WeaponActionDamage.hit(f.player,third,full,1,false,new Vec3(0,0,1));
        equal(charge.getDouble(chargeState),maximum,"Additional direct contacts cannot spend newly earned charge");
        equal(modifier.at(third,f.player.damageSources().playerAttack(f.player),true).flat(),0,"Spent charge generation cannot replay on a later contact");
        f.level.entities.remove(first); f.level.entities.remove(second); f.level.entities.remove(third);
    }
    static final class ActionPlayer extends ServerPlayer {
        int calls,observedLooting,deaths; DamageSource lastSource; boolean secondary; ServerPlayer deathOwner; Runnable afterHurt;
        private ActionPlayer() { super(null,null,null,null); throw new AssertionError("Fixture only"); }
        @Override public boolean hurt(DamageSource source,float amount) {
            calls++; lastSource=source; secondary=EquipmentDamageService.isSecondarySkillDamage();
            if(source.getEntity() instanceof ServerPlayer player && WeaponActionDamage.looting(player)!=null) {
                var looting=level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING);
                observedLooting=EnchantmentHelper.getEnchantmentLevel(looting,player);
            }
            boolean accepted=super.hurt(source,amount);
            if(afterHurt!=null) afterHurt.run();
            return accepted;
        }
        @Override public void awardStat(Stat<?> stat,int amount) { }
        @Override public void onEnterCombat() { }
        @Override public void onLeaveCombat() { }
        @Override public void indicateDamage(double x,double z) { }
        @Override public void crit(Entity target) { }
        @Override public void magicCrit(Entity target) { }
        // Controlled death/world boundary; native hurt and lethal health writes above remain inherited.
        @Override public void die(DamageSource source) {
            deaths++; deathOwner=EquipmentDamageService.skillDamagePlayer(this,source);
            SkillEffectRuntime.onLivingDeath(this,source);
        }
    }
    private static void equal(double actual,double expected,String message) { check(Math.abs(actual-expected)<1e-4,message+": "+actual+" != "+expected); }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
}
