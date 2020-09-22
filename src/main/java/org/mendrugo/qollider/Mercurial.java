package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

final class Mercurial
{
    final Effect.Exec.Lazy lazy;

    Mercurial(Effect.Exec.Lazy lazy)
    {
        this.lazy = lazy;
    }

    Action clone(Repository repository)
    {
        return Step.Exec.Lazy.action(
            Step.Exec.of(
                "hg"
                , "clone"
                , repository.cloneUri().toString()
            )
            , lazy
        );
    }
}
