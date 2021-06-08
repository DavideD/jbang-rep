///usr/bin/env jbang "$0" "$@" ; exit $?
// //DEPS <dependency1> <dependency2>
//JAVA 16

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.*;

import static java.lang.System.*;

// Replace '...' in this code with proper Java expressions so that the first println prints 'true'
// and the second one prints 'false' when running on Java 16.
// This is a fair puzzle:
//        - No reflection;
//        - No hacking the output stream;
//        - No unchecked code (e. g., List<StringBuilder> contains StringBuilder objects only);
//        - No hidden replacement of library classes (List is standard java.util.List, Set is java.util.Set, etc.).
public class StringBuilderInHashMap {

    public static void main(String... args) {
        // The real code should be:
        // List<StringBuilder> list = ...;
        // StringBuilder sb = ...;
        List<StringBuilder> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        Set<StringBuilder> set = new HashSet<>( list );
        set.add( sb );
        out.println( set.contains( sb ) );
        sb.append( "oops" );
        out.println( set.contains( sb ) );
    }
}
