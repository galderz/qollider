package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

import static org.mendrugo.qollider.Sandbox.git;
import static org.mendrugo.qollider.Sandbox.qollider;

public class GitTest
{
    @Test
    void cloneRepository()
    {
        Asserts.plan(
            qollider().plan(
                git().clone(
                    Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master")
                )
            )
            , Expect.gitClone("openjdk/jdk11u-dev", "master")
        );
    }

    @Test
    void cloneFullRepository()
    {
        Asserts.plan(
            qollider().plan(
                git().clone(
                    Repository.of("https://github.com/graalvm/labs-openjdk-11/tree/jvmci-20.2-b02?depth=0")
                )
            )
            , Expect.gitCloneFull("graalvm/labs-openjdk-11", "jvmci-20.2-b02")
        );
    }
}
