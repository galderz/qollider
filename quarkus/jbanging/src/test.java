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
    static final Logger LOG = LogManager.getLogger(test.class);

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
        LOG.info("Test the combo!");
        final var paths = LocalPaths.newSystemPaths();
        Sequential.test(paths);
        return 0;
    }
}

class Sequential
{
    static void test(LocalPaths paths)
    {
        Java.installJDK(paths);
    }
}

class Java
{
    static final Logger LOG = LogManager.getLogger(Java.class);

    static void installJDK(LocalPaths paths)
    {
        if (paths.java.toFile().exists()) {
            LOG.info("Skipping JDK install");
            return;
        }

        final var steps = Stream.of(
            Java.downloadJDK(paths)
            , Java.extractJDK(paths)
        );

        steps.forEach(OperatingSystem::exec);
    }

    private static OperatingSystem.Command extractJDK(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "tar"
                , "-xzvpf"
                , "jdk.tar.gz"
                ,"-C"
                , "jdk"
                , "--strip-components"
                , "1"
            )
            , paths.root
            , Stream.empty()
        );
    }

    private static OperatingSystem.Command downloadJDK(LocalPaths paths)
    {
        return new OperatingSystem.Command(
            Stream.of(
                "curl"
                , "-L"
                , jdkURL(jdk())
                , "--output"
                , "jdk.tar.gz"
            )
            , paths.root
            , Stream.empty()
        );
    }

    private static JDK jdk()
    {
        var osName = OperatingSystem.type() == OperatingSystem.Type.MAC_OS
            ? "darwin"
            : "linux";

        return new JDK(
            "20.0-b02"
            , "11.0.6+9"
            , osName
        );
    }

    private static String jdkURL(JDK jdk)
    {
        String base = "https://github.com/graalvm/labs-openjdk-11/releases/download";
        return String.format(
            "%1$s/jvmci-%2$s/labsjdk-ce-%3$s-jvmci-%2$s-%4$s-amd64.tar.gz"
            , base
            , jdk.version
            , jdk.javaVersion
            , jdk.osName
        );
    }

    private static class JDK
    {
        final String version;
        final String javaVersion;
        final String osName;

        JDK(String version, String javaVersion, String osName)
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

    final Path root;
    final Path jdk;
    final Path javaHome;
    final Path java;

    private LocalPaths(Path root, Path jdk, Path javaHome, Path java)
    {
        this.root = root;
        this.jdk = jdk;
        this.javaHome = javaHome;
        this.java = java;
    }

    static LocalPaths newSystemPaths()
    {
        var root = OperatingSystem.mkdirs(rootPath());
        var jdk = OperatingSystem.mkdirs(root.resolve("jdk"));
        var javaHome = OperatingSystem.type() == OperatingSystem.Type.MAC_OS
            ? jdk.resolve("Contents").resolve("Home")
            : jdk;
        var java = javaHome.resolve("bin").resolve("java");
        return new LocalPaths(root, jdk, javaHome, java);
    }

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

    public static Path mkdirs(Path path)
    {
        final var directory = path.toFile();
        if (!directory.exists() && !directory.mkdirs())
        {
            throw new RuntimeException(String.format(
                "Unable to create path: %s"
                , path)
            );
        }

        return path;
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
        return command -> {
            exec(command);
            return null;
        };
    }

    static void exec(Command command)
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
