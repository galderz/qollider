package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

public class Maven
{
    private static final String VERSION = "3.6.3";
    private static final URL BIN_URL = URLs.of(
        "https://downloads.apache.org/maven/maven-3/%1$s/binaries/apache-maven-%1$s-bin.tar.gz"
        , VERSION
    );

    final Effects effects;
    final Path home;
    final Path today;

    public Maven(Effects effects, Path home, Path today)
    {
        this.effects = effects;
        this.home = home;
        this.today = today;
    }

    public record Build(Repository tree, List<String> additionalBuildArgs) {}

    public Action build(Build build)
    {
        final var maven = Path.of("maven");

        final var binInstall =
            Job.Install.install(new Job.Install(BIN_URL, maven, home), effects.install());

        final var git = new Git(effects, today);
        final var cloneAction = git.clone(build.tree);

        final var buildArgs = Lists.concat(
            List.of(
                home.resolve(maven).resolve(Path.of("bin", "mvn")).toString()
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
            )
            , build.additionalBuildArgs
            , extraArgs(build.tree.name())
        );

        final var buildAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                today
                , Path.of(build.tree.name())
                , List.of(
                    OperatingSystem.EnvVar.javaHome(today.resolve(Homes.graal()))
                )
                , buildArgs
            )
            , effects.lazy()
        );

        return Action.of(binInstall, cloneAction, buildAction);
    }

    public record Test(String suite, List<String> additionalTestArgs) {}

    public Action test(Test test)
    {
        final var maven = Path.of("maven");

        final var testArgs = Lists.concat(
            List.of(
                home.resolve(maven).resolve(Path.of("bin", "mvn")).toString()
                , "install"
                , "-Dnative"
                , "-Dformat.skip"
            )
            , test.additionalTestArgs
            , extraArgs(test.suite)
        );

        final var testAction = Step.Exec.Lazy.action(
            Step.Exec.of(
                today
                , testPath(test.suite)
                , List.of(
                    OperatingSystem.EnvVar.javaHome(today.resolve(Homes.graal()))
                )
                , testArgs
            )
            , effects.lazy()
        );

        return Action.of(testAction);
    }

    private static Path testPath(String suite)
    {
        return suite.equals("quarkus")
            ? Path.of("quarkus", "integration-tests")
            : Path.of(suite);
    }

    private static List<String> extraArgs(String name)
    {
        if (!"quarkus".equals(name) && name.contains("quarkus"))
        {
            return List.of("-Dquarkus.version=999-SNAPSHOT");
        }
        else if ("quarkus-platform".equals(name))
        {
            return List.of(
                "-Dquarkus.version=999-SNAPSHOT"
                , "-Dcamel-quarkus.version=1.1.0-SNAPSHOT"
            );
        }

        return List.of();
    }
}
