package org.mendrugo.qollider;

import java.nio.file.Path;

record Expect(Step step, boolean touched)
{
    static Expect extract(String tar, String path)
    {
        return Expect.of(Step.Exec.of(
            Path.of("")
            , "tar"
            , "-xzpf"
            , tar
            , "-C"
            , path
            , "--strip-components"
            , "1"
        ));
    }

    static Expect download(String url, String path)
    {
        return Expect.of(new Step.Download(
            URLs.of(url)
            , Path.of(path)
        ));
    }

    static Expect gitClone(String repo, String branch)
    {
        return Expect.of(Step.Exec.of(
            "git"
            , "clone"
            , "-b"
            , branch
            , "--depth"
            , "1"
            , String.format("https://github.com/%s", repo)
        ));
    }

    static Expect gitCloneFull(String repo, String branch)
    {
        return Expect.of(Step.Exec.of(
            "git"
            , "clone"
            , "-b"
            , branch
            , String.format("https://github.com/%s", repo)
        ));
    }

    static Expect link(String link, String target)
    {
        return Expect.of(new Step.Linking(
            Path.of(link)
            , Path.of(target)
        ));
    }

    static Expect of(Step step)
    {
        return new Expect(step, true);
    }
}
