package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.posture.PostureService;
import com.mistaboom.essence_ascendance.posture.ThreatFacingResolver;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
        p.invulnerableTime=0;check(p.hurt(source,4),"First native adaptation hit accepted");
        close(PostureService.snapshot(p).incoming().resistance(),0,"First native exact damage ID unmitigated");
        check(PostureService.snapshot(p).stacks()==1,"Native measured positive health hit seeds first stack");
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
        check(PostureService.snapshot(p).meter()==1,"Controlled server-accepted motion evidence fills Evasive meter");
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
        close(s.meter(),1-SkillEffectRuntime.resolvedSettings(p).posture().evasive().successDrainFraction(),"Native dodge drains once");
        long event=s.incoming().event();EquipmentDamageService.endDamage(p,source,true,false);
        check(PostureService.snapshot(p).incoming().event()==event,"Duplicate native completion cannot change dodge");
        p.stopUsingItem();data.setLoadoutSelection(SkillGroups.DEFENSE_POSTURE,SkillIds.BULWARK_STANCE);SkillEffectRuntime.refresh(p);
        attacker.setPos(0,0,2);
        p.setPos(0,0,0);PostureService.forget(p);SkillEffectRuntime.refresh(p);p.setDeltaMovement(Vec3.ZERO);p.hurtTime=0;ProjectileNativeInterceptionTest.set(Entity.class,p,"onGround",true);
        var settings=SkillEffectRuntime.resolvedSettings(p).posture();
        for(int t=0;t<settings.bulwark().buildTicks()+settings.movement().stableTicks();t++){f.level.tick++;PostureService.tick(SkillEffectRuntime.context(p));}
        check(PostureService.snapshot(p).meter()==1&&PostureService.snapshot(p).threat().facing(),"Bounded real native hostility/LOS query charges frontal stillness");
        check(ThreatFacingResolver.incoming(p,attacker,settings.bulwark()),"Native responsible attacker in front accepted");
        f.level.occluded=true;check(!ThreatFacingResolver.incoming(p,attacker,settings.bulwark()),"Controlled native LOS terrain rejects hidden threats");f.level.occluded=false;
        p.invulnerableTime=0;p.setHealth(20);p.setDeltaMovement(Vec3.ZERO);p.hurtTime=0;
        check(p.hurt(source,4),"Bulwark native incoming hit remains accepted");
        s=PostureService.snapshot(p);outcome=EquipmentDamageService.lastGuardOutcome(p).orElseThrow();
        close(s.incoming().resistance(),settings.bulwark().maximumResistance(),"Full frontal meter applies generated resistance");
        check(outcome.attemptedKnockback().lengthSqr()>0&&outcome.acceptedKnockback().lengthSqr()==0,"Correlated actual native knockback attempt is fully rejected at threshold");
        check(s.incoming().knockback().contains("bulwark"),"Posture diagnostic retains exact correlated force decision");
        p.setYRot(180);p.invulnerableTime=0;p.setHealth(20);p.hurt(source,4);
        check(PostureService.snapshot(p).incoming().resistance()==0,"Turning releases resistance immediately before next tick");
        p.getAbilities().invulnerable=true;SkillEffectRuntime.refresh(p);check(!PostureService.snapshot(p).effective(),"Invalid defender lifecycle discards transient state");
        p.getAbilities().invulnerable=false;PostureService.forget(p);f.close();
        System.out.println("Native posture outcome checks passed: "+checks+" (actual transformed native damage/knockback; controlled movement/terrain, no world)");
    }
    private static void chargeEvasive(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var p=f.player;p.hurtTime=0;ProjectileNativeInterceptionTest.set(Entity.class,p,"onGround",true);p.setDeltaMovement(Vec3.ZERO);PostureService.forget(p);SkillEffectRuntime.refresh(p);
        int build=SkillEffectRuntime.resolvedSettings(p).posture().evasive().buildTicks();
        for(int tick=0;tick<build;tick++) {
            f.level.tick++;AttunementGameplay.setMovementIntent(p,true);Vec3 before=p.position();p.setPos(before.x+.1,before.y,before.z);
            PostureService.moved(p,before,p.level().dimension().location().toString(),p.getYRot(),p.getXRot());
            PostureService.tick(SkillEffectRuntime.context(p));
        }
    }
    private static void close(double a,double b,String message){check(Math.abs(a-b)<1e-5,message+": "+a+" vs "+b);}
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
}
