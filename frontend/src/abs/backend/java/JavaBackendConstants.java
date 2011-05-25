/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.java;

import abs.backend.java.lib.expr.Let;
import abs.backend.java.lib.runtime.ABSAndGuard;
import abs.backend.java.lib.runtime.ABSExpGuard;
import abs.backend.java.lib.runtime.ABSFutureGuard;
import abs.backend.java.lib.runtime.ABSRuntime;
import abs.backend.java.lib.types.ABSType;

public class JavaBackendConstants {
    public static String LIB_TYPES_PACKAGE = ABSType.class.getPackage().getName();
    public static String LIB_EXPR_PACKAGE = Let.class.getPackage().getName();
    public static String LIB_IMPORT_STATEMENT = "import " + LIB_TYPES_PACKAGE + ".*; " + "import " + LIB_EXPR_PACKAGE
            + ".*;";
    public static String ABSRUNTIME = ABSRuntime.class.getName();
    public static String ANDGUARD = ABSAndGuard.class.getName();
    public static String CLAIMGUARD = ABSFutureGuard.class.getName();
    public static String EXPGUARD = ABSExpGuard.class.getName();

}
