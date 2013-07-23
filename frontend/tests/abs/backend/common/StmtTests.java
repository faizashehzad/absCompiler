/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import abs.backend.BackendTestDriver;

@RunWith(Parameterized.class)
public class StmtTests extends SemanticTests {

    public StmtTests(BackendTestDriver d) {
        super(d);
    }

    @Test
    public void ifThen() {
        assertEvalTrue("{ Bool testresult = False; if (True) testresult = True;  }");
    }

    @Test
    public void ifFalse() {
        assertEvalTrue("{ Bool testresult = False; if (False) skip; else testresult = True;  }");
    }

    @Test
    public void whileFalse() {
        assertEvalTrue("{ Bool testresult = False; while (False) { } testresult = True;  }");
    }

    @Test
    public void assignStmt() {
        assertEvalTrue("{ Bool testresult = False; Bool x = True; testresult = x; }");
    }
    
    @Test
    public void declIfExpStmt() {
        // dynamic Java backend generated invalid code for this case
        assertEvalTrue("{Bool x = True; Int i = if x then 1 else 2; Bool testresult = (i == 1); }");
    }

    @Test
    public void assignIfExpStmt() {
        // dynamic Java backend generated invalid code for this case
        assertEvalTrue("{Bool x = True; Int i = 0; i = if x then 1 else 2; Bool testresult = (i == 1); }");
    }

    @Test
    public void useOfVariablesInsideExpr() {
        assertEvalTrue("{ Bool testresult = False; Bool b = True; testresult = case b { _ => b; }; }");
    }

}
