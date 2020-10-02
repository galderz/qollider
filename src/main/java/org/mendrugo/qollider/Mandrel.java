package org.mendrugo.qollider;

import org.mendrugo.qollider.OperatingSystem.EnvVar;
import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Roots;

import java.nio.file.Path;
import java.util.List;

public final class Mandrel
{
    final Effect.Exec.Lazy lazy;
    final Effect.Linking linking;
    final Roots roots;

    Mandrel(Effect.Exec.Lazy lazy, Effect.Linking linking, Roots roots) {
        this.lazy = lazy;
        this.linking = linking;
        this.roots = roots;
    }

    public record Build(Repository tree, Repository mx, Repository packaging) {}

    public Action build(Build build)
    {
        final var git = new Git(lazy);
        final var mxAction = git.clone(build.mx);
        final var packagingAction = git.clone(build.packaging);
        final var treeAction = git.clone(build.tree);

        final var today = roots.today();
        final var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                Path.of("mandrel-packaging")
                , List.of(
                    EnvVar.javaHome(today.apply(Homes.java()))
                )
                , today.apply(Homes.java()).resolve(Path.of("bin", "java")).toString()
                , "-ea"
                , "build.java"
                , "--mx-home"
                , today.apply(Path.of(build.mx.name())).toString()
                , "--mandrel-repo"
                , today.apply(Path.of(build.tree.name())).toString()
            )
            , lazy
        );

        final var target = Path.of(
            build.packaging.name()
            , "mandrel-11-dev"
        );

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), target)
            , linking
        );

        return Action.of(mxAction, packagingAction, treeAction, buildAction, linkAction);
    }
}
