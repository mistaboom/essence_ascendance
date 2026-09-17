package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Marker item for data-bearing Essentium carriers. */
public final class EssentiumItem extends Item {

    public enum CarrierForm {
        NUGGET(1L, 9L),
        INGOT(1L, 1L),
        BLOCK(9L, 1L);

        private final long capacityNumerator;
        private final long capacityDenominator;

        CarrierForm(long capacityNumerator, long capacityDenominator) {
            this.capacityNumerator = capacityNumerator;
            this.capacityDenominator = capacityDenominator;
        }

        public long capacityNumerator() {
            return capacityNumerator;
        }

        public long capacityDenominator() {
            return capacityDenominator;
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (form != CarrierForm.BLOCK) {
            return super.useOn(context);
        }
        return EssentiumBlock.placeCarrier(context);
    }
}
