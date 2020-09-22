package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;

import java.nio.file.Path;

public class Graal
{
    final Effect.Exec.Lazy lazy;
    final Effect.Install install;
    final Effect.Linking linking;

    public Graal(Effect.Exec.Lazy lazy, Effect.Install install, Effect.Linking linking) {
        this.lazy = lazy;
        this.install = install;
        this.linking = linking;
    }

    static Get get(String url)
    {
        return Get.of(url, "graalvm");
    }

    Action get(Get get)
    {
        final var installAction = install(get);
        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), get.path())
            , linking
        );
        return Action.of(installAction, linkAction);
    }

    private Action install(Get get)
    {
        final var installAction = Job.Install.install(
            new Job.Install(get.url(), get.path())
            , install
        );

        final var orgName = Path.of(get.url().getPath()).getName(0);
        if (orgName.equals(Path.of("graalvm")))
        {
            final var guNativeImageOut = Step.Exec.Lazy.action(
                Step.Exec.of(
                    Path.of("graalvm", "bin")
                    , "./gu"
                    , "install"
                    , "native-image"
                )
                , lazy
            );

            return Action.of(guNativeImageOut, installAction);
        }

        return installAction;
    }
}
