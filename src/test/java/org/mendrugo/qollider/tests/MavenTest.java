package org.mendrugo.qollider.tests;

import org.junit.jupiter.api.Test;
import org.mendrugo.qollider.Asserts;
import org.mendrugo.qollider.Expect;
import org.mendrugo.qollider.Maven;
import org.mendrugo.qollider.Repository;
import org.mendrugo.qollider.Sandbox;

import java.util.List;

public class MavenTest
{
    @Test
    void build()
    {
        final var qollider = Sandbox.qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.maven().build(
                    new Maven.Build(
                        Repository.of("https://github.com/infinispan/infinispan/tree/master")
                        , List.of()
                    )
                )
            )
            , Expect.mavenBinDownload()
            , Expect.mavenBinExtract()
            , Expect.gitCloneBranch("infinispan/infinispan", "master", 1)
            , Expect.mavenBuild("infinispan")
        );
    }

    @Test
    void buildAdditionalArgs()
    {
        final var qollider = Sandbox.qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.maven().build(
                    new Maven.Build(
                        Repository.of("https://github.com/infinispan/infinispan-quarkus/tree/master")
                        , List.of(
                            "-pl"
                            , "!:infinispan-quarkus-integration-test-server"
                        )
                    )
                )
            )
            , Expect.mavenBinDownload()
            , Expect.mavenBinExtract()
            , Expect.gitCloneBranch("infinispan/infinispan-quarkus", "master", 1)
            , Expect.mavenBuild(
                "infinispan-quarkus"
                , "-pl"
                , "!:infinispan-quarkus-integration-test-server"
                , "-Dquarkus.version=999-SNAPSHOT"
            )
        );
    }

    @Test
    void test()
    {
        final var qollider = Sandbox.qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.maven().test(
                    new Maven.Test(
                        "quarkus"
                        , List.of()
                    )
                )
            )
            , Expect.mavenTest("quarkus/integration-tests")
        );
    }
}
