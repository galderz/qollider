//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 16
//JAVAC_OPTIONS --enable-preview -source 16
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.5.2
//DEPS org.mendrugo.qollider:qollider:${qollider.version:LATEST}

import org.mendrugo.qollider.Graal;
import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repositories;
import org.mendrugo.qollider.Repository;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Script to build Graal.
 */
@Command(
    description = "Build Graal"
    , mixinStandardHelpOptions = true
    , name = "graal"
)
public class graal implements Callable<Integer>
{
    @Option(
        description = "JDK repository URI"
        , names = {"-j", "--jdk"}
    )
    private Repository jdk = Repositories.LABS_JDK;

    @Option(
        description = "Debug level"
        , names = {"-d", "--jdk-debug-level"}
    )
    private Jdk.DebugLevel jdkDebugLevel = Jdk.DebugLevel.FASTDEBUG;

    @Option(
        description = "Graal repository URI"
        , names = {"-g", "--graal"}
    )
    private Repository graal = Repositories.GRAAL;

    @Override
    public Integer call() throws Exception
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(
                    new Jdk.Build(jdk, jdkDebugLevel)
                )
                , qollider.graal().build(
                    new Graal.Build(graal, Repositories.MX)
                )
            )
            .run();

        return 0;
    }

    public static void main(String... args)
    {
        int exitCode = new CommandLine(new graal())
            .registerConverter(Repository.class, Repository::of)
            .execute(args);
        System.exit(exitCode);
    }
}
