
# Arquitetura do JackCompiler

Este documento descreve a arquitetura implementada do JackCompiler, abrangendo o analisador léxico da Parte 1 e o analisador sintático da Parte 2. Ambos integram o mesmo projeto Java 21 e compartilham os componentes necessários, preservando a separação de responsabilidades.

## 1. Organização e dependências

O código de produção está organizado em oito pacotes:

| Pacote | Responsabilidade | Dependências internas |
|---|---|---|
| `jackcompiler.token` | Tipos dos tokens, valores e posições de origem | Nenhuma |
| `jackcompiler.lexer` | Reconhecimento léxico e tratamento de espaços e comentários | `token` |
| `jackcompiler.syntax` | Representação imutável da árvore sintática concreta | `token` |
| `jackcompiler.parser` | Reconhecimento da gramática Jack por análise descendente recursiva | `token`, `syntax` |
| `jackcompiler.xml` | Serialização de tokens e árvores sintáticas no formato XML oficial | `token`, `syntax` |
| `jackcompiler.io` | Descoberta de fontes, leitura UTF-8, resolução de destinos e escrita de arquivos | Nenhuma |
| `jackcompiler.application` | Coordenação dos casos de uso de tokenização e análise sintática | `io`, `lexer`, `parser`, `xml` |
| `jackcompiler.cli` | Interpretação de argumentos, seleção de comandos e apresentação de resultados e erros | `application`, `lexer`, `parser` |

As dependências são direcionadas das camadas de entrada e coordenação para os componentes especializados.

O analisador léxico não depende do parser, das árvores sintáticas, da serialização XML ou da interface de linha de comando. O parser utiliza os contratos públicos definidos na Parte 1 sem conhecer o sistema de arquivos ou a CLI.

O pacote `application` coordena o processamento, mas não depende de `cli`. A serialização permanece independente da análise léxica e sintática.

Essa organização permite utilizar `JackTokenizer` e `JackParser` diretamente por suas APIs, sem executar o programa pela linha de comando.

## 2. Fluxo de execução

O JAR utiliza `jackcompiler.cli.JackCompiler` como classe principal. A CLI interpreta os argumentos por meio de `CliArgumentParser` e `CliArguments` e encaminha a operação ao caso de uso correspondente.

### Tokenização

```text
JackCompiler
  -> CliArgumentParser
  -> TokenizeCommand
     -> JackFileResolver
     -> JackSourceReader
     -> JackTokenizer
        -> TokenizationResult
     -> TokenXmlWriter
     -> OutputPathResolver
     -> TextFileWriter
  -> resultado ou diagnóstico
```

O comando `tokenize` recebe um arquivo Jack ou um diretório, identifica os fontes elegíveis e processa cada arquivo individualmente.

O scanner produz um `TokenizationResult`, posteriormente serializado por `TokenXmlWriter`. A saída segue o padrão `NomeT.xml`.

### Análise sintática

```text
JackCompiler
  -> CliArgumentParser
  -> ParseCommand
     -> JackFileResolver
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

O comando `parse` reutiliza o mesmo scanner e os mesmos serviços de entrada e saída da Parte 1.

A diferença está na etapa posterior à tokenização: `JackParser` recebe o resultado léxico e constrói uma árvore sintática concreta, que `ParseTreeXmlWriter` transforma no XML de saída `NomeP.xml`.

`TokenizeCommand` e `ParseCommand` permanecem como casos de uso distintos. Ambos reutilizam os serviços concretos de que necessitam, sem introduzir um coordenador genérico para suas operações.

## 3. Representação léxica

O pacote `jackcompiler.token` estabelece o contrato compartilhado entre scanner e parser.

### Tipos principais

- **`TokenType`:** enumeração das cinco categorias léxicas oficiais: `KEYWORD`, `SYMBOL`, `INTEGER_CONSTANT`, `STRING_CONSTANT` e `IDENTIFIER`.
- **`SourcePosition`:** registro imutável de linha e coluna, ambas iniciadas em 1.
- **`Token`:** registro imutável com tipo, valor e posição de origem.
- **`TokenizationResult`:** registro que contém a sequência imutável de tokens e a posição correspondente ao fim da entrada.

O scanner não produz um token artificial de EOF. A posição final é armazenada separadamente em `TokenizationResult.endPosition()`, permitindo que o parser comunique erros encontrados no término inesperado da entrada.

A API pública do scanner é:

```java
new JackTokenizer().tokenize(source)
```

O método recebe o conteúdo textual da fonte e retorna `TokenizationResult`. Erros de reconhecimento são comunicados por `LexicalException`, que preserva a posição original da falha.

### Reconhecimento léxico

`JackTokenizer` cria um `SourceCursor` independente para cada análise.

Antes de reconhecer o próximo token, `TriviaSkipper` descarta espaços e comentários. Os reconhecedores são consultados nesta ordem:

1. `StringRecognizer`
2. `IntegerRecognizer`
3. `IdentifierRecognizer`
4. `SymbolRecognizer`

A escolha depende das verificações de cada reconhecedor. Caracteres que não correspondem a nenhuma categoria produzem um erro léxico explícito.

`JackLexicalRules` centraliza as regras da linguagem, incluindo as 21 palavras reservadas, os 19 símbolos oficiais, as condições de formação de identificadores e o limite de `32767` para constantes inteiras.

O conjunto de símbolos inclui `[` e `]`, utilizados na indexação de arrays. Embora esses caracteres estejam ausentes da lista apresentada no enunciado da Parte 1, pertencem à especificação oficial de Jack e são necessários para reconhecer programas como `ArrayTest`.

`SourceCursor` acompanha posições por pontos de código Unicode. As sequências LF, CRLF e CR são tratadas como quebras lógicas de linha.

Os tokens preservam seus valores originais. O escape de caracteres reservados do XML é realizado apenas durante a serialização, não durante o reconhecimento léxico.

## 4. Analisador sintático

O pacote `jackcompiler.parser` implementa um analisador descendente recursivo (*recursive descent parser*) para a gramática Jack.

A organização separa o ponto de entrada público, a navegação pelos tokens e o reconhecimento de diferentes grupos de produções gramaticais.

### JackParser

`JackParser` é a fachada pública do analisador sintático.

Sua operação principal é:

```java
new JackParser().parse(tokenizationResult)
```

Cada chamada cria um `TokenCursor` e os componentes internos necessários para analisar uma classe Jack completa.

O resultado é um `GrammarNode` correspondente à produção `class`.

Após reconhecer o fechamento da classe, o método verifica se todos os tokens foram consumidos. A presença de tokens adicionais é tratada como erro sintático.

O parser recebe diretamente o resultado produzido pelo scanner. Não realiza leitura de arquivos, tokenização independente ou serialização XML.

### TokenCursor

`TokenCursor` navega pela sequência imutável recebida do scanner e mantém a posição atual de leitura.

As operações disponibilizadas são:

| Método | Responsabilidade |
|---|---|
| `peek()` | Consultar o token atual sem consumi-lo |
| `peek(offset)` | Consultar um token posterior |
| `match(type)` | Verificar o tipo do token atual |
| `match(type, value)` | Verificar o tipo e o valor do token atual |
| `advance()` | Consumir e retornar o token atual |
| `consume(type)` | Exigir e consumir um token de determinado tipo |
| `consume(type, value)` | Exigir e consumir um token de determinado tipo e valor |
| `isAtEnd()` | Verificar se todos os tokens foram consumidos |
| `position()` | Obter a posição atual ou a posição original do EOF |

As operações `consume` não avançam o cursor quando o token encontrado não corresponde ao esperado.

O compartilhamento de um único cursor pelos componentes internos preserva a ordem de consumo dos tokens e permite reconhecer estruturas recursivas sem duplicar o estado de navegação.

### ClassStructureParser

`ClassStructureParser` reconhece a estrutura de classes, declarações e sub-rotinas.

Suas principais produções são:

- `class`
- `classVarDec`
- `subroutineDec`
- `parameterList`
- `subroutineBody`
- `varDec`
- `type`

Os métodos auxiliares reconhecem nomes de classes, sub-rotinas e variáveis, além do tipo de retorno de uma sub-rotina.

O componente utiliza `StatementParser` para reconhecer os comandos presentes no corpo de cada sub-rotina.

### StatementParser

`StatementParser` reconhece sequências de comandos e as cinco formas de instrução previstas na gramática Jack:

- `letStatement`
- `ifStatement`
- `whileStatement`
- `doStatement`
- `returnStatement`

`parseStatements()` reconhece a repetição de comandos. Para cada ocorrência, `parseStatement()` identifica a produção correspondente e delega seu reconhecimento ao método específico.

Os comandos que contêm expressões utilizam `ExpressionParser`. Estruturas como `if` e `while` podem chamar recursivamente `parseStatements()` para reconhecer comandos aninhados.

O método `parseStatement()` não acrescenta um nó genérico à árvore: retorna o nó específico da instrução reconhecida.

### ExpressionParser

`ExpressionParser` reconhece:

- `expression`
- `term`
- `expressionList`
- `subroutineCall`
- `op`
- `unaryOp`
- `keywordConstant`

O reconhecimento contempla constantes, identificadores, expressões entre parênteses, operadores binários e unários, acesso a arrays e chamadas diretas ou qualificadas de sub-rotinas.

As produções auxiliares possuem métodos específicos, como `parseOp()`, `parseUnaryOp()` e `parseKeywordConstant()`, utilizados pelos métodos que reconhecem as estruturas maiores.

`parseSubroutineCall()` é compartilhado entre a análise de termos e o reconhecimento de comandos `do`, evitando duplicar a gramática das chamadas de sub-rotinas.

O método retorna os nós correspondentes aos tokens e à lista de expressões, sem criar um elemento sintático intermediário que não exista no XML oficial.

### Identificadores e chamadas qualificadas

As produções `className`, `subroutineName` e `varName` utilizam lexicalmente a categoria `IDENTIFIER`. Métodos específicos tornam explícito o papel gramatical do identificador nos contextos em que esse papel é determinado pela sintaxe.

Em uma chamada qualificada, como `obj.method()` ou `Class.method()`, o identificador anterior ao ponto pode representar uma variável ou uma classe. Essa distinção não pode ser estabelecida apenas pela sequência de tokens.

Por esse motivo, `parseCallQualifier()` reconhece o identificador qualificador sem atribuir antecipadamente uma classificação que dependa de informações externas à gramática.

A representação XML preserva o identificador original em ambos os casos.

## 5. Precedência, associatividade e agrupamento

A gramática oficial de Jack define expressões binárias pela produção:

```text
expression ::= term (op term)*
```

Essa produção não impõe uma hierarquia obrigatória de precedência entre operadores binários.

O `ExpressionParser` reconhece um termo inicial e, em seguida, zero ou mais pares formados por operador e termo. A árvore preserva essa sequência, sem introduzir agrupamentos implícitos baseados nas convenções de outras linguagens.

Assim, a entrada:

```jack
2 + 3 * 4
```

é representada como uma expressão que contém três termos e dois operadores, em sua ordem original. O parser não calcula um resultado aritmético nem reorganiza a expressão para privilegiar a multiplicação.

Os parênteses são tratados de forma distinta. A produção `term` permite reconhecer uma expressão entre `(` e `)`, gerando um novo nó `expression` aninhado dentro de `term`.

Portanto, agrupamentos explícitos são preservados na árvore sintática.

Essa organização foi adotada para manter correspondência com a gramática e com os arquivos XML oficiais do nand2tetris. A especificação da linguagem permite diferentes políticas de precedência para compiladores, mas não determina que o XML do analisador sintático introduza uma política adicional.

## 6. Representação da árvore sintática

O pacote `jackcompiler.syntax` define uma árvore sintática concreta e imutável.

A interface selada `ParseNode` admite duas implementações:

| Tipo | Representação |
|---|---|
| `GrammarNode` | Produção gramatical, identificada por `SyntaxTag`, com filhos em ordem |
| `TerminalNode` | Token léxico original, preservando tipo, valor e posição |

Ambas as implementações são registros Java (*records*).

`GrammarNode` realiza uma cópia imutável da lista de filhos. `TerminalNode` mantém a referência ao token original.

O uso de uma árvore sintática concreta permite preservar elementos importantes para a equivalência XML, incluindo palavras reservadas, identificadores, símbolos delimitadores e operadores.

### Categorias de nós gramaticais

A enumeração `SyntaxTag` contém as 15 categorias utilizadas no formato XML sintático oficial:

```text
CLASS
CLASS_VAR_DEC
SUBROUTINE_DEC
PARAMETER_LIST
SUBROUTINE_BODY
VAR_DEC
STATEMENTS
LET_STATEMENT
IF_STATEMENT
WHILE_STATEMENT
DO_STATEMENT
RETURN_STATEMENT
EXPRESSION
TERM
EXPRESSION_LIST
```

Nem toda produção reconhecida pelo parser corresponde a um `GrammarNode` próprio.

Essa distinção é intencional: a responsabilidade de reconhecer uma produção gramatical não implica a necessidade de introduzir um elemento adicional na representação XML.

## 7. Serialização XML

O pacote `jackcompiler.xml` contém os componentes responsáveis pela produção das duas modalidades de XML.

### TokenXmlWriter

`TokenXmlWriter.toXml(TokenizationResult)` gera documentos com o elemento raiz `<tokens>` e um elemento para cada token reconhecido.

A formatação de um terminal fica concentrada em `TokenXmlWriter.formatToken(Token)`, método interno ao pacote `xml` que também é reutilizado pelo serializador sintático.

### ParseTreeXmlWriter

`ParseTreeXmlWriter.toXml(GrammarNode)` percorre recursivamente a árvore sintática concreta.

Para cada `GrammarNode`, utiliza a categoria `SyntaxTag` para produzir as tags de abertura e fechamento. Para cada `TerminalNode`, reutiliza a formatação de token definida em `TokenXmlWriter`.

A serialização mantém:

- ordem dos elementos e dos tokens;
- hierarquia das produções representadas;
- indentação de dois espaços por nível;
- terminadores de linha LF;
- ausência de declaração XML;
- espaços convencionais dentro dos elementos terminais.

`XmlEscaper` realiza o escape de caracteres reservados e rejeita caracteres incompatíveis com XML 1.0.

### Compatibilidade com o nand2tetris

O enunciado da Parte 2 solicita tanto o reconhecimento das produções gramaticais quanto a equivalência estrutural com os XMLs oficiais.

O JackCompiler adota as categorias e a hierarquia utilizadas nas referências do Project 10 do nand2tetris.

Produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são reconhecidas pelo parser, mas não introduzem tags intermediárias próprias.

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

O operador é representado pelo terminal `<symbol>`, sem uma tag `<op>`.

Da mesma forma, os comandos aparecem diretamente como `<letStatement>`, `<ifStatement>`, `<whileStatement>`, `<doStatement>` ou `<returnStatement>` dentro de `<statements>`, sem um elemento intermediário `<statement>`.

Essa representação evita diferenças estruturais em relação aos arquivos oficiais e mantém independente a organização dos métodos de análise sintática.

## 8. Entrada, saída e proteção dos arquivos

Os dois comandos compartilham os serviços do pacote `jackcompiler.io`.

| Componente | Responsabilidade |
|---|---|
| `JackFileResolver` | Resolver um arquivo `.jack` ou os arquivos elegíveis de um diretório |
| `JackSourceReader` | Ler o conteúdo com decodificação UTF-8 estrita |
| `OutputPathResolver` | Determinar o nome e o diretório do XML de saída |
| `TextFileWriter` | Publicar o XML em disco conforme a política de escrita |
| `OutputKind` | Diferenciar a saída léxica da sintática |
| `WritePolicy` | Diferenciar as políticas `CREATE_NEW` e `REPLACE_EXISTING` |

### Resolução de fontes

`JackFileResolver` aceita arquivos `.jack` ou diretórios.

Na entrada por diretório, seleciona somente arquivos regulares com extensão `.jack` diretamente contidos nele. A seleção é não recursiva e os arquivos são processados em ordem determinística de nome.

`JackSourceReader` preserva o conteúdo textual lido e rejeita sequências de bytes inválidas em UTF-8.

### Resolução dos destinos

`OutputPathResolver` utiliza o tipo de saída para determinar o sufixo e o diretório padrão:

| Operação | Sufixo | Diretório padrão |
|---|---|---|
| Tokenização | `T.xml` | `target/generated-tokens/` |
| Análise sintática | `P.xml` | `target/generated-parser/` |

Um diretório definido por `--output-dir` substitui o destino padrão de qualquer uma das operações.

A resolução produz caminhos absolutos normalizados e verifica possíveis aliases entre destino e arquivo-fonte.

### Publicação e colisões

`TextFileWriter` recebe o conteúdo completo do XML antes de iniciar sua escrita.

A publicação utiliza um arquivo temporário no diretório de destino, com duas políticas:

- **`REPLACE_EXISTING`:** utilizada nos diretórios padrão; permite substituir arquivos regulares preexistentes. Tenta uma movimentação atômica e utiliza uma movimentação convencional quando a operação atômica não é suportada.
- **`CREATE_NEW`:** utilizada quando o usuário fornece `--output-dir`; rejeita destinos existentes e utiliza um hard link para publicar o arquivo sem sobrescrever outro arquivo com o mesmo nome.

A implementação verifica links simbólicos nos caminhos de saída, impede a gravação em diretórios protegidos de código e recursos de teste e não permite publicar XML sobre arquivos-fonte `.jack`.

Os arquivos temporários são removidos após a operação ou falhas recuperáveis.

Não se pressupõe que todas as operações de publicação sejam atômicas em qualquer sistema de arquivos.

### Processamento em lote

`TokenizeCommand` e `ParseCommand` seguem a mesma política de processamento:

- os arquivos selecionados são processados em ordem;
- a primeira falha interrompe a operação;
- os resultados já publicados permanecem;
- os arquivos seguintes não são processados;
- não existe rollback do lote completo.

Essa política impede que uma falha seja ignorada e evita apresentar como concluído um processamento que não terminou.

## 9. Tratamento de erros

O JackCompiler distingue falhas de argumentos, análise léxica, análise sintática e operações de entrada e saída.

**`LexicalException`** representa erros de reconhecimento de tokens e preserva a posição de origem.

**`SyntaxException`** representa violações da gramática. A exceção mantém a descrição do elemento esperado, o token encontrado — quando existente — e sua posição. No fim da entrada, utiliza a posição EOF fornecida pelo resultado léxico.

**`SourceProcessingException`** associa uma falha ao arquivo-fonte processado e preserva sua causa. Em falhas anteriores à seleção dos arquivos, utiliza o caminho da entrada solicitada.

A CLI interpreta essas falhas e apresenta diagnósticos em `stderr`, utilizando os seguintes códigos de saída:

| Código | Significado |
|---|---|
| `0` | Sucesso ou exibição da ajuda |
| `1` | Falha durante o processamento |
| `2` | Argumentos inválidos |

Erros léxicos e sintáticos apresentam arquivo, linha e coluna. Os demais diagnósticos não recebem posições artificiais.

## 10. Contratos e verificação arquitetural

A integração entre os analisadores preserva o contrato público da Parte 1:

```text
String
  -> JackTokenizer.tokenize(...)
  -> TokenizationResult
  -> JackParser.parse(...)
  -> GrammarNode
  -> ParseTreeXmlWriter.toXml(...)
  -> String XML
```

Cada etapa possui responsabilidades delimitadas e pode ser utilizada sem executar as outras camadas de aplicação ou interface.

Os testes verificam, entre outros aspectos:

- a API pública do scanner e seus limites de dependência;
- o reconhecimento das produções da gramática;
- a preservação de tokens e posições;
- a estrutura imutável dos nós sintáticos;
- os erros léxicos e sintáticos;
- a integração entre scanner, parser e serialização;
- a equivalência estrutural dos XMLs com as referências oficiais;
- a execução dos comandos por meio do JAR empacotado.

`LexerPublicApiTest` verifica aspectos da visibilidade e do contrato léxico, incluindo referências explícitas a dependências proibidas no código de produção. Essas verificações não constituem uma análise estática completa de todas as dependências possíveis.

Os testes de aceitação utilizam os programas oficiais `Square`, `ExpressionLessSquare` e `ArrayTest`. A comparação dos XMLs sintáticos preserva a hierarquia, a ordem dos filhos e os valores significativos dos terminais.

A execução integral é realizada com:

```bash
mvn clean verify
```

Os detalhes dos cenários, as evidências de execução e os resultados estão em [testing.md](testing.md). A correspondência entre os requisitos acadêmicos e suas verificações está em [acceptance.md](acceptance.md).

As instruções de construção e utilização encontram-se no [README.md](../README.md).
