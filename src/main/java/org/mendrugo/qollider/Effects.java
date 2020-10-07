package org.mendrugo.qollider;

import java.nio.file.Path;

record Effects(
    Effect.Exec.Lazy lazy
    , Effect.Install install
    , Effect.Linking linking
    , Path root
)
{
    static Effects of(FileTree tree)
    {
        return new Effects(
            Effect.Exec.Lazy.of(OperatingSystem.of(tree))
            , Effect.Install.of(Web.of(tree), OperatingSystem.of(tree))
            , new Effect.Linking(tree::symlink)
            , tree.root
        );
    }
}
