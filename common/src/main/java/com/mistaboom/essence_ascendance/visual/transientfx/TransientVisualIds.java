package com.mistaboom.essence_ascendance.visual.transientfx;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

/** Loader- and side-neutral recipe identities used by dispatchers and client registries. */
public final class TransientVisualIds {
    public static final ResourceLocation WORLD_IMPACT_PULSE = id("impact_pulse");
    public static final ResourceLocation WORLD_GATHERING_SPRITZ = id("gathering_spritz");
    public static final ResourceLocation WORLD_UTILITY_ACKNOWLEDGE = id("utility_acknowledge");
    public static final ResourceLocation WORLD_PROCESSOR_COMPLETE = id("processor_complete");
    public static final ResourceLocation WORLD_KINDLING = id("kindling");
    public static final ResourceLocation WORLD_IGNITION = id("ignition");
    public static final ResourceLocation WORLD_COMBUSTION = id("combustion");
    public static final ResourceLocation WORLD_FROSTBITE = id("frostbite");
    public static final ResourceLocation WORLD_FREEZE = id("freeze");
    public static final ResourceLocation WORLD_SHATTER = id("shatter");
    public static final ResourceLocation WORLD_STATIC_ARC = id("static_arc");
    public static final ResourceLocation WORLD_ROOTING = id("rooting");
    public static final ResourceLocation WORLD_EXPLOSIVE_PAYLOAD = id("explosive_payload");
    public static final ResourceLocation WORLD_PROJECTILE_GUIDANCE = id("projectile_guidance");
    public static final ResourceLocation WORLD_PROJECTILE_REDIRECT = id("projectile_redirect");
    public static final ResourceLocation WORLD_PROJECTILE_PIERCE = id("projectile_pierce");
    public static final ResourceLocation WORLD_PROJECTILE_DRAG = id("projectile_drag");
    public static final ResourceLocation WORLD_PROJECTILE_INTERCEPT = id("projectile_intercept");
    public static final ResourceLocation WORLD_SHIELD_RAM = id("shield_ram");
    public static final ResourceLocation WORLD_GUARD_RESPONSE = id("guard_response");
    public static final ResourceLocation WORLD_REFLECTION_RETURN = id("reflection_return");
    public static final ResourceLocation WORLD_CROWD_REPRISAL = id("crowd_reprisal");
    public static final ResourceLocation WORLD_RIPOSTE_RELEASE = id("riposte_release");
    public static final ResourceLocation WORLD_STORED_FORCE = id("stored_force");
    public static final ResourceLocation WORLD_STATUS_REJECTION = id("status_rejection");
    public static final ResourceLocation WORLD_SHATTERING_WARD = id("shattering_ward");

    public static final ResourceLocation GUI_ANVIL_COMPRESSION = id("anvil_compression");
    public static final ResourceLocation GUI_GRINDSTONE_SWEEP = id("grindstone_sweep");
    public static final ResourceLocation GUI_ENCHANTING_GLYPH = id("enchanting_glyph");
    public static final ResourceLocation GUI_TRADE_ACKNOWLEDGE = id("trade_acknowledge");
    public static final ResourceLocation GUI_PROCESSOR_COMPLETE = id("processor_complete");

    private TransientVisualIds() { }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}
