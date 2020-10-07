package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;

import java.nio.file.Path;

public final class Graal
{
    final Effects today;

    public Graal(Effects today)
    {
        this.today = today;
    }

    public record Build(Repository tree, Repository mx)
    {
        public Build
        {
            if ("mandrel".equals(tree.name()))
            {
                throw new IllegalArgumentException("Mandrel repos should be built with mandrel-build");
            }
        }
    }

    public Action build(Build build)
    {
        final var git = new Git(today.lazy());
        final var mxAction = git.clone(build.mx);
        final var treeAction = git.clone(build.tree);

        final var svm = Path.of(build.tree.name(), "substratevm");
        var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                svm
                , today.root().resolve(Path.of(build.mx.name(), "mx")).toString()
                , "--java-home"
                , today.root().resolve(Homes.java()).toString()
                , "build"
            )
            , today.lazy()
        );

        final var target = Path.of(
            build.tree.name()
            ,"sdk"
            , "latest_graalvm_home"
        );

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), target)
            , today.linking()
        );

        return Action.of(mxAction, treeAction, buildAction, linkAction);
    }

    public static Get get(String url)
    {
        return Get.of(url, "graalvm");
    }

    public Action get(Get get)
    {
        final var installAction = install(get);
        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), get.path())
            , today.linking()
        );
        return Action.of(installAction, linkAction);
    }

    private Action install(Get get)
    {
        final var installAction = Job.Install.install(
            new Job.Install(get.url(), get.path())
            , today.install()
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
                , today.lazy()
            );

            return Action.of(installAction, guNativeImageOut);
        }

        return installAction;
    }
}
