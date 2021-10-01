//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 16
//JAVAC_OPTIONS --enable-preview -source 16
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.5.2
//DEPS org.mendrugo.qollider:qollider:${qollider.version:LATEST}

import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repositories;
import org.mendrugo.qollider.Repository;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Script to build a JDK.
 */
@Command(
    description = "Build a JDK"
    , mixinStandardHelpOptions = true
    , name = "jdk"
)
public class jdk implements Callable<Integer>
{
    @Option(
        description = "JDK repository URI"
        , names = {"-j", "--jdk"}
    )
    private Repository jdk = Repositories.JDK_JDK;

    @Option(
        description = "Debug level"
        , names = {"-d", "--debug-level"}
    )
    private Jdk.DebugLevel debugLevel = Jdk.DebugLevel.FASTDEBUG;

    @Override
    public Integer call() throws Exception
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(new Jdk.Build(jdk, debugLevel))
            )
            .run();

        return 0;
    }

    public static void main(String... args)
    {
        int exitCode = new CommandLine(new jdk())
            .registerConverter(Repository.class, Repository::of)
            .registerConverter(Jdk.DebugLevel.class, debugLevel -> Jdk.DebugLevel.valueOf(debugLevel.toUpperCase()))
            .execute(args);
        System.exit(exitCode);
    }
}
