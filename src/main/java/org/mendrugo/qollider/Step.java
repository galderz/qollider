package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Output;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

interface Step
{
    record Download(URL url, Path path) implements Step
    {
        static Action action(Step.Download download, Effect.Download effects)
        {
            return Action.of(() ->
            {
                final var marker = Marker.of(download).query(effects.exists());
                if (marker.exists())
                    return marker;

                effects.download().accept(download);
                return marker.touch(effects.touch());
            });
        }
    }

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
            static Action action(Step.Exec exec, Effect.Exec.Lazy effects)
            {
                return Action.of(() ->
                {
                    final var marker = Marker.of(exec).query(effects.exists());
                    if (marker.exists())
                        return marker;

                    effects.exec().accept(exec);
                    return marker.touch(effects.touch());
                });
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

    record Extract(Path tar, Path path) implements Step
    {
        static Action action(Step.Extract extract, Effect.Extract effects)
        {
            return Action.of(() ->
            {
                effects.mkdirs().accept(extract.path); // cheap so do it regardless, no marker

                return Exec.Lazy.action(
                    Exec.of(
                        "tar"
                        , "-xzpf"
                        , extract.tar.toString()
                        , "-C"
                        , extract.path.toString()
                        , "--strip-components"
                        , "1"
                    )
                    , effects.lazy()
                ).items().get(0).get();
            });
        }
    }

    record Linking(Path link, Path target) implements Step
    {
        static Action link(Step.Linking linking, Effect.Linking effects)
        {
            return Action.of(() ->
            {
                final var target = linking.target;
                return effects.symLink().apply(linking.link, target);
            });
        }
    }
}
