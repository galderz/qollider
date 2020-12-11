///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 14
//JAVAC_OPTIONS --enable-preview -source 14
//JAVA_OPTIONS --enable-preview
//DEPS info.picocli:picocli:4.5.2
//DEPS org.mendrugo.qollider:qollider:${qollider.version:0.1}

import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Mandrel;
import org.mendrugo.qollider.Maven;
import org.mendrugo.qollider.Qollider;
import org.mendrugo.qollider.Repositories;
import org.mendrugo.qollider.Repository;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import static java.lang.System.*;

/**
 * Script to Infinispan Quarkus native server.
 */
@Command(
    description = "Build Infinispan Quarkus native server"
    , mixinStandardHelpOptions = true
    , name = "infinispan-quarkus"
)
public class infinispan_quarkus implements Callable<Integer>
{
    @Option(
        description = "JDK repository"
        , names = {"-j", "--jdk"}
    )
    private Repository jdk = Repositories.JDK_11_DEV;

    @Option(
        description = "Mandrel repository"
        , names = {"-m", "--mandrel"}
    )
    private Repository mandrel = Repositories.MANDREL;

    @Option(
        description = "Infinispan repository"
        , names = {"-i", "--infinispan"}
    )
    private Repository infinispan = Repositories.INFINISPAN;

    @Option(
        description = "Quarkus repository"
        , names = {"-q", "--quarkus"}
    )
    private Repository quarkus = Repositories.QUARKUS;

    @Option(
        description = "Infinispan Quarkus repository"
        , names = {"-iq", "--infinispan-quarkus"}
    )
    private Repository infinispanQuarkus = Repositories.INFINISPAN_QUARKUS;

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
                    new Mandrel.Build(mandrel, Repositories.MX, Repositories.MANDREL_PACKAGING)
                )
                , qollider.maven().build(
                    new Maven.Build(infinispan, List.of("-s", "maven-settings.xml"))
                )
                , qollider.maven().build(
                    new Maven.Build(quarkus, List.of())
                )
                , qollider.maven().build(
                    new Maven.Build(infinispanQuarkus, List.of(
                        "-pl"
                        , "!:infinispan-quarkus-integration-test-server"
                    ))
                )
                , qollider.maven().build(
                    new Maven.Build(infinispanQuarkus, List.of(
                        "-Dnative-noargs"
                        , "-pl"
                        , ":infinispan-quarkus-server-runner"
                        , "-Dquarkus.native.debug.enabled=true"
                        , "-Dquarkus.native.additional-build-args=-H:-DeleteLocalSymbols,-H:+PreserveFramePointer,--allow-incomplete-classpath"
                        , "dependency:sources"
                    ))
                )
            )
            .run();

        final var srcDir = Path.of(getProperty("user.home"), ".qollider", "cache", "latest", "infinispan-quarkus", "server-runner", "target");
        final var dstDir = Path.of(getProperty("user.home"), ".qollider", "cache", "latest", "tracing-infinispan-native");
        if (dstDir.toFile().exists())
        {
            Files.walk(dstDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }

        Files.walk(srcDir).forEach(srcPath ->
        {
            Path dstPath = dstDir.resolve(srcDir.relativize(srcPath));
            try
            {
                Files.copy(srcPath, dstPath);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        });

        Files.walk(srcDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);

        qollider
            .plan(
                qollider.maven().build(
                    new Maven.Build(infinispanQuarkus, List.of(
                        "-Dnative-noargs"
                        , "-pl"
                        , ":infinispan-quarkus-server-runner"
                        , "-Dquarkus.native.additional-build-args=--allow-incomplete-classpath"
                    ))
                )
            )
            .run();

        return 0;
    }

    public static void main(String... args)
    {
        int exitCode = new CommandLine(new infinispan_quarkus())
            .registerConverter(Repository.class, Repository::of)
            .execute(args);
        System.exit(exitCode);
    }
}
