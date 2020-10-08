package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.nio.file.Path;

public final class Git
{
    final Effect.Exec.Lazy lazy;
    final Path root;

    Git(Effect.Exec.Lazy lazy, Path root)
    {
        this.lazy = lazy;
        this.root = root;
    }

    public Action clone(Repository repo)
    {
        return Step.Exec.Lazy.action(Step.Exec.of(root, toClone(repo)), lazy);
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
