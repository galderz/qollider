package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

import static org.mendrugo.qollider.Sandbox.jdk;
import static org.mendrugo.qollider.Sandbox.qollider;

public class JdkTest
{
    @Test
    void buildLabsJdk()
    {
        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).build(
                    new Jdk.Build(
                        Repository.of("https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b02")
                    )
                )
            )
            , Expect.gitClone("graalvm/labs-openjdk-11", "jvmci-20.2-b02", 20)
            , Expect.javaLabsJdkBuild()
            , Expect.link("java_home", "labs-openjdk-11/java_home")
        );
    }

    @Test
    void buildOpenJDK()
    {
        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).build(
                    new Jdk.Build(
                        Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master")
                    )
                )
            )
            , Expect.gitClone("openjdk/jdk11u-dev", "master", 1)
            , Expect.javaOpenJdkConfigure()
            , Expect.javaOpenJdkMake()
            , Expect.javaOpenJdkLink()
        );
    }

    @Test
    void get()
    {
        final var url =
            "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.8%2B10/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz";

        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).get(
                    Jdk.Get.of(url, "jdk")
                )
            )
            , Expect.download(url, "downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz")
            , Expect.extract("downloads/OpenJDK11U-jdk_x64_mac_hotspot_11.0.8_10.tar.gz", "jdk")
            , Expect.link("java_home", "jdk/Contents/Home")
        );
    }

    @Test
    void getBootJdkJdk()
    {
        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.LINUX, Hardware.Arch.AARCH64).getBoot(
                    new Jdk.Build(Repository.of(
                        "https://github.com/openjdk/jdk/tree/master"
                    ))
                )
            )
            , Expect.jdk14DownloadLinux()
            , Expect.bootJdk14Extract()
            , Expect.link("bootjdk_home", "boot-jdk-14")
        );
    }

    @Test
    void getBootJdk11Linux()
    {
        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.LINUX, Hardware.Arch.AARCH64).getBoot(
                    new Jdk.Build(Repository.of(
                        "https://github.com/openjdk/jdk11u-dev/tree/master"
                    ))
                )
            )
            , Expect.jdk11DownloadLinux()
            , Expect.bootJdk11ExtractLinux()
            , Expect.bootJdk11LinkLinux()
        );
    }

    @Test
    void getBootJdk11MacOs()
    {
        Asserts.plan(
            qollider().plan(
                jdk(OperatingSystem.Type.MAC_OS, Hardware.Arch.X64).getBoot(
                    new Jdk.Build(Repository.of(
                        "https://github.com/openjdk/jdk11u-dev/tree/master"
                    ))
                )
            )
            , Expect.jdk11DownloadMacOs()
            , Expect.bootJdk11ExtractMacOs()
            , Expect.link("bootjdk_home", "boot-jdk-11/Contents/Home")
        );
    }
}
