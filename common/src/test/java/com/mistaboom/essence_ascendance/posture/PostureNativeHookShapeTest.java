package com.mistaboom.essence_ascendance.posture;

import com.mistaboom.essence_ascendance.verification.TargetTestInputs;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.jar.JarFile;

/** Checks real mapped loader bytecode shapes; transformed execution is covered separately by native fixtures. */
public final class PostureNativeHookShapeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        inspect(name -> TargetTestInputs.nativeClass("fabric", name), false);
        inspect(name -> TargetTestInputs.nativeClass("neoforge", name), true);
        System.out.println("Posture native hook shapes passed: " + checks + " (exact target Fabric and NeoForge artifacts; no world)");
    }
    private interface Source {InputStream open(String name)throws Exception;}
    private static void inspect(Source source,boolean neo)throws Exception {
        //? if >=26.1.2
        /*boolean modern=true;*/
        //? if <26.1.2
        boolean modern=false;
        var living=read(source,"net/minecraft/world/entity/LivingEntity");
        String level=modern?"Lnet/minecraft/server/level/ServerLevel;":"";
        var hurt=method(living,modern?"hurtServer":"hurt","("+level+"Lnet/minecraft/world/damagesource/DamageSource;F)Z");
        String blocking=modern?"applyItemBlocking":"isDamageSourceBlocked";
        check(count(hurt,blocking)==1,"Exactly one native pre-shield dodge point");
        check(index(hurt,"isInvulnerableTo")<index(hurt,blocking),"Native invulnerability rejects before roll");
        check(index(hurt,"isDeadOrDying")<index(hurt,blocking),"Native death rejects before roll");
        if(modern) {
            var block=method(living,"applyItemBlocking","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F");
            check(count(hurt,"resolveBlockedDamage")==0&&count(hurt,"hurtBlockingItem")==0,
                    "Native hurtServer delegates shield resolution and durability until after the dodge boundary");
            check(count(block,"resolveBlockedDamage")==1&&count(block,"hurtBlockingItem")==1
                            &&index(block,"resolveBlockedDamage")<index(block,"hurtBlockingItem"),
                    "The sole native item-blocking boundary resolves blocked damage before its single durability write");
        } else check(index(hurt,"isDamageSourceBlocked")<index(hurt,"hurtCurrentlyUsedShield"),"No shield durability/block result exists before dodge");
        check(index(hurt,blocking)<index(hurt,"actuallyHurt"),"Dodge precedes native armor and health writes");
        check(count(method(living,"knockback","(DDD)V"),"setDeltaMovement")==1,"One native accepted knockback velocity write");
        var entity=read(source,"net/minecraft/world/entity/Entity");
        check(method(entity,"push","(DDD)V")!=null,"Native entity force has one shared signature");
        check(count(method(entity,"setDeltaMovement","(DDD)V"),"setDeltaMovement")==1,"Scalar velocity overload delegates once to monitored vector boundary");
        var connection=read(source,"net/minecraft/server/network/ServerGamePacketListenerImpl");
        check(method(connection,"teleport",modern?"(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V":"(DDDFFLjava/util/Set;)V")!=null,
                "Native corrections and teleports share lifecycle reset boundary");
        if(neo) {
            check(index(hurt,"onEntityIncomingDamage")<index(hurt,blocking),"NeoForge incoming event resolves reduction before dodge");
            check(living.fields.stream().filter(field->field.name.equals("damageContainers")&&field.desc.equals("Ljava/util/Stack;")).count()==1,
                    "Exact NeoForge transient damage-container stack available for early-abort cleanup");
            check(count(hurt,"push")==1&&count(hurt,"pop")>=1,"NeoForge native damage container brackets the hit");
        }
    }
    private static ClassNode read(Source source,String name)throws Exception {try(var in=source.open(name)){var node=new ClassNode();new ClassReader(in).accept(node,0);return node;}}
    private static MethodNode method(ClassNode node,String name,String desc){
        var found=node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();
        if(found.size()!=1)throw new AssertionError("Expected one exact native method "+node.name+"."+name+desc+", found "+found.size());
        return found.getFirst();
    }
    private static int count(MethodNode method,String call){int n=0;for(var insn:method.instructions)if(insn instanceof MethodInsnNode m&&m.name.equals(call))n++;return n;}
    private static int index(MethodNode method,String call){for(var insn:method.instructions)if(insn instanceof MethodInsnNode m&&m.name.equals(call))return method.instructions.indexOf(insn);return Integer.MAX_VALUE;}
    private static void check(boolean pass,String reason){checks++;if(!pass)throw new AssertionError(reason);}
}
