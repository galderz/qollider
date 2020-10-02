package org.mendrugo.qollider;

import org.mendrugo.qollider.Qollider.Roots;

// TODO use it in Graal/Mandrel/Jdk...etc
record Effects(
    Effect.Exec.Lazy lazy
    , Effect.Install install
    , Effect.Linking linking
    , Roots roots
)
{}
