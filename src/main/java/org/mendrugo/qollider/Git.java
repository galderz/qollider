package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import static org.mendrugo.qollider.Step.*;
import static org.mendrugo.qollider.Step.Exec.*;

final class Git
{
    final Lazy.Effects lazy;

    Git(Lazy.Effects lazy)
    {
        this.lazy = lazy;
    }

    Action<Exec, Lazy.Effects> clone(Repository repo)
    {
        return new Action<>(Exec.of(toClone(repo)), lazy, Lazy.action());
    }

    static String[] toClone(Repository repo)
    {
        final var result = Lists.mutable(
            "git"
            , "clone"
            , "-b"
            , repo.branch()
        );

        if (repo.depth() > 0)
        {
            result.add("--depth");
            result.add(String.valueOf(repo.depth()));
        }

        result.add(repo.cloneUri().toString());

        return result.toArray(String[]::new);
    }
}
