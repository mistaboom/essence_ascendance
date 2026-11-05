package com.mistaboom.essence_ascendance.client.audio;

import java.util.function.BiConsumer;

/** Custom controls acknowledge one accepted activation; native widgets retain their native click. */
public final class AudioUi {
    private static BiConsumer<String,Object> playback=(semantic,control)->{ };
    private AudioUi() { }
    public static void install(BiConsumer<String,Object> sink) { playback=java.util.Objects.requireNonNull(sink); }
    public static void press(Object screen,String control) { playback.accept("ui.press",new Control(screen,control)); }
    private record Control(Object screen,String id) { }
}
