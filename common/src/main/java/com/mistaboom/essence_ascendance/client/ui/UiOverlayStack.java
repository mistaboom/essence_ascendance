package com.mistaboom.essence_ascendance.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves overlay ownership once for rendering, pointer dispatch, keyboard
 * routing and tooltip suppression. Higher z-order layers win.
 */
public final class UiOverlayStack {
    public enum PointerPolicy {
        /** Capture pointer events only within the layer bounds. */
        CAPTURE_BOUNDS,
        /** Capture every pointer event while this layer is present. */
        MODAL
    }

    public enum TooltipPolicy {
        ALLOW,
        SUPPRESS_BOUNDS,
        SUPPRESS_ALL
    }

    public record Layer(
            String id,
            UiBounds bounds,
            int zOrder,
            PointerPolicy pointerPolicy,
            TooltipPolicy tooltipPolicy,
            boolean ownsKeyboard
    ) {
        public Layer {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Overlay id is required");
            }
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(pointerPolicy, "pointerPolicy");
            Objects.requireNonNull(tooltipPolicy, "tooltipPolicy");
        }
    }

    private final List<Layer> ascending;

    public UiOverlayStack(List<Layer> layers) {
        List<Layer> copy = new ArrayList<>(layers);
        copy.sort(Comparator.comparingInt(Layer::zOrder));
        var ids = new HashSet<String>();
        for (Layer layer : copy) {
            if (!ids.add(layer.id())) {
                throw new IllegalArgumentException("Duplicate overlay id: " + layer.id());
            }
        }
        ascending = List.copyOf(copy);
    }

    public List<Layer> renderingOrder() {
        return ascending;
    }

    public Optional<Layer> pointerOwner(double mouseX, double mouseY) {
        for (int index = ascending.size() - 1; index >= 0; index--) {
            Layer layer = ascending.get(index);
            if (layer.pointerPolicy() == PointerPolicy.MODAL
                    || layer.bounds().contains(mouseX, mouseY)) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    public Optional<Layer> keyboardOwner() {
        for (int index = ascending.size() - 1; index >= 0; index--) {
            if (ascending.get(index).ownsKeyboard()) {
                return Optional.of(ascending.get(index));
            }
        }
        return Optional.empty();
    }

    public boolean allowsTooltip(double mouseX, double mouseY) {
        for (int index = ascending.size() - 1; index >= 0; index--) {
            Layer layer = ascending.get(index);
            if (layer.tooltipPolicy() == TooltipPolicy.SUPPRESS_ALL) {
                return false;
            }
            if (layer.tooltipPolicy() == TooltipPolicy.SUPPRESS_BOUNDS
                    && layer.bounds().contains(mouseX, mouseY)) {
                return false;
            }
        }
        return true;
    }
}
