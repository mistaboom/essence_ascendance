package com.mistaboom.essence_ascendance.client.ui.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Immutable item-model description. Registration/model lookup happens only at draw time. */
public final class ItemPresentation {
    private final ResourceLocation resource;
    private final DataComponentPatch components;
    private final Component label;
    private final Component caption;

    public ItemPresentation(ResourceLocation resource, DataComponentPatch components, Component label) {
        this(resource, components, label, Component.empty());
    }

    public ItemPresentation(ResourceLocation resource, DataComponentPatch components, Component label, Component caption) {
        this.resource = Objects.requireNonNull(resource);
        this.components = Objects.requireNonNull(components);
        this.label = Objects.requireNonNull(label).copy();
        this.caption = Objects.requireNonNull(caption).copy();
    }

    public ResourceLocation resource() { return resource; }
    public DataComponentPatch components() { return components; }
    public ItemStack stack() {
        ItemStack stack = BuiltInRegistries.ITEM.get(resource).getDefaultInstance();
        if (!stack.isEmpty()) stack.applyComponents(components);
        return stack;
    }
    public Component label() { return label.copy(); }
    public Component caption() { return caption.copy(); }
    public ItemPresentation withCaption(Component caption) { return new ItemPresentation(resource, components, label, caption); }
}
