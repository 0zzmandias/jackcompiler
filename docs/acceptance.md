
# Aceitação e rastreabilidade do JackCompiler

Este documento relaciona os requisitos das Partes 1 e 2 do JackCompiler aos componentes implementados e às evidências de validação disponíveis.

O projeto reúne o analisador léxico e o analisador sintático da linguagem Jack, desenvolvidos em Java 21, com saídas XML compatíveis com as referências oficiais do nand2tetris.

## 1. Escopo implementado

O JackCompiler possui dois comandos:

| Comando | Responsabilidade | Saída |
|---|---|---|
| `tokenize` | Reconhecer e classificar os tokens do código-fonte Jack | `NomeT.xml` |
| `parse` | Reconhecer a gramática Jack e serializar sua árvore sintática | `NomeP.xml` |

O analisador sintático reutiliza o scanner da Parte 1 por meio de `TokenizationResult`, sem duplicar o reconhecimento léxico.

Os comandos podem processar um arquivo `.jack` ou os arquivos elegíveis diretamente contidos em um diretório. Ambos utilizam os mesmos serviços de leitura, resolução de destinos e escrita.

A validação contempla testes unitários, de integração, de aceitação contra materiais oficiais e de execução do JAR empacotado.

## 2. Matriz de requisitos — Parte 1

A Parte 1 corresponde à implementação do analisador léxico e à geração dos XMLs de tokens.

| Requisito | Implementação | Evidência |
|---|---|---|
| Cinco categorias de tokens | `TokenType`, `Token` | `TokenTest`, `JackTokenizerTest` |
| Reconhecimento das 21 palavras reservadas | `JackLexicalRules`, `IdentifierRecognizer` | `JackLexicalRulesTest`, `IdentifierRecognizerTest` |
| Reconhecimento dos 19 símbolos oficiais | `JackLexicalRules`, `SymbolRecognizer` | `JackLexicalRulesTest`, `SymbolRecognizerTest` |
| Identificadores válidos | `IdentifierRecognizer`, `JackLexicalRules` | `IdentifierRecognizerTest`, `JackTokenizerTest` |
| Constantes inteiras de `0` a `32767` | `IntegerRecognizer` | `IntegerRecognizerTest`, `JackTokenizerTest` |
| Constantes de string | `StringRecognizer` | `StringRecognizerTest`, `JackTokenizerTest` |
| Descarte de espaços e quebras de linha | `WhitespaceConsumer`, `TriviaSkipper` | `WhitespaceConsumerTest`, `TriviaSkipperTest` |
| Comentários `//`, `/* ... */` e `/** ... */` | `LineCommentConsumer`, `BlockCommentConsumer` | Testes individuais e `TriviaSkipperTest` |
| Preservação da ordem e dos valores dos tokens | `JackTokenizer`, `TokenizationResult` | `JackTokenizerTest`, `OfficialTokenXmlCompatibilityTest` |
| Posições de linha e coluna | `SourceCursor`, `SourcePosition` | `SourceCursorTest`, `SourcePositionTest` |
| Tratamento de entradas léxicas inválidas | `LexicalException`, reconhecedores | `LexicalExceptionTest`, testes dos reconhecedores |
| API léxica reutilizável | `JackTokenizer.tokenize(String)` | `LexerPublicApiTest` |
| Separação entre análise léxica, arquivos e XML | Pacotes `lexer`, `io`, `xml` | `LexerPublicApiTest`, testes dos componentes |
| XML de tokens no formato oficial | `TokenXmlWriter`, `XmlEscaper` | `TokenXmlWriterTest`, `OfficialTokenXmlCompatibilityTest` |
| Escape de caracteres XML | `XmlEscaper` | `XmlEscaperTest`, `TokenXmlWriterTest` |
| Compatibilidade com os exemplos oficiais | `JackTokenizer`, `TokenXmlWriter` | `ReferenceProgramsTest`, `OfficialTokenXmlCompatibilityTest` |
| Execução pela linha de comando | `JackCompiler`, `CliArgumentParser`, `TokenizeCommand` | `CliIntegrationTest`, `JarExecutionIT` |

### 2.1. Interpretação dos símbolos `[` e `]`

A lista de símbolos apresentada no enunciado da Parte 1 omite os colchetes `[` e `]`.

Entretanto, ambos pertencem ao conjunto oficial de 19 símbolos da linguagem Jack e são necessários para construções como:

```jack
let value = array[index];
```

A implementação reconhece os dois caracteres para manter a conformidade com a linguagem e permitir a análise de arrays.

Essa decisão é verificada pelos testes das regras léxicas e dos símbolos, além dos programas oficiais que utilizam indexação, especialmente `ArrayTest`.

A inclusão desses caracteres não representa uma extensão da gramática Jack.

## 3. Matriz de requisitos — Parte 2

A Parte 2 corresponde à implementação do analisador sintático descendente recursivo e à produção dos XMLs de árvores sintáticas.

### 3.1. Reconhecimento da gramática

| Requisito | Implementação | Evidência |
|---|---|---|
| Análise descendente recursiva | `JackParser`, `ClassStructureParser`, `StatementParser`, `ExpressionParser` | Testes dos componentes e `JackParserTest` |
| Reconhecimento de classes | `ClassStructureParser.parseClass()` | `ClassStructureParserTest` |
| Declarações de variáveis de classe | `parseClassVarDec()` | `ClassStructureParserDeclarationTest` |
| Declarações de sub-rotinas | `parseSubroutineDec()` | `ClassStructureParserSubroutineTest` |
| Listas de parâmetros | `parseParameterList()` | `ClassStructureParserSubroutineTest` |
| Corpos de sub-rotinas | `parseSubroutineBody()` | `ClassStructureParserSubroutineTest`, `JackParserTest` |
| Declarações de variáveis locais | `parseVarDec()` | `ClassStructureParserDeclarationTest`, `ClassStructureParserSubroutineTest` |
| Reconhecimento de tipos | `parseType()`, `parseReturnType()` | Testes de declarações e sub-rotinas |
| Sequências de comandos | `StatementParser.parseStatements()` | `StatementParserTest` |
| Seleção da produção de comando | `StatementParser.parseStatement()` | `StatementParserTest` e testes dos cinco comandos |
| Comando `let` | `parseLetStatement()` | `StatementParserLetTest` |
| Comando `if` | `parseIfStatement()` | `StatementParserIfTest` |
| Comando `while` | `parseWhileStatement()` | `StatementParserWhileTest` |
| Comando `do` | `parseDoStatement()` | `StatementParserDoTest` |
| Comando `return` | `parseReturnStatement()` | `StatementParserReturnTest` |
| Expressões | `ExpressionParser.parseExpression()` | `ExpressionParserTest` |
| Termos | `parseTerm()` | `ExpressionParserTest`, `ExpressionParserRecursiveTermTest` |
| Listas de expressões | `parseExpressionList()` | `ExpressionParserExpressionListTest` |
| Chamadas de sub-rotinas | `parseSubroutineCall()` | `ExpressionParserSubroutineCallTest` |
| Acesso a arrays | `parseTerm()`, `parseLetStatement()` | `ExpressionParserArrayAccessTest`, `StatementParserLetTest` |
| Operadores binários | `parseOp()` | `ExpressionParserTest` |
| Operadores unários | `parseUnaryOp()` | `ExpressionParserRecursiveTermTest` |
| Constantes de palavra reservada | `parseKeywordConstant()` | `ExpressionParserTest` |
| Nomes de classes, variáveis e sub-rotinas | Métodos auxiliares dos parsers estruturais | Testes das declarações, expressões e comandos |
| Reconhecimento de uma classe completa | `JackParser.parse(TokenizationResult)` | `JackParserTest`, `OfficialParserXmlCompatibilityTest` |

### 3.2. Organização por produções gramaticais

O enunciado solicita análise descendente recursiva com uma função por não-terminal.

A implementação distribui as produções entre três componentes:

- `ClassStructureParser`: estrutura de classes, declarações, tipos e sub-rotinas.
- `StatementParser`: sequências de comandos e produções específicas de instruções.
- `ExpressionParser`: expressões, termos, listas, chamadas e produções auxiliares.

As produções auxiliares `op`, `unaryOp` e `keywordConstant` possuem métodos próprios. A produção `statement` é reconhecida por `parseStatement()`, que delega a análise ao método correspondente ao comando encontrado.

Os métodos de reconhecimento de identificadores tornam explícitos os papéis gramaticais de nomes de classes, sub-rotinas e variáveis nos contextos em que a sintaxe permite essa distinção.

Uma chamada qualificada pode começar pelo nome de uma classe ou de uma variável. Nessa situação, `parseCallQualifier()` reconhece o identificador sem atribuir uma classificação que dependa de informações semânticas.

Esses métodos fazem parte da organização interna do parser e não determinam, por si só, quais elementos serão produzidos no XML.

### 3.3. Navegação e erros sintáticos

| Requisito | Implementação | Evidência |
|---|---|---|
| Consulta do token atual | `TokenCursor.peek()` | `TokenCursorTest` |
| Consulta antecipada de tokens | `TokenCursor.peek(int)` | `TokenCursorTest` |
| Verificação de tipo e valor | `TokenCursor.match(...)` | `TokenCursorTest` |
| Consumo de tokens | `TokenCursor.advance()` | `TokenCursorTest` |
| Consumo com validação | `TokenCursor.consume(...)` | `TokenCursorTest` |
| Reconhecimento de EOF | `TokenCursor.isAtEnd()`, `TokenizationResult.endPosition()` | `TokenCursorTest`, `JackParserTest` |
| Erros com elemento esperado e token encontrado | `SyntaxException` | `SyntaxExceptionTest`, testes do parser |
| Preservação de linha e coluna | `Token`, `SourcePosition`, `SyntaxException` | `TokenCursorTest`, `JackParserTest`, `JarExecutionIT` |
| Rejeição de tokens adicionais | `JackParser.parse(...)` | `JackParserTest` |
| Integração com o scanner da Parte 1 | `JackTokenizer` → `TokenizationResult` → `JackParser` | `JackParserTest`, `OfficialParserXmlCompatibilityTest` |

O parser utiliza os tokens originais do scanner e não modifica suas posições.

Quando uma produção encontra um token incompatível, a exceção sintática informa o elemento esperado e a posição correspondente. Caso a entrada termine prematuramente, é utilizada a posição EOF preservada pelo scanner.

O reconhecimento de uma classe somente é aceito como completo quando não restam tokens após o fechamento da produção `class`.

### 3.4. Precedência e associatividade

O enunciado da Parte 2 menciona o tratamento adequado de precedência e associatividade.

A gramática oficial de Jack define:

```text
expression ::= term (op term)*
```

Essa produção não estabelece uma precedência obrigatória entre operadores binários.

O JackCompiler preserva os termos e operadores na ordem original e reconhece agrupamentos explícitos por parênteses.

Por exemplo:

```jack
2 + 3 * 4
```

é representado como uma sequência de termos e operadores dentro de `expression`, sem introduzir uma prioridade matemática adicional.

Já uma expressão entre parênteses produz uma estrutura aninhada, conforme a gramática `term`.

**Decisão de implementação:** preservar a estrutura gramatical e o XML oficial, sem impor uma política própria de precedência binária na etapa de análise sintática.

A especificação de Jack permite políticas de precedência definidas por implementações, mas não exige uma tabela específica para o XML produzido no Project 10.

Os testes `ExpressionParserTest` e `ExpressionParserRecursiveTermTest` verificam a estrutura das expressões, a ordem dos operadores e os agrupamentos explícitos.

A fundamentação arquitetural encontra-se em [architecture.md](architecture.md).

## 4. Matriz de requisitos — XML sintático

| Requisito | Implementação | Evidência |
|---|---|---|
| Representação da árvore sintática | `ParseNode`, `GrammarNode`, `TerminalNode` | `ParseNodeTest`, `GrammarNodeTest`, `TerminalNodeTest` |
| Categorias gramaticais | `SyntaxTag` | `SyntaxTagTest`, `ParseTreeXmlWriterTest` |
| Preservação dos tokens originais | `TerminalNode` | `TerminalNodeTest`, testes do parser |
| Preservação da ordem dos filhos | `GrammarNode` | `GrammarNodeTest`, `ParseTreeXmlWriterTest` |
| Serialização da árvore | `ParseTreeXmlWriter` | `ParseTreeXmlWriterTest` |
| Formatação dos terminais | `TokenXmlWriter.formatToken(...)` | `TokenXmlWriterTest`, `ParseTreeXmlWriterTest` |
| Escape de caracteres reservados | `XmlEscaper` | `XmlEscaperTest`, testes de XML |
| Estrutura XML compatível com nand2tetris | `SyntaxTag`, `ParseTreeXmlWriter` | `OfficialParserXmlCompatibilityTest` |
| Saída com sufixo `P.xml` | `OutputPathResolver`, `ParseCommand` | `OutputPathResolverTest`, `ParseCommandTest` |
| Geração do XML pelo comando `parse` | `ParseCommand`, `JackCompiler` | `ParseCommandTest`, `JarExecutionIT` |
| Validação contra arquivos oficiais | `JackParser`, `ParseTreeXmlWriter` | `OfficialParserXmlCompatibilityTest` |

### 4.1. Tags XML e não-terminais

O enunciado menciona que os não-terminais devem aparecer como tags XML, mas também exige equivalência estrutural com os arquivos oficiais.

O formato do Project 10 utiliza 15 categorias de elementos gramaticais, representadas por `SyntaxTag`.

Produções auxiliares como `statement`, `op`, `unaryOp`, `keywordConstant` e `subroutineCall` não possuem necessariamente elementos XML individuais.

Por exemplo, uma operação binária é representada assim:

```xml
<expression>
  <term>
    <integerConstant> 2 </integerConstant>
  </term>
  <symbol> + </symbol>
  <term>
    <integerConstant> 3 </integerConstant>
  </term>
</expression>
```

Não é acrescentada uma tag `<op>` entre `<expression>` e `<symbol>`.

Da mesma maneira, um comando `let` aparece como `<letStatement>` dentro de `<statements>`, sem um elemento intermediário `<statement>`.

**Decisão de implementação:** utilizar a hierarquia XML oficial do nand2tetris, mantendo o reconhecimento das produções gramaticais separado da representação serializada.

Essa escolha é verificada diretamente por `OfficialParserXmlCompatibilityTest`, que compara as árvores produzidas com os arquivos de referência.

## 5. Matriz de requisitos — Integração e execução

| Requisito | Implementação | Evidência |
|---|---|---|
| Projeto desenvolvido em Java 21 | `pom.xml`, código de produção | `BuildEnvironmentTest`, Maven |
| Construção reproduzível | Maven e plugins configurados | `mvn clean verify`, GitHub Actions |
| JAR executável | `pom.xml`, `JackCompiler` | `JarExecutionIT` |
| Comandos `tokenize` e `parse` | `CliArgumentParser`, `CliArguments`, `JackCompiler` | `CliArgumentParserTest`, `CliIntegrationTest` |
| Ajuda pela CLI | `CliArgumentParser.USAGE`, `JackCompiler` | `CliArgumentParserTest`, `JarExecutionIT` |
| Entrada por arquivo `.jack` | `JackFileResolver` | `JackFileResolverTest`, `ParseCommandTest` |
| Entrada por diretório | `JackFileResolver` | `JackFileResolverTest`, `JarExecutionIT` |
| Ordenação determinística | `JackFileResolver` | `JackFileResolverTest`, `JarExecutionIT` |
| Leitura UTF-8 estrita | `JackSourceReader` | `JackSourceReaderTest`, `JarExecutionIT` |
| Saídas `T.xml` e `P.xml` | `OutputPathResolver`, `OutputKind` | `OutputPathResolverTest`, `JarExecutionIT` |
| Diretórios padrão de saída | `OutputPathResolver` | `OutputPathResolverTest`, testes dos casos de uso |
| Diretório personalizado por `--output-dir` | `CliArgumentParser`, `OutputPathResolver` | `CliArgumentParserTest`, `JarExecutionIT` |
| Rejeição de colisões no destino personalizado | `WritePolicy.CREATE_NEW`, `TextFileWriter` | `TextFileWriterTest`, `JarExecutionIT` |
| Substituição controlada no destino padrão | `WritePolicy.REPLACE_EXISTING`, `TextFileWriter` | `TextFileWriterTest`, `JarExecutionIT` |
| Proteção dos arquivos-fonte e de referência | `OutputPathResolver`, `TextFileWriter` | `OutputPathResolverTest`, `TextFileWriterTest`, `JarExecutionIT` |
| Rejeição de links simbólicos nos destinos | `TextFileWriter` | `TextFileWriterTest`, `JarExecutionIT` |
| Escrita por arquivo temporário | `TextFileWriter` | `TextFileWriterTest` |
| Processamento fail-fast | `TokenizeCommand`, `ParseCommand` | Testes dos casos de uso, `JarExecutionIT` |
| Diagnósticos léxicos e sintáticos | `JackCompiler`, `LexicalException`, `SyntaxException` | `CliIntegrationTest`, `JarExecutionIT` |
| Códigos de saída `0`, `1` e `2` | `JackCompiler` | `CliIntegrationTest`, `JarExecutionIT` |
| Integração contínua | `.github/workflows/ci.yml` | GitHub Actions |

O processamento de diretórios não é recursivo. Os arquivos elegíveis são processados em ordem, e a primeira falha interrompe o lote.

As saídas concluídas anteriormente permanecem disponíveis; não existe rollback do conjunto completo.

O conteúdo XML é preparado antes da publicação. Falhas de análise impedem a publicação de um novo XML correspondente ao arquivo que falhou.

A política padrão admite a substituição de um XML regular existente, enquanto a opção `--output-dir` utiliza uma política que rejeita colisões.

## 6. Referências oficiais e critérios de comparação

Os recursos de aceitação foram obtidos do repositório [nand2tetris/projects](https://github.com/nand2tetris/projects), na revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença correspondente encontra-se em `src/test/resources/nand2tetris/LICENSE`.

### 6.1. Programas utilizados

| Conjunto | Arquivos-fonte |
|---|---|
| `Square` | `Main.jack`, `Square.jack`, `SquareGame.jack` |
| `ExpressionLessSquare` | `Main.jack`, `Square.jack`, `SquareGame.jack` |
| `ArrayTest` | `Main.jack` |

São sete programas, cada um acompanhado de uma referência léxica `*T.xml` e de uma referência sintática `*.xml`.

### 6.2. Critério para o XML léxico

`OfficialTokenXmlCompatibilityTest` compara a saída produzida pelo scanner com o XML oficial correspondente.

A comparação é textual e exige correspondência de elementos, valores, ordem, espaços e escapes.

Somente os terminadores de linha da referência são normalizados para LF.

Os testes exercitam tanto a API de tokenização quanto o caso de uso `TokenizeCommand`, com preservação dos arquivos-fonte.

### 6.3. Critério para o XML sintático

`OfficialParserXmlCompatibilityTest` compara a estrutura XML gerada com as referências oficiais.

São executadas 14 comparações principais:

- sete pelo fluxo `JackTokenizer` → `JackParser` → `ParseTreeXmlWriter`;
- sete pelo fluxo de aplicação `ParseCommand`.

A equivalência considera:

- nomes dos elementos;
- hierarquia gramatical;
- quantidade e ordem dos filhos;
- categorias dos terminais;
- valores dos tokens;
- espaços significativos das strings;
- espaços convencionais dos elementos terminais;
- ausência de atributos inesperados.

São desconsideradas diferenças de espaços de formatação entre elementos gramaticais e aceitas entidades XML equivalentes.

Os testes também verificam que diferenças indevidas de hierarquia, ordem, conteúdo ou espaços significativos são rejeitadas.

Essa comparação é independente da implementação do parser, pois utiliza XMLs oficiais como referência.

### 6.4. Validação do JAR

`JarExecutionIT` executa `target/jackcompiler.jar` em processos Java separados, após o empacotamento.

Os testes abrangem as operações `tokenize` e `parse`, os códigos de saída, o tratamento de erros, os diretórios de entrada e saída, os arquivos oficiais e as condições de proteção contra sobrescritas indevidas.

Para os XMLs sintáticos produzidos pelo JAR, a verificação também utiliza o resultado da API do parser. A comparação independente contra as referências oficiais é realizada pelos testes de aceitação descritos anteriormente.

Os materiais oficiais não são utilizados como destinos de escrita pelos testes.

## 7. Documentação

A documentação do projeto está distribuída entre quatro arquivos:

| Arquivo | Conteúdo |
|---|---|
| [`README.md`](../README.md) | Apresentação, integrante, linguagem, construção, execução, exemplos, validação e desafios |
| [`architecture.md`](architecture.md) | Componentes, contratos, dependências e decisões técnicas |
| [`testing.md`](testing.md) | Estratégia de testes, cenários, recursos oficiais e resultados |
| [`acceptance.md`](acceptance.md) | Correspondência entre requisitos, implementação e evidências |

O README identifica o integrante e sua matrícula, informa a linguagem utilizada, apresenta os comandos de construção e execução, fornece exemplos de uso e explica os nomes dos XMLs produzidos.

Também registra a validação contra os programas oficiais e descreve os principais desafios encontrados na integração dos analisadores.

As decisões relativas aos símbolos `[` e `]`, à precedência dos operadores e à representação XML são explicadas na documentação, com detalhamento técnico em `architecture.md`.

## 8. Evidências de validação

A validação local do commit [`ea89741`](https://github.com/0zzmandias/jackcompiler/commit/ea89741d1bde0ce5652766c6a5c148007ad1941b) foi realizada com:

```bash
mvn clean verify
```

Os resultados registrados foram:

| Verificação | Resultado |
|---|---|
| Formatação pelo Spotless | Aprovada |
| Compilação Java 21 | Aprovada |
| Surefire | 1.101 testes aprovados |
| Failsafe | 48 testes aprovados |
| Falhas | 0 |
| Erros | 0 |
| Testes ignorados | 0 |
| **Total** | **1.149 testes aprovados** |
| Maven | **BUILD SUCCESS** |

O mesmo commit foi verificado pelo GitHub Actions, com resultado bem-sucedido:

[GitHub Actions — execução 40446352404](https://github.com/0zzmandias/jackcompiler/actions/runs/40446352404)

A execução abrangeu os analisadores léxico e sintático, a compatibilidade XML, os casos de uso, a CLI e os testes de integração do executável.

Os resultados apresentados correspondem ao código do commit identificado. Alterações posteriores devem ser verificadas por uma nova execução dos testes.

## 9. Alcance da aceitação

As evidências demonstram que os requisitos relacionados neste documento possuem implementação identificável e verificações automatizadas correspondentes.

A compatibilidade com os materiais oficiais é sustentada por comparações diretas dos XMLs de tokens e por comparações estruturais independentes dos XMLs sintáticos.

Os testes unitários complementam essas referências ao exercitar produções gramaticais, condições de erro e situações específicas não necessariamente presentes nos programas oficiais.

A aprovação da suíte significa que nenhuma falha foi detectada nos cenários executados. Ela não representa uma prova formal de correção para todas as entradas possíveis.

Para reprodução das evidências e detalhes dos critérios de teste, consulte [testing.md](testing.md).
