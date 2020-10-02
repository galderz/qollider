package org.mendrugo.qollider.tests;

import org.junit.jupiter.api.Test;
import org.mendrugo.qollider.Asserts;
import org.mendrugo.qollider.Expect;
import org.mendrugo.qollider.Jdk;
import org.mendrugo.qollider.Repository;
import org.mendrugo.qollider.Sandbox;

// TODO add test that does get + get boot (they should have different roots)
public class JdkTest
{
    @Test
    void buildLabsJdk()
    {
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().build(
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
    void buildOpenJdk()
    {
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().build(
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
    void buildOpenJdkMercurial()
    {
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().build(
                    new Jdk.Build(
                        Repository.of("http://hg.openjdk.java.net/jdk-updates/jdk11u-dev")
                    )
                )
            )
            , Expect.mercurialOpenJdkClone()
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

        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().get(
                    Jdk.get(url)
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
        final var qollider = Sandbox.qolliderLinux();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().getBoot(
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
        final var qollider = Sandbox.qolliderLinux();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().getBoot(
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
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.jdk().getBoot(
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
