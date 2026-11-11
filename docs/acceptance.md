
# Aceitação e rastreabilidade do JackCompiler

Este documento relaciona os requisitos funcionais e operacionais do JackCompiler aos componentes responsáveis por sua implementação e às evidências de validação correspondentes.

O sistema integra três operações:

1. Análise léxica de programas Jack;
2. Análise sintática e geração de XML;
3. Compilação de programas Jack para instruções da máquina virtual Hack.

A rastreabilidade contempla os contratos dos componentes, os testes automatizados e os resultados de execução dos programas oficiais do nand2tetris.

## 1. Escopo implementado

| Componente | Funcionalidade principal | Situação |
|---|---|---|
| Analisador léxico | Tokenização Jack e geração de XML léxico | Implementado e validado |
| Analisador sintático | Análise descendente recursiva e geração de XML sintático | Implementado e validado |
| Compilador Jack → VM | Tradução de programas Jack para instruções VM | Implementado e validado |

As operações são disponibilizadas pelos subcomandos `tokenize`, `parse` e `compile`, respectivamente.

O compilador utiliza diretamente as representações produzidas pelos analisadores léxico e sintático, sem depender de arquivos XML intermediários.

## 2. Matriz de requisitos — Parte 1

| Requisito | Implementação | Evidência |
|---|---|---|
| Palavras reservadas | `JackLexicalRules`, `IdentifierRecognizer` | Testes léxicos |
| Símbolos da linguagem | `JackLexicalRules`, `SymbolRecognizer` | `SymbolRecognizerTest` |
| Constantes inteiras | `IntegerRecognizer` | `IntegerRecognizerTest` |
| Strings | `StringRecognizer` | `StringRecognizerTest` |
| Identificadores | `IdentifierRecognizer` | `IdentifierRecognizerTest` |
| Espaços e comentários | `TriviaSkipper` e consumidores | Testes de trivia |
| Posições de linha e coluna | `SourceCursor`, `SourcePosition` | `SourceCursorTest` |
| Erros léxicos | `LexicalException` | `LexicalExceptionTest` |
| Tokens imutáveis | `Token`, `TokenizationResult` | Testes de token |
| Serialização XML | `TokenXmlWriter`, `XmlEscaper` | Testes XML |
| Comparação com XMLs oficiais | `OfficialTokenXmlCompatibilityTest` | Testes de aceitação |
| API pública independente da CLI | `JackTokenizer` | `LexerPublicApiTest` |

### 2.1. Símbolos da linguagem

O analisador reconhece os 19 símbolos oficiais de Jack:

```text
{ } ( ) [ ] . , ; + - * / & | < > = ~
```

Os colchetes `[` e `]` são necessários ao reconhecimento de expressões indexadas e operações com arrays.

A implementação segue o conjunto de símbolos definido pela linguagem Jack, incluindo os colchetes não enumerados no enunciado acadêmico da Parte 1.

## 3. Matriz de requisitos — Parte 2

### 3.1. Reconhecimento da gramática

| Requisito | Implementação | Evidência |
|---|---|---|
| Classe completa | `JackParser`, `ClassStructureParser` | `JackParserTest` |
| Variáveis de classe | `ClassStructureParser` | Testes de declarações |
| Funções, métodos e construtores | `ClassStructureParser` | Testes de sub-rotinas |
| Parâmetros e variáveis locais | `ClassStructureParser` | Testes de estrutura |
| Comando `let` | `StatementParser` | `StatementParserLetTest` |
| Comando `if`/`else` | `StatementParser` | `StatementParserIfTest` |
| Comando `while` | `StatementParser` | `StatementParserWhileTest` |
| Comando `do` | `StatementParser` | `StatementParserDoTest` |
| Comando `return` | `StatementParser` | `StatementParserReturnTest` |
| Expressões e termos | `ExpressionParser` | `ExpressionParserTest` |
| Arrays | `ExpressionParser` | `ExpressionParserArrayAccessTest` |
| Chamadas | `ExpressionParser` | `ExpressionParserSubroutineCallTest` |
| Expressões aninhadas | `ExpressionParser` | Testes de termos recursivos |
| Fim correto da entrada | `JackParser`, `TokenCursor` | `JackParserTest` |

O parser recebe diretamente um `TokenizationResult` e produz uma árvore sintática concreta (CST).

O reconhecimento gramatical não depende da leitura de XML léxico e não executa novamente a tokenização.

### 3.2. Produções gramaticais

O reconhecimento das estruturas da linguagem é distribuído entre:

- `ClassStructureParser`: classes, declarações e sub-rotinas;
- `StatementParser`: comandos da linguagem;
- `ExpressionParser`: expressões, termos, arrays e chamadas.

Produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são tratadas pelos componentes sintáticos, mesmo quando não correspondem a elementos XML individuais.

### 3.3. Navegação e erros

| Requisito | Implementação | Evidência |
|---|---|---|
| Consulta de tokens | `TokenCursor.peek(...)` | `TokenCursorTest` |
| Verificação de tipo e valor | `TokenCursor.match(...)` | `TokenCursorTest` |
| Consumo validado | `TokenCursor.consume(...)` | `TokenCursorTest` |
| Identificação de EOF | `TokenCursor`, `TokenizationResult` | Testes do parser |
| Erros sintáticos posicionados | `SyntaxException` | `SyntaxExceptionTest` |
| Preservação de posições | `Token`, `SourcePosition` | Testes de parser e CLI |
| Rejeição de tokens adicionais | `JackParser` | `JackParserTest` |

### 3.4. Precedência e associatividade

A gramática Jack define:

```text
expression ::= term (op term)*
```

O parser preserva a sequência de termos e operadores sem introduzir precedência binária convencional.

Parênteses explícitos são representados por nós sintáticos aninhados.

A tradução VM utiliza a sequência representada pela CST, conforme a estrutura gramatical reconhecida.

## 4. Matriz de requisitos — XML sintático

| Requisito | Implementação | Evidência |
|---|---|---|
| Árvore sintática imutável | `ParseNode`, `GrammarNode`, `TerminalNode` | Testes dos nós |
| Categorias gramaticais oficiais | `SyntaxTag` | `SyntaxTagTest` |
| Ordem dos filhos | `GrammarNode` | `GrammarNodeTest` |
| Preservação dos terminais | `TerminalNode` | `TerminalNodeTest` |
| Serialização XML | `ParseTreeXmlWriter` | `ParseTreeXmlWriterTest` |
| Formatação dos terminais | `TokenXmlWriter` | Testes XML |
| Escape XML | `XmlEscaper` | `XmlEscaperTest` |
| Estrutura oficial | `OfficialParserXmlCompatibilityTest` | Testes de aceitação |
| Saída `P.xml` | `OutputPathResolver`, `ParseCommand` | Testes I/O |
| Integração da aplicação | `ParseCommand`, CLI | Testes de integração |

A serialização utiliza as quinze categorias gramaticais definidas pelo formato XML do Project 10.

As produções auxiliares não exigem elementos XML intermediários adicionais.

A compatibilidade com as referências oficiais é verificada estruturalmente, considerando hierarquia, ordem e conteúdo significativo dos terminais.

## 5. Matriz de requisitos — Parte 3

### 5.1. Tabela de símbolos

| Requisito | Implementação | Evidência |
|---|---|---|
| Declarações imutáveis | `Symbol` | `SymbolTest` |
| Quatro categorias | `SymbolKind` | `SymbolTest` |
| Índices independentes | `SymbolTable` | `SymbolTableTest` |
| Escopo de classe | `SymbolTable` | `SymbolTableTest` |
| Escopo de sub-rotina | `SymbolTable` | `SymbolTableTest` |
| Reinício do escopo local | `startSubroutine(...)` | `SymbolTableTest` |
| Receptor implícito de métodos | `startSubroutine(true)` | Testes de símbolos e classes |
| Rejeição de duplicidades | `SymbolTable` | `SymbolTableTest` |
| Sombreamento entre escopos | `SymbolTable` | `SymbolTableTest` |

A tabela de símbolos mantém escopos independentes para declarações de classe e de sub-rotina.

Os símbolos são classificados em `STATIC`, `FIELD`, `ARGUMENT` e `LOCAL`, com índices independentes por categoria.

### 5.2. Emissor VM

| Requisito | Implementação | Evidência |
|---|---|---|
| Segmentos de memória | `VmSegment` | `VmWriterTest` |
| Operações aritméticas | `VmArithmetic` | `VmWriterTest` |
| `push` e `pop` | `VmWriter` | `VmWriterTest` |
| `label`, `goto`, `if-goto` | `VmWriter` | `VmWriterTest` |
| `function` e `call` | `VmWriter` | `VmWriterTest` |
| `return` | `VmWriter` | `VmWriterTest` |
| Validação de operandos | `VmWriter` | `VmWriterTest` |
| Formato textual VM | `VmWriter.toVm()` | `VmWriterTest` |

O emissor produz instruções VM em memória e não realiza operações de escrita de arquivos.

### 5.3. Compilação semântica

| Requisito | Implementação | Evidência |
|---|---|---|
| CST → VM | `JackVmCompiler`, `ClassCompiler` | `JackVmCompilerTest` |
| Contrato público | `JackVmCompiler` | `CompilerPublicApiTest` |
| Estado isolado | `CompilationContext` | `CompilationContextTest` |
| Navegação da CST | `SyntaxTreeNavigator` | `SyntaxTreeNavigatorTest` |
| Catálogo de rotinas | `SubroutineCatalog` | `SubroutineCatalogTest` |
| Funções e variáveis locais | `ClassCompiler` | `ClassCompilerTest` |
| Métodos e `this` | `ClassCompiler` | `ClassCompilerObjectTest` |
| Construtores e alocação | `ClassCompiler` | Testes de classes e objetos |
| Chamadas locais | `ExpressionCompiler` | Testes de chamadas |
| Chamadas de classe e objeto | `ExpressionCompiler` | Testes de chamadas |
| Constantes e strings | `ExpressionCompiler` | Testes de constantes e variáveis |
| Operadores binários e unários | `ExpressionCompiler` | `ExpressionCompilerTest` |
| Arrays — leitura | `ExpressionCompiler` | `ExpressionCompilerArrayAccessTest` |
| Arrays — escrita | `StatementCompiler` | `StatementCompilerTest` |
| `let`, `do`, `return` | `StatementCompiler` | `StatementCompilerTest` |
| `if`, `else`, `while` | `StatementCompiler` | `StatementCompilerControlFlowTest` |
| Rótulos determinísticos | `CompilationContext`, `StatementCompiler` | Testes de fluxo |
| Diagnósticos semânticos | `CompilationException` | `CompilationExceptionTest` |

O compilador utiliza diretamente a CST, sem depender de XML intermediário ou de um parser adicional.

O código produzido segue as convenções da VM Hack e utiliza serviços do Jack OS para operações como alocação de memória, construção de strings, multiplicação e divisão.

A verificação semântica abrange inconsistências identificáveis pelos componentes implementados. Não constitui um sistema geral de inferência de tipos ou de análise interclasses completa.

## 6. Matriz de requisitos — Integração operacional

| Requisito | Implementação | Evidência |
|---|---|---|
| Java 21 e Maven | `pom.xml` | `BuildEnvironmentTest`, Maven |
| JAR executável | `pom.xml`, `JackCompiler` | Testes `Jar*IT` |
| Comandos `tokenize`, `parse`, `compile` | CLI e casos de uso | Testes de CLI |
| Ajuda | `CliArgumentParser` | Testes de argumentos |
| Entrada por arquivo | `JackFileResolver` | `JackFileResolverTest` |
| Diretório não recursivo em `tokenize` e `parse` | `resolve(Path)` | `JackFileResolverTest` |
| Diretório recursivo em `compile` | `resolveRecursively(Path)` | `JackFileResolverTest` |
| Ordem determinística | `JackFileResolver` | Testes de resolução |
| Leitura UTF-8 estrita | `JackSourceReader` | `JackSourceReaderTest` |
| Saídas XML | `OutputPathResolver` | `OutputPathResolverTest` |
| Saídas VM | `OutputPathResolver` | `OutputPathResolverTest` |
| Espelhamento de subdiretórios | `resolveVmBatch(...)` | Testes I/O e compilação |
| Destino personalizado | CLI, `OutputPathResolver` | Testes de aplicação |
| Colisões | `WritePolicy`, `TextFileWriter` | Testes I/O e JAR |
| Proteção das fontes | `OutputPathResolver`, `TextFileWriter` | Testes I/O |
| Links simbólicos | Resolvedor e escritor | Testes I/O |
| Escrita temporária | `TextFileWriter` | `TextFileWriterTest` |
| Processamento `fail-fast` | Casos de uso | Testes de aplicação e JAR |
| Diagnósticos posicionados | Exceções e CLI | Testes de CLI |
| Códigos de saída | `JackCompiler` | Testes de CLI e JAR |
| Contratos dos comandos `tokenize` e `parse` | `TokenizeCommand`, `ParseCommand` | Testes específicos e de integração |

A compilação em lote determina os arquivos de entrada e os destinos antes do processamento.

Cada arquivo passa por leitura, tokenização, análise sintática, compilação VM e publicação. Na primeira falha, o processamento é interrompido, sem rollback global.

As políticas de escrita diferenciam destinos padrão e personalizados.

## 7. Referências oficiais e critérios de comparação

Os recursos de referência foram obtidos do repositório [nand2tetris/projects](https://github.com/nand2tetris/projects), na revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença MIT correspondente encontra-se em:

```text
src/test/resources/nand2tetris/LICENSE
```

### 7.1. Referências XML

A validação dos componentes léxico e sintático utiliza sete fontes Jack dos conjuntos Square, ExpressionLessSquare e ArrayTest.

`OfficialTokenXmlCompatibilityTest` compara as saídas léxicas com os arquivos oficiais `*T.xml`.

`OfficialParserXmlCompatibilityTest` compara a estrutura dos XMLs sintáticos com as referências oficiais, verificando hierarquia, ordem e terminais.

A verificação dos arquivos XML é independente da geração VM.

### 7.2. Referências VM

A compilação VM é verificada com seis programas oficiais do Project 11:

| Programa | Fontes Jack | Evidência automatizada | Evidência manual |
|---|---:|---|---|
| Seven | 1 | `OfficialVmGenerationTest` | Exibição de 7 |
| Average | 1 | `OfficialVmGenerationTest` | Média 6 |
| ConvertToBin | 1 | `OfficialVmGenerationTest` | Binário de 13 |
| ComplexArrays | 1 | `OfficialVmGenerationTest` | Cinco valores corretos |
| Square | 3 | `OfficialVmGenerationTest` | Jogo interativo funcional |
| Pong | 4 | `OfficialVmGenerationTest` | Placar e Game Over |

Os onze arquivos Jack utilizados produzem onze arquivos VM, distribuídos pelos seis programas.

Os testes automatizados verificam a geração do código, sem executar as instruções no Virtual Machine Emulator.

### 7.3. Resultados comportamentais

| Programa | Resultado observado | Situação |
|---|---|---|
| Seven | Valor `7` | Aprovado |
| Average | Entradas `4`, `6`, `8`; média `6` | Aprovado |
| ConvertToBin | RAM com os 16 bits corretos de `13` | Aprovado |
| ComplexArrays | `5`, `40`, `0`, `77`, `110` | Aprovado |
| Square | Movimento, tamanho e encerramento funcionais | Aprovado |
| Pong | Movimento, colisões, placar `3` e `Game Over` | Aprovado |

Os resultados foram observados durante a execução dos programas compilados no Virtual Machine Emulator 2.5.

Os procedimentos de reprodução e os resultados detalhados estão descritos em [testing.md](testing.md).

## 8. Evidências de validação automatizada

A suíte completa foi executada localmente em **11/11/2026**, utilizando o comando:

```bash
mvn clean verify
```

A execução corresponde ao commit:

[`d77a6ba441ad3969192023a3b32672ca5831d597`](https://github.com/0zzmandias/jackcompiler/commit/d77a6ba441ad3969192023a3b32672ca5831d597)

Os resultados registrados foram:

| Verificação | Resultado |
|---|---|
| Spotless | 144 arquivos conformes |
| Compilação Java 21 | 58 arquivos de produção |
| Compilação dos testes | 86 arquivos |
| Surefire | 1.799 testes |
| Failsafe | 64 testes |
| **Total** | **1.863 testes aprovados** |
| Falhas | 0 |
| Erros | 0 |
| Testes ignorados | 0 |
| JAR executável | Gerado |
| Maven | `BUILD SUCCESS` |

`OfficialVmGenerationTest` executou 12 testes, todos aprovados.

A execução incluiu testes dos componentes léxicos e sintáticos, da tabela de símbolos, da geração VM, dos casos de uso, da CLI e do JAR executável.

Os resultados identificam a revisão do código efetivamente submetida à verificação.

### 8.1. Integração contínua

O repositório contém um workflow GitHub Actions configurado para Java 21 e Maven.

O workflow utiliza:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Durante a validação da Parte 3, a execução dos jobs foi impedida por uma restrição administrativa de cobrança da plataforma GitHub (*billing lock*).

O runner não executou as etapas de construção e testes nessas tentativas. Portanto, a execução local aprovada não constitui evidência de aprovação da CI.

Os resultados automatizados locais e a execução manual dos programas oficiais são registrados independentemente do GitHub Actions.

## 9. Documentação e autoria

A documentação técnica do JackCompiler está organizada nos seguintes arquivos:

| Arquivo | Conteúdo |
|---|---|
| [`README.md`](../README.md) | Apresentação, autoria, requisitos, execução, validação, desafios, aprendizados e uso de IA |
| [`architecture.md`](architecture.md) | Arquitetura, componentes, contratos e dependências |
| [`testing.md`](testing.md) | Estratégia de testes, reprodução e resultados |
| [`acceptance.md`](acceptance.md) | Matriz de rastreabilidade e evidências de aceitação |

O README identifica o integrante responsável pelo projeto e documenta a ferramenta de inteligência artificial utilizada, suas finalidades e os procedimentos empregados para validar os resultados.

## 10. Alcance da aceitação

As evidências registradas demonstram a implementação e a validação dos requisitos nos cenários contemplados pela suíte de testes e pelos programas oficiais utilizados.

A compatibilidade XML é verificada mediante comparações com referências oficiais independentes da implementação.

A geração VM é verificada por testes automatizados e pela execução comportamental dos seis programas selecionados do Project 11.

Os resultados não constituem prova formal de correção para todos os programas Jack possíveis.

A validação também não equivale a uma análise estática completa de tipos entre classes, otimização de código ou execução de todo o ecossistema Jack.

A execução local da suíte Maven e a situação da integração contínua são apresentadas separadamente, de modo a identificar precisamente a origem das evidências.

Os procedimentos reproduzíveis e os resultados detalhados estão disponíveis em [testing.md](testing.md).
