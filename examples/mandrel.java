//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 16
//JAVAC_OPTIONS --enable-preview -source 16
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.5.2
//DEPS org.mendrugo.qollider:qollider:${qollider.version:LATEST}

import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Mandrel;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repositories;
import org.mendrugo.qollider.Repository;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Script to build Mandrel.
 */
@Command(
    description = "Build Mandrel"
    , mixinStandardHelpOptions = true
    , name = "mandrel"
)
public class mandrel implements Callable<Integer>
{
    @Option(
        description = "JDK repository URI"
        , names = {"-j", "--jdk"}
    )
    private Repository jdk = Repositories.JDK_11_DEV;

    @Option(
        description = "Mandrel repository URI"
        , names = {"-m", "--mandrel"}
    )
    private Repository mandrel = Repositories.MANDREL;

    @Override
    public Integer call() throws Exception
    {
        final var qollider = Qollider.of();
        qollider
            .plan(
                qollider.jdk().build(
                    new Jdk.Build(jdk)
                )
                , qollider.mandrel().build(
                    new Mandrel.Build(
                        mandrel
                        , Repositories.MX
                        , Repositories.MANDREL_PACKAGING
                    )
                )
            )
            .run();

        return 0;
    }

    public static void main(String... args)
    {
        int exitCode = new CommandLine(new mandrel())
            .registerConverter(Repository.class, Repository::of)
            .execute(args);
        System.exit(exitCode);
    }
}
