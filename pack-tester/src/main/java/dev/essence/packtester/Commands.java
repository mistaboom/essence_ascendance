package dev.essence.packtester;

import java.util.List;

/** Native argument vectors only. Embedded quotes are not legal Windows path characters. */
final class Commands {
    static List<String> checked(List<String> args) {
        for (String arg : args) {
            if (arg.indexOf('\0') >= 0 || arg.indexOf('\n') >= 0 || arg.indexOf('\r') >= 0
                    || System.getProperty("os.name").startsWith("Windows") && arg.indexOf('"') >= 0)
                throw new IllegalArgumentException("Invalid native process argument; do not embed shell quotes in paths");
        }
        return List.copyOf(args);
    }
}
