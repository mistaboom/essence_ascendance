package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.RecentHostileCombat;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.VitalitySustenanceEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Virtual automatic food completion, manual-use preservation, and transformed owner-local sustain hooks. */
public final class NativeVitalitySustenanceTest {
    private static int checks;
    public static void run() throws ReflectiveOperationException {
        var f = new ProjectileNativeInterceptionTest.Fixture(FoodPlayer.class);
        NativeGuardOutcomeTest.registry(f);
        FoodPlayer player = (FoodPlayer) f.player;
        player.drops = new ArrayList<>();
        var listener = ProjectileNativeInterceptionTest.instance(SilentListener.class);
        listener.player = player; player.connection = listener;
        var inventoryMenu = new InventoryMenu(player.getInventory(), false, player);
        ProjectileNativeInterceptionTest.set(Player.class, player, "inventoryMenu", inventoryMenu);
        player.containerMenu = inventoryMenu;
        ProjectileNativeInterceptionTest.set(ServerLevel.class, f.level, "players", new ArrayList<>(List.of(player)));
        ProjectileNativeInterceptionTest.set(ServerLevel.class, f.level, "sleepStatus", new SleepStatus());
        var data = f.saved.getPlayerData(player.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.FEAST_REFLEX), SkillRegistry.require(SkillIds.INNER_SUSTENANCE)));
        data.setLoadoutSelection(SkillGroups.VITALITY_SUSTENANCE, SkillIds.FEAST_REFLEX);
        SkillEffectRuntime.refresh(player);
        var tuning = SkillEffectRuntime.resolvedSettings(player).vitality();
        check(CommittedSkillService.isEffective(player, SkillIds.FEAST_REFLEX), "real committed Feast selection");
        var bread = new ItemStack(Items.BREAD, 4);
        // Use a high-nutrition native component so one meal can cross the
        // vanilla food threshold during the hurt-player path below.
        bread.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(8).saturationModifier(.6f).build());
        check(bread.getUseDuration(player) == Math.max(1,(int)Math.ceil(32*tuning.feastReflex().useDurationMultiplier())),
                "transformed native food duration shortened");
        var potion = new ItemStack(Items.POTION);
        check(potion.getUseDuration(player) < 32, "component-free native drinks shortened");
        check(new ItemStack(Items.BOW).getUseDuration(player) == 72000, "weapon use remains ordinary");

        player.getInventory().setItem(0,new ItemStack(Items.STICK));
        player.getInventory().setItem(2,bread);
        player.getInventory().setItem(9,new ItemStack(Items.COOKED_BEEF,64));
        check(VitalitySustenanceEffects.chooseFood(player.getInventory(), stack -> true) == 2,
                "selection ignores food outside hotbar");
        var poisoned = new ItemStack(Items.COOKED_BEEF);
        poisoned.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(20)
                .effect(new MobEffectInstance(MobEffects.POISON,100),1).build());
        player.getInventory().setItem(1,poisoned);
        check(VitalitySustenanceEffects.chooseFood(player.getInventory(), stack -> true) == 2,
                "harmful component effects disqualify high-nutrition meal");
        var stew = new ItemStack(Items.SUSPICIOUS_STEW);
        stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS,new net.minecraft.world.item.component.SuspiciousStewEffects(List.of(
                new net.minecraft.world.item.component.SuspiciousStewEffects.Entry(MobEffects.POISON,100))));
        check(!VitalitySustenanceEffects.suitable(stew),"stew-specific harmful effects are also unsuitable");
        var better = new ItemStack(Items.COOKED_BEEF,2);
        player.getInventory().setItem(3,better);
        player.getInventory().setItem(4,better.copy());
        check(VitalitySustenanceEffects.chooseFood(player.getInventory(), stack -> true) == 3,
                "nutrition priority and stable lowest-slot tie");
        player.getInventory().setItem(3,ItemStack.EMPTY); player.getInventory().setItem(4,ItemStack.EMPTY);
        hungry(player);
        player.getAbilities().invulnerable = true;
        check(!player.hurt(player.damageSources().generic(),1) && !player.isUsingItem(),
                "native invulnerability rejects damage without starting a meal");
        player.getAbilities().invulnerable = false; player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(),0);
        check(!player.isUsingItem(),"native zero outcome does not start a meal");
        player.invulnerableTime = 0;
        check(player.hurt(player.damageSources().generic(),1),"actual native positive hurt accepted");
        check(!player.isUsingItem() && player.getInventory().selected == 0 && bread.getCount() == 3
                        && player.getFoodData().getFoodLevel() == 18,
                "accepted loss completes a virtual meal without changing the active hand");
        check(listener.carriedItemPackets == 0,
                "virtual automatic use does not move the client's carried-item cursor");
        SkillEffectRuntime.onAcceptedDamage(player, player.damageSources().generic(),1,0);
        check(bread.getCount() == 3 && player.getFoodData().getFoodLevel() == 18,
                "duplicate accepted event does not duplicate a virtual meal");
        check(player.statsAwarded > 0,
                "ordinary virtual consumption still awards the native item statistic (stats="
                        +player.statsAwarded+")");

        // At full health Feast also runs without a damage callback, but it
        // must refuse a meal that would waste half its nutrition exactly.
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(16);
        player.getFoodData().setSaturation(0);
        f.level.tick++;
        SkillEffectRuntime.tick(player);
        check(!player.isUsingItem(), "full-health Feast refuses a meal wasting half its nutrition");
        player.getFoodData().setFoodLevel(15);
        f.level.tick++;
        SkillEffectRuntime.tick(player);
        check(!player.isUsingItem() && player.getInventory().selected == 0 && bread.getCount() == 2
                        && player.getFoodData().getFoodLevel() == 20,
                "full-health Feast completes a useful virtual meal without changing the active hand");

        // A visible food deficit remains actionable while a small saturation
        // reserve is present; Feast must not wait for saturation to reach zero.
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(4);
        f.level.tick++;
        SkillEffectRuntime.onAcceptedDamage(player, player.damageSources().generic(), 1, 0);
        check(!player.isUsingItem() && player.getInventory().selected == 0 && bread.getCount() == 1
                        && player.getFoodData().getFoodLevel() == 18,
                "virtual meals use a visible food deficit even with saturation remaining");

        data.setLoadoutSelection(SkillGroups.VITALITY_SUSTENANCE,SkillIds.FEAST_REFLEX); SkillEffectRuntime.refresh(player);
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.BOW)); player.startUsingItem(InteractionHand.OFF_HAND);
        player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(15);
        int beforeManual = bread.getCount(); f.level.tick++;
        SkillEffectRuntime.onAcceptedDamage(player,player.damageSources().generic(),1,0);
        check(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
                        && player.getInventory().selected == 0 && bread.getCount() == beforeManual,
                "automatic meal never interrupts another active use");
        player.stopUsingItem();
        player.getCooldowns().addCooldown(Items.BREAD,20); f.level.tick++;
        SkillEffectRuntime.onAcceptedDamage(player,player.damageSources().generic(),1,0);
        check(player.getFoodData().getFoodLevel() == 15 && bread.getCount() == beforeManual,
                "cooldown food cannot auto-consume");
        player.getCooldowns().removeCooldown(Items.BREAD);
        f.level.tick++; SkillEffectRuntime.onAcceptedDamage(player,player.damageSources().generic(),1,0);
        check(!player.isUsingItem() && bread.getCount() == beforeManual - 1,
                "virtual meal consumes immediately after cooldown clears");

        // Use a stackable native FOOD component conversion to cover full-inventory container conservation.
        for (int slot=0;slot<36;slot++) player.getInventory().setItem(slot,new ItemStack(Items.STICK,64));
        var meal = new ItemStack(Items.BREAD,2);
        meal.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(4).saturationModifier(.5f)
                .usingConvertsTo(Items.BOWL).effect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100),1).build());
        player.getInventory().setItem(2,meal); player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(15); player.getFoodData().setSaturation(0); f.level.tick++;
        SkillEffectRuntime.onAcceptedDamage(player,player.damageSources().generic(),1,0);
        check(meal.getCount() == 1 && player.getFoodData().getFoodLevel() == 19 && player.hasEffect(MobEffects.MOVEMENT_SPEED),
                "real stacked component food preserves nutrition and native food effects");
        check(player.drops.size() == 1 && player.drops.getFirst().is(Items.BOWL),
                "full inventory retains one returned container via drop fallback; drops="+player.drops);
        player.getFoodData().setFoodLevel(15); player.getFoodData().setSaturation(0); f.level.tick++;
        SkillEffectRuntime.onAcceptedDamage(player,player.damageSources().generic(),1,0);
        check(player.getInventory().getItem(2).is(Items.BOWL) && player.drops.size() == 1,
                "last native meal replaces its hotbar slot with container exactly once");

        data.setLoadoutSelection(SkillGroups.VITALITY_SUSTENANCE,SkillIds.INNER_SUSTENANCE); SkillEffectRuntime.refresh(player);
        hungry(player);
        var sustain = tuning.innerSustenance();
        for (int tick=0;tick<sustain.hungerRecoveryIntervalTicks();tick++) { f.level.tick++; SkillEffectRuntime.tick(player); }
        check(player.getFoodData().getFoodLevel() == 10+sustain.hungerPerRecovery()
                && player.getFoodData().getSaturationLevel() == (float)sustain.saturationPerRecovery(),
                "quiet native hunger and saturation recovery uses generated amounts");
        var other = f.player(FoodPlayer.class,new Vec3(0,0,3),"sustenance_other");
        RecentHostileCombat.acceptedDamage(player,other);
        int before = player.getFoodData().getFoodLevel();
        for(int tick=0;tick<sustain.combatTimeoutTicks()-1;tick++) { f.level.tick++; SkillEffectRuntime.tick(player); }
        check(player.getFoodData().getFoodLevel()==before, "recent accepted hostile combat suppresses recovery");
        check(RecentHostileCombat.remaining(SkillEffectRuntime.context(other),sustain.combatTimeoutTicks())==0,
                "combat state is owned per player");
        SkillEffectRuntime.reset(player);
        check(RecentHostileCombat.remaining(SkillEffectRuntime.context(player),sustain.combatTimeoutTicks())==0,
                "lifecycle reset removes combat and partial recovery state");
        player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20);
        check(VitalitySustenanceEffects.fullySustained(player), "native full food and saturation activates protection");
        check(!VitalitySustenanceEffects.fullySustained(other), "another player's food/selection cannot borrow protection");
        player.getFoodData().setExhaustion(0);
        ProjectileNativeInterceptionTest.set(Entity.class,player,"onGround",true);
        ProjectileNativeInterceptionTest.set(Entity.class,player,"fluidOnEyes",java.util.Set.of()); player.setSprinting(true);
        player.checkMovementStatistics(10,0,0);
        check(player.getFoodData().getExhaustionLevel()==0, "transformed actual sprint exertion is suppressed at full saturation");
        player.causeFoodExhaustion(1);
        check(player.getFoodData().getExhaustionLevel()>0, "explicit native exhaustion costs remain intact");
        player.getFoodData().setSaturation(19); player.getFoodData().setExhaustion(0);
        player.checkMovementStatistics(10,0,0);
        check(player.getFoodData().getExhaustionLevel()>0,"ordinary passive exhaustion resumes below full saturation");
        player.getFoodData().setSaturation(20);
        var sleep = new SleepStatus(); other.sleeping=true;
        sleep.update(List.of(player,other));
        check(sleep.areEnoughSleeping(100) && sleep.areEnoughDeepSleeping(100,List.of(player,other)),
                "full sustained awake owner need not sleep for another player to skip night");
        player.sleeping=true; sleep.update(List.of(player));
        check(sleep.areEnoughSleeping(100), "owner may still voluntarily sleep"); player.sleeping=false;
        sleep.update(List.of(player)); check(!sleep.areEnoughSleeping(100), "awake owners do not automatically skip night");
        var phantom = ProjectileNativeInterceptionTest.instance(Phantom.class);
        check(!TargetingConditions.DEFAULT.test(phantom,player), "transformed native phantom targeting rejects owning sustained player");
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class,phantom,"target",player);
        var awareness=Arrays.stream(Phantom.class.getDeclaredMethods()).filter(m->m.getName().contains("releaseSustainedTarget")).findFirst().orElseThrow();
        awareness.setAccessible(true);
        awareness.invoke(phantom,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("aiStep",false));
        check(phantom.getTarget()==null,"transformed pre-AI hook releases an already-swooping protected target");
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class,phantom,"target",other);
        awareness.invoke(phantom,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("aiStep",false));
        check(phantom.getTarget()==other,"pre-AI protection does not clear another player's target");
        var spawner = new PhantomSpawner();
        var spawnHook = Arrays.stream(PhantomSpawner.class.getDeclaredMethods()).filter(m->m.getName().contains("ignoreRestedOwner")).findFirst().orElseThrow();
        spawnHook.setAccessible(true);
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> ordinary=arguments->false;
        check((boolean)spawnHook.invoke(spawner,player,ordinary) && !(boolean)spawnHook.invoke(spawner,other,ordinary),
                "transformed phantom spawner exclusion applies only to the owner");
        data.setLoadoutSelection(SkillGroups.VITALITY_SUSTENANCE,SkillIds.FEAST_REFLEX); SkillEffectRuntime.refresh(player);
        check(!VitalitySustenanceEffects.fullySustained(player), "branch change immediately removes hunger/sleep/phantom protection");
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class,phantom,"target",player);
        awareness.invoke(phantom,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("aiStep",false));
        check(phantom.getTarget()==player,"deselected owners may again remain native phantom targets");
        player.getFoodData().setExhaustion(0); player.checkMovementStatistics(10,0,0);
        check(player.getFoodData().getExhaustionLevel()>0,"deselection immediately restores native movement costs");
        sleep.update(List.of(player,other)); check(!sleep.areEnoughSleeping(100), "ordinary multiplayer sleep quorum restored");
        // A native sleep-list query can happen before the first gameplay tick after selecting Inner.
        other.sleeping=false;
        ProjectileNativeInterceptionTest.set(ServerLevel.class,f.level,"players",new ArrayList<>(List.of(player,other)));
        var nativeSleep=new SleepStatus();
        ProjectileNativeInterceptionTest.set(ServerLevel.class,f.level,"sleepStatus",nativeSleep);
        data.setLoadoutSelection(SkillGroups.VITALITY_SUSTENANCE,SkillIds.INNER_SUSTENANCE); SkillEffectRuntime.refresh(player);
        nativeSleep.update(List.of(player,other)); check(nativeSleep.sleepersNeeded(100)==1,"pre-tick selection excludes full owner");
        data.setLoadoutSelection(SkillGroups.VITALITY_SUSTENANCE,SkillIds.FEAST_REFLEX); SkillEffectRuntime.refresh(player);
        check(nativeSleep.sleepersNeeded(100)==2,"pre-tick deselection recomputes the native sleep denominator immediately");
        f.close(); SkillEffectRuntime.forget(other); CommittedSkillService.forget(other);
        System.out.println("Native Vitality sustenance passed: "+checks+" (virtual meals, manual-use preservation, inventory, containers, effects, sleep/phantoms; no world)");
    }
    private static void hungry(FoodPlayer player) { player.getFoodData().setFoodLevel(10); player.getFoodData().setSaturation(0); }
    static final class SilentListener extends ServerGamePacketListenerImpl {
        int packets;
        int carriedItemPackets;
        private SilentListener() { super(null,null,null,null); throw new AssertionError("No native listener construction"); }
        @Override public void send(Packet<?> packet) {
            packets++;
            if (packet instanceof ClientboundSetCarriedItemPacket) carriedItemPackets++;
        }
    }
    static final class FoodPlayer extends ServerPlayer {
        int statsAwarded; boolean sleeping; List<ItemStack> drops;
        private FoodPlayer() { super(null,null,null,null); throw new AssertionError("No native player construction"); }
        @Override public void awardStat(Stat<?> stat,int amount) { statsAwarded+=amount; }
        @Override public void onEnterCombat() { }
        @Override public void onLeaveCombat() { }
        @Override public void indicateDamage(double x,double z) { }
        @Override public boolean isSleeping() { return sleeping; }
        @Override public boolean isSleepingLongEnough() { return sleeping; }
        @Override protected void onEffectAdded(MobEffectInstance effect,Entity source) { effect.getEffect().value().addAttributeModifiers(getAttributes(),effect.getAmplifier()); }
        @Override protected void onEffectUpdated(MobEffectInstance effect,boolean refresh,Entity source) { }
        @Override protected void onEffectRemoved(MobEffectInstance effect) { effect.getEffect().value().removeAttributeModifiers(getAttributes()); }
        @Override public ItemEntity drop(ItemStack stack,boolean random,boolean retainOwnership) { drops.add(stack.copy()); return null; }
    }
    private static void check(boolean okay,String message) { checks++; if(!okay) throw new AssertionError(message); }
}
