package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.net.URL;
import java.nio.file.Path;

public final class Jdk
{
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

    private static Path installJdkHome(Path path, Effect.Install effects)
    {
        return effects.download().osType().get().isMac()
            ? path.resolve(Path.of("Contents", "Home"))
            : path;
    }

    record Get(URL url, Path path)
    {
        static Get of(String url, String path)
        {
            return new Get(URLs.of(url), Path.of(path));
        }
    }
}
