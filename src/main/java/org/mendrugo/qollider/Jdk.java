package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.net.URL;
import java.nio.file.Path;

import static java.lang.String.format;

public final class Jdk
{
    static final Version JDK_11 = new Version(11, 0, 7, 10);
    static final Version JDK_14 = new Version(14, 0, 2, 12);

    final Effect.Install install;
    final Effect.Linking linking;

    public Jdk(Effect.Install install, Effect.Linking linking) {
        this.install = install;
        this.linking = linking;
    }

    Action get(Get get)
    {
        final var installOut =
            Job.Install.install(new Job.Install(get.url, get.path), install);

        final var linkOut = Step.Linking.link(
            new Step.Linking(Homes.java(), installJdkHome(get.path, install))
            , linking
        );

        return new Action(Lists.append(linkOut, installOut));
    }

    Action getBoot(Build build)
    {
        final var boot =
            "jdk".equals(build.tree.name())
                ? new Boot(Jdk.JDK_14, Path.of("boot-jdk-14"))
                : new Boot(Jdk.JDK_11, Path.of("boot-jdk-11"));

        final var installOut = installBoot(boot);

        final var linkOut = Step.Linking.link(
            new Step.Linking(Homes.bootJdk(), installJdkHome(boot.path, install))
            , linking
        );

        return new Action(Lists.append(linkOut, installOut.items()));
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

        return new Action(Job.Install.install(new Job.Install(url, boot.path), install));
    }

    private static Path installJdkHome(Path path, Effect.Install effects)
    {
        return effects.download().osType().get().isMac()
            ? path.resolve(Path.of("Contents", "Home"))
            : path;
    }

    record Build(Repository tree)
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

    record Get(URL url, Path path)
    {
        static Get of(String url, String path)
        {
            return new Get(URLs.of(url), Path.of(path));
        }
    }

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
}
