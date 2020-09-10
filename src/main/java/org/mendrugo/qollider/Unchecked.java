package org.mendrugo.qollider;

final class Unchecked
{
    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj)
    {
        return (T) obj;
    }
}
