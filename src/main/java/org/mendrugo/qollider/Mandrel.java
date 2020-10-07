package org.mendrugo.qollider;

import org.mendrugo.qollider.OperatingSystem.EnvVar;
import org.mendrugo.qollider.Qollider.Action;

import java.nio.file.Path;
import java.util.List;

public final class Mandrel
{
    final Effects today;

    public Mandrel(Effects today)
    {
        this.today = today;
    }

    public record Build(Repository tree, Repository mx, Repository packaging) {}

    public Action build(Build build)
    {
        final var git = new Git(today.lazy());
        final var mxAction = git.clone(build.mx);
        final var packagingAction = git.clone(build.packaging);
        final var treeAction = git.clone(build.tree);

        final var todayRoot = today.root();
        final var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                Path.of("mandrel-packaging")
                , List.of(
                    EnvVar.javaHome(todayRoot.resolve(Homes.java()))
                )
                , todayRoot.resolve(Homes.java()).resolve(Path.of("bin", "java")).toString()
                , "-ea"
                , "build.java"
                , "--mx-home"
                , todayRoot.resolve(Path.of(build.mx.name())).toString()
                , "--mandrel-repo"
                , todayRoot.resolve(Path.of(build.tree.name())).toString()
            )
            , today.lazy()
        );

        final var target = Path.of(
            build.packaging.name()
            , "mandrel-11-dev"
        );

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), target)
            , today.linking()
        );

        return Action.of(mxAction, packagingAction, treeAction, buildAction, linkAction);
    }
}
