package com.mistaboom.essence_ascendance.verification;

import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
//? if >=26.1.2
/*import net.minecraft.core.HolderSet;*/

import java.util.List;

/** Constructs native tool components from the same fixture rules on both versions. */
public final class ToolTestCompat {
    private ToolTestCompat() { }

    public static Tool create(List<Tool.Rule> rules, float defaultSpeed, int damagePerBlock) {
        //? if >=26.1.2
        /*return new Tool(rules, defaultSpeed, damagePerBlock, false);*/
        //? if <26.1.2
        return new Tool(rules, defaultSpeed, damagePerBlock);
    }

    public static Tool.Rule overrideSpeed(List<Block> blocks, float speed) {
        //? if >=26.1.2
        /*return Tool.Rule.overrideSpeed(HolderSet.direct(blocks.stream().map(Block::builtInRegistryHolder).toList()), speed);*/
        //? if <26.1.2
        return Tool.Rule.overrideSpeed(blocks, speed);
    }
}
