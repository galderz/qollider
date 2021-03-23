//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.5.2
//DEPS org.mendrugo.qollider:qollider:${qollider.version:0.0.7}

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

    @Override
    public Integer call() throws Exception
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(new Jdk.Build(jdk))
            )
            .run();

        return 0;
    }

    public static void main(String... args)
    {
        int exitCode = new CommandLine(new jdk())
            .registerConverter(Repository.class, Repository::of)
            .execute(args);
        System.exit(exitCode);
    }
}
