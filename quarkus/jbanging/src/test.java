//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.1.4
//DEPS org.apache.logging.log4j:log4j-core:2.13.0

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(
    description = "Test quarkus"
    , mixinStandardHelpOptions = true
    , name = "test"
    , version = "test 0.1"
)
public class test implements Callable<Integer>
{
    static final Logger logger = LogManager.getLogger(test.class);

    public static void main(String[] args)
    {
        Configurator.initialize(new DefaultConfiguration());
        Configurator.setRootLevel(Level.DEBUG);

        int exitCode = new CommandLine(new test()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call()
    {
        logger.info("Test the combo!");
        Sequential.test();
        return 0;
    }
}

class Sequential
{
    static void test()
    {
        final var root = LocalPaths.rootPath();
        Java.installLabsJDK(root);
    }
}

class Java
{
    static void installLabsJDK(Path root)
    {
        OperatingSystem.exec()
            .compose(Java.downloadLabsJDK(root))
            .apply(labsJDK());
    }

    private static Function<LabsJDK, OperatingSystem.Command> downloadLabsJDK(Path root)
    {
        return labsJDK ->
            new OperatingSystem.Command(
                Stream.of(
                    "curl"
                    , "-L"
                    , labsURL(labsJDK)
                    , "--output"
                    , "labsjdk.tar.gz"
                )
                , root
                , Stream.empty()
            );
    }

    private static LabsJDK labsJDK()
    {
        var osName = OperatingSystem.type() == OperatingSystem.Type.MAC_OS
            ? "darwin"
            : "linux";

        return new LabsJDK(
            "20.0-b02"
            , "11.0.6+9"
            , osName
        );
    }

    private static String labsURL(LabsJDK labsJDK)
    {
        String base = "https://github.com/graalvm/labs-openjdk-11/releases/download";
        return String.format(
            "%1$s/jvmci-%2$s/labsjdk-ce-%3$s-jvmci-%2$s-%4$s-amd64.tar.gz"
            , base
            , labsJDK.version
            , labsJDK.javaVersion
            , labsJDK.osName
        );
    }

    private static class LabsJDK
    {
        final String version;
        final String javaVersion;
        final String osName;

        LabsJDK(String version, String javaVersion, String osName)
        {
            this.version = version;
            this.javaVersion = javaVersion;
            this.osName = osName;
        }
    }
}

class LocalPaths
{
    static final Logger logger = LogManager.getLogger(LocalPaths.class);

    static Path rootPath()
    {
        var date = LocalDate.now();
        var formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        var today = date.format(formatter);
        logger.info("Today is {}", today);

        var tmpDir = System.getProperty("java.io.tmpdir");
        logger.info("Temp directory: {}", tmpDir);

        final var root = Path.of(tmpDir, "quarkus-with-graal", today);
        logger.info("Root path: {}", root);

        final var directory = root.toFile();
        if (!directory.exists() && !directory.mkdirs())
        {
            throw new RuntimeException(String.format(
                "Unable to create path: %s"
                , root)
            );
        }

        return root;
    }
}

//class Git
//{
//    static Function<String, OperatingSystem.Command> clone(Path directory)
//    {
//        return branch ->
//            new OperatingSystem.Command(
//                Stream.of(
//                    "git"
//                    , "checkout"
//                    , branch
//                )
//                , directory
//                , Stream.empty()
//            );
//    }
//}

class OperatingSystem
{
    static final Logger logger = LogManager.getLogger(OperatingSystem.class);

    public enum Type
    {
        WINDOWS, MAC_OS, LINUX, OTHER
    }

    public static Type type()
    {
        String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);

        if ((OS.contains("mac")) || (OS.contains("darwin")))
            return Type.MAC_OS;

        if (OS.contains("win"))
            return Type.WINDOWS;

        if (OS.contains("nux"))
            return Type.LINUX;

        return Type.OTHER;
    }

    static Function<Command, Void> exec()
    {
        return OperatingSystem::exec;
    }

    private static Void exec(Command command)
    {
        final var commandList = command.command
            .filter(Predicate.not(String::isEmpty))
            .collect(Collectors.toList());

        logger.debug("Execute {} in {}", commandList, command.directory);
        try
        {
            var processBuilder = new ProcessBuilder(commandList)
                .directory(command.directory.toFile())
                .inheritIO();

            command.envVars.forEach(
                envVar -> processBuilder.environment()
                    .put(envVar.name, envVar.value)
            );

            Process process = processBuilder.start();

            if (process.waitFor() != 0)
            {
                throw new RuntimeException(
                    "Failed, exit code: " + process.exitValue()
                );
            }

            return null;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    static class Command
    {
        final Stream<String> command;
        final Path directory;
        final Stream<EnvVar> envVars;

        Command(Stream<String> command, Path directory, Stream<EnvVar> envVars)
        {
            this.command = command;
            this.directory = directory;
            this.envVars = envVars;
        }
    }

    static class EnvVar
    {
        final String name;
        final String value;

        EnvVar(String name, String value)
        {
            this.name = name;
            this.value = value;
        }
    }
}
