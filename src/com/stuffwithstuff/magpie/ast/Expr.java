package com.stuffwithstuff.magpie.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.stuffwithstuff.magpie.ast.pattern.MatchCase;
import com.stuffwithstuff.magpie.ast.pattern.Pattern;
import com.stuffwithstuff.magpie.interpreter.Name;
import com.stuffwithstuff.magpie.parser.Position;
import com.stuffwithstuff.magpie.util.Pair;

/**
 * Base class for AST expression node classes. Any chunk of Magpie code can be
 * represented by an instance of one of the subclasses of this class.
 * 
 * <p>Also includes factory methods to create the appropriate nodes.
 * 
 * @author bob
 */
public abstract class Expr {  
  public static Expr array(Position position, List<Expr> elements) {
    return new ArrayExpr(position, elements);
  }

  public static Expr assign(Position position, String name, Expr value) {
    return new AssignExpr(position, name, value);
  }
  
  public static Expr bool(boolean value) {
    return bool(Position.none(), value);
  }
  
  public static Expr bool(Position position, boolean value) {
    return new BoolExpr(position, value);
  }

  public static Expr break_(Position position) {
    return new BreakExpr(position);
  }
  
  public static Expr call(Position position, Expr leftArg, String name, Expr rightArg) {
    return new CallExpr(position, name, Expr.record(leftArg, rightArg));
  }
  
  public static Expr call(Position position, String name, Expr arg) {
    return new CallExpr(position, name, arg);
  }
  
  public static Expr call(Position position, Expr leftArg, String name) {
    return new CallExpr(position, name, Expr.record(leftArg, Expr.nothing()));
  }

  public static Expr class_(Position position, String doc,
      String name, List<String> parents, Map<String, Field> fields) {
    return new ClassExpr(position, doc, name, parents, fields);
  }

  public static FnExpr fn(Expr body, String doc) {
    return new FnExpr(Position.none(), doc, body);
  }
  
  public static FnExpr fn(Position position, String doc, Expr body) {
    return new FnExpr(position, doc, body);
  }
  
  public static FnExpr fn(Position position, String doc, Pattern pattern,
      Expr body) {
    return new FnExpr(position, doc, pattern, body);
  }
  
  // TODO(bob): Hackish. Eliminate.
  public static Expr if_(Expr condition, Expr thenExpr, Expr elseExpr) {
    if (elseExpr == null) elseExpr = Expr.nothing();
    
    List<MatchCase> cases = new ArrayList<MatchCase>();
    cases.add(new MatchCase(Pattern.value(Expr.bool(true)), thenExpr));
    cases.add(new MatchCase(elseExpr));
    
    return match(Position.surrounding(condition, elseExpr),
        condition,
        cases);
  }
  
  public static Expr import_(Position position, String scheme, String module,
      String prefix, boolean isOnly,
      List<ImportDeclaration> declarations) {
    return new ImportExpr(position, scheme, module, prefix, isOnly,
        declarations);
  }
  
  public static Expr int_(int value) {
    return int_(Position.none(), value);
  }
  
  public static Expr int_(Position position, int value) {
    return new IntExpr(position, value);
  }
  
  public static Expr loop(Position position, Expr body) {
    return new LoopExpr(position, body);
  }
  
  public static Expr match(Position position,
      Expr value, List<MatchCase> cases) {
    return new MatchExpr(position, value, cases);
  }
  
  public static Expr method(Position position, String doc, String name) {
    return method(position, doc, name, null, null);
  }
  
  public static Expr method(Position position, String doc, String name,
      Pattern pattern, Expr body) {
    return new MethodExpr(position, doc, name, pattern, body);
  }

  public static Expr name(Position position, String name) {
    return new NameExpr(position, name);
  }
  
  public static Expr name(String name) {
    return name(Position.none(), name);
  }

  public static Expr nothing() {
    return nothing(Position.none());  
  }
  
  public static Expr nothing(Position position) {
    return new NothingExpr(Position.none());  
  }

  public static Expr quote(Position position, Expr body) {
    return new QuoteExpr(position, body);
  }

  public static Expr record(Position position,
      List<Pair<String, Expr>> fields) {
    return new RecordExpr(position, fields);
  }

  public static Expr record(Expr... fields) {
    List<Pair<String, Expr>> recordFields =
        new ArrayList<Pair<String, Expr>>();
    
    for (int i = 0; i < fields.length; i++) {
      recordFields.add(new Pair<String, Expr>(
          Name.getTupleField(i), fields[i]));
    }
    
    return record(Position.surrounding(fields[0], fields[fields.length - 1]),
        recordFields);
  }

  public static Expr return_(Position position, Expr value) {
    return new ReturnExpr(position, value);
  }

  public static Expr scope(Expr body) {
    return scope(body, new ArrayList<MatchCase>());
  }

  public static Expr scope(Expr body, List<MatchCase> catches) {
    return new ScopeExpr(body, catches);
  }
  
  public static Expr sequence(List<Expr> exprs) {
    switch (exprs.size()) {
    case 0:
      return nothing();
    case 1:
      return exprs.get(0);
    default:
      return new SequenceExpr(exprs);
    }
  }
  
  public static Expr sequence(Expr... exprs) {
    return sequence(Arrays.asList(exprs));
  }

  public static Expr string(String text) {
    return string(Position.none(), text);
  }

  public static Expr string(Position position, String text) {
    return new StringExpr(position, text);
  }

  public static Expr throw_(Position position, Expr value) {
    return new ThrowExpr(position, value);
  }

  public static Expr unquote(Position position, Expr value) {
    return new UnquoteExpr(position, value);
  }

  public static Expr var(Position position, boolean isMutable, String name, Expr value) {
    return var(position, isMutable, Pattern.variable(name), value);
  }
  
  public static Expr var(Position position, boolean isMutable, Pattern pattern, Expr value) {
    return new VarExpr(position, isMutable, pattern, value);
  }

  public Expr(Position position, String doc) {
    mPosition = position;
    mDoc = doc;
  }
  
  public Expr(Position position) {
    this(position, "");
  }
  
  public Position getPosition() { return mPosition; }
  
  public String getDoc() { return mDoc; }
  
  public boolean isLiteral() {
    return false;
  }
  
  public abstract <TReturn, TContext> TReturn accept(
      ExprVisitor<TReturn, TContext> visitor, TContext context);
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder, "");
    return builder.toString();
  }
  
  public abstract void toString(StringBuilder builder, String indent);
  
  private final Position mPosition;
  private final String mDoc;
}

