package org.mendrugo.qollider;

import java.nio.file.Path;

public class Homes
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
}
