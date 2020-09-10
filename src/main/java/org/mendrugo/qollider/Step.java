package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Output;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

interface Step
{
    record Exec(
        List<String> args
        , Path directory
        , List<OperatingSystem.EnvVar> envVars
    ) implements Step
    {
        @Override
        public String toString()
        {
            if (Path.of("").equals(directory) && envVars.isEmpty())
            {
                return String.format(
                    "$ %s"
                    , String.join(" ", args)
                );
            }
            else if (envVars.isEmpty())
            {
                return String.format(
                    "%s $ %s"
                    , directory
                    , String.join(" ", args)
                );
            }
            else
            {
                return String.format(
                    "%s $ %s %s"
                    , directory
                    , envVars.stream().map(Objects::toString).collect(Collectors.joining(" "))
                    , String.join(" ", args)
                );
            }
        }

        static Exec of(Path path, List<OperatingSystem.EnvVar> envVars, String... args)
        {
            return new Exec(List.of(args), path, envVars);
        }

        static Exec of(Path path, String... args)
        {
            return new Exec(List.of(args), path, List.of());
        }

        static Exec of(String... args)
        {
            return new Exec(Arrays.asList(args), Path.of(""), emptyList());
        }

        final static class Lazy
        {
            static Marker run(Exec exec, Effects effects)
            {
                final var marker = Marker.of(exec).query(effects.exists);
                if (marker.exists())
                    return marker;

                effects.exec().accept(exec);
                return marker.touch(effects.touch);
            }

            static BiFunction<Exec, Effects, ? extends Output> action()
            {
                return (exec, effects) ->
                {
                    final var marker = Marker.of(exec).query(effects.exists);
                    if (marker.exists())
                        return marker;

                    effects.exec().accept(exec);
                    return marker.touch(effects.touch);
                };
            }

            record Effects(
                Predicate<Path> exists
                , Consumer<Exec> exec
                , Function<Marker, Boolean> touch
            )
            {
                static Effects of(OperatingSystem os)
                {
                    return new Effects(os.fs::exists, os::exec, os.fs::touch);
                }
            }
        }

        record Eager(Step.Exec exec) implements Output
        {
            static Eager run(Exec exec, Effects effects)
            {
                effects.exec().accept(exec);
                return new Eager(exec);
            }

            record Effects(Consumer<Exec> exec)
            {
                static Effects of(OperatingSystem os)
                {
                    return new Effects(os::exec);
                }
            }
        }
    }

//    record Extract(Path tar, Path path) implements Step
//    {
//        static Marker extract(Extract extract, Extract.Effects effects)
//        {
//            effects.mkdirs.accept(extract.path); // cheap so do it regardless, no marker
//
//            return Exec.Lazy.run(
//                Exec.of(
//                    "tar"
//                    , "-xzpf"
//                    , extract.tar.toString()
//                    , "-C"
//                    , extract.path.toString()
//                    , "--strip-components"
//                    , "1"
//                )
//                , effects.exec
//            );
//        }
//        record Effects(
//            Exec.Lazy.Effects exec
//            , Consumer<Path> mkdirs
//        )
//        {
//            static Effects of(OperatingSystem os)
//            {
//                final var exec = Exec.Lazy.Effects.of(os);
//                return new Effects(exec, os.fs::mkdirs);
//            }
//
//        }
//
//    }

//    record Download(URL url, Path path) implements Step
//    {
//        static Marker lazy(Download task, Effects effects)
//        {
//            final var marker = Marker.of(task).query(effects.exists);
//            if (marker.exists())
//                return marker;
//
//            effects.download.accept(task);
//            return marker.touch(effects.touch);
//        }
//        record Effects(
//            Predicate<Path> exists
//            , Function<Marker, Boolean> touch
//            , Consumer<Download> download
//            , Supplier<OperatingSystem.Type> osType
//            , Supplier<Hardware.Arch> arch
//        )
//        {
//            static Effects of(Web web, OperatingSystem os, Hardware hw)
//            {
//                return new Effects(os.fs::exists, os.fs::touch, web::download, os::type, hw::arch);
//            }
//        }
//
//    }

//    record Linking(Path link, Path target) implements Step
//    {
//        static Link link(Linking linking, Effects effects)
//        {
//            final var target = linking.target;
//            return effects.symLink.apply(linking.link, target);
//        }
//
//        record Effects(BiFunction<Path, Path, Link> symLink) {}
//    }
}
