package com.mistaboom.essence_ascendance.client.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Small deterministic focus model. Hosts remain responsible for translating
 * the selected key to their native widget focus API.
 */
public final class UiFocusController<T> {
    private List<T> order = List.of();
    private T focused;

    public void setOrder(List<T> focusOrder) {
        order = List.copyOf(focusOrder);
        if (focused != null && !order.contains(focused)) {
            focused = null;
        }
    }

    public Optional<T> focused() {
        return Optional.ofNullable(focused);
    }

    public Optional<T> focus(T key) {
        focused = key != null && order.contains(key) ? key : null;
        return focused();
    }

    public Optional<T> move(int direction) {
        if (order.isEmpty()) {
            focused = null;
            return Optional.empty();
        }
        if (direction == 0) {
            return focused();
        }
        int step = direction < 0 ? -1 : 1;
        int current = focused == null ? (step > 0 ? -1 : 0) : order.indexOf(focused);
        focused = order.get(Math.floorMod(current + step, order.size()));
        return Optional.of(focused);
    }

    public void clear() {
        focused = null;
    }

    public boolean isFocused(T key) {
        return Objects.equals(focused, key);
    }
}
