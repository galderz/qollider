package org.mendrugo.qollider;

import org.mendrugo.qollider.OperatingSystem.EnvVar;

import java.nio.file.Path;
import java.util.List;

public record Expect(Step step, boolean touched)
{
    public static Expect bootJdk11ExtractLinux()
    {
        return extract("downloads/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.7_10.tar.gz", "boot-jdk-11", Sandbox.home());
    }

    public static Expect bootJdk11ExtractMacOs()
    {
        return extract("downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz", "boot-jdk-11", Sandbox.home());
    }

    public static Expect bootJdk16Extract()
    {
        return extract("downloads/OpenJDK16U-jdk_aarch64_linux_hotspot_16.0.1_9.tar.gz", "boot-jdk-16", Sandbox.home());
    }

    public static Expect bootJdk11LinkLinux()
    {
        return Expect.of(new Step.Linking(
            Path.of("/home/bootjdk_home")
            , Path.of("boot-jdk-11")
            , Sandbox.home()
        ));
    }

    public static Expect extract(String tar, String path, Path root)
    {
        return Expect.of(Step.Exec.of(
            root
            , "tar"
            , "-xzpf"
            , tar
            , "-C"
            , path
            , "--strip-components"
            , "1"
        ));
    }

    public static Expect download(String url, String path, Path root)
    {
        return Expect.of(new Step.Download(
            URLs.of(url)
            , Path.of(path)
            , root
        ));
    }

    public static Expect gitCloneBranch(String repo, String branch, int depth)
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , "git"
            , "clone"
            , "-b"
            , branch
            , "--depth"
            , String.valueOf(depth)
            , String.format("https://github.com/%s", repo)
        ));
    }

    public static Expect gitCloneFull(String repo, String branch)
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , "git"
            , "clone"
            , "-b"
            , branch
            , String.format("https://github.com/%s", repo)
        ));
    }

    public static Expect gitCloneFull(String repo)
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , "git"
            , "clone"
            , String.format("https://github.com/%s", repo)
        ));
    }

    public static Expect gitCheckoutCommit(String commitId, String name)
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today().resolve(name)
            , "git"
            , "checkout"
            , commitId
        ));
    }

    public static Expect graalBuild()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of("graal", "substratevm")
            , "/today/mx/mx"
            , "--java-home"
            , "/today/java_home"
            , "build"
        ));
    }

    public static Expect graalLink()
    {
        return Expect.of(new Step.Linking(
            Path.of("graalvm_home")
            , Path.of("graal", "sdk", "latest_graalvm_home")
            , Sandbox.today()
        ));
    }

    public static Expect guNativeImage()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of("graalvm/bin")
            , "./gu"
            , "install"
            , "native-image"
        ));
    }

    public static Expect javaLabsJdkBuild()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of("labs-openjdk-11")
            , "python"
            , "build_labsjdk.py"
            , "--boot-jdk"
            , "/home/bootjdk_home"
            , "--configure-option=--disable-warnings-as-errors"
        ));
    }

    public static Expect javaOpenJdkConfigure()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of("jdk11u-dev")
            , "bash"
            , "configure"
            , "--with-conf-name=graal-server-fastdebug"
            , "--disable-warnings-as-errors"
            , "--with-jvm-features=graal"
            , "--with-jvm-variants=server"
            , "--with-extra-cflags=-fcommon"
            , "--with-debug-level=fastdebug"
            , "--with-boot-jdk=/home/bootjdk_home"
        ));
    }

    public static Expect javaOpenJdkMake()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of("jdk11u-dev")
            , "make"
            , "graal-builder-image"
        ));
    }

    public static Expect javaOpenJdkLink()
    {
        return Expect.of(new Step.Linking(
            Path.of("java_home")
            , Path.of("jdk11u-dev/build/graal-server-fastdebug/images/graal-builder-jdk")
            , Sandbox.today()
        ));
    }

    public static Expect jdk11DownloadLinux()
    {
        return download(
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.7_10.tar.gz"
            , "downloads/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.7_10.tar.gz"
            , Sandbox.home()
        );
    }

    public static Expect jdk11DownloadMacOs()
    {
        return download(
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.7%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz"
            , "downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.7_10.tar.gz"
            , Sandbox.home()
        );
    }

    public static Expect jdk16DownloadLinux()
    {
        return download(
            "https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jdk_aarch64_linux_hotspot_16.0.1_9.tar.gz"
            , "downloads/OpenJDK16U-jdk_aarch64_linux_hotspot_16.0.1_9.tar.gz"
            , Sandbox.home()
        );
    }

    public static Expect link(String link, String target)
    {
        return Expect.of(new Step.Linking(
            Path.of(link)
            , Path.of(target)
            , Sandbox.today()
        ));
    }

    public static Expect mandrelBuild()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of("mandrel-packaging")
            , List.of(
                EnvVar.of("JAVA_HOME", "/today/java_home")
            )
            , "/today/java_home/bin/java"
            , "-ea"
            , "build.java"
            , "--mx-home"
            , "/today/mx"
            , "--mandrel-repo"
            , "/today/mandrel"
            , "--mandrel-home"
            , "/today/graalvm_home"
        ));
    }

    public static Expect mercurialOpenJdkClone()
    {
        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , "hg"
            , "clone"
            , "https://hg.openjdk.java.net/jdk-updates/jdk11u-dev"
        ));
    }

    public static Expect mavenBinDownload()
    {
        return download(
            "https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz"
            , "downloads/apache-maven-3.6.3-bin.tar.gz"
            , Sandbox.home()
        );
    }

    public static Expect mavenBinExtract()
    {
        return extract("downloads/apache-maven-3.6.3-bin.tar.gz", "maven", Sandbox.home());
    }

    public static Expect mavenBuild(String name, String... extraArgs)
    {
        final var args = Lists.concat(
            List.of(
                Sandbox.home().resolve(Path.of("maven", "bin", "mvn")).toString()
                , "install"
                , "-DskipTests"
                , "-DskipITs"
                , "-Denforcer.skip"
                , "-Dformat.skip"
            )
            , List.of(extraArgs)
        );

        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of(name)
            , List.of(new EnvVar("JAVA_HOME", Sandbox.today().resolve(Homes.graal())))
            , args
        ));
    }

    public static Expect mavenTest(String name, String... extraArgs)
    {
        final var args = Lists.concat(
            List.of(
                Sandbox.home().resolve(Path.of("maven", "bin", "mvn")).toString()
                , "install"
                , "-Dnative"
                , "-Dformat.skip"
            )
            , List.of(extraArgs)
        );

        return Expect.of(Step.Exec.of(
            Sandbox.today()
            , Path.of(name)
            , List.of(new EnvVar("JAVA_HOME", Sandbox.today().resolve(Homes.graal())))
            , args
        ));
    }

    static Expect of(Step step)
    {
        return new Expect(step, true);
    }
}
