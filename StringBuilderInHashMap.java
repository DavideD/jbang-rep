///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
// JAVA 16

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.System.*;

public class StringBuilderInHashMap {

    public static void main(String... args) {
        List<StringBuilder> list = null; //...
        StringBuilder sb = null; // ...
        Set<StringBuilder> set = new HashSet<>( list );
        set.add( sb );
        out.println( set.contains( sb ) );
        sb.append( "oops" );
        out.println( set.contains( sb ) );
    }
}
