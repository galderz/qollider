package org.mendrugo.qollider;

import org.junit.jupiter.api.Test;

public class GitTest
{
    @Test
    void cloneRepository()
    {
        Asserts.plan(
            Sandbox.qollider().plan(
                Sandbox.git().clone(
                    Repository.of("https://github.com/openjdk/jdk11u-dev/tree/master")
                )
            )
            , Expect.gitOpenJdkClone()
        );

    }
}
