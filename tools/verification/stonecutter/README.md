# 26.1.2 port verification

Run from the project root, with the Gradle JVM on Java 25. Production and native runtime gates remain in force.

## Full-source diagnostics

```powershell
.\gradlew.bat -I gradle/port-diagnostics.init.gradle :common:26.1.2:compileJava :neoforge:26.1.2:portAllSourceDiagnostics :fabric:26.1.2:portAllSourceDiagnostics --continue --console=plain --max-workers=2 *> build/port2612-prompt4-final-compile.log
python tools/verification/stonecutter/audit_mixin_targets.py neoforge
python tools/verification/stonecutter/audit_mixin_targets.py fabric
python tools/verification/stonecutter/write_compiler_ledger.py build/port2612-prompt4-final-compile.log '<handoff>/COMPILER-ERRORS-26.1.2.md'
```

The diagnostic tasks include **every** common and loader production Java source. They compile against each loader's resolved dependencies without consuming partial common class output. They are separate failing diagnostic tasks, not alternate production builds. `-proc:none` means that these tasks do not test annotation processing or Mixin injection. Normal common compilation retains its normal annotation processing.

`audit_mixin_targets.py` reads the current projected production sources, required common/server mixin lists, and the exact classpath/JDK receipts from those tasks. It reads native bytecode through ASM without initializing game classes. It checks target classes and method selectors, invocation/field anchors, handler argument prefixes, explicit enclosing arguments, shadows, and named accessors/invokers. It writes JSON receipts under `build/`. It does not prove transformation success, runtime ordering, local liveness, interaction between mixins, or gameplay behavior. Native fixtures remain required. Selected sensitive locals and field ordinals were also reviewed manually; see the handoff audit.

The ledger generator preserves every emitted diagnostic, including repeated locations and common diagnostics under different loader contexts. Unknown source responsibilities fail classification for manual review. This classification follows the reviewed presentation responsibilities; the script alone is not an architectural review.

## Isolated API invariants

```powershell
.\gradlew.bat -I gradle/port-diagnostics.init.gradle :neoforge:26.1.2:portTransactionJournalInvariants :neoforge:26.1.2:portFoodOriginCodecInvariants --console=plain
```

These compile and execute the actual production commit journal and provenance codec with their real 26.1.2 dependencies. The journal covers root/nested rollback and exactly-once commit flags; the codec covers native-schema compatibility, independent hidden effects, legacy root keys, invalid data, and the historical depth limit using JSON and NBT. They do not initialize Minecraft, start a server, or replace full compilation.

`MachineTransfer2612Test.java` additionally tests actual slot contents, capacity, component identity, permissions and nested transfers. The loader-initialized fixture is built with `-I gradle/transfer-native-2612.init.gradle :neoforge:26.1.2:transferNative2612FixtureArtifacts` and runs in a disposable packaged NeoForge server with its probe JAR and a fixture copy of the production JAR. `build/prompt5-transfer-native-2612-exact-console.log` records 16 passing assertions, registry-bound `ServerStartedEvent`, clean save/stop, and Java exit 0. The normal production JAR and mods folder were restored afterward. This early-stop fixture does not substitute for the separate packaged-server world session. The older standalone attempt remains a historical failure because no FML loader was active.

`NativeCallSites.java` and `native_query.py` are read-only investigation helpers. They take resolved artifact paths or generated audit receipts; do not substitute a guessed cache artifact.
