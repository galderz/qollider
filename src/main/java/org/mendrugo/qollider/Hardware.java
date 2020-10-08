package org.mendrugo.qollider;

import java.util.Locale;
import java.util.regex.Pattern;

final class Hardware
{
    static final Pattern X64 = Pattern.compile("^(x8664|amd64|ia32e|em64t|x64)$");
    static final Pattern AARCH64 = Pattern.compile("^(aarch64)$");

    enum Arch
    {
        X64
        , AARCH64
        , UNKNOWN
        ;

        @Override
        public String toString()
        {
            return super.toString().toLowerCase();
        }
    }

    // TODO aarch() not unit tested, limit to getting the property
    static Arch arch()
    {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
        if (X64.matcher(arch).matches())
        {
            return Arch.X64;
        }
        else if (AARCH64.matcher(arch).matches())
        {
            return Arch.AARCH64;
        }
        else
        {
            throw new IllegalArgumentException(String.format(
                "Unsupported architecture: %s"
                , arch
            ));
        }
    }
}
