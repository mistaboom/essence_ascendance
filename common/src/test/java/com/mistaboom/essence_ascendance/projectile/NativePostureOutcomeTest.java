package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.network.SkillEffectHudPayload;
import com.mistaboom.essence_ascendance.posture.PostureService;
import com.mistaboom.essence_ascendance.posture.ThreatFacingResolver;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPresentation;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Transformed actual ServerPlayer/LivingEntity.hurt and knockback, controlled memory-only level boundaries. */
public final class NativePostureOutcomeTest {
    private static int checks;
    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit no-world fixture required");
        var f=new ProjectileNativeInterceptionTest.Fixture(NativeGuardOutcomeTest.NativePlayer.class);
        NativeGuardOutcomeTest.registry(f);
        var p=f.player; var attacker=f.player(NativeGuardOutcomeTest.MeasuredTarget.class,new Vec3(0,0,2),"posture_attacker"); attacker.scale=1;
        var data=f.saved.getPlayerData(p.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.EVASIVE_CURRENT),SkillRegistry.require(SkillIds.BULWARK_STANCE),SkillRegistry.require(SkillIds.ADAPTIVE_GUARD)));
        data.setLoadoutSelection(SkillGroups.DEFENSE_POSTURE,SkillIds.ADAPTIVE_GUARD); SkillEffectRuntime.refresh(p);
        var source=p.damageSources().playerAttack(attacker);
        check(!PostureService.eligible(p,p.damageSources().playerAttack(p),4,false),"Self damage rejected by shared relationship policy");
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class,f.server,"pvp",false);
        check(!PostureService.eligible(p,source,4,false),"Native disabled PvP cannot grant posture outcomes");
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class,f.server,"pvp",true);
        var arrow=f.arrow(net.minecraft.world.entity.projectile.Arrow.class);
        ProjectileOwnership.transferNative(arrow,attacker);
        check(PostureService.eligible(p,p.damageSources().arrow(arrow,attacker),4,false),"Projectile resolves its actual living shooter");
        ProjectileOwnership.transferNative(arrow,p);
        check(!PostureService.eligible(p,p.damageSources().arrow(arrow,attacker),4,false),"Mismatched native shooter ownership fails closed");
        ProjectileNativeInterceptionTest.set(Entity.class,p,"onGround",true); p.setYRot(0);p.setXRot(0);
        check(SkillEffectRuntime.context(p).isEffective(SkillIds.ADAPTIVE_GUARD),"Real saved choice selects only Adaptive Guard");
        var presentation=new SkillEffectHudPresentation();
        var initialHud=transmittedHud(f);
        check(initialHud.entries().stream().anyMatch(card->card.id().equals(SkillIds.ADAPTIVE_GUARD)&&!card.active()),
                "Selected Adaptive Guard is present but inactive before any qualifying hit");
        presentation.replace(initialHud.entries(),initialHud.serverGameTime());
        check(presentation.visibleEntries(initialHud.serverGameTime()).isEmpty(),"Unseeded adaptation has no fabricated visible stacks");
        p.invulnerableTime=0;check(p.hurt(source,4),"First native adaptation hit accepted");
        close(PostureService.snapshot(p).incoming().resistance(),0,"First native exact damage ID unmitigated");
        check(PostureService.snapshot(p).stacks()==1,"Native measured positive health hit seeds first stack");
        long firstHitLifecycle=PostureService.snapshot(p).lifecycle();
        var firstHitHud=transmittedHud(f);
        var firstHitCard=firstHitHud.entries().stream().filter(card->card.id().equals(SkillIds.ADAPTIVE_GUARD)).findFirst().orElseThrow();
        check(firstHitCard.active()&&firstHitCard.badge().arguments().getFirst().equals("1")
                        &&firstHitCard.meter().kind()==SkillEffectHudEntry.MeterKind.TIMER
                        &&firstHitCard.meter().expiresAt()==PostureService.snapshot(p).expiresAt(),
                "Actual native first-hit outcome reaches shared HUD codec with one stack and authoritative expiry");
        presentation.replace(firstHitHud.entries(),firstHitHud.serverGameTime());
        check(presentation.visibleEntries(firstHitHud.serverGameTime()).stream().anyMatch(card->card.id().equals(SkillIds.ADAPTIVE_GUARD)),
                "Shared client presentation visibly opens Adaptive Guard on the first accepted native hit");
        check(PostureService.snapshot(p).lifecycle()==firstHitLifecycle&&PostureService.snapshot(p).stacks()==1,
                "Runtime HUD snapshot and codec do not reconcile away the active adaptation lifecycle");
        p.invulnerableTime=0;p.setHealth(20);check(p.hurt(source,4),"Second native adaptation hit accepted");
        var s=PostureService.snapshot(p);check(s.stacks()==2,"Second native hit commits exactly one stack");
        close(s.incoming().resistance(),SkillEffectRuntime.resolvedSettings(p).posture().adaptive().resistancePerStack(),"Same native type receives typed generated reduction");
        close(s.incoming().confirmedPrevention(),p.getHealth()-16,"Exact incoming prevention measured before armor");
        int stacks=s.stacks();p.hurt(source,0);check(PostureService.snapshot(p).stacks()==stacks,"Zero native probe never creates stacks");
        p.hurt(source,4);check(PostureService.snapshot(p).stacks()==stacks,"Cooldown-rejected native hit never creates stacks");
        p.invulnerableTime=0;p.setHealth(20);
        EquipmentDamageService.withSecondarySkillDamage(()->p.hurt(source,4));
        check(PostureService.snapshot(p).stacks()==stacks,"Secondary native damage cannot seed or stack adaptation");
        p.invulnerableTime=0;p.setHealth(20);p.hurt(p.damageSources().generic(),4);
        check(PostureService.snapshot(p).stacks()==1&&PostureService.snapshot(p).damageType().equals("minecraft:generic"),"Environment uses exact registry identity and reseeds");
        var modRegistry=new net.minecraft.core.MappedRegistry<net.minecraft.world.damagesource.DamageType>(net.minecraft.core.registries.Registries.DAMAGE_TYPE,com.mojang.serialization.Lifecycle.stable());
        var modKey=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,net.minecraft.resources.ResourceLocation.parse("fixture:unfamiliar_arcane"));
        var modHolder=modRegistry.register(modKey,new net.minecraft.world.damagesource.DamageType("untranslated_custom",.1F),net.minecraft.core.RegistrationInfo.BUILT_IN);
        modRegistry.freeze();
        var modSource=new net.minecraft.world.damagesource.DamageSource(modHolder,attacker);
        p.invulnerableTime=0;p.setHealth(20);p.hurt(modSource,4);
        check(PostureService.snapshot(p).damageType().equals("fixture:unfamiliar_arcane")&&PostureService.snapshot(p).stacks()==1,
                "Actual native unfamiliar modded DamageType keys registry ID, not translated/message name");
        p.invulnerableTime=0;p.setHealth(20);p.hurt(modSource,4);
        check(PostureService.snapshot(p).stacks()==2&&PostureService.snapshot(p).incoming().resistance()>0,"Repeated native modded type receives adaptation");
        var stale=PostureService.snapshot(p).incoming();long lifecycle=PostureService.snapshot(p).lifecycle();
        PostureService.forget(p);SkillEffectRuntime.refresh(p);PostureService.finish(p,stale,true,false,4,1);
        check(PostureService.snapshot(p).lifecycle()!=lifecycle&&PostureService.snapshot(p).stacks()==0,
                "A completed pre-reset damage event cannot resurrect adaptation in a new posture lifecycle");
        data.setLoadoutSelection(SkillGroups.DEFENSE_POSTURE,SkillIds.EVASIVE_CURRENT);SkillEffectRuntime.refresh(p);
        check(PostureService.snapshot(p).meter()==0&&PostureService.snapshot(p).stacks()==0,"Switching owned exclusive choice clears old adaptation");
        chargeEvasive(f);
        attacker.setPos(p.getX(),p.getY(),p.getZ()+2);
        check(PostureService.snapshot(p).meter()==1&&p.getDeltaMovement().y==0&&!PostureService.snapshot(p).movement().equals("server_velocity_acceleration"),
                "Native ground contact cancelling downward gravity every tick permits Evasive charge from accepted motion evidence");
        for(int gap=1;gap<=2;gap++) {
            f.level.tick++;groundContact(f);PostureService.tick(SkillEffectRuntime.context(p));
            close(PostureService.snapshot(p).meter(),1,"A brief packet gap holds Evasive without draining or inventing movement");
        }
        f.level.tick++;groundContact(f);PostureService.tick(SkillEffectRuntime.context(p));
        check(PostureService.snapshot(p).meter()<1,"Missing movement beyond two ticks resumes normal drain despite held input");
        chargeEvasive(f);f.level.tick++;AttunementGameplay.setMovementIntent(p,false);PostureService.tick(SkillEffectRuntime.context(p));
        check(PostureService.snapshot(p).meter()<1,"Released movement input drains immediately even within sample grace");
        chargeEvasive(f);
        p.hurtTime=5; evasiveStep(f);
        check(PostureService.snapshot(p).meter()<1&&PostureService.snapshot(p).movement().equals("recent_native_hit"),
                "Evasive still rejects movement evidence during native hit recovery");
        chargeEvasive(f);
        p.setDeltaMovement(new Vec3(0,.42,0));
        double beforeLift=PostureService.snapshot(p).meter();
        evasiveStep(f);
        check(PostureService.snapshot(p).movement().equals("server_velocity_acceleration")&&PostureService.snapshot(p).meter()<beforeLift,
                "Actual transformed positive upward velocity still suppresses charge despite simultaneous deliberate input");
        chargeEvasive(f);
        attacker.setPos(p.getX(),p.getY(),p.getZ()+2);
        var shield=new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get());p.setItemInHand(InteractionHand.OFF_HAND,shield);p.startUsingItem(InteractionHand.OFF_HAND);
        ProjectileNativeInterceptionTest.set(LivingEntity.class,p,"useItemRemaining",shield.getUseDuration(p)-EquipmentShieldService.raiseDelayTicks(p,shield));
        p.invulnerableTime=0;p.setHealth(20);p.hurtTime=0;
        int durability=shield.getDamageValue();
        int[] rolls={0};
        check(!EquipmentDamageService.withPostureTestRoll(()->{rolls[0]++;return 0;},()->p.hurt(source,4)),"Actual transformed native dodge returns false before shield");
        s=PostureService.snapshot(p);var outcome=EquipmentDamageService.lastGuardOutcome(p).orElseThrow();
        check(s.incoming().dodged()&&s.incoming().roll()==0&&rolls[0]==1,"Exactly one scoped deterministic native roll recorded: "+s.incoming()+" calls="+rolls[0]);
        check(!outcome.successfulBlock()&&!outcome.perfect()&&outcome.requestedReflection()==0&&outcome.wardExtension()==0,"Dodge cannot fabricate native block/perfect/reflection/Ward rewards");
        check(shield.getDamageValue()==durability&&p.getHealth()==20,"Dodge bypasses native shield durability and health write");
        var dodgeHud=transmittedHud(f).entries().stream().filter(card->card.id().equals(SkillIds.EVASIVE_CURRENT)).findFirst().orElseThrow();
        check(s.recentDodge()&&dodgeHud.active()&&dodgeHud.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.dodged"))),
                "Confirmed native dodge reaches the real shared packet in the existing dodge-percent line");
        close(s.meter(),1-SkillEffectRuntime.resolvedSettings(p).posture().evasive().successDrainFraction(),"Native dodge drains once");
        long event=s.incoming().event();EquipmentDamageService.endDamage(p,source,true,false);
        check(PostureService.snapshot(p).incoming().event()==event,"Duplicate native completion cannot change dodge");
        zombieDodgeOutcomes(f);
        p.stopUsingItem();p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        data.setLoadoutSelection(SkillGroups.DEFENSE_POSTURE,SkillIds.BULWARK_STANCE);SkillEffectRuntime.refresh(p);
        check(p.getMainHandItem().isEmpty()&&p.getOffhandItem().isEmpty()&&SkillEffectRuntime.context(p).isEffective(SkillIds.BULWARK_STANCE),
                "Bulwark is effective with both hands empty and no shield equipped");
        check(!PostureService.snapshot(p).recentDodge(),"Switching posture immediately clears previous dodge feedback");
        attacker.setPos(0,0,2);
        p.setPos(0,0,0);PostureService.forget(p);SkillEffectRuntime.refresh(p);p.setDeltaMovement(Vec3.ZERO);p.hurtTime=0;ProjectileNativeInterceptionTest.set(Entity.class,p,"onGround",true);
        var settings=SkillEffectRuntime.resolvedSettings(p).posture();
        for(int t=0;t<settings.bulwark().buildTicks()+settings.movement().stableTicks();t++){
            f.level.tick++;groundContact(f);PostureService.tick(SkillEffectRuntime.context(p));
        }
        check(PostureService.snapshot(p).meter()==1&&PostureService.snapshot(p).threat().facing(),
                "Native ground contact cancelling downward gravity every tick permits Bulwark frontal stillness charge");
        check(ThreatFacingResolver.incoming(p,attacker,settings.bulwark()),"Native responsible attacker in front accepted");
        f.level.occluded=true;check(!ThreatFacingResolver.incoming(p,attacker,settings.bulwark()),"Controlled native LOS terrain rejects hidden threats");f.level.occluded=false;
        p.invulnerableTime=0;p.setHealth(20);p.setDeltaMovement(Vec3.ZERO);p.hurtTime=0;
        check(p.hurt(source,4),"Bulwark native incoming hit remains accepted");
        s=PostureService.snapshot(p);outcome=EquipmentDamageService.lastGuardOutcome(p).orElseThrow();
        close(s.incoming().resistance(),settings.bulwark().maximumResistance(),"Full frontal meter applies generated resistance");
        check(outcome.attemptedKnockback().lengthSqr()>0&&outcome.acceptedKnockback().lengthSqr()==0,"Correlated actual native knockback attempt is fully rejected at threshold");
        check(s.incoming().knockback().contains("bulwark"),"Posture diagnostic retains exact correlated force decision");
        check(p.hurtTime>0&&!outcome.successfulBlock()&&p.getHealth()<20,
                "Unarmed Bulwark takes actual health damage and enters the native hurt animation without a shield block");
        for(int tick=1;tick<=40;tick++) {
            f.level.tick++;groundContact(f);PostureService.tick(SkillEffectRuntime.context(p));
            close(PostureService.snapshot(p).meter(),1,"Standing unarmed Bulwark survives the hurt animation on tick "+tick);
            if(p.hurtTime>0)p.hurtTime--;
            if(tick%20==0) {
                p.invulnerableTime=0;p.setHealth(20);check(p.hurt(source,4),"Repeated unblocked Bulwark hit is accepted");
                close(PostureService.snapshot(p).incoming().resistance(),settings.bulwark().maximumResistance(),
                        "Repeated frontal hits retain full Bulwark resistance without any shield");
            }
        }
        var bulwarkHud=transmittedHud(f).entries().stream().filter(card->card.id().equals(SkillIds.BULWARK_STANCE)).findFirst().orElseThrow();
        check(bulwarkHud.active()&&bulwarkHud.meter().equals(SkillEffectHudEntry.Meter.progress(1)),
                "Unarmed Bulwark remains visible at full strength through repeated unblocked combat");
        p.setYRot(180);p.invulnerableTime=0;p.setHealth(20);p.hurt(source,4);
        check(PostureService.snapshot(p).incoming().resistance()==0,"Turning releases resistance immediately before next tick");
        bulwarkHeldItemComparison(f,attacker);
        p.getAbilities().invulnerable=true;SkillEffectRuntime.refresh(p);check(!PostureService.snapshot(p).effective(),"Invalid defender lifecycle discards transient state");
        p.getAbilities().invulnerable=false;PostureService.forget(p);f.close();
        System.out.println("Native posture outcome checks passed: "+checks+" (actual transformed native damage/knockback; controlled movement/terrain, no world)");
    }
    private static void zombieDodgeOutcomes(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var p=f.player;p.stopUsingItem();p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var zombies=List.of(zombie(f),zombie(f));
        int successes=0,failures=0;
        // Exercise the production RandomSource path too, not just a forced successful test roll.
        ProjectileNativeInterceptionTest.set(Entity.class,p,"random",RandomSource.create(8137));
        for(int attempt=0;attempt<24;attempt++) {
            chargeEvasive(f);p.invulnerableTime=0;p.setHealth(20);
            var zombie=zombies.get(attempt%2);zombie.setPos(p.getX(),p.getY(),p.getZ()+1);
            var display=new SkillEffectHudPresentation();
            com.mistaboom.essence_ascendance.skill.effect.CombatHudActivity.confirmedDefense(p,p.damageSources().mobAttack(zombie));
            display.replace(SkillEffectRuntime.hudSnapshot(p).entries(),f.level.tick);
            boolean accepted=zombie.doHurtTarget(p);
            var result=PostureService.snapshot(p);
            check(result.incoming().chance()==.2&&result.incoming().roll()>=0&&result.incoming().roll()<1,
                    "Actual unarmed zombie melee makes a production random roll at the full meter's 20 percent chance");
            var hud=transmittedHud(f);display.replace(hud.entries(),hud.serverGameTime());
            var card=display.visibleEntries(f.level.tick).stream().filter(entry->entry.id().equals(SkillIds.EVASIVE_CURRENT)).findFirst().orElseThrow();
            if(result.incoming().dodged()) {
                successes++;
                check(!accepted&&p.getHealth()==20&&result.meter()==.5&&card.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.dodged"))),
                        "A successful real zombie dodge prevents damage, spends half charge and says Dodged on the regular card");
            } else {
                failures++;
                check(accepted&&p.getHealth()<20&&result.meter()==0&&card.active()&&card.meter().fraction()==0
                                &&card.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.evasive","0.0"))),
                        "A failed real zombie roll immediately displays zero chance instead of retaining the pre-hit full card");
            }
        }
        check(successes>0&&failures>0,"Seeded production RNG against two zombies covers both successes and failures: "+successes+" / "+failures);
        System.out.println("Two-zombie Evasive outcomes: "+successes+" dodges / "+failures+" damaging hits (24 full-meter native attacks, production RNG)");
    }
    private static net.minecraft.world.entity.monster.Zombie zombie(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var zombie=ProjectileNativeInterceptionTest.instance(net.minecraft.world.entity.monster.Zombie.class);
        ProjectileNativeInterceptionTest.initializeEntity(zombie,net.minecraft.world.entity.EntityType.ZOMBIE,f.level,new Vec3(0,0,2),new net.minecraft.world.phys.AABB(-.3,0,1.7,.3,1.95,2.3));
        ProjectileNativeInterceptionTest.defineData(zombie,net.minecraft.world.entity.monster.Zombie.class);
        ProjectileNativeInterceptionTest.set(LivingEntity.class,zombie,"attributes",new net.minecraft.world.entity.ai.attributes.AttributeMap(net.minecraft.world.entity.monster.Zombie.createAttributes().build()));
        ProjectileNativeInterceptionTest.set(LivingEntity.class,zombie,"activeEffects",new java.util.HashMap<>());
        ProjectileNativeInterceptionTest.set(LivingEntity.class,zombie,"useItem",ItemStack.EMPTY);
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class,zombie,"handItems",net.minecraft.core.NonNullList.withSize(2,ItemStack.EMPTY));
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class,zombie,"armorItems",net.minecraft.core.NonNullList.withSize(4,ItemStack.EMPTY));
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class,zombie,"bodyArmorItem",ItemStack.EMPTY);
        zombie.setHealth(20);f.level.entities.add(zombie);return zombie;
    }
    private static void bulwarkHeldItemComparison(ProjectileNativeInterceptionTest.Fixture f, LivingEntity attacker) throws ReflectiveOperationException {
        var p=f.player;var source=p.damageSources().playerAttack((net.minecraft.world.entity.player.Player)attacker);
        var settings=SkillEffectRuntime.resolvedSettings(p).posture();
        for(var held:List.of(ItemStack.EMPTY,new ItemStack(net.minecraft.world.item.Items.SHIELD),
                new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get()))) {
            p.stopUsingItem();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,held);
            p.setPos(0,0,0);attacker.setPos(0,0,2);p.setYRot(0);p.setXRot(0);p.hurtTime=0;p.setDeltaMovement(Vec3.ZERO);
            ProjectileNativeInterceptionTest.set(Entity.class,p,"onGround",true);
            PostureService.forget(p);SkillEffectRuntime.refresh(p);
            com.mistaboom.essence_ascendance.skill.effect.CombatHudActivity.clear();
            for(int tick=0;tick<settings.bulwark().buildTicks()+settings.movement().stableTicks();tick++) {
                f.level.tick++;p.push(.05,0,0);
                // Controlled blocked-translation boundary: actual native push/velocity hooks execute,
                // then terrain cancels velocity without changing authoritative position.
                groundContact(f);SkillEffectRuntime.tick(p);EquipmentShieldService.tick(p);
            }
            check(!p.isUsingItem()&&PostureService.snapshot(p).meter()==1,
                    "Bulwark charges through stationary push attempts with a lowered held item: "+held);
            var chargedHud=SkillEffectRuntime.hudSnapshot(p).entries().stream().filter(card->card.id().equals(SkillIds.BULWARK_STANCE)).findFirst().orElseThrow();
            check(!chargedHud.active(),"Holding an item alone never creates combat or reveals the Bulwark card");
            p.invulnerableTime=0;p.setHealth(20);check(p.hurt(source,4),"Unblocked native hit qualifies equally with a lowered held item: "+held);
            close(PostureService.snapshot(p).incoming().resistance(),settings.bulwark().maximumResistance(),
                    "Pending force with no displacement cannot invalidate incoming Bulwark protection");
            f.level.tick++;groundContact(f);SkillEffectRuntime.tick(p);EquipmentShieldService.tick(p);
            var combatHud=SkillEffectRuntime.hudSnapshot(p).entries().stream().filter(card->card.id().equals(SkillIds.BULWARK_STANCE)).findFirst().orElseThrow();
            check(combatHud.active()&&combatHud.meter().fraction()==1&&!EquipmentDamageService.lastGuardOutcome(p).orElseThrow().successfulBlock(),
                    "Empty hand, lowered vanilla shield and lowered Ascendance shield have identical active Bulwark cards");
            Vec3 before=p.position();p.setPos(before.x+.1,before.y,before.z);
            PostureService.moved(p,before,p.level().dimension().location().toString(),p.getYRot(),p.getXRot());
            p.invulnerableTime=0;p.setHealth(20);p.hurt(source,4);
            check(PostureService.snapshot(p).incoming().resistance()==0,
                    "Actual displacement still releases Bulwark immediately, independent of the held item");
        }
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
    }
    private static void chargeEvasive(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var p=f.player;p.hurtTime=0;ProjectileNativeInterceptionTest.set(Entity.class,p,"onGround",true);p.setDeltaMovement(Vec3.ZERO);PostureService.forget(p);SkillEffectRuntime.refresh(p);
        int build=SkillEffectRuntime.resolvedSettings(p).posture().evasive().buildTicks();
        for(int tick=0;tick<build;tick++) evasiveStep(f);
    }
    private static void evasiveStep(ProjectileNativeInterceptionTest.Fixture f) {
        var p=f.player;f.level.tick++;groundContact(f);AttunementGameplay.setMovementIntent(p,true);
        Vec3 before=p.position();p.setPos(before.x+.1,before.y,before.z);
        PostureService.moved(p,before,p.level().dimension().location().toString(),p.getYRot(),p.getXRot());
        PostureService.tick(SkillEffectRuntime.context(p));
    }
    private static void groundContact(ProjectileNativeInterceptionTest.Fixture f) {
        // Real Entity.move sets onGround before this real Block callback cancels downward gravity.
        // The fixture supplies the collision boundary without opening a world or overriding velocity.
        f.player.setDeltaMovement(new Vec3(0,-.0784,0));
        Blocks.STONE.updateEntityAfterFallOn(f.level,f.player);
    }
    private static SkillEffectHudSnapshot transmittedHud(ProjectileNativeInterceptionTest.Fixture f) {
        var snapshot=SkillEffectRuntime.hudSnapshot(f.player);
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),f.level.registryAccess());
        try {
            SkillEffectHudPayload.CODEC.encode(buffer,new SkillEffectHudPayload(snapshot));
            var decoded=SkillEffectHudPayload.CODEC.decode(buffer).snapshot();
            check(decoded.equals(snapshot),"Actual runtime posture HUD snapshot survives the shared S2C codec");
            return decoded;
        } finally {buffer.release();}
    }
    private static void close(double a,double b,String message){check(Math.abs(a-b)<1e-5,message+": "+a+" vs "+b);}
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
}
