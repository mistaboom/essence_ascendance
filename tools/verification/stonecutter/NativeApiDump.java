import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

class NativeApiDump {
    static final Map<String,Object> classes = new TreeMap<>();
    static void read(String name) throws Exception {
        if (classes.containsKey(name)) return;
        try (var stream = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (stream == null) { classes.put(name, Map.of("missing", true)); return; }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES);
            var methods = new ArrayList<Object>();
            for (var method : node.methods) {
                var calls = new ArrayList<String>();
                var fields = new ArrayList<String>();
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode m) calls.add("L" + m.owner + ";" + m.name + m.desc);
                    if (instruction instanceof FieldInsnNode f) fields.add("L" + f.owner + ";" + f.name + ":" + f.desc);
                }
                var locals = new ArrayList<Object>();
                if (method.localVariables != null) for (var local : method.localVariables)
                    locals.add(Map.of("name", local.name, "desc", local.desc, "index", local.index));
                var fieldOps = new ArrayList<Object>();
                for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode f)
                    fieldOps.add(Map.of("target", "L" + f.owner + ";" + f.name + ":" + f.desc, "opcode", f.getOpcode()));
                methods.add(Map.of("name", method.name, "desc", method.desc, "access", method.access, "calls", calls, "fields", fields, "locals", locals, "fieldOps", fieldOps));
            }
            var fields = new ArrayList<Object>();
            for (var field : node.fields) fields.add(Map.of("name", field.name, "desc", field.desc, "access", field.access));
            var data = new LinkedHashMap<String,Object>();
            data.put("super", node.superName); data.put("interfaces", node.interfaces);
            data.put("methods", methods); data.put("fields", fields);
            classes.put(name, data);
            if (node.superName != null) read(node.superName);
            for (var face : node.interfaces) read(face);
        }
    }
    public static void main(String[] args) throws Exception {
        for (var name : Files.readAllLines(Path.of(args[0]))) if (!name.isBlank()) read(name);
        Files.writeString(Path.of(args[1]), new Gson().toJson(classes));
    }
}
