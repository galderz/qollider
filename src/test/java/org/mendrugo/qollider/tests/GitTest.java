package org.mendrugo.qollider.tests;

import org.junit.jupiter.api.Test;
import org.mendrugo.qollider.Asserts;
import org.mendrugo.qollider.Expect;
import org.mendrugo.qollider.Repository;

import static org.mendrugo.qollider.Sandbox.qolliderUnknown;

public class GitTest
{
    @Test
    void cloneRepository()
    {
        final var qollider = qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.git().clone(
                    Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master")
                )
            )
            , Expect.gitCloneBranch("openjdk/jdk11u-dev", "master", 1)
        );
    }

    @Test
    void cloneFullRepository()
    {
        final var qollider = qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.git().clone(
                    Repository.of("https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b02?depth=0")
                )
            )
            , Expect.gitCloneFull("graalvm/labs-openjdk-11", "jvmci-20.2-b02")
        );
    }

    @Test
    void cloneBranchWithPath()
    {
        final var qollider = qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.git().clone(
                    Repository.of("https://github.com/olpaw/graal/tree/paw/2367")
                )
            )
            , Expect.gitCloneBranch("olpaw/graal", "paw/2367", 1)
        );
    }

    @Test
    void cloneCommit()
    {
        final var qollider = qolliderUnknown();
        Asserts.plan(
            qollider.plan(
                qollider.git().clone(
                    Repository.of("https://github.com/wburns/infinispan/commit/707629178e2b5ac237c95b257208dfbea5e584b2")
                )
            )
            , Expect.gitCloneFull("wburns/infinispan")
            , Expect.gitCheckoutCommit("707629178e2b5ac237c95b257208dfbea5e584b2", "infinispan")
        );
    }
}
