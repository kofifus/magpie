#include "Ast.h"
#include "Compiler.h"
#include "ErrorReporter.h"
#include "Method.h"
#include "MethodCompiler.h"
#include "Module.h"
#include "Object.h"
#include "Resolver.h"
#include "VM.h"

namespace magpie
{
  void Compiler::compileModule(VM& vm, ErrorReporter& reporter,
                                  gc<ModuleAst> ast, Module* module)
  {
    Compiler compiler(vm, reporter);
    
    // Every module implicitly imports core (except core itself, which is
    // assumed to be the first module loaded).
    if (vm.coreModule() != NULL)
    {
      module->imports().add(vm.coreModule());
    }
    
    for (int i = 0; i < ast->body()->expressions().count(); i++)
    {
      compiler.declareTopLevel(ast->body()->expressions()[i], module);
    }
    
    gc<Chunk> code = MethodCompiler(compiler).compileBody(module, ast->body());
    module->setBody(code);
  }
  
  gc<Chunk> Compiler::compileMultimethod(VM& vm, ErrorReporter& reporter,
                                         Multimethod& multimethod)
  {
    Compiler compiler(vm, reporter);
    return MethodCompiler(compiler).compile(multimethod);
  }
  
  void Compiler::compileExpression(VM& vm, ErrorReporter& reporter,
                                   gc<Expr> expr, Module* module)
  {
    Compiler compiler(vm, reporter);
    
    compiler.declareTopLevel(expr, module);
    
    gc<Chunk> code = MethodCompiler(compiler).compileBody(module, expr);
    module->setBody(code);
  }

  int Compiler::findMethod(gc<String> signature)
  {
    return vm_.findMultimethod(signature);
  }

  methodId Compiler::addMethod(gc<Method> method)
  {
    return vm_.addMethod(method);
  }
  
  symbolId Compiler::addSymbol(gc<String> name)
  {
    return vm_.addSymbol(name);
  }

  int Compiler::addRecordType(Array<int>& nameSymbols)
  {
    return vm_.addRecordType(nameSymbols);
  }

  int Compiler::getModuleIndex(Module& module)
  {
    return vm_.getModuleIndex(module);
  }
    
  int Compiler::findNative(gc<String> name)
  {
    return vm_.findNative(name);
  }

  void Compiler::declareTopLevel(gc<Expr> expr, Module* module)
  {
    DefExpr* def = expr->asDefExpr();
    if (def != NULL)
    {
      declareMultimethod(SignatureBuilder::build(*def));
      return;
    }
    
    DefClassExpr* defClass = expr->asDefClassExpr();
    if (defClass != NULL)
    {
      declareClass(*defClass, module);
      return;
    }
    
    VariableExpr* var = expr->asVariableExpr();
    if (var != NULL)
    {
      declareVariables(var->pattern(), module);
      return;
    }
  }
  
  void Compiler::declareClass(DefClassExpr& classExpr, Module* module)
  {
    declareVariable(classExpr.pos(), classExpr.name(), module);

    // Synthesize the constructor and field accessors.
    Array<gc<DefExpr> > synthesizedMethods;
    synthesizedMethods.add(synthesizeConstructor(classExpr));

    // Create getters for fields.
    // TODO(bob): Create setters for fields.
    for (int i = 0; i < classExpr.fields().count(); i++)
    {
      synthesizedMethods.add(synthesizeGetter(classExpr, i));
    }

    // TODO(bob): Stuffing this back in the AST is a bit gross. An intermediate
    // representation would help here.
    classExpr.setSynthesizedMethods(synthesizedMethods);

    // Declare the automatically-generated methods.
    for (int i = 0; i < synthesizedMethods.count(); i++)
    {
      declareMultimethod(SignatureBuilder::build(*synthesizedMethods[i]));
    }
  }

  gc<DefExpr> Compiler::synthesizeConstructor(DefClassExpr& classExpr)
  {
    const SourcePos& pos = classExpr.pos();

    // Match the class object itself on the left.
    gc<Pattern> leftPattern = new ValuePattern(pos,
        new NameExpr(pos, classExpr.name()));

    // Match the fields on the right.
    // TODO(bob): Skip fields that have initializers.
    gc<Pattern> rightPattern;
    if (classExpr.fields().count() > 0)
    {
      Array<PatternField> fields;
      for (int i = 0; i < classExpr.fields().count(); i++)
      {
        gc<ClassField> field = classExpr.fields()[i];
        // TODO(bob): Handle field pattern.
        fields.add(PatternField(field->name(), new WildcardPattern(pos)));
      }

      rightPattern = new RecordPattern(pos, fields);
    }
    
    return new DefExpr(pos, leftPattern, String::create("new"), rightPattern,
                       gc<Pattern>(),
                       new NativeExpr(pos, String::create("objectNew")));
  }

  gc<DefExpr> Compiler::synthesizeGetter(DefClassExpr& classExpr,
                                         int fieldIndex)
  {
    // Match the class on the left.
    const SourcePos& pos = classExpr.pos();
    gc<Pattern> leftPattern = new TypePattern(pos,
        new NameExpr(pos, classExpr.name()));

    // TODO(bob): Give this a real body that returns the field!
    return new DefExpr(pos, leftPattern,
                       classExpr.fields()[fieldIndex]->name(), gc<Pattern>(),
                       gc<Pattern>(),
                       new GetFieldExpr(pos, fieldIndex));
  }

  int Compiler::declareMultimethod(gc<String> signature)
  {
    return vm_.declareMultimethod(signature);
  }
  
  void Compiler::declareVariables(gc<Pattern> pattern, Module* module)
  {
    RecordPattern* record = pattern->asRecordPattern();
    if (record != NULL)
    {
      for (int i = 0; i < record->fields().count(); i++)
      {
        declareVariables(record->fields()[i].value, module);
      }
      
      return;
    }
    
    VariablePattern* variable = pattern->asVariablePattern();
    if (variable != NULL)
    {
      declareVariable(variable->pos(), variable->name(), module);
      
      if (!variable->pattern().isNull())
      {
        declareVariables(variable->pattern(), module);
      }
    }
  }

  void Compiler::declareVariable(SourcePos pos, gc<String> name, Module* module)
  {
    // Make sure there isn't already a top-level variable with that name.
    int existing = module->findVariable(name);
    if (existing != -1)
    {
      reporter_.error(pos,
          "There is already a variable '%s' defined in this module.",
          name->cString());
    }
    
    module->addVariable(name, gc<Object>());
  }
  
  gc<String> SignatureBuilder::build(const CallExpr& expr, bool isLValue)
  {
    SignatureBuilder builder;
    
    if (!expr.leftArg().isNull())
    {
      builder.writeArg(expr.leftArg());
    }
    
    builder.add(expr.name()->cString());
    if (isLValue) builder.add("=");

    if (!expr.rightArg().isNull())
    {
      builder.add(" ");
      builder.writeArg(expr.rightArg());
    }
    
    // TODO(bob): Can you do destructuring here?
    if (isLValue) builder.add("=0:");
    
    return String::create(builder.signature_, builder.length_);
  }
  
  gc<String> SignatureBuilder::build(const DefExpr& method)
  {
    SignatureBuilder builder;
    
    if (!method.leftParam().isNull())
    {
      builder.writeParam(method.leftParam());
    }
    
    builder.add(method.name()->cString());
    if (!method.value().isNull()) builder.add("=");
    
    if (!method.rightParam().isNull())
    {
      builder.add(" ");
      builder.writeParam(method.rightParam());
    }
    
    if (!method.value().isNull())
    {
      builder.add("=");
      builder.writeParam(method.value());
    }
    
    return String::create(builder.signature_, builder.length_);
  }
  
  void SignatureBuilder::writeArg(gc<Expr> expr)
  {
    // TODO(bob): Clean up. Redundant with build().
    // If it's a record, destructure it into the signature.
    RecordExpr* record = expr->asRecordExpr();
    if (record != NULL)
    {
      for (int i = 0; i < record->fields().count(); i++)
      {
        add(record->fields()[i].name);
        add(":");
      }
      
      return;
    }
    
    // Right now, all other exprs mean "some arg goes here".
    add("0:");
  }

  void SignatureBuilder::writeParam(gc<Pattern> pattern)
  {
    // If it's a record, destructure it into the signature.
    RecordPattern* record = pattern->asRecordPattern();
    if (record != NULL)
    {
      for (int i = 0; i < record->fields().count(); i++)
      {
        add(record->fields()[i].name);
        add(":");
      }
      
      return;
    }
    
    // Any other pattern is implicitly a single-field record.
    add("0:");
  }
  
  void SignatureBuilder::add(gc<String> text)
  {
    add(text->cString());
  }
  
  void SignatureBuilder::add(const char* text)
  {
    int length = static_cast<int>(strlen(text));
    ASSERT(length_ + length < MAX_LENGTH, "Signature too long.");
    
    strcpy(signature_ + length_, text);
    length_ += strlen(text);
  }
}