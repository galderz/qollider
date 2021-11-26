package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static java.lang.String.format;

public final class Jdk
{
    static final Version JDK_11 = new Version(11, 0, 13, 8);
    static final Version JDK_17 = new Version(17, 0, 1, 12);

    final Effects effects;
    final Path home;
    final Path today;

    public Jdk(Effects effects, Path home, Path today) {
        this.effects = effects;
        this.home = home;
        this.today = today;
    }

    public Action build(Build build)
    {
        final var getBootAction = getBoot(build);

        final var cloneAction = clone(build.tree());

        final var buildSteps = switch (build.javaType())
        {
            case OPENJDK -> OpenJDK.buildSteps(build, home, today);
            case LABSJDK -> LabsJDK.buildSteps(build, home, today);
        };

        final var buildAction =
            buildSteps.stream()
                .map(t -> Step.Exec.Lazy.action(t, effects.lazy()))
                .collect(Collectors.toList());

        final var linkAction = link(build);

        return Action.of(Lists.concat(List.of(getBootAction, cloneAction), buildAction, List.of(linkAction)));
    }

    private Action link(Build build)
    {
        final var jdkPath = Path.of(build.tree.name());
        final var target = switch (build.javaType())
        {
            case OPENJDK -> OpenJDK.javaHome(build, jdkPath);
            case LABSJDK -> LabsJDK.javaHome(jdkPath);
        };

        final var link = Homes.java();
        return Step.Linking.link(new Step.Linking(link, target, today), effects.linking());
    }

    private Action clone(Repository tree)
    {
        return switch (tree.type())
        {
            case GIT -> new Git(effects, today).clone(tree);
            case MERCURIAL -> new Mercurial(effects.lazy(), today).clone(tree);
        };
    }

    public static Get get(String url)
    {
        return Get.of(url, "jdk");
    }

    public Action get(Get get)
    {
        final var installAction =
            Job.Install.install(new Job.Install(get.url(), get.path(), today), effects.install());

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.java(), installJdkHome(get.path(), effects.install()), today)
            , effects.linking()
        );

        return Action.of(installAction, linkAction);
    }

    public Action getBoot(Build build)
    {
        final var boot = build.boot();

        final var installAction = installBoot(boot);

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.bootJdk(), installJdkHome(boot.path, effects.install()), home)
            , effects.linking()
        );

        return Action.of(installAction, linkAction);
    }

    private Action installBoot(Boot boot)
    {
        final var javaBaseUrl = format(
            "https://github.com/adoptium/temurin%d-binaries/releases/download"
            , boot.version().major()
        );

        final var osType = effects.install().download().osType().get();
        final var javaOsType = osType.isMac() ? "mac" : osType.toString();
        final var arch = effects.install().download().arch().get().toString();

        final var url = URLs.of(
            "%s/jdk-%s%%2B%d/OpenJDK%sU-jdk_%s_%s_hotspot_%s_%s.tar.gz"
            , javaBaseUrl
            , boot.version().majorMinorMicro()
            , boot.version().build()
            , boot.version().major()
            , arch
            , javaOsType
            , boot.version().majorMinorMicro()
            , boot.version().build()
        );

        return Job.Install.install(new Job.Install(url, boot.path, home), effects.install());
    }

    private static Path installJdkHome(Path path, Effect.Install effects)
    {
        return effects.download().osType().get().isMac()
            ? path.resolve(Path.of("Contents", "Home"))
            : path;
    }

    public record Build(Repository tree, DebugLevel debugLevel)
    {
        Type javaType()
        {
            return type(tree);
        }

        Jdk.Boot boot()
        {
            if (tree.name().contains("11"))
                return new Boot(Jdk.JDK_11, Path.of("boot-jdk-11"));

            return new Boot(Jdk.JDK_17, Path.of("boot-jdk-17"));
        }

        private static Jdk.Type type(Repository repo)
        {
            return repo.name().startsWith("labs") ? Type.LABSJDK : Type.OPENJDK;
        }
    }

    public enum DebugLevel
    {
        RELEASE
        , FASTDEBUG
        , SLOWDEBUG;

        @Override
        public String toString()
        {
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }

    record Boot(Jdk.Version version, Path path) {}

    enum Type
    {
        OPENJDK
        , LABSJDK

    }
    record Version(int major, int minor, int micro, int build)
    {
        String majorMinorMicro()
        {
            return String. format("%d.%d.%d", major, minor, micro);
        }
    }

    private static final class OpenJDK
    {
        static List<Step.Exec> buildSteps(Build build, Path home, Path today)
        {
            return List.of(configure(build, home, today), make(build, today));
        }

        static Path javaHome(Build build, Path jdk)
        {
            return jdk.resolve(
                Path.of(
                    "build"
                    , "graal-server-" + build.debugLevel
                    , "images"
                    , "graal-builder-jdk"
                )
            );
        }

        private static Step.Exec configure(Build build, Path home, Path today)
        {
            return Step.Exec.of(
                today
                , Path.of(build.tree.name())
                , "bash"
                , "configure"
                , "--with-conf-name=graal-server-" + build.debugLevel
                , "--disable-warnings-as-errors"
                , "--with-jvm-variants=server"
                // Workaround for https://bugs.openjdk.java.net/browse/JDK-8235903 on newer GCC versions
                , "--with-extra-cflags=-fcommon"
                , "--with-debug-level=" + build.debugLevel
                , format("--with-boot-jdk=%s", home.resolve(Homes.bootJdk()))
            );
        }

        private static Step.Exec make(Build build, Path today)
        {
            return Step.Exec.of(
                today
                , Path.of(build.tree.name())
                , "make"
                , "graal-builder-image"
            );
        }
    }

    private static final class LabsJDK
    {
        static List<Step.Exec> buildSteps(Build build, Path home, Path today)
        {
            return List.of(buildJDK(build, home, today));
        }

        static Path javaHome(Path jdk)
        {
            return jdk.resolve("java_home");
        }

        private static Step.Exec buildJDK(Build build, Path home, Path today)
        {
            return Step.Exec.of(
                today
                , Path.of(build.tree.name())
                , "python"
                , "build_labsjdk.py"
                , "--boot-jdk"
                , home.resolve(Homes.bootJdk()).toString()
                , "--configure-option=--disable-warnings-as-errors"
            );
        }
    }
}
