package org.mendrugo.qollider;

import org.mendrugo.qollider.OperatingSystem.EnvVar;
import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Output;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

interface Step
{
    Path root();

    record Download(URL url, Path path, Path root) implements Step
    {
        @Override
        public String toString()
        {
            return String.format("%s$ wget %s -O %s%n", showPath(root), url, path);
        }

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

    record Exec(List<String> args, Path directory, List<EnvVar> envVars, Path root) implements Step
    {
        @Override
        public String toString()
        {
            return String.format(
                "%s%s"
                , showPath(root, directory)
                , showCommand(args, envVars)
            );
        }

        private static String showCommand(List<String> args, List<EnvVar> envVars)
        {
            final var envAndArgs = Lists.concat(
                envVars.stream().map(Object::toString).collect(Collectors.toList())
                , args
            );

            return String.format(
                "$ %s%n"
                , String.join(" ", envAndArgs)
            );
        }

        static Exec of(Path root, Path path, List<EnvVar> envVars, String... args)
        {
            return of(root, path, envVars, List.of(args));
        }

        static Exec of(Path root, Path path, List<EnvVar> envVars, List<String> args)
        {
            return new Exec(args, path, envVars, root);
        }

        static Exec of(Path root, Path path, String... args)
        {
            return of(root, path, List.of(), args);
        }

        static Exec of(Path root, String... args)
        {
            return of(root, Path.of(""), args);
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
                static Effects of()
                {
                    return new Effects(OperatingSystem::exec);
                }
            }
        }
    }

    record Extract(Path tar, Path path, Path root) implements Step
    {
        static Action action(Step.Extract extract, Effect.Extract effects)
        {
            return Action.of(() ->
            {
                effects.mkdirs().accept(extract.root.resolve(extract.path)); // cheap so do it regardless, no marker

                return Exec.Lazy.action(
                    Exec.of(
                        extract.root
                        , "tar"
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

    record Linking(Path link, Path target, Path root) implements Step
    {
        static Action link(Step.Linking linking, Effect.Linking effects)
        {
            return Action.of(() ->
            {
                final var link = linking.root.resolve(linking.link);
                return effects.symLink().apply(link, linking.target);
            });
        }
    }

    private static String showPath(Path... paths)
    {
        return String.format(
            "\uF07C %s%n"
            , Stream.of(paths)
                .map(Object::toString)
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.joining("/"))
        );
    }
}
