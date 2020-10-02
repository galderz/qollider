package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;
import org.mendrugo.qollider.Qollider.Roots;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

public final class Jdk
{
    static final Version JDK_11 = new Version(11, 0, 7, 10);
    static final Version JDK_14 = new Version(14, 0, 2, 12);

    final Effect.Exec.Lazy lazy;
    final Effect.Install install;
    final Effect.Linking linking;
    final Roots roots;

    Jdk(Effect.Exec.Lazy lazy, Effect.Install install, Effect.Linking linking, Roots roots) {
        this.lazy = lazy;
        this.install = install;
        this.linking = linking;
        this.roots = roots;
    }

    public Action build(Build build)
    {
        final var cloneAction = clone(build.tree());

        final var buildSteps = switch (build.javaType())
        {
            case OPENJDK -> new OpenJDK(roots).buildSteps(build);
            case LABSJDK -> new LabsJDK(roots).buildSteps(build);
        };

        final var buildAction =
            buildSteps.stream()
                .map(t -> Step.Exec.Lazy.action(t, lazy))
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
        return Step.Linking.link(new Step.Linking(link, target), linking);
    }

    private Action clone(Repository tree)
    {
        return switch (tree.type())
        {
            case GIT -> new Git(lazy).clone(tree);
            case MERCURIAL -> new Mercurial(lazy).clone(tree);
        };
    }

    public static Get get(String url)
    {
        return Get.of(url, "jdk");
    }

    public Action get(Get get)
    {
        final var installAction =
            Job.Install.install(new Job.Install(get.url(), get.path()), install);

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.java(), installJdkHome(get.path(), install))
            , linking
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
            new Step.Linking(Homes.bootJdk(), installJdkHome(boot.path, install))
            , linking
        );

        return Action.of(installAction, linkAction);
    }

    private Action installBoot(Boot boot)
    {
        final var javaBaseUrl = format(
            "https://github.com/AdoptOpenJDK/openjdk%d-binaries/releases/download"
            , boot.version().major()
        );

        final var osType = install.download().osType().get();
        final var javaOsType = osType.isMac() ? "mac" : osType.toString();
        final var arch = install.download().arch().get().toString();

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

        return Job.Install.install(new Job.Install(url, boot.path), install);
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
        final Roots roots;

        private OpenJDK(Roots roots)
        {
            this.roots = roots;
        }

        List<Step.Exec> buildSteps(Build build)
        {
            return List.of(configure(build, roots), make(build));
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

        private static Step.Exec configure(Build build, Roots roots)
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
                , format("--with-boot-jdk=%s", roots.home().apply(Homes.bootJdk()))
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
        final Roots roots;

        private LabsJDK(Roots roots)
        {
            this.roots = roots;
        }

        List<Step.Exec> buildSteps(Build build)
        {
            return List.of(buildJDK(build, roots));
        }

        static Path javaHome(Path jdk)
        {
            return jdk.resolve("java_home");
        }

        private static Step.Exec buildJDK(Build build, Roots roots)
        {
            return Step.Exec.of(
                Path.of(build.tree.name())
                , "python"
                , "build_labsjdk.py"
                , "--boot-jdk"
                , roots.home().apply(Homes.bootJdk()).toString()
            );
        }
    }
}
