package org.mendrugo.qollider;

record Effects(
    Effect.Exec.Lazy lazy
    , Effect.Install install
    , Effect.Linking linking
)
{
    static Effects of()
    {
        return new Effects(
            Effect.Exec.Lazy.of()
            , Effect.Install.of()
            , new Effect.Linking(FileTree::symlink)
        );
    }
}
