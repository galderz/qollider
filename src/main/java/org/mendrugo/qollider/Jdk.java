package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

public final class Jdk
{
    static final Version JDK_11 = new Version(11, 0, 7, 10);
    static final Version JDK_14 = new Version(14, 0, 2, 12);

    final Effects home;
    final Effects today;

    public Jdk(Effects home, Effects today) {
        this.home = home;
        this.today = today;
    }

    public Action build(Build build)
    {
        final var cloneAction = clone(build.tree());

        final var buildSteps = switch (build.javaType())
        {
            case OPENJDK -> OpenJDK.buildSteps(build, home.root());
            case LABSJDK -> LabsJDK.buildSteps(build, home.root());
        };

        final var buildAction =
            buildSteps.stream()
                .map(t -> Step.Exec.Lazy.action(t, today.lazy()))
                .collect(Collectors.toList());

        final var linkAction = link(build);

        return Action.of(Lists.merge(cloneAction, buildAction, linkAction));
    }

    private Action link(Build build)
    {
        final var jdkPath = Path.of(build.tree.name());
        final var target = switch (build.javaType())
            {
                case OPENJDK -> OpenJDK.javaHome(jdkPath);
                case LABSJDK -> LabsJDK.javaHome(jdkPath);
            };

        final var link = Homes.java();
        return Step.Linking.link(new Step.Linking(link, target), today.linking());
    }

    private Action clone(Repository tree)
    {
        return switch (tree.type())
        {
            case GIT -> new Git(today.lazy()).clone(tree);
            case MERCURIAL -> new Mercurial(today.lazy()).clone(tree);
        };
    }

    public static Get get(String url)
    {
        return Get.of(url, "jdk");
    }

    public Action get(Get get)
    {
        final var installAction =
            Job.Install.install(new Job.Install(get.url(), get.path()), today.install());

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.java(), installJdkHome(get.path(), today.install()))
            , today.linking()
        );

        return Action.of(installAction, linkAction);
    }

    public Action getBoot(Build build)
    {
        final var boot =
            "jdk".equals(build.tree.name())
                ? new Boot(Jdk.JDK_14, Path.of("boot-jdk-14"))
                : new Boot(Jdk.JDK_11, Path.of("boot-jdk-11"));

        final var installAction = installBoot(boot);

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.bootJdk(), installJdkHome(boot.path, home.install()))
            , home.linking()
        );

        return Action.of(installAction, linkAction);
    }

    private Action installBoot(Boot boot)
    {
        final var javaBaseUrl = format(
            "https://github.com/AdoptOpenJDK/openjdk%d-binaries/releases/download"
            , boot.version().major()
        );

        final var osType = home.install().download().osType().get();
        final var javaOsType = osType.isMac() ? "mac" : osType.toString();
        final var arch = home.install().download().arch().get().toString();

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

        return Job.Install.install(new Job.Install(url, boot.path), home.install());
    }

    private static Path installJdkHome(Path path, Effect.Install effects)
    {
        return effects.download().osType().get().isMac()
            ? path.resolve(Path.of("Contents", "Home"))
            : path;
    }

    public record Build(Repository tree)
    {
        Type javaType()
        {
            return type(tree);
        }

        private static Jdk.Type type(Repository repo)
        {
            return repo.name().startsWith("labs") ? Type.LABSJDK : Type.OPENJDK;
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
        static List<Step.Exec> buildSteps(Build build, Path homeRoot)
        {
            return List.of(configure(build, homeRoot), make(build));
        }

        static Path javaHome(Path jdk)
        {
            return jdk.resolve(
                Path.of(
                    "build"
                    , "graal-server-release"
                    , "images"
                    , "graal-builder-jdk"
                )
            );
        }

        private static Step.Exec configure(Build build, Path home)
        {
            return Step.Exec.of(
                Path.of(build.tree.name())
                , "bash"
                , "configure"
                , "--with-conf-name=graal-server-release"
                , "--disable-warnings-as-errors"
                , "--with-jvm-features=graal"
                , "--with-jvm-variants=server"
                // Workaround for https://bugs.openjdk.java.net/browse/JDK-8235903 on newer GCC versions
                , "--with-extra-cflags=-fcommon"
                , "--enable-aot=no"
                , format("--with-boot-jdk=%s", home.resolve(Homes.bootJdk()))
            );
        }

        private static Step.Exec make(Build build)
        {
            return Step.Exec.of(
                Path.of(build.tree.name())
                , "make"
                , "graal-builder-image"
            );
        }
    }

    private static final class LabsJDK
    {
        static List<Step.Exec> buildSteps(Build build, Path home)
        {
            return List.of(buildJDK(build, home));
        }

        static Path javaHome(Path jdk)
        {
            return jdk.resolve("java_home");
        }

        private static Step.Exec buildJDK(Build build, Path home)
        {
            return Step.Exec.of(
                Path.of(build.tree.name())
                , "python"
                , "build_labsjdk.py"
                , "--boot-jdk"
                , home.resolve(Homes.bootJdk()).toString()
            );
        }
    }
}
