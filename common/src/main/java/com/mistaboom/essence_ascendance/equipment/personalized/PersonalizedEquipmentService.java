package com.mistaboom.essence_ascendance.equipment.personalized;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** An owner-carried overlay on ordinary equipment, with no new item, component or catalog entry. */
public final class PersonalizedEquipmentService {
    public static final String RESOURCE = "/data/essence_ascendance/personalized_equipment.json";
    public static final String MOB_OPENING_LAUNCH = "mob_opening_launch";
    public static final String MOB_LAUNCH_STEERING = "mob_launch_steering";
    private static final List<PersonalizedEquipmentDefinition> DEFINITIONS = load();
    private static final Map<UUID, List<PersonalizedEquipmentDefinition>> BY_PLAYER = index();
    private PersonalizedEquipmentService() { }

    public static List<PersonalizedEquipmentDefinition> definitions() { return DEFINITIONS; }

    public static Optional<PersonalizedEquipmentDefinition> active(Player player, ItemStack stack) {
        if (player == null) return Optional.empty();
        var candidates = BY_PLAYER.get(player.getUUID());
        if (candidates == null) return Optional.empty();
        if (stack == null || stack.isEmpty() || !player.isAlive() || player.isSpectator()
                || !carries(player, stack)
                || !EquipmentTierData.isAscendanceEquipment(stack) || FracturedEquipmentData.isFractured(stack)
                || stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage()) return Optional.empty();
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String tier = EquipmentTierData.tier(stack).serializedName();
        return candidates.stream().filter(p -> p.itemId().equals(item) && p.tier().equals(tier)).findFirst();
    }

    private static boolean carries(Player player, ItemStack stack) {
        if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) return true;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (inventory.getItem(slot) == stack) return true;
        // Picking up a slot in a menu still belongs to this player until it is placed or dropped.
        return player.containerMenu != null && player.containerMenu.getCarried() == stack;
    }

    private static List<PersonalizedEquipmentDefinition> load() {
        var stream = PersonalizedEquipmentService.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing equipment personalization definitions");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return PersonalizedEquipmentDefinition.read(reader);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not load equipment personalization definitions", failure);
        }
    }

    private static Map<UUID, List<PersonalizedEquipmentDefinition>> index() {
        Map<UUID, List<PersonalizedEquipmentDefinition>> result = new HashMap<>();
        for (var definition : DEFINITIONS)
            result.computeIfAbsent(definition.playerId(), id -> new ArrayList<>()).add(definition);
        result.replaceAll((id, entries) -> List.copyOf(entries));
        return Map.copyOf(result);
    }
}
