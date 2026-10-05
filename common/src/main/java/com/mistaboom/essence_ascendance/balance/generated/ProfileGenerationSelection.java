package com.mistaboom.essence_ascendance.balance.generated;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The sole lifecycle decision: presence and an explicit rebuild request. No external inputs. */
final class ProfileGenerationSelection {
    private ProfileGenerationSelection() { }
    @FunctionalInterface interface Source<T> { T get() throws IOException; }
    static boolean generationRequired(Path profile, boolean rebuild) { return rebuild || !Files.exists(profile); }
    static <T> T select(boolean generate, Source<T> saved, Source<T> generation) throws IOException {
        return generate ? generation.get() : saved.get();
    }
}
