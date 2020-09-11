package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.util.List;

final class Git
{
    final Effect.Exec.Lazy lazy;

    Git(Effect.Exec.Lazy lazy)
    {
        this.lazy = lazy;
    }

    Action clone(Repository repo)
    {
        return new Action(List.of(
            Step.Exec.Lazy.action(Step.Exec.of(toClone(repo)), lazy)
        ));
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