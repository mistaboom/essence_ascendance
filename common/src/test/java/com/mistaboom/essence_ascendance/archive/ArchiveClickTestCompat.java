package com.mistaboom.essence_ascendance.archive;

import net.minecraft.network.chat.ClickEvent;

/** Reads the authored Archive link payload from each native click-event form. */
final class ArchiveClickTestCompat {
    private ArchiveClickTestCompat() { }

    static String target(ClickEvent click) {
        //? if >=26.1.2
        /*return click instanceof ClickEvent.Custom custom && custom.payload().orElse(null) instanceof net.minecraft.nbt.StringTag tag ? tag.value() : null;*/
        //? if <26.1.2
        return click.getValue();
    }
}
