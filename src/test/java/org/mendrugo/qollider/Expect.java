package org.mendrugo.qollider;

record Expect(Step step, boolean touched)
{
    static Expect gitOpenJdkClone()
    {
        return gitClone("openjdk/jdk11u-dev", "master");
    }

    private static Expect gitClone(String repo, String branch)
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

    static Expect of(Step step)
    {
        return new Expect(step, true);
    }
}
