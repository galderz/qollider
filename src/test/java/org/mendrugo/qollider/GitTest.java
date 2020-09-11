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
            , Expect.gitOpenJdkClone()
        );

    }
}
