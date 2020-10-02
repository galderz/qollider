package org.mendrugo.qollider;

import static java.lang.String.format;

final class Illegal
{
    static IllegalStateException value(String value)
    {
        return new IllegalStateException(format("Unexpected value: %s", value));
    }
}
