package org.mendrugo.qollider;

import java.nio.file.Path;

final class Homes
{
    static Path java()
    {
        return Path.of("java_home");
    }

    static Path bootJdk()
    {
        return Path.of("bootjdk_home");
    }

    static Path graal()
    {
        return Path.of("graalvm_home");
    }

    private Homes() {}
}
