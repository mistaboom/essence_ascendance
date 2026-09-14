package com.mistaboom.essence_ascendance.posture;

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
        inspect(name->PostureNativeHookShapeTest.class.getClassLoader().getResourceAsStream(name+".class"),false);
        Path root=Path.of("").toAbsolutePath();
        while(root!=null&&!Files.isDirectory(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft")))root=root.getParent();
        if(root==null)throw new AssertionError("Mapped loader cache required");
        List<Path> jars;
        try(var paths=Files.walk(root.resolve(".gradle/loom-cache/minecraftMaven/net/minecraft"))) {
            jars=paths.filter(path->path.getFileName().toString().startsWith("neoforge-")&&path.toString().endsWith(".jar")&&!path.toString().endsWith("-sources.jar")).toList();
        }
        check(!jars.isEmpty(),"NeoForge native artifact exists");
        for(Path path:jars)try(JarFile jar=new JarFile(path.toFile())){inspect(name->jar.getInputStream(jar.getJarEntry(name+".class")),true);}
        System.out.println("PostureNativeHookShapeTest: "+checks+" exact common/Fabric and NeoForge bytecode checks passed");
    }
    private interface Source {InputStream open(String name)throws Exception;}
    private static void inspect(Source source,boolean neo)throws Exception {
        var living=read(source,"net/minecraft/world/entity/LivingEntity");
        var hurt=method(living,"hurt","(Lnet/minecraft/world/damagesource/DamageSource;F)Z");
        check(count(hurt,"isDamageSourceBlocked")==1,"Exactly one native pre-shield dodge point");
        check(index(hurt,"isInvulnerableTo")<index(hurt,"isDamageSourceBlocked"),"Native invulnerability rejects before roll");
        check(index(hurt,"isDeadOrDying")<index(hurt,"isDamageSourceBlocked"),"Native death rejects before roll");
        check(index(hurt,"isDamageSourceBlocked")<index(hurt,"hurtCurrentlyUsedShield"),"No shield durability/block result exists before dodge");
        check(index(hurt,"isDamageSourceBlocked")<index(hurt,"actuallyHurt"),"Dodge precedes native armor and health writes");
        check(count(method(living,"knockback","(DDD)V"),"setDeltaMovement")==1,"One native accepted knockback velocity write");
        var entity=read(source,"net/minecraft/world/entity/Entity");
        check(method(entity,"push","(DDD)V")!=null,"Native entity force has one shared signature");
        check(count(method(entity,"setDeltaMovement","(DDD)V"),"setDeltaMovement")==1,"Scalar velocity overload delegates once to monitored vector boundary");
        var connection=read(source,"net/minecraft/server/network/ServerGamePacketListenerImpl");
        check(method(connection,"teleport","(DDDFFLjava/util/Set;)V")!=null,"Native corrections and teleports share lifecycle reset boundary");
        if(neo) {
            check(index(hurt,"onEntityIncomingDamage")<index(hurt,"isDamageSourceBlocked"),"NeoForge incoming event resolves reduction before dodge");
            check(living.fields.stream().filter(field->field.name.equals("damageContainers")&&field.desc.equals("Ljava/util/Stack;")).count()==1,
                    "Exact NeoForge transient damage-container stack available for early-abort cleanup");
            check(count(hurt,"push")==1&&count(hurt,"pop")>=1,"NeoForge native damage container brackets the hit");
        }
    }
    private static ClassNode read(Source source,String name)throws Exception {try(var in=source.open(name)){var node=new ClassNode();new ClassReader(in).accept(node,0);return node;}}
    private static MethodNode method(ClassNode node,String name,String desc){return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static int count(MethodNode method,String call){int n=0;for(var insn:method.instructions)if(insn instanceof MethodInsnNode m&&m.name.equals(call))n++;return n;}
    private static int index(MethodNode method,String call){for(var insn:method.instructions)if(insn instanceof MethodInsnNode m&&m.name.equals(call))return method.instructions.indexOf(insn);return Integer.MAX_VALUE;}
    private static void check(boolean pass,String reason){checks++;if(!pass)throw new AssertionError(reason);}
}
