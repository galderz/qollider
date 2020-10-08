package org.mendrugo.qollider;

import org.mendrugo.qollider.OperatingSystem.EnvVar;
import org.mendrugo.qollider.Qollider.Action;

import java.nio.file.Path;
import java.util.List;

public final class Mandrel
{
    final Effects effects;
    final Path today;

    public Mandrel(Effects effects, Path today)
    {
        this.effects = effects;
        this.today = today;
    }

    public record Build(Repository tree, Repository mx, Repository packaging) {}

    public Action build(Build build)
    {
        final var git = new Git(effects.lazy(), today);
        final var mxAction = git.clone(build.mx);
        final var packagingAction = git.clone(build.packaging);
        final var treeAction = git.clone(build.tree);

        final var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                today
                , Path.of("mandrel-packaging")
                , List.of(
                    EnvVar.javaHome(today.resolve(Homes.java()))
                )
                , today.resolve(Homes.java()).resolve(Path.of("bin", "java")).toString()
                , "-ea"
                , "build.java"
                , "--mx-home"
                , today.resolve(Path.of(build.mx.name())).toString()
                , "--mandrel-repo"
                , today.resolve(Path.of(build.tree.name())).toString()
            )
            , effects.lazy()
        );

        final var target = Path.of(
            build.packaging.name()
            , "mandrel-11-dev"
        );

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), target, today)
            , effects.linking()
        );

        return Action.of(mxAction, packagingAction, treeAction, buildAction, linkAction);
    }
}
