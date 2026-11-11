
# Arquitetura do JackCompiler

Este documento descreve a arquitetura implementada do JackCompiler, abrangendo as três etapas: análise léxica, análise sintática e compilação Jack para VM.

O projeto utiliza Java 21 e Maven. A Parte 3 amplia os componentes existentes sem reimplementar o scanner ou o parser e sem modificar os contratos públicos aprovados nas entregas anteriores.

## 1. Organização e dependências

O código de produção está organizado em onze pacotes:

| Pacote | Responsabilidade | Dependências internas principais |
|---|---|---|
| `token` | Tokens, tipos e posições de origem | Nenhuma |
| `lexer` | Reconhecimento léxico | `token` |
| `syntax` | Árvore sintática concreta e imutável | `token` |
| `parser` | Reconhecimento da gramática Jack | `token`, `syntax` |
| `xml` | Serialização XML | `token`, `syntax` |
| `symbol` | Declarações, índices e escopos | Nenhuma |
| `vm` | Formatação das instruções VM | Nenhuma |
| `compiler` | Tradução da CST em código VM | `token`, `syntax`, `symbol`, `vm` |
| `io` | Descoberta, leitura, destinos e publicação | Nenhuma camada de análise |
| `application` | Coordenação dos casos de uso | Componentes de análise, compilação e I/O |
| `cli` | Argumentos, comandos e diagnósticos | `application` e tipos de diagnóstico |

As dependências apontam das camadas de entrada e coordenação para os componentes especializados.

O scanner não depende do parser. O parser não depende do compilador. O compilador não acessa diretamente o sistema de arquivos e não depende da CLI.

`application` coordena os componentes, sem depender de `cli`.

Não foram introduzidas dependências cíclicas deliberadas entre essas camadas.

## 2. Fluxos de execução

O JAR utiliza `jackcompiler.cli.JackCompiler` como classe principal.

`CliArgumentParser` interpreta os argumentos e `CliArguments` representa a operação solicitada.

A CLI delega o processamento ao caso de uso correspondente.

### 2.1. Tokenização

```text
JackCompiler
  -> CliArgumentParser
  -> TokenizeCommand
     -> JackFileResolver.resolve
     -> JackSourceReader
     -> JackTokenizer
        -> TokenizationResult
     -> TokenXmlWriter
     -> OutputPathResolver
     -> TextFileWriter
  -> resultado ou diagnóstico
```

A saída segue o formato `NomeT.xml`.

### 2.2. Análise sintática

```text
JackCompiler
  -> CliArgumentParser
  -> ParseCommand
     -> JackFileResolver.resolve
     -> JackSourceReader
     -> JackTokenizer
        -> TokenizationResult
     -> JackParser
        -> GrammarNode
     -> ParseTreeXmlWriter
     -> OutputPathResolver
     -> TextFileWriter
  -> resultado ou diagnóstico
```

A saída segue o formato `NomeP.xml`.

O parser recebe diretamente os tokens do scanner. Não utiliza o XML léxico como entrada intermediária.

### 2.3. Compilação Jack para VM

```text
JackCompiler
  -> CliArgumentParser
  -> CompileCommand
     -> JackFileResolver.resolveRecursively
     -> OutputPathResolver.resolveVmBatch
     -> JackSourceReader
     -> JackTokenizer
        -> TokenizationResult
     -> JackParser
        -> GrammarNode
     -> JackVmCompiler
        -> ClassCompiler
           -> CompilationContext
           -> SubroutineCatalog
           -> SymbolTable
           -> StatementCompiler
              -> ExpressionCompiler
           -> VmWriter
        -> String VM
     -> TextFileWriter
  -> resultado ou diagnóstico
```

A saída segue o formato `Nome.vm`.

`TokenizeCommand`, `ParseCommand` e `CompileCommand` são casos de uso distintos. Os três reutilizam os serviços concretos necessários sem introduzir um coordenador genérico para todos os fluxos.

## 3. Contratos léxicos

O pacote `jackcompiler.token` representa a entrada léxica compartilhada.

| Tipo | Responsabilidade |
|---|---|
| `TokenType` | Cinco categorias oficiais |
| `SourcePosition` | Linha e coluna, iniciadas em 1 |
| `Token` | Tipo, lexema e posição original |
| `TokenizationResult` | Sequência imutável de tokens e posição final |

O scanner não produz um token artificial de EOF. A posição do fim da entrada é armazenada em `TokenizationResult.endPosition()`.

A API pública é:

```java
new JackTokenizer().tokenize(source)
```

Cada chamada utiliza estado independente de leitura. `SourceCursor` acompanha as posições e reconhece LF, CRLF e CR como quebras lógicas de linha.

`TriviaSkipper` descarta espaços e comentários antes do reconhecimento de cada token.

A ordem dos reconhecedores é:

1. `StringRecognizer`;
2. `IntegerRecognizer`;
3. `IdentifierRecognizer`;
4. `SymbolRecognizer`.

`JackLexicalRules` centraliza as palavras reservadas, os símbolos oficiais e as regras de formação dos tokens.

Os valores originais dos tokens são preservados. O escape XML ocorre apenas na serialização.

Erros léxicos são representados por `LexicalException`, com posição da origem.

## 4. Contratos sintáticos

O pacote `jackcompiler.parser` implementa análise descendente recursiva.

A fachada pública é:

```java
new JackParser().parse(tokenizationResult)
```

Ela recebe um `TokenizationResult` e retorna um `GrammarNode` correspondente à classe Jack reconhecida.

### 4.1. TokenCursor

`TokenCursor` navega pelos tokens e concentra as operações:

| Operação | Responsabilidade |
|---|---|
| `peek()` | Consultar o token atual |
| `peek(offset)` | Consultar um token posterior |
| `match(...)` | Verificar tipo e/ou valor |
| `advance()` | Consumir um token |
| `consume(...)` | Consumir com validação |
| `isAtEnd()` | Verificar fim dos tokens |

Os erros de reconhecimento são representados por `SyntaxException`.

Caso a entrada termine prematuramente, o parser utiliza a posição EOF informada pelo scanner. Após reconhecer a classe, verifica se restaram tokens inesperados.

### 4.2. Reconhecedores gramaticais

A análise é dividida em três componentes:

- `ClassStructureParser`: classes, declarações, tipos, parâmetros e sub-rotinas;
- `StatementParser`: comandos `let`, `if`, `while`, `do` e `return`;
- `ExpressionParser`: expressões, termos, arrays e chamadas.

Produções auxiliares são reconhecidas por métodos internos, sem exigir uma tag XML própria.

Os identificadores são interpretados de acordo com o contexto sintático. Uma chamada qualificada pode começar por nome de classe ou variável; a distinção semântica necessária é realizada posteriormente na compilação.

### 4.3. Precedência e associatividade

A gramática Jack define:

```text
expression ::= term (op term)*
```

O parser preserva a sequência de termos e operadores sem impor uma tabela convencional de precedência.

Parênteses explícitos são representados por termos aninhados.

O compilador utiliza essa mesma estrutura, emitindo as operações na ordem representada pela CST.

## 5. Árvore sintática concreta

O pacote `jackcompiler.syntax` contém:

- `ParseNode`: contrato de representação dos nós;
- `GrammarNode`: nó gramatical com filhos ordenados;
- `TerminalNode`: nó terminal associado a um token;
- `SyntaxTag`: categorias gramaticais serializáveis.

A árvore é imutável e preserva os tokens originais.

Os quinze elementos gramaticais do formato XML oficial são representados por `SyntaxTag`.

Essa mesma estrutura é utilizada pelo parser, pelo serializador XML e pelo compilador VM.

Não foi introduzida uma segunda AST ou uma conversão para XML como requisito de compilação.

## 6. Serialização XML

O pacote `jackcompiler.xml` permanece independente da análise gramatical e da compilação VM.

`TokenXmlWriter` serializa a sequência léxica, enquanto `ParseTreeXmlWriter` percorre os nós da CST.

`XmlEscaper` concentra o escape dos caracteres reservados.

A serialização preserva o formato oficial de terminais e usa UTF-8 e terminadores LF.

### 6.1. Produções auxiliares

Produções como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são reconhecidas, mas não introduzem obrigatoriamente elementos XML.

Por exemplo:

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

Não existe uma tag `<op>` intermediária.

Essa estrutura é verificada contra os arquivos XML oficiais do Project 10.

## 7. Tabela de símbolos

O pacote `jackcompiler.symbol` contém:

- `Symbol`: declaração imutável com nome, tipo, categoria e índice;
- `SymbolKind`: `STATIC`, `FIELD`, `ARGUMENT` e `LOCAL`;
- `SymbolTable`: escopos, contadores e resolução de nomes.

A tabela possui dois escopos:

| Escopo | Categorias |
|---|---|
| Classe | `STATIC`, `FIELD` |
| Sub-rotina | `ARGUMENT`, `LOCAL` |

Os índices começam em zero e são independentes por categoria.

`startSubroutine(boolean isMethod)` limpa o escopo da sub-rotina e reinicia os contadores correspondentes. Em métodos, reserva o índice zero de argumentos para o receptor implícito.

A resolução consulta primeiro o escopo da sub-rotina e depois o escopo de classe.

Duplicações no mesmo escopo são rejeitadas. O sombreamento entre escopos é permitido.

A tabela não escreve VM e não conhece os segmentos de memória da máquina virtual.

## 8. Emissão de instruções VM

O pacote `jackcompiler.vm` contém:

- `VmSegment`: segmentos válidos da VM;
- `VmArithmetic`: operações aritméticas e lógicas;
- `VmWriter`: emissor textual com validação de operandos.

`VmWriter` suporta:

```text
push segment index
pop segment index
add
sub
neg
eq
gt
lt
and
or
not
label name
goto name
if-goto name
function name nLocals
call name nArgs
return
```

O emissor valida nomes, contagens e restrições dos segmentos.

A saída é mantida em memória até a chamada de `toVm()`. O componente não acessa arquivos nem conhece a CST.

As instruções são separadas por LF.

## 9. Compilador Jack → VM

A fachada pública é:

```java
new JackVmCompiler().compile(classTree)
```

Recebe uma árvore `GrammarNode` completa e retorna uma `String` contendo o programa VM.

Cada invocação cria um `ClassCompiler` e estado de compilação independentes.

### 9.1. ClassCompiler

Coordena:

- identificação da classe;
- registro de variáveis de classe;
- catálogo de sub-rotinas;
- criação do escopo de cada rotina;
- registro de argumentos e variáveis locais;
- prólogos de funções, métodos e construtores;
- compilação do corpo.

O catálogo é construído antes da tradução dos corpos, permitindo chamadas a rotinas declaradas posteriormente.

### 9.2. CompilationContext

Mantém o estado da classe em compilação:

- nome da classe;
- tabela de símbolos;
- emissor VM;
- sub-rotina corrente;
- modalidade relevante ao contexto;
- contadores de rótulos.

O estado não é compartilhado entre invocações independentes.

### 9.3. SubroutineCatalog

Registra informações das sub-rotinas pertencentes à classe atual, incluindo modalidade, parâmetros e retorno declarado.

Permite distinguir chamadas locais de métodos, funções e construtores, além de detectar declarações duplicadas e inconsistências identificáveis.

Não substitui a tabela de variáveis.

### 9.4. ExpressionCompiler

Traduz termos, operações, variáveis, strings, arrays e chamadas.

O mapeamento de variáveis é:

| Categoria | Segmento |
|---|---|
| `STATIC` | `static` |
| `FIELD` | `this` |
| `ARGUMENT` | `argument` |
| `LOCAL` | `local` |

As operações `*` e `/` são traduzidas para chamadas a `Math.multiply` e `Math.divide`.

Constantes booleanas seguem a representação VM: verdadeiro como `-1`, falso e nulo como zero.

Strings são construídas usando `String.new` e `String.appendChar`.

Leituras de arrays calculam endereços e utilizam `pointer 1` e `that 0`.

Chamadas de métodos empilham o receptor e ajustam o número de argumentos. Chamadas de funções e construtores não recebem receptor implícito.

### 9.5. StatementCompiler

Traduz:

- `let`: atribuição simples ou indexada;
- `do`: chamada com descarte do retorno;
- `return`: valor retornado ou zero convencional para `void`;
- `if` e `if-else`: desvios condicionais;
- `while`: laços com rótulos de controle.

A escrita indexada preserva o valor a atribuir enquanto calcula e configura o endereço de destino.

Os rótulos são gerados por contexto de sub-rotina, de forma determinística e sem colisões nos aninhamentos previstos.

### 9.6. Métodos e construtores

Funções emitem:

```text
function Classe.rotina nLocals
```

Métodos associam o receptor implícito a `pointer 0`.

Construtores calculam a quantidade de campos, chamam `Memory.alloc` e configuram `pointer 0`.

Campos são acessados pelo segmento `this`.

O compilador rejeita usos de `this` ou de campos que dependam de instância em funções estáticas, quando identificáveis.

### 9.7. Diagnósticos semânticos

`CompilationException` comunica erros semânticos ou estruturas de CST incompatíveis com a geração VM.

Quando uma posição de origem está disponível, ela é preservada. Caso contrário, o diagnóstico não inventa linha ou coluna.

A implementação verifica inconsistências locais necessárias para emitir VM corretamente, mas não constitui um verificador estático global de tipos.

Classes externas e bibliotecas Jack OS podem ser utilizadas sem compilação conjunta de suas fontes.

`SyntaxTreeNavigator` fornece leitura estrutural segura da CST, sem implementar um novo parser.

## 10. Entrada, saída e processamento

O pacote `jackcompiler.io` é compartilhado pelos três casos de uso.

### 10.1. Descoberta de fontes

`JackFileResolver` possui dois contratos:

| Operação | Utilização |
|---|---|
| `resolve(Path)` | `tokenize` e `parse`, sem recursão |
| `resolveRecursively(Path)` | `compile`, com recursão |

A extensão `.jack` é sensível a maiúsculas e minúsculas.

A resolução recursiva ordena os caminhos relativos, rejeita a travessia de links simbólicos de diretórios e falha se não encontrar fontes elegíveis.

### 10.2. Resolução dos destinos

`OutputKind` representa `TOKEN_XML`, `SYNTAX_XML` e `VM`.

`OutputPathResolver` preserva os destinos XML anteriores e fornece os contratos específicos:

```java
resolveVm(source, input, outputDirectory)
resolveVmBatch(input, sources, outputDirectory)
```

A compilação de diretórios com destino personalizado espelha os subdiretórios relativos.

O lote é planejado antes do processamento, verificando colisões de destinos e aliases das fontes.

### 10.3. Políticas de escrita

`TextFileWriter` publica conteúdo UTF-8 sob duas políticas:

- `REPLACE_EXISTING`: substituição controlada de arquivo regular;
- `CREATE_NEW`: rejeição de destino ocupado.

A escrita utiliza arquivo temporário no diretório de destino.

Para substituição, tenta movimentação atômica e utiliza alternativa quando o sistema não a suporta. Para criação exclusiva, utiliza publicação que impede sobrescrita por colisão.

O componente verifica caminhos simbólicos, arquivos incompatíveis e diretórios protegidos.

Os destinos são revalidados na publicação, pois o sistema de arquivos pode mudar entre o planejamento e a escrita.

### 10.4. Processamento em lote

`CompileCommand` determina os arquivos e destinos antes de compilar.

Cada fonte é processada sequencialmente:

```text
leitura
  -> tokenização
  -> análise sintática
  -> compilação VM
  -> publicação
```

O conteúdo VM de um arquivo é produzido integralmente antes de sua escrita.

Uma falha interrompe o lote. Saídas já publicadas permanecem; não há transação global nem rollback.

Essa política também é preservada nos casos de uso anteriores, respeitando seus contratos próprios.

## 11. CLI e tratamento de erros

A CLI oferece os comandos `tokenize`, `parse`, `compile` e `--help`.

Os códigos de saída são:

| Código | Condição |
|---|---|
| 0 | Sucesso ou ajuda |
| 1 | Erro de processamento |
| 2 | Argumentos inválidos |

`SourceProcessingException` associa a falha ao arquivo envolvido.

A apresentação distingue:

- `LexicalException`;
- `SyntaxException`;
- `CompilationException`;
- `IOException` e falhas operacionais.

O diagnóstico utiliza linha e coluna apenas quando essas informações estão disponíveis.

`JackCompiler` imprime arquivos gerados em `stdout` e diagnósticos em `stderr`.

## 12. Verificação arquitetural

Os contratos públicos relevantes são exercitados por:

- `LexerPublicApiTest`;
- `CompilerPublicApiTest`;
- testes específicos de scanner, parser, símbolos, compilador e VM;
- testes dos três casos de uso;
- testes de I/O e CLI;
- testes de integração do JAR;
- testes de aceitação oficial.

A ausência de regressões nos XMLs oficiais é verificada por `OfficialTokenXmlCompatibilityTest` e `OfficialParserXmlCompatibilityTest`.

O contrato de geração VM é exercitado por `OfficialVmGenerationTest`.

As verificações automatizadas não substituem uma auditoria completa de dependências e encapsulamento.

O procedimento integral de validação é:

```bash
mvn clean verify
```

Os cenários de teste e resultados observados estão em [testing.md](testing.md). A correspondência entre requisitos e evidências está em [acceptance.md](acceptance.md).

As instruções de utilização estão no [README.md](../README.md).
