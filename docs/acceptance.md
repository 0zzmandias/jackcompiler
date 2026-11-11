
# Aceitação e rastreabilidade do JackCompiler

Este documento relaciona os requisitos implementados, os componentes responsáveis, os testes correspondentes e as evidências observadas no desenvolvimento do JackCompiler.

O projeto abrange três entregas progressivas:

1. Analisador léxico — Parte 1;
2. Analisador sintático — Parte 2;
3. Compilador Jack para VM — Parte 3.

A Parte 3 reutiliza os componentes anteriores e acrescenta geração de código para a máquina virtual Hack.

## 1. Escopo implementado

| Entrega | Funcionalidade principal | Situação |
|---|---|---|
| Parte 1 | Tokenização Jack e XML léxico | Concluída |
| Parte 2 | Análise descendente recursiva e XML sintático | Concluída |
| Parte 3 | Compilação Jack → VM e execução oficial | Funcionalmente concluída |
| Fechamento documental | Atualização e verificação da documentação | Em revisão |
| Auditoria final | Revisão integral do snapshot | Pendente |

As tags anteriores são preservadas:

```text
v1.0.0-lexer
v2.0.0-parser
```

A tag `v3.0.0-vm` somente será criada após a aprovação final do estado de entrega.

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

### 2.1. Interpretação dos colchetes

O enunciado da Parte 1 omite `[` e `]` em sua enumeração de símbolos.

Ambos fazem parte dos 19 símbolos oficiais de Jack e são necessários para arrays.

A implementação preserva a especificação da linguagem. Não constitui extensão indevida.

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

O parser reutiliza diretamente `TokenizationResult`, sem executar novamente o scanner e sem criar XML intermediário.

### 3.2. Produções gramaticais

`ClassStructureParser`, `StatementParser` e `ExpressionParser` distribuem as responsabilidades de reconhecimento.

Produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são tratadas pelos componentes responsáveis, mesmo quando não correspondem a tags XML individuais.

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

O parser não introduz precedência binária convencional.

Os operadores e termos são preservados em sequência, e parênteses são representados por nós aninhados.

A decisão mantém a estrutura exigida pelo Project 10.

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
| Estrutura oficial | `OfficialParserXmlCompatibilityTest` | Aceitação |
| Saída `P.xml` | `OutputPathResolver`, `ParseCommand` | Testes I/O |
| Integração da aplicação | `ParseCommand`, CLI | Testes de integração |

O XML oficial utiliza quinze categorias gramaticais serializáveis.

Não são introduzidas tags intermediárias artificiais para produções auxiliares.

A equivalência com os arquivos do nand2tetris é verificada estruturalmente, considerando hierarquia, ordem e conteúdo significativo.

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

O emissor produz instruções VM em memória, sem realizar escrita de arquivos.

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

O compilador reutiliza a CST da Parte 2, sem AST secundária ou parser duplicado.

O código produzido segue as convenções da VM Hack e utiliza serviços do Jack OS para operações como alocação de memória, strings, multiplicação e divisão.

A verificação semântica é limitada às inconsistências identificáveis pela implementação. Não existe sistema geral de inferência de tipos entre classes.

## 6. Matriz de requisitos — Integração operacional

| Requisito | Implementação | Evidência |
|---|---|---|
| Java 21 e Maven | `pom.xml` | `BuildEnvironmentTest`, Maven |
| JAR executável | `pom.xml`, `JackCompiler` | Testes `Jar*IT` |
| Comandos `tokenize`, `parse`, `compile` | CLI e casos de uso | Testes de CLI |
| Ajuda | `CliArgumentParser` | Testes de argumentos |
| Entrada por arquivo | `JackFileResolver` | `JackFileResolverTest` |
| Diretório não recursivo nos comandos antigos | `resolve(Path)` | `JackFileResolverTest` |
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
| Regressão dos comandos antigos | `TokenizeCommand`, `ParseCommand` | Testes anteriores |

A compilação em lote determina os arquivos e destinos antes do processamento.

Cada arquivo passa por leitura, tokenização, análise sintática, compilação VM e publicação. Na primeira falha, o processamento é interrompido, sem rollback global.

As políticas de escrita diferenciam destinos padrão e personalizados.

## 7. Referências oficiais e critérios de comparação

Os materiais provêm de [nand2tetris/projects](https://github.com/nand2tetris/projects), revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença correspondente está em:

```text
src/test/resources/nand2tetris/LICENSE
```

### 7.1. Referências XML

As Partes 1 e 2 utilizam sete fontes Jack dos conjuntos Square, ExpressionLessSquare e ArrayTest.

`OfficialTokenXmlCompatibilityTest` compara as saídas léxicas com os arquivos `*T.xml`.

`OfficialParserXmlCompatibilityTest` compara a estrutura dos XMLs sintáticos com os arquivos oficiais, verificando hierarquia, ordem e terminais.

A comparação XML é independente do compilador VM.

### 7.2. Referências VM

A Parte 3 utiliza seis programas oficiais:

| Programa | Fontes Jack | Evidência automatizada | Evidência manual |
|---|---:|---|---|
| Seven | 1 | `OfficialVmGenerationTest` | Exibição de 7 |
| Average | 1 | `OfficialVmGenerationTest` | Média 6 |
| ConvertToBin | 1 | `OfficialVmGenerationTest` | Binário de 13 |
| ComplexArrays | 1 | `OfficialVmGenerationTest` | Cinco valores corretos |
| Square | 3 | `OfficialVmGenerationTest` | Jogo interativo funcional |
| Pong | 4 | `OfficialVmGenerationTest` | Placar e Game Over |

Os onze arquivos Jack oficiais geram onze arquivos VM, distribuídos pelos seis programas.

O teste automatizado verifica geração de código, não execução no VM Emulator.

### 7.3. Resultados comportamentais

| Programa | Resultado efetivamente observado | Situação |
|---|---|---|
| Seven | Valor `7` | Aprovado |
| Average | Entradas `4`, `6`, `8`; média `6` | Aprovado |
| ConvertToBin | RAM com os 16 bits corretos de `13` | Aprovado |
| ComplexArrays | `5`, `40`, `0`, `77`, `110` | Aprovado |
| Square | Movimento, tamanho e encerramento funcionais | Aprovado |
| Pong | Movimento, colisões, placar `3` e `Game Over` | Aprovado |

Os resultados foram verificados em execuções manuais do Virtual Machine Emulator 2.5.

A documentação dos procedimentos encontra-se em [testing.md](testing.md).

## 8. Evidências de validação automatizada

A execução local de 10/11/2026, correspondente ao estado funcional do P3-24, utilizou:

```bash
mvn clean verify
```

| Verificação | Resultado |
|---|---|
| Spotless | Aprovado |
| Compilação Java | Aprovada |
| Surefire | 1.799 |
| Failsafe | 64 |
| **Total** | **1.863** |
| Falhas | 0 |
| Erros | 0 |
| Testes ignorados | 0 |
| Maven | `BUILD SUCCESS` |

Commit funcional:

[`0d389be81a2876ca7ac804085b49493104a96886`](https://github.com/0zzmandias/jackcompiler/commit/0d389be81a2876ca7ac804085b49493104a96886)

Os resultados pertencem ao estado indicado e não devem ser atribuídos automaticamente a alterações posteriores.

### 8.1. Integração contínua

O GitHub Actions possui um workflow de construção em Java 21.

Durante a Parte 3, o serviço apresentou um bloqueio administrativo de cobrança (*billing lock*) que impediu a inicialização dos jobs.

Essa restrição foi identificada como externa ao processo de compilação e aos testes Java. O problema foi encaminhado ao suporte do GitHub.

Enquanto o serviço não estiver disponível para executar o workflow, a aprovação da CI no estado final permanece pendente.

Os resultados locais e a validação manual dos seis programas oficiais estão registrados separadamente e não dependem da execução desse serviço.

## 9. Documentação e autoria

Os documentos do projeto são:

| Arquivo | Responsabilidade |
|---|---|
| [`README.md`](../README.md) | Apresentação, execução, autoria, validação e IA |
| [`architecture.md`](architecture.md) | Componentes, contratos e decisões técnicas |
| [`testing.md`](testing.md) | Estratégia, procedimentos e resultados |
| [`acceptance.md`](acceptance.md) | Rastreabilidade dos requisitos |

O README contém a identificação do integrante, os comandos de utilização e a declaração de apoio recebido de IA.

A identificação completa de modelos utilizados em sessões anteriores à revisão documental depende de registros verificáveis. Essa declaração deve ser conferida antes do fechamento definitivo.

## 10. Pendências da auditoria final

| Item | Situação |
|---|---|
| Implementação dos 24 incrementos P3 | Concluída |
| Execução dos seis programas oficiais | Aprovada |
| Regressão local no P3-24 | Aprovada |
| Revisão final dos quatro documentos | Em andamento |
| Conferência da declaração de IA | Pendente de validação final |
| Auditoria integral do histórico e do código | Pendente |
| Regressão após documentação | Pendente |
| CI no snapshot de entrega | Aguardando liberação administrativa |
| Tag `v3.0.0-vm` | Não criada |
| Demonstração final da entrega | Pendente |

Uma pendência não deve ser registrada como aprovada antes da evidência correspondente.

## 11. Alcance da aceitação

As evidências disponíveis demonstram que os requisitos funcionais implementados possuem componentes identificáveis e testes correspondentes.

A compatibilidade XML é sustentada por comparações com arquivos oficiais independentes.

A geração VM é verificada automaticamente e seu funcionamento foi confirmado nos seis programas oficiais do Project 11.

Esses resultados não constituem uma prova formal de correção para qualquer entrada Jack possível.

A conclusão definitiva da Parte 3 exige conferência da documentação, auditoria do histórico e do código, repetição da verificação integral e tratamento explícito da dependência administrativa da CI.

Para os procedimentos reproduzíveis e os detalhes dos resultados, consulte [testing.md](testing.md).
