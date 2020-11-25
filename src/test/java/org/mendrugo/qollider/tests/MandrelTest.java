package org.mendrugo.qollider.tests;

import org.junit.jupiter.api.Test;
import org.mendrugo.qollider.Asserts;
import org.mendrugo.qollider.Expect;
import org.mendrugo.qollider.Mandrel;
import org.mendrugo.qollider.Repositories;
import org.mendrugo.qollider.Sandbox;

public class MandrelTest
{
    @Test
    void build()
    {
        final var qollider = Sandbox.qolliderMacOs();
        Asserts.plan(
            qollider.plan(
                qollider.mandrel().build(
                    new Mandrel.Build(Repositories.MANDREL, Repositories.MX, Repositories.MANDREL_PACKAGING)
                )
            )
            , Expect.gitCloneBranch("graalvm/mx", "master", 1)
            , Expect.gitCloneBranch("graalvm/mandrel-packaging", "master", 1)
            , Expect.gitCloneBranch("graalvm/mandrel", "mandrel/20.2", 1)
            , Expect.mandrelBuild()
        );
    }
}
