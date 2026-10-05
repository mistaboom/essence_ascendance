package com.mistaboom.essence_ascendance.balance.generated;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Repairs the audited CDP runtime pack's concurrent data-provider writes before publication. */
public final class StableRuntimePackResources {
    public static final String CDP_VERSION = "1.11.9";
    public static final String OWNER = "plus.dragons.createdragonsplus.data.runtime.RuntimePackResources";
    private static final Logger LOGGER = LogUtils.getLogger();

    private StableRuntimePackResources() { }

    /** Called only at constructor return, before CDP starts or publishes any data providers. */
    public static boolean prepare(Object pack, String cdpVersion) {
        if (!CDP_VERSION.equals(cdpVersion) || pack == null || !pack.getClass().getName().equals(OWNER)) return false;
        try {
            var field = pack.getClass().getDeclaredField("resources");
            int modifiers = field.getModifiers();
            if (field.getType() != Map.class || !Modifier.isPrivate(modifiers) || !Modifier.isFinal(modifiers)
                    || Modifier.isStatic(modifiers) || !field.trySetAccessible()) return false;
            Object resources = field.get(pack);
            if (!(resources instanceof Map<?, ?> map) || resources.getClass() != HashMap.class) return false;
            // Keep the original entries, null contract, and HashMap iteration behavior. In particular,
            // CDP can store a nullable logo supplier during construction. All later writers use this
            // field, and readers traverse its keys only after addDataProvider has joined every save.
            field.set(pack, synchronizeWrites(map));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // An optional integration must not prevent launch; leave native resource behavior intact.
            LOGGER.warn("Could not synchronize Create: Dragons Plus {} runtime resources", cdpVersion, failure);
            return false;
        }
    }

    static <K, V> Map<K, V> synchronizeWrites(Map<K, V> resources) {
        return Collections.synchronizedMap(resources);
    }
}
