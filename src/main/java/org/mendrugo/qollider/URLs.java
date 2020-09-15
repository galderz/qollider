package org.mendrugo.qollider;

import java.net.MalformedURLException;
import java.net.URL;

final class URLs
{
    static URL of(String url)
    {
        try
        {
            return new URL(url);
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
    }

    static URL of(String format, Object... args)
    {
        return URLs.of(String.format(format, args));
    }
}
