package com.stuffwithstuff.magpie.parser;

import java.util.HashMap;
import java.util.Map;

import com.stuffwithstuff.magpie.SourceReader;
import com.stuffwithstuff.magpie.parser.Token;
import com.stuffwithstuff.magpie.parser.TokenType;

public class Lexer implements TokenReader {

  public Lexer(SourceReader text) {
    mText = text;
    mLine = 1;
    mCol = 1;
    
    mStartLine = 1;
    mStartCol = 1;
    mRead = "";
  }

  public Token readToken() {
    char c = advance();
    switch (c) {
      // Whitespace.
    case ' ':
    case '\t':
      return readWhitespace();

      // Punctuators.
    case '(': return makeToken(TokenType.LEFT_PAREN);
    case ')': return makeToken(TokenType.RIGHT_PAREN);
    case '[': return makeToken(TokenType.LEFT_BRACKET);
    case ']': return makeToken(TokenType.RIGHT_BRACKET);
    case '{': return makeToken(TokenType.LEFT_BRACE);
    case '}': return makeToken(TokenType.RIGHT_BRACE);
    case '`': return makeToken(TokenType.BACKTICK);
    case ',': return makeToken(TokenType.COMMA);

      // Match line ending characters.
    case ';':
    case '\n':
    case '\r':
      return makeToken(TokenType.LINE);

      // Strings.
    case '"':
      return readString();
      
      // Comments.
    case '/':
      switch (peek()) {
      case '/': return readLineComment();
      case '*': return readBlockComment();
      default:  return readName();
      }
      
      // Need to handle numeric literals.
    case '-':
      if (isDigit(peek())) {
        // A negative number.
        return readNumber();
      } else if (isOperator(peek())) {
        // An operator starting with "-".
        return readName();
      } else {
        // A "-" by itself.
        return makeToken(TokenType.NAME);
      }
      
    case '\\':
      return makeToken(TokenType.LINE_CONTINUATION);
      
      // EOF.
    case '\0': return makeToken(TokenType.EOF);
    
    default:
      if (isName(c)) {
        // Identifier.
        return readName();
      } else if (isOperator(c)) {
        // Operator.
        return readOperator();
      } else if (isDigit(c)) {
        // Number.
        return readNumber();
      } else {
        // TODO(bob): Emit error token instead of throwing.
        throw new ParseException(Position.none(), "Unknown character.");
      }
    }
  }
  
  private Token readWhitespace() {
    while (true) {
      switch (peek()) {
      case ' ':
      case '\t':
        advance();
        break;
        
      default:
        return makeToken(TokenType.WHITESPACE);
      }
    }
  }
  
  private Token readString() {
    StringBuilder escaped = new StringBuilder();
    
    while (true) {
      char c = advance();
      switch (c) {
      case '\\':
        // String escape.
        char e = advance();
        switch (e) {
        case 'b': escaped.append("\b"); break;
        case 'f': escaped.append("\f"); break;
        case 'n': escaped.append("\n"); break;
        case 'r': escaped.append("\r"); break;
        case 't': escaped.append("\t"); break;
        case '"': escaped.append("\""); break;
        case '\\': escaped.append("\\"); break;
        case '0': escaped.append("\0"); break;
        
        case 'x':
          int a = readHexDigit();
          int b = readHexDigit();
          int code = (a << 4) | b;
          // TODO(bob): 4-digit escape code too.
          escaped.append(Character.toChars(code)[0]);
          break;
          
        default:
          // TODO(bob): Emit error token instead of throwing.
          throw new ParseException(Position.none(), "Unknown string escape.");
        }
        break;
        
      case '"':
        return makeToken(TokenType.STRING, escaped.toString());
       
        // TODO(bob): Emit error token instead of throwing.
      case '\0': throw new ParseException(Position.none(), "Unterminated string.");
      
      default:
        escaped.append(c);
      }
    }
  }
  
  private int readHexDigit() {
    char c = Character.toLowerCase(advance());
    int digit = "0123456789abcdef".indexOf(c);
    if (digit == -1) {
      // TODO(bob): Emit error token instead of throwing.
      throw new ParseException(Position.none(), "Expected hex digit.");
    }
    
    return digit;
  }
  
  private Token readLineComment() {
    advance(); // Consume second "/".

    int slashCount = 2;

    // Consume any number of additional leading "/".
    while (peek() == '/') {
      ++slashCount;
      advance();
    }
    
    // See if it's a "///" doc comment.
    boolean isDoc = slashCount == 3;
      
    while (true) {
      switch (peek()) {
      case '\n':
      case '\r':
      case '\0':
        String value = mRead.substring(slashCount).trim();
        return makeToken(
            isDoc ? TokenType.DOC_COMMENT : TokenType.LINE_COMMENT, value);
        
      default:
        advance();
      }
    }
  }

  private Token readBlockComment() {
    while (true) {
      switch (advance()) {
      case '*':
        switch (advance()) {
        case '/': return makeToken(TokenType.BLOCK_COMMENT);
        // TODO(bob): Emit error token instead of throwing.
        case '\0': throw new ParseException(Position.none(), "Unterminated block comment.");
        default: // Do nothing, keep advancing.
        }
        break;
        
        // TODO(bob): Emit error token instead of throwing.
      case '\0': throw new ParseException(Position.none(), "Unterminated block comment.");
      default: // Do nothing, keep advancing.
      }
    }
  }

  private Token readName() {
    while (true) {
      if (isName(peek()) || isDigit(peek())) {
        advance();
      } else if (peek() == ':') {
        advance();
        
        // Trim off the ":".
        String value = mRead.substring(0, mRead.length() - 1);
        return makeToken(TokenType.FIELD, value);
      } else {
        return makeToken(TokenType.NAME);
      }
    }
  }

  private Token readOperator() {
    while (true) {
      if (isName(peek()) || isOperator(peek())) {
        advance();
      } else if (peek() == ':') {
        advance();
        
        // Trim off the ":".
        String value = mRead.substring(0, mRead.length() - 1);
        return makeToken(TokenType.FIELD, value);
      } else {
        return makeToken(TokenType.NAME);
      }
    }
  }
  
  private Token readNumber() {
    while (true) {
      if (isDigit(peek())) {
        advance();
      } else if (peek() == ':') {
        advance();
        
        // Trim off the ":".
        String value = mRead.substring(0, mRead.length() - 1);
        return makeToken(TokenType.FIELD, value);
      } else {
        return makeToken(TokenType.INT, Integer.parseInt(mRead));
      }
    }
  }
  
  private char peek() {
    return mText.current();
  }
  
  private char advance() {
    char c = mText.current();
    mText.advance();

    mRead += c;

    // Update the position.
    if (c == '\n') {
      mLine++;
      mCol = 1;
    } else {
      mCol++;
    }

    return c;
  }

  private Token makeToken(TokenType type) {
    return makeToken(type, mRead);
  }
  
  private Token makeToken(TokenType type, Object value) {
    // Handle reserved words.
    if (type == TokenType.NAME) {
      if (sKeywords.containsKey(mRead)) {
        type = sKeywords.get(mRead);
      } else if (mRead.equals("false")) {
        type = TokenType.BOOL;
        value = false;
      } else if (mRead.equals("true")) {
        type = TokenType.BOOL;
        value = true;
      }
    }
    
    Token token = new Token(currentPosition(), type, mRead, value);
    
    mStartLine = mLine;
    mStartCol = mCol;
    mRead = "";
    
    return token;
  }
  
  private Position currentPosition() {
    return new Position(mText.getDescription(),
        mStartLine, mStartCol, mLine, mCol);
  }
  
  private boolean isDigit(final char c) {
    return (c >= '0') && (c <= '9');
  }
  
  private boolean isName(final char c) {
    return ((c >= 'a') && (c <= 'z'))
        || ((c >= 'A') && (c <= 'Z'))
        || (c == '_') || (c == '.');
  }
  
  private boolean isOperator(final char c) {
    return ("~!$%^&*-=+|/?<>".indexOf(c) != -1);
  }
  
  private final SourceReader mText;
  private String mRead;
  private int mStartLine;
  private int mStartCol;
  private int mLine;
  private int mCol;
  
  private static final Map<String, TokenType> sKeywords;
  
  static {
    sKeywords = new HashMap<String, TokenType>();
    sKeywords.put("and",      TokenType.AND);
    sKeywords.put("as",       TokenType.AS);
    sKeywords.put("break",    TokenType.BREAK);
    sKeywords.put("case",     TokenType.CASE);
    sKeywords.put("catch",    TokenType.CATCH);
    sKeywords.put("def",      TokenType.DEF);
    sKeywords.put("defclass", TokenType.DEFCLASS);
    sKeywords.put("do",       TokenType.DO);
    sKeywords.put("else",     TokenType.ELSE);
    sKeywords.put("end",      TokenType.END);
    sKeywords.put("export",   TokenType.EXPORT);
    sKeywords.put("fn",       TokenType.FN);
    sKeywords.put("for",      TokenType.FOR);
    sKeywords.put("import",   TokenType.IMPORT);
    sKeywords.put("if",       TokenType.IF);
    sKeywords.put("in",       TokenType.IN);
    sKeywords.put("is",       TokenType.IS);
    sKeywords.put("match",    TokenType.MATCH);
    sKeywords.put("nothing",  TokenType.NOTHING);
    sKeywords.put("only",     TokenType.ONLY);
    sKeywords.put("or",       TokenType.OR);
    sKeywords.put("return",   TokenType.RETURN);
    sKeywords.put("then",     TokenType.THEN);
    sKeywords.put("throw",    TokenType.THROW);
    sKeywords.put("val",      TokenType.VAL);
    sKeywords.put("var",      TokenType.VAR);
    sKeywords.put("while",    TokenType.WHILE);
    sKeywords.put("with",     TokenType.WITH);
    
    sKeywords.put("*",    TokenType.ASTERISK);
    sKeywords.put("/",    TokenType.SLASH);
    sKeywords.put("%",    TokenType.PERCENT);
    sKeywords.put("+",    TokenType.PLUS);
    sKeywords.put("-",    TokenType.MINUS);
    sKeywords.put("<",    TokenType.LT);
    sKeywords.put(">",    TokenType.GT);
    sKeywords.put("<=",   TokenType.LTE);
    sKeywords.put(">=",   TokenType.GTE);
    sKeywords.put("=",    TokenType.EQ);
    sKeywords.put("==",   TokenType.EQEQ);
    sKeywords.put("!=",   TokenType.NOTEQ);
  }
}
