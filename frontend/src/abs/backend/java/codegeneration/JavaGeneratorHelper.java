/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package abs.backend.java.codegeneration;

import java.io.PrintStream;

import abs.backend.java.JavaBackend;
import abs.backend.java.lib.runtime.ABSBuiltInFunctions;
import abs.backend.java.lib.runtime.ABSFut;
import abs.backend.java.lib.runtime.ABSRuntime;
import abs.backend.java.lib.runtime.Task;
import abs.backend.java.lib.types.ABSValue;
import abs.common.Position;
import abs.frontend.ast.ASTNode;
import abs.frontend.ast.AsyncCall;
import abs.frontend.ast.ClassDecl;
import abs.frontend.ast.Decl;
import abs.frontend.ast.FnApp;
import abs.frontend.ast.FunctionDecl;
import abs.frontend.ast.List;
import abs.frontend.ast.MethodImpl;
import abs.frontend.ast.MethodSig;
import abs.frontend.ast.ParamDecl;
import abs.frontend.ast.ParametricDataTypeDecl;
import abs.frontend.ast.ParametricFunctionDecl;
import abs.frontend.ast.PureExp;
import abs.frontend.ast.ReturnStmt;
import abs.frontend.ast.Stmt;
import abs.frontend.ast.TypeParameterDecl;
import abs.frontend.typechecker.Type;
import abs.frontend.typechecker.TypeCheckerHelper;
import beaver.Symbol;

public class JavaGeneratorHelper {

    private static final String FLI_METHOD_PREFIX = "fli_";

    public static void generateHelpLine(ASTNode<?> node, PrintStream stream) {
        Position pos = new Position(node);
        stream.println("// " + pos.getPositionString());
    }

    public static void generateArgs(PrintStream stream, List<PureExp> args) {
        generateArgs(stream, null, args);
    }

    public static void generateArgs(PrintStream stream, String firstArg, List<PureExp> args) {
        stream.print("(");
        boolean first = true;

        if (firstArg != null) {
            stream.print(firstArg);
            first = false;
        }

        for (PureExp e : args) {
            if (!first)
                stream.print(", ");

            e.generateJava(stream);
            first = false;
        }
        stream.print(")");

    }

    public static void generateParamArgs(PrintStream stream, List<ParamDecl> params) {
        generateParamArgs(stream,null,params);
    }
    public static void generateParamArgs(PrintStream stream, String firstArg, List<ParamDecl> params) {
        stream.print("(");
        boolean first = true;
        
        if (firstArg != null) {
            stream.print(firstArg);
            first = false;
        }

        for (ParamDecl d : params) {
            if (!first)
                stream.print(", ");
            stream.print(JavaBackend.getVariableName(d.getName()));
            first = false;
        }
        stream.print(")");
    }

    public static void generateParams(PrintStream stream, List<ParamDecl> params) {
        generateParams(stream, null, params);
    }

    public static void generateParams(PrintStream stream, String firstArg, List<ParamDecl> params) {
        stream.print("(");

        boolean first = true;
        if (firstArg != null) {
            stream.print(firstArg);
            first = false;
        }

        for (ParamDecl d : params) {
            if (!first)
                stream.print(", ");
            // stream.print("final ");
            d.generateJava(stream);
            first = false;
        }
        stream.print(")");
    }

    public static void generateTypeParameters(PrintStream stream, Decl dtd,
            boolean plusExtends) {
        List<TypeParameterDecl> typeParams = null;
        if (dtd instanceof ParametricDataTypeDecl) {
            typeParams = ((ParametricDataTypeDecl)dtd).getTypeParameters();
        } else
        if (dtd instanceof ParametricFunctionDecl) {
            typeParams = ((ParametricFunctionDecl)dtd).getTypeParameters();
        } else
            return;
        if (typeParams.getNumChild() > 0) {
            stream.print("<");
            boolean isFirst = true;
            for (TypeParameterDecl d : typeParams) {
                if (isFirst)
                    isFirst = false;
                else
                    stream.print(",");
                stream.print(d.getName());
                if (plusExtends)
                    stream.print(" extends " + ABSValue.class.getName());
            }
            stream.print(">");
        }
    }

    public static void generateBuiltInFnApp(PrintStream stream, FnApp app) {
        FunctionDecl d = (FunctionDecl) app.getDecl();
        String name = d.getName();
        stream.print(ABSBuiltInFunctions.class.getName() + "." + name);
        generateArgs(stream, app.getParams());
    }

    public static String getDebugString(Stmt stmt) {
        return getDebugString(stmt, stmt.getStart());
    }

    public static String getDebugString(Stmt stmt, int pos) {
        int line = Symbol.getLine(pos);
        String fileName = stmt.getCompilationUnit().getFileName().replace("\\", "\\\\");
        return "if (__ABS_getRuntime().debuggingEnabled()) __ABS_getRuntime().nextStep(\""
                + fileName + "\"," + line + ");";
    }
    
    public static void generateMethodSig(String indent, PrintStream stream, MethodSig sig, boolean async) {
        generateMethodSig(indent, stream, sig, async, "", "");
    }
    
    public static void generateMethodSig(String indent, PrintStream stream, MethodSig sig, boolean async, String modifier, String prefix) {
       JavaGeneratorHelper.generateHelpLine(sig,stream);
       stream.print(indent+"public "+modifier+" ");
       if (async) {
           prefix = "async_";
           stream.print(ABSFut.class.getName()+"<");
       }
       
       sig.getReturnType().generateJava(stream);
       
       if (async)
           stream.print(">");
       stream.print(" "+prefix+JavaBackend.getMethodName(sig.getName()));
       JavaGeneratorHelper.generateParams(stream, sig.getParams());
    }
    
    public static void generateAsyncMethod(String indent, PrintStream stream, MethodImpl method) {
       final MethodSig sig = method.getMethodSig();
       generateMethodSig(indent,stream,sig,true,"final","");
       stream.println("{ return ("+ABSFut.class.getName()+")");
       generateAsyncCall(stream, "this", null, method.getContextDecl().getType(), null, sig.getParams(), 
             TypeCheckerHelper.getTypes(sig.getParams()),sig.getName());
       stream.println(indent+"; }");
    }
    
    
    public static void generateAsyncCall(PrintStream stream, AsyncCall call) {
       final PureExp callee = call.getCallee();
       final List<PureExp> params = call.getParams();
       final String method = call.getMethod();
       
       generateAsyncCall(stream, null, callee, callee.getType(), params, null, 
             TypeCheckerHelper.getTypesFromExp(params), method);
       
    }

    private static void generateAsyncCall(PrintStream stream, final String calleeString, 
          final PureExp callee, final Type calleeType, final List<PureExp> args, 
          final List<ParamDecl> params,
          final java.util.List<Type> paramTypes,
          final String method) 
    {
        stream.print(ABSRuntime.class.getName()+".asyncCall(");
        String targetType = JavaBackend.getQualifiedString(calleeType);
        stream.print("new "+Task.class.getName()+"<"+targetType+">(this,");
        stream.print(ABSRuntime.class.getName()+".checkForNull(");
        if (calleeString != null)
            stream.print(calleeString);
        else
            callee.generateJava(stream);
        stream.print(")) {");
        int i = 0;
        for (Type t : paramTypes) {
            stream.print(JavaBackend.getQualifiedString(t)+" arg"+i+";");
            i++;
        }

        generateTaskGetArgsMethod(stream, paramTypes.size());
        generateTaskInitMethod(stream, paramTypes);

        stream.print(" public java.lang.String methodName() { return \""+method+"\"; }");

        stream.print(" public Object execute() {");
        stream.print(" return target."+JavaBackend.getMethodName(method)+"(");
        generateArgStringList(stream, paramTypes.size());
        stream.print(");");
        stream.println(" }}");
        stream.print("     .init");
        if (args != null)
            JavaGeneratorHelper.generateArgs(stream,args);
        else
            JavaGeneratorHelper.generateParamArgs(stream, params);
        stream.print(")");
    }

    private static void generateTaskInitMethod(PrintStream stream, final java.util.List<Type> paramTypes) {
        int i;
        stream.print("    public "+Task.class.getName()+"<?> init(");
           i = 0;
           for (Type t : paramTypes) {
               if (i > 0) stream.print(",");
               stream.print(JavaBackend.getQualifiedString(t)+" _arg"+i);
               i++;
           }
           stream.print(") {");
           for (i = 0; i < paramTypes.size(); i++) {
               stream.print("arg"+i+" = _arg"+i+";");
           }
           stream.print(" return this; }");
    }

    private static void generateTaskGetArgsMethod(PrintStream stream, final int n) {
        stream.print(" protected "+ABSValue.class.getName()+"[] getArgs() { return new "+ABSValue.class.getName()+"[] { ");
           generateArgStringList(stream, n);
           stream.print(" }; } ");
    }

    private static void generateArgStringList(PrintStream stream, final int n) {
        for (int i = 0; i < n; i++) {
               if (i > 0) stream.print(",");
               stream.print("arg"+i);
           }
    }

    
    public static void generateClassDecl(PrintStream stream, final ClassDecl decl) {
        new ClassDeclGenerator("", stream, decl).generate();
    }
    
    public static void generateMethodImpl(String indent, PrintStream stream, final MethodImpl m) {
      // Async variant
      JavaGeneratorHelper.generateAsyncMethod(indent,stream,m);

        // Sync variant
      generateMethodSig(indent,stream,m.getMethodSig(),false,"final","");
      generateMethodBody(indent, stream, m, false);
      
      if (m.isForeign()) {
          generateFLIMethod(indent,stream,m);
      }      
        
    }

    private static void generateMethodBody(String indent, PrintStream stream, final MethodImpl m, boolean isFliMethod) {
        boolean addReturn = false;
        if (m.getMethodSig().getReturnType().getType().isUnitType()) {
          if (m.getBlock().getNumStmt() == 0 ||
              (! (m.getBlock().getStmt(m.getBlock().getNumStmt()-1) instanceof ReturnStmt))) {
            addReturn = true;
          }
        }

        stream.println("{ __ABS_checkSameCOG(); ");
          
          if (!isFliMethod && m.isForeign()) {
              stream.print(indent+"return this.");
              stream.print(FLI_METHOD_PREFIX+JavaBackend.getMethodName(m.getMethodSig().getName()));
              JavaGeneratorHelper.generateParamArgs(stream, m.getMethodSig().getParams());
              stream.println(";");
          } else {
              stream.println("    if (__ABS_getRuntime().debuggingEnabled()) {");
              stream.println("       "+Task.class.getName()+"<?> __ABS_currentTask = __ABS_getRuntime().getCurrentTask();");
              stream.println("       __ABS_currentTask.newStackFrame(this,\""+m.getMethodSig().getName()+"\");");
              for (ParamDecl d : m.getMethodSig().getParams()) {
                  stream.print("     __ABS_currentTask.setLocalVariable(");
                  stream.println("\""+d.getName()+"\","+d.getName()+");");
              }
              stream.println("    }");
              m.getBlock().generateJava(indent, stream, addReturn);
          
          }
          stream.println("}");
    }

    private static void generateFLIMethod(String indent, PrintStream stream, MethodImpl m) {
        generateMethodSig(indent, stream, m.getMethodSig(), false, "", FLI_METHOD_PREFIX);
        generateMethodBody(indent,stream,m, true);
    }
    
}