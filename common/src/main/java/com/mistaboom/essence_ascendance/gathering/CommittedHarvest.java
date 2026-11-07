package com.mistaboom.essence_ascendance.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayDeque;

/** Synchronous native removal proof. The outer destroyBlock return is not a successful-removal receipt. */
public final class CommittedHarvest {
    private static final ThreadLocal<ArrayDeque<Scope>> SCOPES = new ThreadLocal<>();
    private CommittedHarvest() { }
    public static Scope begin(ServerLevel level,BlockPos position,BlockState source) {
        var scopes=SCOPES.get();if(scopes==null){scopes=new ArrayDeque<>();SCOPES.set(scopes);}
        var scope=new Scope(level,position.immutable(),source);scopes.push(scope);return scope;
    }
    /** Observe the exact native boolean before later destroy/replant callbacks run. */
    public static void removed(ServerLevel level,BlockPos position,BlockState before,boolean removed) {
        var scopes=SCOPES.get();var scope=scopes==null?null:scopes.peek();
        if(scope!=null&&scope.level==level&&scope.position.equals(position)&&scope.source==before&&!before.isAir()
                &&removed&&level.getBlockState(position)!=before)scope.removed=true;
    }
    public static final class Scope implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos position;
        private final BlockState source;
        private boolean removed,closed;
        private Scope(ServerLevel level,BlockPos position,BlockState source){this.level=level;this.position=position;this.source=source;}
        public boolean committed(){return removed;}
        @Override public void close(){
            if(closed)return;
            var scopes=SCOPES.get();if(scopes==null||scopes.peek()!=this)throw new IllegalStateException("Harvest scopes closed out of order");
            scopes.pop();if(scopes.isEmpty())SCOPES.remove();closed=true;
        }
    }
}
