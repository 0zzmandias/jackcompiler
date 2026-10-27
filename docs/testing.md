
# Testes e validação do JackCompiler

Este documento descreve a estratégia de testes, os materiais de referência, os critérios de comparação e as evidências de validação dos analisadores léxico e sintático do JackCompiler.

As duas partes são verificadas conjuntamente para assegurar que a integração do parser preserve os contratos e os comportamentos estabelecidos para o scanner.

## 1. Ambiente de testes

O projeto utiliza **Java 21**, **Apache Maven** e **JUnit 5 (Jupiter)**.

As principais ferramentas de construção e validação são:

| Componente | Versão | Responsabilidade |
|---|---|---|
| JDK | 21 | Compilação e execução |
| JUnit Jupiter | 5.11.4 | Testes automatizados |
| Maven Compiler Plugin | 3.14.0 | Compilação Java |
| Maven Surefire Plugin | 3.5.2 | Execução dos testes unitários e de aceitação |
| Maven Failsafe Plugin | 3.5.2 | Execução dos testes de integração do JAR |
| Maven JAR Plugin | 3.4.2 | Empacotamento do executável |
| Spotless | 2.43.0 | Verificação da formatação Java |
| Google Java Format | 1.24.0 | Padronização da formatação |

A configuração dessas ferramentas está definida em [`pom.xml`](../pom.xml).

A integração contínua utiliza GitHub Actions com JDK 21, distribuição Temurin, conforme [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

## 2. Execução da suíte

Na raiz do repositório, execute:

```bash
mvn clean verify
```

O comando executa as seguintes etapas:

1. **Limpeza:** remove os artefatos da construção anterior.
2. **Formatação:** verifica o código Java com Spotless.
3. **Compilação:** compila os arquivos de produção e de teste para Java 21.
4. **Testes:** executa os testes pelo Surefire.
5. **Empacotamento:** produz `target/jackcompiler.jar`.
6. **Integração:** executa os testes do JAR empacotado pelo Failsafe.
7. **Verificação:** confirma os resultados das etapas anteriores.

Se o Spotless identificar problemas de formatação, execute:

```bash
mvn spotless:apply
mvn clean verify
```

O comando `spotless:apply` aplica a formatação configurada no projeto. A execução posterior de `clean verify` confirma que a construção completa permanece válida.

### Relatórios produzidos

Os relatórios podem ser consultados em:

```text
target/
├── surefire-reports/
├── failsafe-reports/
└── jackcompiler.jar
```

Para verificar os relatórios e o manifesto do executável:

```bash
ls target/surefire-reports
ls target/failsafe-reports

grep -h "Tests run:" target/failsafe-reports/*.txt

unzip -p target/jackcompiler.jar META-INF/MANIFEST.MF
```

O manifesto do JAR identifica `jackcompiler.cli.JackCompiler` como classe principal.

Os testes Failsafe executam o JAR em processos Java independentes, após seu empacotamento. Essa verificação é diferente de invocar diretamente os métodos da aplicação durante os testes unitários.

## 3. Estratégia de testes

A suíte está distribuída por responsabilidades, com verificações específicas para os componentes e testes de integração entre eles.

### 3.1. Analisador léxico

| Área | Classes de teste | Verificações principais |
|---|---|---|
| Tokens e posições | `TokenTest`, `SourcePositionTest`, `TokenizationResultTest` | Validação dos dados, imutabilidade, posições e EOF |
| Regras da linguagem | `JackLexicalRulesTest` | Palavras reservadas, símbolos, identificadores e limites |
| Navegação na fonte | `SourceCursorTest` | Avanço, consulta, Unicode e quebras de linha |
| Reconhecimento de tokens | `IdentifierRecognizerTest`, `IntegerRecognizerTest`, `StringRecognizerTest`, `SymbolRecognizerTest` | Categorias léxicas, lexemas e entradas inválidas |
| Espaços e comentários | `WhitespaceConsumerTest`, `LineCommentConsumerTest`, `BlockCommentConsumerTest`, `TriviaSkipperTest` | Descarte de conteúdo não significativo e erros em comentários |
| Scanner integrado | `JackTokenizerTest`, `LexicalExceptionTest` | Sequência de tokens, posições e diagnósticos |
| Contrato público | `LexerPublicApiTest` | API reutilizável, encapsulamento e limites de dependência |

Os testes verificam as cinco categorias de tokens, as 21 palavras reservadas e os 19 símbolos oficiais da linguagem Jack.

Também são examinados os limites de constantes inteiras, as regras de identificadores, o conteúdo literal das strings e os formatos de comentário.

As posições são verificadas considerando linhas e colunas iniciadas em 1, incluindo os diferentes terminadores de linha e a navegação por pontos de código Unicode.

`LexerPublicApiTest` exercita o scanner a partir de um pacote externo e verifica propriedades de sua interface pública. A classe também identifica determinadas referências explícitas a dependências não permitidas no código de produção; não constitui, isoladamente, uma análise estática completa da arquitetura.

### 3.2. Analisador sintático

| Área | Classes de teste | Verificações principais |
|---|---|---|
| Navegação por tokens | `TokenCursorTest` | Consulta, consumo, lookahead e EOF |
| Estrutura das classes | `ClassStructureParserTest`, `ClassStructureParserDeclarationTest`, `ClassStructureParserSubroutineTest` | Classes, variáveis, parâmetros, sub-rotinas e tipos |
| Expressões | `ExpressionParserTest`, `ExpressionParserRecursiveTermTest` | Termos, operadores, constantes, agrupamentos e recursão |
| Arrays e argumentos | `ExpressionParserArrayAccessTest`, `ExpressionParserExpressionListTest` | Indexação e listas de expressões |
| Chamadas de sub-rotinas | `ExpressionParserSubroutineCallTest` | Chamadas diretas, qualificadas e aninhadas |
| Sequências e comandos | `StatementParserTest`, `StatementParserLetTest`, `StatementParserIfTest`, `StatementParserWhileTest`, `StatementParserDoTest`, `StatementParserReturnTest` | Cinco tipos de comando, combinações e aninhamento |
| Integração do parser | `JackParserTest` | Reconhecimento de classes completas e rejeição de entradas inválidas |
| Erros sintáticos | `SyntaxExceptionTest` | Elementos esperados, tokens encontrados e posições |

Os testes do parser abrangem tanto construções válidas quanto violações da gramática Jack.

Entre as condições verificadas estão:

- reconhecimento das declarações e estruturas de uma classe;
- comandos e expressões aninhados;
- chamadas de sub-rotinas com e sem qualificador;
- acesso a elementos de arrays;
- expressões e listas de argumentos vazias quando permitidas;
- operadores binários e unários;
- preservação da sequência original dos termos e operadores;
- agrupamentos explícitos por parênteses;
- delimitadores ausentes, tokens inesperados e EOF prematuro;
- rejeição de tokens adicionais após o fechamento de uma classe.

Os testes de expressões verificam que o parser não introduz uma precedência binária artificial. Expressões entre parênteses são reconhecidas recursivamente e representadas por estruturas aninhadas.

A análise sintática preserva os tokens e suas posições originais, permitindo que erros sejam relacionados ao ponto correspondente no arquivo-fonte.

### 3.3. Árvore sintática e XML

| Área | Classes de teste | Verificações principais |
|---|---|---|
| Nós da árvore | `ParseNodeTest`, `GrammarNodeTest`, `TerminalNodeTest` | Representação e imutabilidade |
| Categorias gramaticais | `SyntaxTagTest` | Elementos suportados pela árvore |
| XML de tokens | `TokenXmlWriterTest` | Formatação de terminais e estrutura de tokens |
| XML sintático | `ParseTreeXmlWriterTest` | Hierarquia, ordem, indentação e terminais |
| Escape XML | `XmlEscaperTest` | Entidades XML e rejeição de caracteres inválidos |

A árvore sintática utiliza `GrammarNode` para produções representadas como elementos estruturais e `TerminalNode` para os tokens originais.

Os testes verificam a preservação da ordem dos filhos e das informações dos terminais, assim como a serialização das 15 categorias gramaticais previstas no formato oficial.

Também são examinados o escape de caracteres reservados, o tratamento de caracteres incompatíveis com XML 1.0 e a preservação do conteúdo significativo de strings.

### 3.4. Aplicação, arquivos e CLI

| Área | Classes de teste | Verificações principais |
|---|---|---|
| Descoberta de fontes | `JackFileResolverTest` | Arquivos, diretórios, seleção e ordenação |
| Leitura | `JackSourceReaderTest` | UTF-8 estrito e preservação do conteúdo |
| Resolução de destinos | `OutputPathResolverTest` | Sufixos, diretórios, colisões e aliases |
| Escrita | `TextFileWriterTest` | Publicação, arquivos temporários e proteção dos destinos |
| Tokenização | `TokenizeCommandTest` | Processamento completo e tratamento de falhas |
| Análise sintática | `ParseCommandTest` | Integração entre scanner, parser, XML e escrita |
| Exceções de aplicação | `SourceProcessingExceptionTest` | Contextualização e preservação de causas |
| Argumentos e CLI | `CliArgumentsTest`, `CliArgumentParserTest`, `CliIntegrationTest` | Comandos, opções, ajuda e códigos de saída |
| Ambiente | `BuildEnvironmentTest` | Compatibilidade com o ambiente Java |

Os testes verificam que `tokenize` e `parse` compartilham os serviços de entrada e saída, mas executam seus respectivos fluxos de processamento.

Também cobrem a política de processamento **fail-fast**, a rejeição de destinos inválidos e a preservação de arquivos já existentes diante de falhas.

Os diagnósticos léxicos e sintáticos são verificados com suas posições originais. A CLI também é testada quanto aos códigos de saída `0`, `1` e `2` e à separação entre `stdout` e `stderr`.

## 4. Validação com os arquivos oficiais

As verificações de aceitação utilizam materiais do repositório oficial [nand2tetris/projects](https://github.com/nand2tetris/projects), revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença dos materiais está preservada em:

```text
src/test/resources/nand2tetris/LICENSE
```

### 4.1. Programas de referência

São utilizados sete programas Jack, distribuídos em três conjuntos:

| Conjunto | Programas | Quantidade |
|---|---|---:|
| `Square` | `Main`, `Square`, `SquareGame` | 3 |
| `ExpressionLessSquare` | `Main`, `Square`, `SquareGame` | 3 |
| `ArrayTest` | `Main` | 1 |
| **Total** | | **7** |

Cada programa possui um arquivo-fonte `.jack`, uma referência `*T.xml` para tokens e uma referência `*.xml` para a árvore sintática.

A organização é:

```text
src/test/resources/nand2tetris/
├── LICENSE
├── Square/
│   ├── Main.jack
│   ├── MainT.xml
│   ├── Main.xml
│   ├── Square.jack
│   ├── SquareT.xml
│   ├── Square.xml
│   ├── SquareGame.jack
│   ├── SquareGameT.xml
│   └── SquareGame.xml
├── ExpressionLessSquare/
│   ├── Main.jack
│   ├── MainT.xml
│   ├── Main.xml
│   ├── Square.jack
│   ├── SquareT.xml
│   ├── Square.xml
│   ├── SquareGame.jack
│   ├── SquareGameT.xml
│   └── SquareGame.xml
└── ArrayTest/
    ├── Main.jack
    ├── MainT.xml
    └── Main.xml
```

Os arquivos oficiais são utilizados como referências de leitura. Quando os testes precisam executar os comandos da aplicação, os fontes são copiados para diretórios temporários.

### 4.2. Comparação do XML léxico

A classe `OfficialTokenXmlCompatibilityTest` executa a comparação contra os arquivos oficiais `*T.xml`.

São realizados dois tipos principais de verificação:

1. **Scanner e serializador:** `JackTokenizer` e `TokenXmlWriter` são executados diretamente sobre o código-fonte oficial.
2. **Caso de uso completo:** `TokenizeCommand` processa uma cópia do fonte e produz o XML em um diretório temporário.

A comparação exige correspondência textual com a referência. A única normalização aplicada ao arquivo oficial é a conversão de terminadores CRLF ou CR para LF.

Não são removidos espaços de conteúdo, alterados escapes, reorganizados tokens ou modificados seus valores.

O programa `ArrayTest/Main` possui uma verificação adicional para preservar os espaços significativos da string `HOW MANY NUMBERS?`.

A execução por `TokenizeCommand` também verifica que os bytes do arquivo-fonte permanecem inalterados.

### 4.3. Comparação do XML sintático

A classe `OfficialParserXmlCompatibilityTest` verifica a compatibilidade com os arquivos oficiais `*.xml`.

Assim como na validação léxica, são exercitados dois fluxos:

1. **API:** `JackTokenizer`, `JackParser` e `ParseTreeXmlWriter`.
2. **Aplicação:** `ParseCommand`, incluindo geração do arquivo `*P.xml`.

São realizadas **14 comparações principais**, duas para cada um dos sete programas oficiais.

A comparação sintática utiliza o processamento estruturado do XML, verificando:

- nomes dos elementos;
- hierarquia dos nós;
- quantidade e ordem dos filhos;
- categorias e valores dos terminais;
- presença dos espaços convencionais que delimitam o conteúdo dos terminais;
- preservação dos espaços pertencentes aos valores de strings;
- ausência de atributos inesperados;
- ausência de conteúdo não permitido nos elementos gramaticais.

A comparação desconsidera apenas diferenças de espaços de formatação entre elementos gramaticais e aceita representações equivalentes de entidades XML.

**Não são ignoradas diferenças de hierarquia, ordem ou conteúdo significativo.**

O analisador XML utilizado pelos testes desabilita declarações DTD e mecanismos de entidades externas, evitando a resolução de recursos externos durante a comparação.

Existem ainda testes específicos que confirmam a rejeição de árvores com elementos ausentes, ordem incorreta, valores diferentes ou texto indevido.

Esse critério permite validar a estrutura produzida pelo JackCompiler sem exigir que a indentação física seja idêntica à do arquivo oficial.

### 4.4. Critério de conformidade estrutural

O XML sintático segue as categorias definidas pelo Project 10 do nand2tetris.

Produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são reconhecidas durante a análise, mas não produzem elementos intermediários próprios no XML.

Por isso, os testes de aceitação verificam a hierarquia efetivamente utilizada nas referências oficiais, e não uma representação alternativa com uma tag para cada produção auxiliar.

A decisão e sua relação com a gramática estão detalhadas em [architecture.md](architecture.md).

## 5. Testes do JAR executável

A classe `JarExecutionIT` é executada pelo Maven Failsafe depois da etapa de empacotamento.

Diferentemente dos testes que invocam diretamente componentes Java, ela utiliza `ProcessBuilder` para iniciar processos independentes com:

```bash
java -jar target/jackcompiler.jar
```

Esses testes verificam o comportamento observável do executável, incluindo:

- ajuda e argumentos inválidos;
- execução dos comandos `tokenize` e `parse`;
- processamento de arquivos e diretórios;
- ordenação determinística das saídas;
- compatibilidade dos XMLs de tokens com as referências oficiais;
- geração de XMLs sintáticos consistentes com o parser;
- códigos de saída, `stdout` e `stderr`;
- diagnósticos léxicos e sintáticos com linha e coluna;
- processamento interrompido na primeira falha;
- rejeição de colisões em diretórios personalizados;
- substituição controlada de saídas nos diretórios padrão;
- preservação de saídas anteriores quando uma nova análise falha;
- rejeição de destinos simbólicos, aliases e diretórios protegidos;
- rejeição de arquivos com UTF-8 inválido;
- preservação dos arquivos-fonte;
- caminhos com espaços e caracteres Unicode;
- rejeição de caracteres incompatíveis com XML 1.0 sem publicar arquivos de saída.

Os testes sintáticos do JAR comparam seu XML com o resultado da API do próprio parser. A comparação independente com os XMLs oficiais é realizada por `OfficialParserXmlCompatibilityTest`.

Essa divisão permite testar tanto a conformidade da análise sintática com as referências quanto a consistência do executável empacotado com a implementação validada.

## 6. Reprodução manual

Além da suíte automatizada, os comandos podem ser verificados diretamente pelo JAR.

O exemplo abaixo utiliza um diretório temporário, sem modificar os materiais oficiais.

Na raiz do repositório:

```bash
mvn clean verify

TEMP_DIR="$(mktemp -d)"
printf 'class Main {}\n' > "$TEMP_DIR/Main.jack"

java -jar target/jackcompiler.jar \
  tokenize "$TEMP_DIR/Main.jack" \
  --output-dir "$TEMP_DIR/tokens"

java -jar target/jackcompiler.jar \
  parse "$TEMP_DIR/Main.jack" \
  --output-dir "$TEMP_DIR/parser"

cat "$TEMP_DIR/tokens/MainT.xml"
cat "$TEMP_DIR/parser/MainP.xml"

rm -r "$TEMP_DIR"
```

A tokenização deve produzir:

```xml
<tokens>
<keyword> class </keyword>
<identifier> Main </identifier>
<symbol> { </symbol>
<symbol> } </symbol>
</tokens>
```

A análise sintática deve produzir:

```xml
<class>
  <keyword> class </keyword>
  <identifier> Main </identifier>
  <symbol> { </symbol>
  <symbol> } </symbol>
</class>
```

O arquivo-fonte deve permanecer inalterado após as duas operações.

A opção `--help` pode ser verificada separadamente:

```bash
java -jar target/jackcompiler.jar --help
```

Os exemplos são verificações manuais complementares. A suíte automatizada abrange condições adicionais que não aparecem nessas execuções simples.

## 7. Resultados de validação

A versão correspondente ao commit [`ea89741`](https://github.com/0zzmandias/jackcompiler/commit/ea89741d1bde0ce5652766c6a5c148007ad1941b), que inclui a refatoração dos métodos de reconhecimento das produções gramaticais, foi validada com `mvn clean verify`.

Os resultados da execução local foram:

| Verificação | Resultado |
|---|---|
| Spotless | Aprovado |
| Compilação Java 21 | Aprovada |
| Surefire | 1.101 testes aprovados |
| Failsafe | 48 testes aprovados |
| Falhas | 0 |
| Erros | 0 |
| Testes ignorados | 0 |
| **Total** | **1.149 testes aprovados** |
| Construção Maven | **BUILD SUCCESS** |

A execução também confirmou os testes de compatibilidade XML do scanner e do parser, incluindo os programas oficiais utilizados como referências.

### Integração contínua

O GitHub Actions executa a construção e os testes automaticamente nos eventos configurados para a branch `main` e em pull requests direcionados a ela.

O workflow utiliza Java 21 (Temurin) e executa:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

A execução correspondente ao commit `ea89741` foi concluída com sucesso:

**[GitHub Actions — execução 40446352404](https://github.com/0zzmandias/jackcompiler/actions/runs/40446352404)**

Esses resultados documentam as verificações realizadas sobre o commit identificado. Uma alteração posterior de código ou de testes constitui uma nova versão do projeto e deve ser validada novamente.

## 8. Alcance das evidências

Os testes automatizados oferecem evidências de conformidade com a gramática Jack, os formatos XML oficiais e os contratos de execução implementados.

A comparação com os sete programas oficiais é especialmente relevante porque verifica a saída produzida contra referências independentes da implementação.

Entretanto, esse conjunto não representa todas as combinações possíveis da linguagem. Por isso, a suíte também contém testes unitários para regras específicas, casos extremos e entradas inválidas que não aparecem necessariamente nos programas de referência.

A aprovação dos testes demonstra que **nenhuma falha foi detectada pelos cenários executados**. Não equivale a uma prova formal de ausência de defeitos para qualquer entrada possível.

As decisões de arquitetura e de representação sintática estão descritas em [architecture.md](architecture.md). A rastreabilidade dos requisitos e de suas evidências de verificação está registrada em [acceptance.md](acceptance.md).

As instruções de construção e utilização do programa encontram-se no [README.md](../README.md).
