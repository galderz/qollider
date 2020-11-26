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
        if (repo instanceof Repository.Branch repoBranch)
        {
            return Step.Exec.Lazy.action(Step.Exec.of(root, toClone(repoBranch)), effects.lazy());
        }
        else if (repo instanceof Repository.Commit repoCommit)
        {
            final var cloneRepo = Step.Exec.of(
                root
                , "git"
                , "clone"
                , "--depth"
                , "100"
                , repo.cloneUri()
            );
            final var checkoutCommit = Step.Exec.of(
                root.resolve(repoCommit.name())
                , "git"
                , "checkout"
                , repoCommit.commitId()
            );

            return Action.of(
                Step.Exec.Lazy.action(cloneRepo, effects.lazy())
                , Step.Exec.Lazy.action(checkoutCommit, effects.lazy())
            );
        }
        else
        {
            throw new IllegalStateException(String.format("Unknown repo: %s", repo));
        }
    }

    private String[] toClone(Repository.Branch repo)
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

        result.add(repo.cloneUri());

        return result.toArray(String[]::new);
    }
}
