package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Link;

import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface Effect
{
    record Download(
        Predicate<Path> exists
        , Function<Marker, Boolean> touch
        , Consumer<Step.Download> download
        , Supplier<OperatingSystem.Type> osType
        , Supplier<Hardware.Arch> arch
    )
    {
        static Effect.Download of(Web web, OperatingSystem os, Hardware hw)
        {
            return new Download(os.fs::exists, os.fs::touch, web::download, os::type, hw::arch);
        }
    }

    // TODO Remove Exec.Lazy and just name it as ExecLazy
    final class Exec
    {
        record Lazy(
            Predicate<Path> exists
            , Consumer<Step.Exec> exec
            , Function<Marker, Boolean> touch
        ) implements Effect
        {
            static Effect.Exec.Lazy of(OperatingSystem os)
            {
                return new Lazy(os.fs::exists, os::exec, os.fs::touch);
            }
        }
    }

    record Extract(
        Effect.Exec.Lazy lazy
        , Consumer<Path> mkdirs
    ) implements Effect
    {
        static Effect.Extract of(OperatingSystem os)
        {
            final var exec = Effect.Exec.Lazy.of(os);
            return new Extract(exec, os.fs::mkdirs);
        }
    }

    record Install(Effect.Download download, Effect.Extract extract)
    {
        // TODO avoid the need to keep you installs (today and home)
        // Instead pass in the path (today or home) and build Web and OS based on that
        static Effect.Install of(Web web, OperatingSystem os)
        {
            final var hw = new Hardware();
            final var download = Effect.Download.of(web, os, hw);
            final var extract = Effect.Extract.of(os);
            return new Install(download, extract);
        }
    }

    record Linking(BiFunction<Path, Path, Link> symLink) implements Effect {}
}
