package org.mendrugo.qollider;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.lang.String.format;

// TODO move to Marker (or Id when refactored?)
final class Hashing
{
    static final MessageDigest SHA1 = getSha1();

    static String sha1(String s)
    {
        SHA1.update(s.getBytes(StandardCharsets.UTF_8));
        return format("%x", new BigInteger(1, SHA1.digest()));
    }

    private static MessageDigest getSha1()
    {
        try
        {
            return MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}
