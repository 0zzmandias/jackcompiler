package jackcompiler.syntax;

/** A node in the concrete syntax tree of a Jack source file. */
public sealed interface ParseNode permits GrammarNode, TerminalNode {}
