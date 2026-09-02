package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.world.item.Item;

/** Marker item for data-bearing Essentium carriers. */
public final class EssentiumItem extends Item {

    public enum CarrierForm {
        INGOT(1L),
        BLOCK(9L);

        private final long capacityMultiplier;

        CarrierForm(long capacityMultiplier) {
            this.capacityMultiplier = capacityMultiplier;
        }

        public long capacityMultiplier() {
            return capacityMultiplier;
        }
    }

    private final CarrierForm form;

    public EssentiumItem(CarrierForm form, Properties properties) {
        super(properties);
        this.form = form;
    }

    public CarrierForm form() {
        return form;
    }
}
