package org.mendrugo.qollider;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class URIs
{
    static Map<String, String> splitQuery(URI uri)
    {
        if (uri.getRawQuery() == null || uri.getRawQuery().isEmpty())
        {
            return Collections.emptyMap();
        }

        return Stream.of(uri.getRawQuery().split("&"))
            .map(e -> e.split("="))
            .collect(
                Collectors.toMap(
                    e -> URLDecoder.decode(e[0], StandardCharsets.UTF_8)
                    , e -> URLDecoder.decode(e[1], StandardCharsets.UTF_8)
                )
            );
    }
}
