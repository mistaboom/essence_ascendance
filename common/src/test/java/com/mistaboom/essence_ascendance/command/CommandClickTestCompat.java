package com.mistaboom.essence_ascendance.command;

import net.minecraft.network.chat.ClickEvent;

/** Verifies suggested commands across the 26 click-event record change. */
final class CommandClickTestCompat {
    private CommandClickTestCompat() { }

    static boolean isSuggest(ClickEvent click) {
        //? if >=26.1.2
        /*return click instanceof ClickEvent.SuggestCommand;*/
        //? if <26.1.2
        return click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND;
    }

    static String value(ClickEvent click) {
        //? if >=26.1.2
        /*return click instanceof ClickEvent.SuggestCommand suggest ? suggest.command() : null;*/
        //? if <26.1.2
        return click == null ? null : click.getValue();
    }
}
