package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Link;

import java.io.File;
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
        static Effect.Download of()
        {
            return new Download(FileTree::exists, FileTree::touch, Web::download, OperatingSystem::type, Hardware::arch);
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
            static Effect.Exec.Lazy of()
            {
                return new Lazy(FileTree::exists, OperatingSystem::exec, FileTree::touch);
            }
        }
    }

    record Extract(
        Effect.Exec.Lazy lazy
        , Consumer<Path> mkdirs
    ) implements Effect
    {
        static Effect.Extract of()
        {
            final var exec = Effect.Exec.Lazy.of();
            return new Extract(exec, FileTree::mkdirs);
        }
    }

    record Install(Effect.Download download, Effect.Extract extract)
    {
        static Effect.Install of()
        {
            final var download = Effect.Download.of();
            final var extract = Effect.Extract.of();
            return new Install(download, extract);
        }
    }

    record Linking(BiFunction<Path, Path, Link> symLink) implements Effect {}
}
