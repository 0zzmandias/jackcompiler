# Arquitetura — Parte 1 (analisador léxico)

Este documento descreve a arquitetura **implementada** do JackCompiler até o incremento 21, no commit `091f66e`. A Parte 2 permanece planejada, não implementada.

## Limites e direção das dependências

| Pacote | Responsabilidade | Dependências internas permitidas |
|---|---|---|
| `jackcompiler.token` | Tipos compartilhados imutáveis (`TokenType`, `Token`, `SourcePosition`, `TokenizationResult`) | Nenhuma |
| `jackcompiler.lexer` | Reconhecimento léxico sem arquivos, XML ou CLI | `token` |
| `jackcompiler.xml` | Escape XML e formatação de tokens | `token` |
| `jackcompiler.io` | Descoberta de fontes, UTF-8, resolução e escrita da saída | Nenhuma |
| `jackcompiler.application` | Caso de uso e contextualização de falhas | `lexer`, `token` por contrato de retorno, `xml`, `io` |
| `jackcompiler.cli` | Argumentos, help, integração e códigos de saída | `application`, exceção léxica pública para diagnóstico |

A dependência parte de `cli` para `application` e serviços especializados; **`application` não depende de `cli`**. O scanner **não importa** `io`, `xml`, `application`, `cli`, `parser` ou `syntax`. Não existem módulos de parser nem de árvore sintática na Parte 1.

## Fluxo de execução

```text
java -jar target/jackcompiler.jar
  -> cli.JackCompiler
     -> cli.CliArgumentParser -> cli.CliArguments
     -> application.TokenizeCommand
        -> io.JackFileResolver
        -> io.JackSourceReader
        -> lexer.JackTokenizer -> token.TokenizationResult
        -> xml.TokenXmlWriter
        -> io.OutputPathResolver
        -> io.TextFileWriter
     -> stdout (resultados) / stderr (erros)
```

`JackCompiler` é a classe principal do manifesto. `CliArgumentParser` não acessa o sistema de arquivos; valida a sintaxe `tokenize <entrada> [--output-dir <diretório>]` ou `--help`. `CliArguments` valida a combinação de comando e caminhos e normaliza os caminhos. O caso de uso recebe diretamente `Path` e `Optional<Path>`, sem conhecer objetos da CLI.

## Representação dos tokens

- `TokenType`: enum pública com `KEYWORD`, `SYMBOL`, `INTEGER_CONSTANT`, `STRING_CONSTANT`, `IDENTIFIER`; não contém regras XML.
- `SourcePosition`: record imutável de linha e coluna positivas, baseadas em 1.
- `Token`: record imutável com tipo, valor significativo e posição não nulos; exclui apenas as aspas delimitadoras do conteúdo de strings.
- `TokenizationResult`: tokens em ordem, copiados defensivamente em lista imutável, e posição de fim da fonte. Não há token artificial de EOF.

API reutilizável: `new JackTokenizer().tokenize(String)` retorna `TokenizationResult` ou lança `LexicalException`, que preserva a `SourcePosition`. Não exige caminho de arquivo ou acesso à CLI.

## Scanner

`JackTokenizer` cria um `SourceCursor` por chamada, descarta trivia e tenta os reconhecedores na ordem: `StringRecognizer`, `IntegerRecognizer`, `IdentifierRecognizer`, `SymbolRecognizer`. Caracteres sem reconhecedor provocam `LexicalException`; não há falha silenciosa.

`SourceCursor` percorre pontos de código Unicode, com `peek`, `peek(offset)`, `advance`, `startsWith`, `isAtEnd` e `position`. CRLF, CR e LF representam uma única quebra lógica de linha, e colunas são contadas por ponto de código.

`JackLexicalRules` centraliza as **21 keywords**, os **19 símbolos**, os caracteres de identificadores e o limite inteiro de `32767`. Os símbolos são:

```text
{ } ( ) [ ] . , ; + - * / & | < > = ~
```

**Justificativa dos colchetes:** `[` e `]` são elementos oficiais de Jack, necessários para a expressão de indexação de arrays (`a[i]`) e presentes nos fixtures de referência. O conjunto de 19 símbolos seria incompleto sem eles; a implementação os reconhece em `JackLexicalRules.isSymbol` e em `SymbolRecognizer`.

Os tipos internos do lexer permanecem com acesso de pacote: reconhecedores de identificadores, inteiros, strings e símbolos; consumidores de espaços, comentários de linha e comentários de bloco; `TriviaSkipper`; e cursor/regras internas. O reconhecimento preserva o lexema de inteiros, valida seu limite sem overflow e conserva o conteúdo literal das strings. Não há processamento de escapes do tipo `\n` dentro da sintaxe Jack.

## Formatação XML

`TokenXmlWriter.toXml(TokenizationResult)` produz `<tokens>`, um terminal por linha e `</tokens>`, sem declaração XML. O documento usa LF. A rotina `formatToken(Token)` tem acesso **interno ao pacote `xml`**, antecipando sua reutilização pelo futuro serializador sintático sem expor uma API de CLI.

`XmlEscaper` transforma `&`, `<`, `>`, aspas duplas e apóstrofos em entidades XML e rejeita pontos de código inválidos no XML 1.0. A saída em disco é UTF-8. O conteúdo de `Token` não é alterado para escapar XML; isso ocorre somente na serialização.

## Entrada, saída e falhas

1. `JackFileResolver.resolve(Path)`: arquivo `.jack` ou diretório com fontes diretamente contidas, ordenadas por nome; seleção não recursiva e extensão sensível à caixa.
2. `JackSourceReader.read(Path)`: UTF-8 estrito, sem substituir bytes inválidos; preserva texto original.
3. `OutputPathResolver.resolve(...)`: mapeia `Main.jack` para `MainT.xml`; padrão em `target/generated-tokens/` relativo ao diretório de trabalho.
4. `TextFileWriter.write(...)`: UTF-8, temporário no diretório de destino, checagem de links simbólicos e de diretórios protegidos, publicação controlada. Em `CREATE_NEW`, usa hard link para evitar substituir um arquivo existente. Em `REPLACE_EXISTING`, tenta movimento atômico, com alternativa se não suportado.
5. `TokenizeCommand.execute(Path, Optional<Path>)`: ordem de serviços definida; destino padrão usa `REPLACE_EXISTING`, `--output-dir` usa `CREATE_NEW`.

A aplicação cria diretórios de destino conforme necessário, recusa aliases do próprio fonte e destinos protegidos e limpa arquivos temporários em falhas recuperáveis. A política padrão **pode substituir um arquivo regular preexistente com o mesmo nome**; a política personalizada rejeita a colisão. Não se promete atomicidade se o sistema de arquivos não a suportar.

O processamento em lote é **fail-fast**, sem rollback de arquivos previamente finalizados. `SourceProcessingException` inclui o caminho do arquivo atual (ou a entrada solicitada antes da seleção dos arquivos) e preserva a causa léxica, de serialização ou de I/O. A CLI emite diagnósticos léxicos com linha/coluna, erros restantes sem localização inventada, e retorna `0`, `1` ou `2` conforme seu contrato.

## Fronteira com a Parte 2

O futuro analisador sintático poderá reutilizar `JackTokenizer`, `Token`, `TokenType`, `SourcePosition`, `TokenizationResult` e os serviços efetivos de entrada/saída e serialização. **Não há comando `parse` nesta entrega**, nem coordenador genérico de casos de uso. Qualquer extração de abstração comum dependerá de necessidade concreta e de aprovação específica; não foi pré-implementada.

## Verificações arquiteturais

A separação de pacotes está sujeita à auditoria de importações e APIs públicas antes da tag. Os testes existentes de tokens, scanner, serialização, I/O e caso de uso são executados por `mvn clean verify`. Consulte [testing.md](testing.md) e [acceptance.md](acceptance.md) para evidências e pendências de auditoria.
