package com.stuffwithstuff.magpie.interpreter.builtin;

public class RuntimeBuiltIns {
  /*
  // TODO(bob): All of these are pretty hacked together. Need to rationalize
  // the scope for these and clean them up.
  
  @Shared
  @Signature("throw(obj -> Never)")
  public static class Throw_ implements BuiltInCallable {
    public Obj invoke(Interpreter interpreter, Obj thisObj, Obj arg) {
      throw new ErrorException(arg);
    }
  }
  
  @Shared
  @Signature("debugDump(object ->)")
  public static class DebugDump implements BuiltInCallable {
    public Obj invoke(Interpreter interpreter, Obj thisObj, Obj arg) {
      
      StringBuilder builder = new StringBuilder();
      dumpObject(builder, arg, "", "");
      System.out.print(builder);
      
      return interpreter.nothing();
    }
  }
  
  private static void dumpObject(StringBuilder builder, Obj object,
      String name, String indent) {
    builder.append(indent);
    
    if (name.length() > 0) {
      builder.append(name).append(": ");
    }
    
    if (object.getValue() != null) {
      builder.append(object.getValue()).append(" ");
    }
    
    builder.append("(").append(object.getClassObj().getName()).append(")\n");
    
    // Don't recurse too deep in case we have a cyclic structure.
    if (indent.length() > 6) return;
    
    for (Entry<String, Obj> field : object.getFields().entries()) {
      dumpObject(builder, field.getValue(), field.getKey(), indent + "  ");
    }
  }
  */
}
