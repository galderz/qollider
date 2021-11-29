package org.mendrugo.qollider;

import org.mendrugo.qollider.OperatingSystem.EnvVar;
import org.mendrugo.qollider.Qollider.Action;
import org.mendrugo.qollider.Qollider.Get;

import java.nio.file.Path;
import java.util.List;

public final class Graal
{
    final Effects effects;
    final Path today;

    public Graal(Effects effects, Path today) {
        this.effects = effects;
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
        final var git = new Git(effects, today);
        final var mxAction = git.clone(build.mx);
        final var treeAction = git.clone(build.tree);

        var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                today
                , Path.of(build.tree.name())
                , List.of(
                    EnvVar.of("MX_PYTHON", "python3")
                )
                , today.resolve(Path.of(build.mx.name(), "mx")).toString()
                , "--java-home"
                , today.resolve(Homes.java()).toString()
                , "--primary-suite-path"
                , "substratevm"
                , "--components=Native Image,LibGraal"
                , "--native-images=native-image,lib:jvmcicompiler"
                , "build"
            )
            , effects.lazy()
        );

        final var target = Path.of(
            build.tree.name()
            ,"sdk"
            , "latest_graalvm_home"
        );

        final var linkAction = Step.Linking.link(
            new Step.Linking(Homes.graal(), target, today)
            , effects.linking()
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
            new Step.Linking(Homes.graal(), get.path(), today)
            , effects.linking()
        );
        return Action.of(installAction, linkAction);
    }

    private Action install(Get get)
    {
        final var installAction = Job.Install.install(
            new Job.Install(get.url(), get.path(), today)
            , effects.install()
        );

        final var orgName = Path.of(get.url().getPath()).getName(0);
        if (orgName.equals(Path.of("graalvm")))
        {
            final var guNativeImageOut = Step.Exec.Lazy.action(
                Step.Exec.of(
                    today
                    , Path.of("graalvm", "bin")
                    , "./gu"
                    , "install"
                    , "native-image"
                )
                , effects.lazy()
            );

            return Action.of(installAction, guNativeImageOut);
        }

        return installAction;
    }
}
