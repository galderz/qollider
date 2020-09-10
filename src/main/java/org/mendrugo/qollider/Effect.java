package org.mendrugo.qollider;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Effect
{
    final static class Exec
    {
        record Lazy(
            Predicate<Path> exists
            , Consumer<Step.Exec> exec
            , Function<Marker, Boolean> touch
        ) implements Effect
        {
            static Lazy of(OperatingSystem os)
            {
                return new Lazy(os.fs::exists, os::exec, os.fs::touch);
            }
        }
    }
}
