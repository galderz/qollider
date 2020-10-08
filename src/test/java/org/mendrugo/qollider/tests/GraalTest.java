package org.mendrugo.qollider.tests;

import org.junit.jupiter.api.Test;
import org.mendrugo.qollider.Asserts;
import org.mendrugo.qollider.Expect;
import org.mendrugo.qollider.Graal;
import org.mendrugo.qollider.Repositories;
import org.mendrugo.qollider.Sandbox;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GraalTest
{
    @Test
    void build()
    {
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.graal().build(
                    new Graal.Build(Repositories.GRAAL, Repositories.MX)
                )
            )
            , Expect.gitClone("graalvm/mx", "master", 1)
            , Expect.gitClone("oracle/graal", "master", 1)
            , Expect.graalBuild()
            , Expect.graalLink()
        );
    }

    @Test
    void buildFailIfMandrel()
    {
        final var qollider = Sandbox.qolliderMacOs();
        final var exception = assertThrows(
            IllegalArgumentException.class
            , () ->
                qollider.plan(
                    qollider.graal().build(
                        new Graal.Build(Repositories.MANDREL, Repositories.MX)
                    )
                ).run()
        );

        assertThat(exception.getMessage(), is("Mandrel repos should be built with mandrel-build"));
    }

    @Test
    void get()
    {
        final var url = "https://doestnotexist.com/archive.tar.gz";
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.graal().get(
                    Graal.get(url)
                )
            )
            , Expect.download(url, "downloads/archive.tar.gz", Sandbox.today())
            , Expect.extract("downloads/archive.tar.gz", "graalvm", Sandbox.today())
            , Expect.link("graalvm_home", "graalvm")
        );
    }

    @Test
    void getAndDownloadNativeImage()
    {
        final var url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.0/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz";
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.graal().get(
                    Graal.get(url)
                )
            )
            , Expect.download(url, "downloads/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz", Sandbox.today())
            , Expect.extract("downloads/graalvm-ce-java8-linux-amd64-19.3.0.tar.gz", "graalvm", Sandbox.today())
            , Expect.guNativeImage()
            , Expect.link("graalvm_home", "graalvm")
        );
    }
}
