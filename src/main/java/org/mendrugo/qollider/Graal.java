package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;
import org.mendrugo.qollider.Qollider.Roots;

import java.nio.file.Path;

public class Graal
{
    final Effect.Exec.Lazy lazy;
    final Effect.Install install;
    final Effect.Linking linking;
    final Roots roots;

    public Graal(Effect.Exec.Lazy lazy, Effect.Install install, Effect.Linking linking, Roots roots) {
        this.lazy = lazy;
        this.install = install;
        this.linking = linking;
        this.roots = roots;
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

        static Build of(String treeUrl, String mxUrl)
        {
            return new Build(Repository.of(treeUrl), Repository.of(mxUrl));
        }
    }

    static Build build()
    {
        return build("https://github.com/oracle/graal/tree/master");
    }

    static Build build(String treeUrl)
    {
        return build(treeUrl, "https://github.com/graalvm/mx/tree/master");
    }

    static Build build(String treeUrl, String mxUrl)
    {
        return Build.of(treeUrl, mxUrl);
    }

    Action build(Build build)
    {
        final var git = new Git(lazy);
        final var mxAction = git.clone(build.mx);
        final var treeAction = git.clone(build.tree);

        final var svm = Path.of(build.tree.name(), "substratevm");
        var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                svm
                , roots.today().apply(Path.of(build.mx.name(), "mx")).toString()
                , "--java-home"
                , roots.today().apply(Homes.java()).toString()
                , "build"
            )
            , lazy
        );

        final var target = Path.of(
            build.tree.name()
            ,"sdk"
            , "latest_graalvm_home"
        );

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), target)
            , linking
        );

        return Action.of(mxAction, treeAction, buildAction, linkAction);
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

            return Action.of(installAction, guNativeImageOut);
        }

        return installAction;
    }
}
