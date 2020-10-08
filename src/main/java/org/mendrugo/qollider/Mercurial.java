package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.nio.file.Path;

final class Mercurial
{
    final Effect.Exec.Lazy lazy;
    final Path root;

    Mercurial(Effect.Exec.Lazy lazy, Path root) {
        this.lazy = lazy;
        this.root = root;
    }

    Action clone(Repository repository)
    {
        return Step.Exec.Lazy.action(
            Step.Exec.of(
                root
                , "hg"
                , "clone"
                , repository.cloneUri().toString()
            )
            , lazy
        );
    }
}
