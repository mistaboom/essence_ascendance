package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.skill.effect.RecentHostileCombat;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.utility.BondedCompanionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
//? if >=26.1.2
/*import net.minecraft.world.entity.animal.feline.Cat;*/
//? if <26.1.2
import net.minecraft.world.entity.animal.Cat;
import java.util.*;
import java.util.function.BiConsumer;

/** Existing native ownership/damage/tick hooks supply facts. No separate pet or combat simulation. */
public final class CompanionOutcomes {
    private static final Map<MinecraftServer, CompanionCatEvidence> CATS = new WeakHashMap<>();
    private static BiConsumer<MinecraftServer,CompanionOutcome> listener;
    private CompanionOutcomes() { }
    public static void register(BiConsumer<MinecraftServer,CompanionOutcome> consumer) {
        if(listener!=null)throw new IllegalStateException("Companion consumer already registered");
        listener=Objects.requireNonNull(consumer);
    }
    public static UUID ownerId(OwnableEntity companion) {
        //? if >=26.1.2
        /*return companion.getOwnerReference()==null?null:companion.getOwnerReference().getUUID();*/
        //? if <26.1.2
        return companion.getOwnerUUID();
    }
    /** Scalar snapshot taken on the synchronous native damage call stack, before owner/skill can change. */
    public record DamageSnapshot(String action, UUID damagedCatOwner, UUID companion, UUID empoweredOwner,
                                 boolean empowered, boolean hostile) { }
    public static DamageSnapshot capture(LivingEntity target,DamageSource source,String action) {
        UUID catOwner=target instanceof Cat cat&&cat.isTame()?ownerId(cat):null;
        LivingEntity killer=source.getEntity() instanceof LivingEntity living?living:null;
        ServerPlayer owner=killer==null?null:BondedCompanionService.empoweredOwner(killer);
        return new DamageSnapshot(action,catOwner,killer==null?null:killer.getUUID(),owner==null?null:owner.getUUID(),
                owner!=null&&AttunementGameplay.eligible(owner),owner!=null&&target instanceof Enemy&&!owner.isAlliedTo(target));
    }
    public static void committed(LivingEntity target,DamageSource source,DamageSnapshot snapshot,double loss,boolean died) {
        if(!(target.level() instanceof ServerLevel level)||!(loss>0)||!Double.isFinite(loss))return;
        if(target instanceof Cat cat) {
            UUID current=cat.isTame()?ownerId(cat):null;
            if(current!=null)evidence(level.getServer()).damaged(cat.getUUID(),current,dimension(cat),now(level.getServer()));
            invalidate(level.getServer(),snapshot.damagedCatOwner(),cat.getUUID(),dimension(cat),"accepted_damage");
            if(!Objects.equals(current,snapshot.damagedCatOwner()))invalidate(level.getServer(),current,cat.getUUID(),dimension(cat),"accepted_damage");
        }
        if(!died||!snapshot.empowered()||snapshot.companion()==null)return;
        if(!(source.getEntity() instanceof LivingEntity killer)||!snapshot.companion().equals(killer.getUUID()))return;
        ServerPlayer owner=BondedCompanionService.empoweredOwner(killer);
        if(owner==null||!AttunementGameplay.eligible(owner)||!Objects.equals(snapshot.empoweredOwner(),owner.getUUID())
                ||owner.level()!=target.level())return;
        publish(level.getServer(),new CompanionOutcome.BondedKill(new CompanionOutcome.Context(snapshot.action(),owner.getUUID(),dimension(target),now(level.getServer())),
                killer.getUUID(),target.getUUID(),snapshot.empoweredOwner(),owner.getUUID(),snapshot.hostile(),true,true));
    }
    /** Loaded native cat ticks establish continuous damage observation, independently of Bonded Companion. */
    public static void observe(LivingEntity entity) {
        if(!(entity instanceof Cat cat)||!(cat.level() instanceof ServerLevel level))return;
        UUID owner=cat.isTame()?ownerId(cat):null;
        var evidence=evidence(level.getServer());var previous=evidence.owner(cat.getUUID());
        if(!Objects.equals(previous,owner))invalidate(level.getServer(),previous,cat.getUUID(),dimension(cat),"ownership_lost");
        if(!cat.isAlive())invalidate(level.getServer(),owner,cat.getUUID(),dimension(cat),"cat_dead");
        if(owner==null){evidence.removed(cat.getUUID());return;}
        evidence.observe(cat.getUUID(),owner,dimension(cat),now(level.getServer()));
    }
    /** Called after actual native ownership/tame mutations, including transfer away and back within one tick. */
    public static void ownershipChanged(TamableAnimal animal,UUID previous,boolean wasTame) {
        if(!(animal instanceof Cat)||!(animal.level() instanceof ServerLevel level))return;
        UUID current=animal.isTame()?ownerId(animal):null;
        if(wasTame&&(!animal.isTame()||!Objects.equals(previous,current)))
            invalidate(level.getServer(),previous,animal.getUUID(),dimension(animal),"ownership_lost");
        if(!Objects.equals(previous,current)||wasTame!=animal.isTame())evidence(level.getServer()).removed(animal.getUUID());
    }
    public static void removed(Entity entity,Entity.RemovalReason reason) {
        if(!(entity instanceof Cat cat)||!(entity.level() instanceof ServerLevel level))return;
        if(reason.shouldDestroy())invalidate(level.getServer(),cat.isTame()?ownerId(cat):null,cat.getUUID(),dimension(cat),"cat_removed");
        evidence(level.getServer()).removed(cat.getUUID());
    }
    public static void playerKilled(ServerPlayer player,CombatOutcome.Kill kill) {
        if(!kill.context().hostile()||!AttunementGameplay.eligible(player))return;
        var skill=SkillEffectRuntime.context(player);
        int timeout=skill.settings().vitality().innerSustenance().combatTimeoutTicks();
        long fightStart=RecentHostileCombat.encounterStartedAt(skill,timeout);
        if(fightStart<0)return;
        var cats=player.serverLevel().getEntitiesOfClass(Cat.class,player.getBoundingBox().inflate(OutcomePolicies.NEARBY_CAT_RADIUS),cat->true);
        if(cats.size()>64)return; // Fail closed rather than choose from a truncated candidate list.
        List<CompanionOutcome.CatFact> facts=new ArrayList<>();
        var evidence=evidence(player.server);
        for(var cat:cats) {
            var owner=cat.isTame()?ownerId(cat):null;
            facts.add(new CompanionOutcome.CatFact(new OutcomePolicies.CatCandidate(cat.getUUID(),owner,dimension(cat),cat.isAlive(),!cat.isRemoved(),cat.isTame(),player.distanceToSqr(cat)),
                    evidence.undamaged(cat.getUUID(),player.getUUID(),dimension(player),fightStart,now(player.server))));
        }
        var c=kill.context();
        publish(player.server,new CompanionOutcome.CatKill(new CompanionOutcome.Context(c.actionId(),c.actor(),c.dimension(),c.committedTick()),c.victim(),facts));
    }
    private static void invalidate(MinecraftServer server,UUID owner,UUID cat,String dimension,String reason) {
        if(owner!=null)publish(server,new CompanionOutcome.CatInvalidated(new CompanionOutcome.Context(AttunementGameplay.action("companion_invalidation"),owner,dimension,now(server)),cat,reason));
    }
    private static void publish(MinecraftServer server,CompanionOutcome fact) {
        if(listener!=null&&server.isSameThread())try{listener.accept(server,fact);}
        catch(RuntimeException failure){EssenceAscendance.LOGGER.error("Companion outcome delivery failed",failure);}
    }
    private static CompanionCatEvidence evidence(MinecraftServer server){return CATS.computeIfAbsent(server,ignored->new CompanionCatEvidence());}
    private static long now(MinecraftServer server){return server.overworld().getGameTime();}
    private static String dimension(Entity entity){return entity.level().dimension().location().toString();}
    public static void clear(MinecraftServer server){CATS.remove(server);}
}
