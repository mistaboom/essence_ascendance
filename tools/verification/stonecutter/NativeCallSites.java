import java.util.jar.*;
import org.objectweb.asm.*;
public class NativeCallSites {
 public static void main(String[] args) throws Exception {
  try (var jar = new JarFile(args[0])) {
   for (var e : jar.stream().filter(x -> x.getName().endsWith(".class") && x.getName().startsWith("net/minecraft/")).toList()) {
    new ClassReader(jar.getInputStream(e)).accept(new ClassVisitor(Opcodes.ASM9) {
     public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex) {
      return new MethodVisitor(Opcodes.ASM9) {
       public void visitMethodInsn(int opcode,String owner,String target,String descriptor,boolean itf) {
        if ((owner+";"+target).matches(args[1])) System.out.println(e.getName()+"::"+name+desc+" -> "+owner+";"+target+descriptor);
       }
      };
     }
    },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
   }
  }
 }
}
