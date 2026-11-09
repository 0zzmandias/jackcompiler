package jackcompiler.compiler;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Catalog of the subroutines declared in one Jack class, built before compiling their bodies. */
final class SubroutineCatalog {

  enum Kind {
    CONSTRUCTOR,
    FUNCTION,
    METHOD
  }

  /** A subroutine signature with only its explicitly declared parameter types. */
  record Signature(String name, Kind kind, String returnType, List<String> parameterTypes) {

    Signature {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(returnType, "returnType");
      parameterTypes = List.copyOf(Objects.requireNonNull(parameterTypes, "parameterTypes"));
    }

    int parameterCount() {
      return parameterTypes.size();
    }

    boolean isMethod() {
      return kind == Kind.METHOD;
    }
  }

  private final String className;
  private final Map<String, Signature> signatures;

  private SubroutineCatalog(String className, Map<String, Signature> signatures) {
    this.className = className;
    this.signatures = Map.copyOf(signatures);
  }

  /** Scans the complete class CST once, including subroutines declared after their callers. */
  static SubroutineCatalog fromClass(GrammarNode root) {
    Objects.requireNonNull(root, "root");
    if (root.tag() != SyntaxTag.CLASS) {
      throw CompilationException.atNode("Expected a Jack class declaration.", root);
    }

    String className;
    try {
      SyntaxTreeNavigator.tokenAt(root, 0, TokenType.KEYWORD, "class");
      className = SyntaxTreeNavigator.tokenAt(root, 1, TokenType.IDENTIFIER).value();
    } catch (IllegalArgumentException cause) {
      throw CompilationException.atNode("Malformed class declaration.", root, cause);
    }

    Map<String, Signature> collected = new LinkedHashMap<>();
    for (GrammarNode declaration :
        SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC)) {
      Signature signature = readSignature(declaration);
      if (collected.putIfAbsent(signature.name(), signature) != null) {
        Token name = SyntaxTreeNavigator.tokenAt(declaration, 2, TokenType.IDENTIFIER);
        throw new CompilationException(
            "Duplicate subroutine '" + signature.name() + "' in class '" + className + "'.",
            name.position());
      }
    }

    return new SubroutineCatalog(className, collected);
  }

  String className() {
    return className;
  }

  int size() {
    return signatures.size();
  }

  /** Resolves only a subroutine declared in this class, without guessing external signatures. */
  Optional<Signature> resolve(String name) {
    return Optional.ofNullable(signatures.get(Objects.requireNonNull(name, "name")));
  }

  private static Signature readSignature(GrammarNode declaration) {
    try {
      SyntaxTreeNavigator.requireChildCount(declaration, 7);
      String keyword = SyntaxTreeNavigator.tokenAt(declaration, 0, TokenType.KEYWORD).value();
      Kind kind =
          switch (keyword) {
            case "constructor" -> Kind.CONSTRUCTOR;
            case "function" -> Kind.FUNCTION;
            case "method" -> Kind.METHOD;
            default -> throw new IllegalArgumentException("Invalid subroutine kind: " + keyword);
          };
      String returnType = readType(declaration, 1, true);
      Token name = SyntaxTreeNavigator.tokenAt(declaration, 2, TokenType.IDENTIFIER);
      SyntaxTreeNavigator.tokenAt(declaration, 3, TokenType.SYMBOL, "(");
      GrammarNode parameters =
          SyntaxTreeNavigator.grammarAt(declaration, 4, SyntaxTag.PARAMETER_LIST);
      SyntaxTreeNavigator.tokenAt(declaration, 5, TokenType.SYMBOL, ")");
      SyntaxTreeNavigator.grammarAt(declaration, 6, SyntaxTag.SUBROUTINE_BODY);

      return new Signature(name.value(), kind, returnType, readParameterTypes(parameters));
    } catch (IllegalArgumentException cause) {
      throw CompilationException.atNode("Malformed subroutine declaration.", declaration, cause);
    }
  }

  private static List<String> readParameterTypes(GrammarNode parameters) {
    int childCount = parameters.children().size();
    if (childCount == 0) {
      return List.of();
    }
    if (childCount < 2 || (childCount - 2) % 3 != 0) {
      throw new IllegalArgumentException("Malformed parameter list.");
    }

    List<String> types = new ArrayList<>();
    for (int index = 0; index < childCount; index += 3) {
      types.add(readType(parameters, index, false));
      SyntaxTreeNavigator.tokenAt(parameters, index + 1, TokenType.IDENTIFIER);
      if (index + 2 < childCount) {
        SyntaxTreeNavigator.tokenAt(parameters, index + 2, TokenType.SYMBOL, ",");
      }
    }
    return List.copyOf(types);
  }

  private static String readType(GrammarNode parent, int index, boolean allowVoid) {
    ParseNode node = parent.children().get(index);
    if (!(node instanceof TerminalNode terminal)) {
      throw new IllegalArgumentException("Expected a Jack type terminal.");
    }
    Token token = terminal.token();
    if (token.type() == TokenType.IDENTIFIER) {
      return token.value();
    }
    if (token.type() == TokenType.KEYWORD) {
      return switch (token.value()) {
        case "int", "char", "boolean" -> token.value();
        case "void" -> {
          if (!allowVoid) {
            throw new IllegalArgumentException("Void is not a parameter type.");
          }
          yield "void";
        }
        default -> throw new IllegalArgumentException("Invalid Jack type: " + token.value());
      };
    }
    throw new IllegalArgumentException("Invalid Jack type token: " + token.type());
  }
}
