package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.skill.effect.MeleeOffenseSnapshot;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/** Native melee amounts/enchantments -> ONE native hurt -> existing defenses, attribution and skill outcome hooks. */
public final class WeaponActionDamage {
    private WeaponActionDamage() { }
    public record Offense(ItemStack weapon, double damage, double knockback, double speed, float readiness,
                          boolean critical, boolean sprinting, int looting, MeleeOffenseSnapshot skills) { }
    private record Contact(ServerPlayer owner, Offense offense) { }
    private static final ThreadLocal<Contact> CONTACT = new ThreadLocal<>();

    public static Offense snapshot(ServerPlayer player) {
        EquipmentAttributeService.sync(player);
        EquipmentGatheringService.sync(player);
        boolean sprint = player.isSprinting();
        boolean critical = player.fallDistance>0 && !player.onGround() && !player.onClimbable()
                && !player.isInWater()
                //? if >=26.1.2
                /*&& !player.isMobilityRestricted()*/
                //? if <26.1.2
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger() && !sprint;
        var weapon=player.getMainHandItem().copy();
        //? if >=26.1.2
        /*var looting=player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(net.minecraft.world.item.enchantment.Enchantments.LOOTING);*/
        //? if <26.1.2
        var looting=player.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                //? if <26.1.2
                .getHolder(net.minecraft.world.item.enchantment.Enchantments.LOOTING);
        int lootingLevel=looting.map(h->EquipmentGatheringService.resolveVirtualEnchantmentLevel(weapon,h,
                weapon.getEnchantments().getLevel(h))).orElse(0);
        return new Offense(weapon,player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                player.getAttributeValue(Attributes.ATTACK_KNOCKBACK),player.getAttributeValue(Attributes.ATTACK_SPEED),
                player.getAttackStrengthScale(.5F),critical,sprint,lootingLevel,SkillEffectRuntime.snapshotMeleeOffense(player));
    }
    /** DamageSource's armor/enchantment and loot callers see the accepted weapon, without replacing a hand stack. */
    public static ItemStack weapon(DamageSource source) {
        Contact contact = CONTACT.get();
        return contact != null && source.getEntity()==contact.owner && source.getDirectEntity()==contact.owner
                ? contact.offense.weapon : null;
    }
    public static Integer looting(ServerPlayer player) {
        Contact contact=CONTACT.get(); return contact!=null && contact.owner==player ? contact.offense.looting : null;
    }
    public static Integer looting(ItemStack stack) {
        Contact contact=CONTACT.get();
        return contact!=null && (stack==contact.offense.weapon || stack==contact.owner.getMainHandItem()) ? contact.offense.looting : null;
    }
    public static EquipmentDamageService.DamageResult hit(ServerPlayer player, Entity target, Offense offense,
                                                          double scalar, boolean primaryEligible, Vec3 direction) {
        TwoHandedEquipmentService.enterCombat();
        try { return nativeHit(player,target,offense,scalar,primaryEligible,direction); }
        finally { TwoHandedEquipmentService.exitCombat(); }
    }
    private static EquipmentDamageService.DamageResult nativeHit(ServerPlayer player,Entity target,Offense offense,
                                                                 double scalar,boolean primaryEligible,Vec3 direction) {
        if (target instanceof LivingEntity living && !EquipmentDamageService.canSkillHarm(player,living))
            return new EquipmentDamageService.DamageResult(false,0);
        if (!target.isAttackable() || target.skipAttackInteraction(player)) return new EquipmentDamageService.DamageResult(false,0);
        DamageSource source = player.damageSources().playerAttack(player);
        float base = (float)offense.damage;
        float enchanted = EnchantmentHelper.modifyDamage(player.serverLevel(),offense.weapon,target,source,base)-base;
        float strength = offense.readiness;
        base *= .2F+strength*strength*.8F;
        base += offense.weapon.getItem().getAttackDamageBonus(target,base,source);
        boolean strong=strength>GlaiveTuning.STRONG_HIT_THRESHOLD;
        boolean critical=offense.critical && strong && target instanceof LivingEntity;
        if (critical) base *= 1.5F;
        float amount = base+enchanted*strength;
        Contact previous = CONTACT.get(); CONTACT.set(new Contact(player,offense));
        try {
            EquipmentDamageService.DamageResult result = target instanceof LivingEntity living
                    ? EquipmentDamageService.runActionMeleeContact(player,living,source,offense.skills,primaryEligible,scalar,
                            //? if >=26.1.2
                            /*() -> living.hurtServer(player.serverLevel(), source, amount))*/
                            //? if <26.1.2
                            () -> living.hurt(source,amount))
                    //? if >=26.1.2
                    /*: new EquipmentDamageService.DamageResult(target.hurtServer(player.serverLevel(), source, (float)(amount*scalar)),0);*/
                    //? if <26.1.2
                    : new EquipmentDamageService.DamageResult(target.hurt(source,(float)(amount*scalar)),0);
            if (result.accepted()) {
                float knockback = EnchantmentHelper.modifyKnockback(player.serverLevel(),offense.weapon,target,source,(float)offense.knockback)
                        +(offense.sprinting && strong ? 1 : 0);
                if (knockback>0 && target instanceof LivingEntity living) living.knockback(knockback*.5,-direction.x,-direction.z);
                EnchantmentHelper.doPostAttackEffectsWithItemSource(player.serverLevel(),target,source,offense.weapon);
                if (critical) player.crit(target);
                if (enchanted>0) player.magicCrit(target);
                player.setLastHurtMob(target);
                player.causeFoodExhaustion(.1F);
            }
            return result;
        } finally { if (previous==null) CONTACT.remove(); else CONTACT.set(previous); }
    }
    /** Charge the original stack once, through native Unbreaking and the existing wear/fracture adapters. */
    public static void wear(ServerPlayer owner, ItemStack original, int slot) {
        TwoHandedEquipmentService.enterCombat();
        try {
            if (owner.getMainHandItem()==original && selectedSlot(owner)==slot)
                original.hurtAndBreak(1,owner,EquipmentSlot.MAINHAND);
        } finally { TwoHandedEquipmentService.exitCombat(); }
    }

    private static int selectedSlot(ServerPlayer player) {
        //? if >=26.1.2
        /*return player.getInventory().getSelectedSlot();*/
        //? if <26.1.2
        return player.getInventory().selected;
    }
}
