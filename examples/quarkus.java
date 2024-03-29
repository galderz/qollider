//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//JAVAC_OPTIONS --enable-preview -source 17
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.5.2
//DEPS org.mendrugo.qollider:qollider:${qollider.version:LATEST}

import org.mendrugo.qollider.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Script to build Quarkus.
 */
@Command(
    description = "Build Quarkus"
    , mixinStandardHelpOptions = true
    , name = "quarkus"
)
public class quarkus implements Callable<Integer>
{
    @Option(
        description = "JDK repository URI"
        , names = {"-j", "--jdk"}
    )
    private Repository jdk = Repositories.JDK_11_DEV;

    @Option(
        description = "JDK Debug level"
        , names = {"-d", "--jdk-debug-level"}
    )
    private Jdk.DebugLevel jdkDebugLevel = Jdk.DebugLevel.FASTDEBUG;

    @Option(
        description = "Graal repository URI"
        , names = {"-g", "--graal"}
    )
    private Repository graal = Repositories.GRAAL;

    @Option(
        description = "Quarkus repository"
        , names = {"-q", "--quarkus"}
    )
    private Repository quarkus = Repositories.QUARKUS;

    @Override
    public Integer call() throws Exception
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(
                    new Jdk.Build(jdk, jdkDebugLevel)
                )
                , qollider.mandrel().build(
                    new Mandrel.Build(graal, Repositories.MX, Repositories.MANDREL_PACKAGING)
                )
                , qollider.maven().build(
                    new Maven.Build(quarkus, List.of())
                )
                , qollider.maven().test(
                    new Maven.Test("quarkus", List.of())
                )
            )
            .run();

        return 0;
    }

    public static void main(String... args)
    {
        int exitCode = new CommandLine(new quarkus())
            .registerConverter(Repository.class, Repository::of)
            .execute(args);
        System.exit(exitCode);
    }
}
