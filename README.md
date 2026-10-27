
# JackCompiler

Projeto acadêmico da disciplina de Compiladores, desenvolvido em **Java 21** para a linguagem Jack, utilizada no curso [nand2tetris](https://www.nand2tetris.org/).

O JackCompiler reúne, em um único projeto, os dois componentes desenvolvidos na Unidade 1:

- **Analisador léxico (`tokenize`):** lê código-fonte Jack, reconhece e classifica os tokens, descarta espaços e comentários e gera XML no formato oficial do nand2tetris.
- **Analisador sintático (`parse`):** reutiliza o analisador léxico, reconhece a gramática Jack por *recursive descent parsing* e produz a árvore sintática em XML, compatível com as referências oficiais.

**Repositório:** [github.com/0zzmandias/jackcompiler](https://github.com/0zzmandias/jackcompiler)

## Integrante

**Helton Alves Sá**  
Matrícula: 20260009631

## Requisitos e construção

- **JDK 21**;
- **Apache Maven**.

Na raiz do repositório:

```bash
java -version
mvn -version
mvn clean verify
```

O comando `mvn clean verify` verifica a formatação Java, compila o projeto, executa os testes automatizados, gera o JAR e testa sua execução em processos Java independentes.

O executável é gerado em:

```text
target/jackcompiler.jar
```

Caso a verificação de formatação falhe, utilize:

```bash
mvn spotless:apply
mvn clean verify
```

A construção usa Maven Surefire e Failsafe, JUnit Jupiter e Spotless com Google Java Format. A integração contínua, implementada no GitHub Actions, executa `mvn clean verify` com Java 21 (Temurin).

## Como executar

A aplicação disponibiliza dois subcomandos, `tokenize` e `parse`, e uma opção de ajuda:

```bash
java -jar target/jackcompiler.jar --help

java -jar target/jackcompiler.jar tokenize Main.jack
java -jar target/jackcompiler.jar parse Main.jack

java -jar target/jackcompiler.jar tokenize projetos/Square
java -jar target/jackcompiler.jar parse projetos/Square

java -jar target/jackcompiler.jar tokenize Main.jack --output-dir resultados/tokens
java -jar target/jackcompiler.jar parse Main.jack --output-dir resultados/parser
```

A sintaxe dos comandos é:

```text
jackcompiler tokenize <file.jack|directory> [--output-dir <directory>]
jackcompiler parse <file.jack|directory> [--output-dir <directory>]
jackcompiler --help
```

Cada comando recebe um arquivo `.jack` ou um diretório. Quando a entrada é um diretório, apenas os arquivos regulares com extensão `.jack` diretamente contidos nele são selecionados, em ordem alfabética. A busca não é recursiva e a extensão diferencia maiúsculas de minúsculas.

### Arquivos gerados

| Comando | Exemplo de saída | Diretório padrão |
|---|---|---|
| `tokenize` | `MainT.xml` | `target/generated-tokens/` |
| `parse` | `MainP.xml` | `target/generated-parser/` |

Os diretórios padrão são relativos ao diretório de trabalho no qual o comando é executado. A opção `--output-dir` permite escolher outro destino.

**Política de escrita:** nos diretórios padrão, um arquivo XML regular preexistente com o mesmo nome pode ser substituído. Em um diretório personalizado, a colisão provoca erro e o arquivo existente não é sobrescrito. O conteúdo é preparado antes da publicação, que utiliza um arquivo temporário e tenta movimentação atômica quando suportada pelo sistema de arquivos.

Os arquivos-fonte não são modificados. A implementação também verifica aliases da fonte, links simbólicos nos caminhos de saída e destinos protegidos, incluindo diretórios de código e de recursos de teste.

Em entradas com múltiplos arquivos, o processamento é **fail-fast**: interrompe na primeira falha, preserva as saídas já concluídas e não processa os arquivos restantes. Não há rollback do lote inteiro.

### Códigos de saída e erros

| Código | Significado |
|---|---|
| `0` | Execução bem-sucedida ou exibição da ajuda |
| `1` | Erro de leitura, análise, serialização ou escrita |
| `2` | Argumentos inválidos |

A ajuda e os resultados são escritos em `stdout`; as mensagens de erro, em `stderr`. Erros léxicos e sintáticos identificam o arquivo e a posição original (**linha e coluna**). A leitura de fontes utiliza UTF-8 estrito e rejeita sequências inválidas.

## Exemplo de tokenização

Considere o arquivo `Main.jack`:

```jack
class Main {}
```

Execute:

```bash
java -jar target/jackcompiler.jar tokenize Main.jack
```

O arquivo `MainT.xml` terá o seguinte conteúdo:

```xml
<tokens>
<keyword> class </keyword>
<identifier> Main </identifier>
<symbol> { </symbol>
<symbol> } </symbol>
</tokens>
```

O analisador reconhece as cinco categorias oficiais: `keyword`, `symbol`, `integerConstant`, `stringConstant` e `identifier`. São implementadas as 21 palavras reservadas e os 19 símbolos da linguagem, constantes inteiras de `0` a `32767`, identificadores e strings, além do descarte de espaços e comentários `//`, `/* ... */` e `/** ... */`.

**Símbolos reconhecidos:** `{ } ( ) [ ] . , ; + - * / & | < > = ~`.

Os valores dos tokens são preservados. A serialização utiliza UTF-8, terminadores de linha LF, os espaços convencionais dos XMLs oficiais e o escape de caracteres reservados de XML.

### Esclarecimento sobre os colchetes `[` e `]`

O item **“Reconhecimento de Tokens”** do enunciado da Parte 1 apresenta uma lista de símbolos que omite `[` e `]`. Entretanto, ambos pertencem aos **19 símbolos oficiais de Jack** e são necessários, por exemplo, para acessar arrays (`a[i]`).

Por isso, os dois símbolos foram implementados deliberadamente. Essa escolha segue a especificação da linguagem e permite analisar os programas oficiais que utilizam arrays, como `ArrayTest`. **Não constitui uma extensão da linguagem.**

## Exemplo de análise sintática

Para o mesmo arquivo `Main.jack`, execute:

```bash
java -jar target/jackcompiler.jar parse Main.jack
```

O arquivo `MainP.xml` conterá:

```xml
<class>
  <keyword> class </keyword>
  <identifier> Main </identifier>
  <symbol> { </symbol>
  <symbol> } </symbol>
</class>
```

O parser reconhece declarações de classes, variáveis e sub-rotinas; parâmetros; comandos `let`, `if`, `while`, `do` e `return`; expressões, termos, acesso a arrays e chamadas de sub-rotinas. Utiliza análise descendente recursiva sobre os tokens produzidos pelo scanner, preservando seus lexemas e posições.

### Precedência e associatividade dos operadores

O enunciado da Parte 2 menciona o tratamento de precedência e associatividade. A linguagem Jack, porém, **não estabelece uma prioridade obrigatória entre operadores binários**: a gramática oficial define a expressão como `term (op term)*`. O agrupamento explícito por parênteses deve ser respeitado.

Por exemplo, a expressão `2 + 3 * 4`, sem parênteses, não possui uma interpretação aritmética única imposta pela especificação de Jack. O parser deste projeto reconhece os termos e operadores em sua sequência original, sem introduzir uma precedência convencional de outras linguagens. Expressões entre parênteses geram estruturas sintáticas aninhadas.

Essa decisão segue o objetivo da análise sintática: produzir uma representação fiel à gramática e aos XMLs oficiais, **sem avaliar as operações aritméticas**. A especificação de Jack permite que implementações definam políticas próprias de precedência, mas não exige uma política específica para esta representação XML.

Referência: Nisan e Schocken, [*The Elements of Computing Systems*](https://www.nand2tetris.org/book), capítulo 9, seção **“Operator Priority and Order of Evaluation”**.

### Tags XML e produções auxiliares

O enunciado também menciona que os não-terminais da gramática devem aparecer como elementos XML. Para satisfazer o critério de **equivalência estrutural com os arquivos oficiais**, o JackCompiler segue a hierarquia XML especificada no [Project 10 do nand2tetris](https://www.nand2tetris.org/project10).

Assim, produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são reconhecidas pelo parser, mas **não introduzem tags XML intermediárias próprias**. Por exemplo, um operador aparece como `<symbol>` dentro de `<expression>`, e um comando `let` aparece como `<letStatement>` dentro de `<statements>`, sem um wrapper `<statement>`.

A distinção entre os métodos que reconhecem as produções gramaticais e os elementos efetivamente serializados é descrita em [docs/architecture.md](docs/architecture.md).

## Arquitetura do projeto

O parser amplia a arquitetura do analisador léxico, reutilizando seus contratos e os serviços compartilhados de entrada e saída.

```text
src/main/java/jackcompiler/
├── application/  # Casos de uso tokenize e parse
├── cli/          # Argumentos, comandos, ajuda e diagnósticos
├── io/           # Descoberta, leitura e publicação dos arquivos
├── lexer/        # Análise léxica
├── parser/       # Análise sintática descendente recursiva
├── syntax/       # Árvore sintática concreta e imutável
├── token/        # Tipos, tokens e posições
└── xml/          # Serialização de tokens e da árvore sintática
```

O fluxo do comando `parse` é:

```text
.jack → leitura → JackTokenizer → TokenizationResult
      → JackParser → GrammarNode → ParseTreeXmlWriter → *P.xml
```

`TokenizeCommand` e `ParseCommand` são casos de uso distintos. Ambos reutilizam os componentes necessários, sem duplicar o scanner e sem depender da interface de linha de comando para executar a análise.

Veja os contratos, as dependências e as decisões de organização em [docs/architecture.md](docs/architecture.md).

## Testes e validação oficial

O projeto utiliza testes unitários, testes de integração, testes do JAR executável e testes de aceitação baseados nos programas oficiais do nand2tetris.

A validação abrange os programas `Main`, `Square` e `SquareGame` dos conjuntos `Square` e `ExpressionLessSquare`, além de `ArrayTest/Main`. Os XMLs de tokens são comparados com as referências `*T.xml`; os XMLs sintáticos são comparados estruturalmente com as referências oficiais `*.xml`, preservando hierarquia, ordem e conteúdo significativo dos terminais.

Na validação do commit [`ea89741`](https://github.com/0zzmandias/jackcompiler/commit/ea89741d1bde0ce5652766c6a5c148007ad1941b), foram aprovados **1.149 testes**, sendo **1.101 pelo Surefire e 48 pelo Failsafe**, sem falhas. O [GitHub Actions](https://github.com/0zzmandias/jackcompiler/actions/runs/40446352404) também concluiu a construção com sucesso.

As referências oficiais foram obtidas de [nand2tetris/projects](https://github.com/nand2tetris/projects), na revisão `6097b770073025891fb8b7a22aa3b72f61f81277`. A licença correspondente está preservada em `src/test/resources/nand2tetris/LICENSE`.

A estratégia de verificação, os critérios de comparação, os casos cobertos e as evidências estão detalhados em [docs/testing.md](docs/testing.md). A correspondência entre requisitos e testes encontra-se em [docs/acceptance.md](docs/acceptance.md).

## Desafios de implementação

O principal desafio da evolução do JackCompiler foi integrar a análise sintática **sem reimplementar nem comprometer o scanner da Parte 1**. Para isso, o parser recebe diretamente o `TokenizationResult` e utiliza um cursor próprio, enquanto a leitura de arquivos, a publicação das saídas e a serialização permanecem em componentes com responsabilidades separadas.

Outro desafio foi conciliar o reconhecimento de todas as produções da gramática com o **formato XML específico do nand2tetris**. A implementação mantém métodos responsáveis pelas regras gramaticais, mas só serializa os elementos previstos nas referências oficiais, inclusive quando uma produção auxiliar não corresponde a uma tag própria. Os testes verificam também erros posicionados, expressões aninhadas e a preservação de espaços significativos em strings.

## Marcos do repositório

O desenvolvimento das duas partes utiliza o mesmo histórico Git. A versão do analisador léxico está identificada pela tag [`v1.0.0-lexer`](https://github.com/0zzmandias/jackcompiler/tree/v1.0.0-lexer), enquanto a evolução sintática permanece na mesma base de código.

O código, os testes e os recursos oficiais estão disponíveis neste repositório sob seus respectivos diretórios `src/main/` e `src/test/`.
