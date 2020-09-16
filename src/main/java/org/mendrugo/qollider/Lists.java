package org.mendrugo.qollider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Lists
{
    static <E> List<E> append(E element, List<? extends E> list)
    {
        final var result = new ArrayList<E>(list);
        result.add(element);
        return Collections.unmodifiableList(result);
    }

    static <E> List<E> prepend(E element, List<E> list)
    {
        final var result = new ArrayList<E>(list.size() + 1);
        result.add(element);
        result.addAll(list);
        return Collections.unmodifiableList(result);
    }

    static <E> List<E> concat(List<? extends E> l1, List<? extends E> l2)
    {
        return Stream.of(l1, l2)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    static <E> List<E> merge(E first, List<E> list, E last)
    {
        final var result = new ArrayList<E>(list.size() + 1);
        result.add(first);
        result.addAll(list);
        result.add(last);
        return Collections.unmodifiableList(result);
    }

//    // TODO make it typesafe
//    static <E> List<E> flatten(Object... elements)
//    {
//        final var result = new ArrayList<E>();
//        for (Object element : elements)
//        {
//            if (element instanceof List<?> l)
//            {
//                result.addAll(Unchecked.cast(l));
//            }
//            else
//            {
//                result.add(Unchecked.cast(element));
//            }
//        }
//        return result;
//    }

    @SafeVarargs
    static <E> List<E> mutable(E... a)
    {
        return new ArrayList<>(List.of(a));
    }
}
