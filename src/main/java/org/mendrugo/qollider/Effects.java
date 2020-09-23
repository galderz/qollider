package org.mendrugo.qollider;

record Effects(
    Effect.Exec.Lazy lazy
    , Effect.Install install
    , Effect.Linking linking
    , Qollider.Roots roots
)
{}
