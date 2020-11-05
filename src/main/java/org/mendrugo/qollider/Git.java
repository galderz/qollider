package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Action;

import java.nio.file.Path;

public final class Git
{
    final Effects effects;
    final Path root;

    Git(Effects effects, Path root)
    {
        this.effects = effects;
        this.root = root;
    }

    public Action version()
    {
        return Step.Exec.Lazy.action(Step.Exec.of(root, "git", "--version"), effects.lazy());
    }

    public Action clone(Repository repo)
    {
        return Step.Exec.Lazy.action(Step.Exec.of(root, toClone(repo)), effects.lazy());
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
