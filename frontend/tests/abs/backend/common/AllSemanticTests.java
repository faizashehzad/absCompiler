package abs.backend.common;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses( {
        PrimitiveTypes.class,
        StmtTests.class,
        ObjectTests.class,
        FunctionalTests.class,
        ConcurrencyTests.class,
        StdLibTests.class}
        )
public class AllSemanticTests {

}
