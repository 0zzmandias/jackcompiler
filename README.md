
# JackCompiler

Projeto acadêmico da disciplina de Compiladores, desenvolvido em Java 21 para a linguagem Jack, utilizada no curso nand2tetris.

## Integrante

**Helton Alves Sá**  
Matrícula: 20260009631

## Objetivo

O JackCompiler é desenvolvido em duas etapas:

- **Parte 1 — Analisador Léxico:** reconhecimento dos tokens da linguagem Jack, descarte de espaços e comentários e geração de arquivos XML no formato nand2tetris.
- **Parte 2 — Analisador Sintático:** reconhecimento da gramática Jack e geração de árvores sintáticas em XML, reutilizando os tokens produzidos pela primeira etapa.

A Parte 1 está implementada e validada. A Parte 2 ainda não foi implementada.

O projeto realiza análise léxica, não compilação para VM ou código de máquina.

## Tecnologias

- Java 21
- Apache Maven
- JUnit Jupiter 5
- Spotless com Google Java Format
- Git e GitHub Actions

## Pré-requisitos

- JDK 21
- Apache Maven
- Terminal com acesso ao comando `java`

Verifique as instalações com:

```bash
java -version
mvn -version
```

## Construção e testes

Na raiz do repositório, execute:

```bash
mvn clean verify
```

Esse comando verifica a formatação, compila o código, executa os testes automatizados e produz:

```text
target/jackcompiler.jar
```

Se precisar aplicar a formatação automática antes da compilação:

```bash
mvn spotless:apply
mvn clean verify
```

O projeto também executa `mvn clean verify` automaticamente no GitHub Actions para alterações enviadas à branch `main` e para pull requests direcionados a ela.

## Execução

### Analisar um arquivo Jack

```bash
java -jar target/jackcompiler.jar tokenize /caminho/para/Main.jack
```

O programa gera um arquivo chamado `MainT.xml` no mesmo diretório do arquivo de entrada.

Por exemplo:

```text
projeto/
├── Main.jack
└── MainT.xml
```

O arquivo `.jack` original não é modificado.

### Analisar um diretório

```bash
java -jar target/jackcompiler.jar tokenize /caminho/para/projeto
```

O programa identifica os arquivos com extensão `.jack` diretamente no diretório informado, em ordem alfabética pelo nome, e gera um arquivo `T.xml` para cada um.

Por exemplo:

```text
projeto/
├── Main.jack
├── MainT.xml
├── Square.jack
└── SquareT.xml
```

A busca não é recursiva: arquivos existentes em subdiretórios não são processados automaticamente.

Arquivos XML previamente gerados com o mesmo nome são substituídos.

### Comando reservado para a Parte 2

A interface também reconhece a operação:

```bash
java -jar target/jackcompiler.jar parse /caminho/para/Main.jack
```

Entretanto, essa operação ainda não está implementada. A aplicação informa essa condição e retorna um código de erro, sem gerar uma árvore sintática.

## Formato de saída

Para o seguinte código Jack:

```java
class Main {}
```

O analisador gera:

```xml
<tokens>
<keyword> class </keyword>
<identifier> Main </identifier>
<symbol> { </symbol>
<symbol> } </symbol>
</tokens>
```

As cinco categorias de tokens suportadas são:

- `keyword`: palavras reservadas da linguagem Jack.
- `symbol`: símbolos definidos pela linguagem.
- `integerConstant`: constantes inteiras de 0 a 32767.
- `stringConstant`: texto entre aspas duplas, sem incluir as aspas no valor.
- `identifier`: identificadores de variáveis, classes e sub-rotinas.

O XML utiliza os nomes de elementos definidos pelo nand2tetris e representa caracteres especiais por entidades XML, como `&lt;`, `&gt;` e `&amp;`.

Espaços em branco e comentários não produzem tokens.

## Tratamento de erros

O analisador identifica erros léxicos, como:

- Caracteres não reconhecidos pela linguagem Jack.
- Constantes inteiras acima de 32767.
- Strings não terminadas.
- Quebras de linha dentro de strings.
- Comentários de bloco não terminados.
- Caracteres não permitidos no conteúdo de um documento XML.

Os erros léxicos são apresentados com linha, coluna e descrição.

O programa utiliza os seguintes códigos de saída:

| Código | Significado |
|---|---|
| `0` | Execução concluída com sucesso |
| `1` | Erro de leitura, gravação, tokenização ou serialização |
| `2` | Argumentos inválidos ou operação ainda não implementada |

Em processamento de diretórios, a execução é interrompida no primeiro erro. Arquivos XML gerados antes desse erro podem permanecer no diretório.

A Parte 1 não verifica se a sequência de tokens constitui um programa Jack sintaticamente válido. Essa responsabilidade pertence à Parte 2.

## Organização do código

O código-fonte principal está organizado nos seguintes pacotes:

```text
src/main/java/jackcompiler/
├── Main.java
├── cli/
│   ├── CommandLineArguments.java
│   └── TokenizeCommand.java
├── io/
│   ├── JackSourceFiles.java
│   └── TokenXmlFiles.java
├── lexer/
├── token/
└── xml/
    └── TokenXmlWriter.java
```

Responsabilidades:

- `Main`: ponto de entrada da aplicação e apresentação de resultados e erros.
- `cli`: interpretação dos argumentos e coordenação das operações.
- `io`: localização, leitura e gravação de arquivos.
- `lexer`: reconhecimento de tokens, gerenciamento do cursor e descarte de comentários e espaços.
- `token`: representação dos tokens e de suas posições.
- `xml`: serialização dos tokens no formato XML.

## Testes automatizados

A Parte 1 possui testes unitários e de integração com JUnit Jupiter.

Eles verificam, entre outros aspectos:

- Reconhecimento das categorias de tokens.
- Identificação de palavras reservadas.
- Limites de constantes inteiras.
- Tratamento de strings e comentários.
- Unicode e posições no código-fonte.
- Erros léxicos e serialização XML.
- Entrada de arquivos e diretórios.
- Execução pela interface de linha de comando.
- Comparação com programas oficiais do nand2tetris.

O marco de validação da Parte 1 possui **510 testes automatizados aprovados**.

### Programas oficiais de referência

Os materiais de referência estão em:

```text
src/test/resources/jackcompiler/reference/
├── LICENSE
├── ArrayTest/
│   ├── Main.jack
│   └── MainT.xml
├── ExpressionLessSquare/
│   ├── Main.jack
│   ├── MainT.xml
│   ├── Square.jack
│   ├── SquareT.xml
│   ├── SquareGame.jack
│   └── SquareGameT.xml
└── Square/
    ├── Main.jack
    ├── MainT.xml
    ├── Square.jack
    ├── SquareT.xml
    ├── SquareGame.jack
    └── SquareGameT.xml
```

São sete programas `.jack`, sete resultados XML de referência e um arquivo de licença.

O teste `ReferenceProgramsTest` compara o resultado do JackCompiler com os XMLs oficiais e também verifica a execução pela interface de linha de comando.

A comparação normaliza exclusivamente as quebras de linha. O conteúdo dos tokens e a estrutura XML devem corresponder às referências.

Os arquivos de referência foram obtidos de:

https://github.com/nand2tetris/projects

Revisão utilizada:

`6097b770073025891fb8b7a22aa3b72f61f81277`

Os exemplos são mantidos com sua licença original, disponível em `src/test/resources/jackcompiler/reference/LICENSE`.

## Estado do desenvolvimento

**Parte 1 — Analisador Léxico:** implementação concluída, com interface executável, geração de XML e validação automatizada por programas oficiais.

**Parte 2 — Analisador Sintático:** implementação pendente.

O desenvolvimento da Parte 2 reutilizará os componentes léxicos existentes, preservando a separação entre reconhecimento de tokens e análise gramatical.
