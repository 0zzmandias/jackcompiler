# JackCompiler

Projeto acadêmico da disciplina de Compiladores, desenvolvido em Java 21 para a linguagem Jack, utilizada no curso [nand2tetris](https://www.nand2tetris.org/).

**Repositório:** [github.com/0zzmandias/jackcompiler](https://github.com/0zzmandias/jackcompiler).

## Integrante

**Helton Alves Sá**  
Matrícula: 20260009631

## Objetivo e situação das entregas

O projeto foi planejado em duas partes:

- **Parte 1 — Analisador léxico:** reconhece tokens Jack, descarta espaços e comentários e produz XML de tokens compatível com o nand2tetris. **Implementação e testes automatizados concluídos; auditoria final e entrega formal ainda pendentes.**
- **Parte 2 — Analisador sintático:** reconhecerá a gramática Jack e produzirá árvores sintáticas XML reutilizando os componentes da Parte 1. **Não iniciada.**

O projeto não gera código VM ou código de máquina. Na Parte 1, uma sequência de tokens pode ser léxica e perfeitamente reconhecível mesmo sem formar um programa sintaticamente válido.

## Ambiente e construção

- JDK 21 e Apache Maven;
- JUnit Jupiter 5 para testes unitários, de aceitação e integração;
- Maven Surefire 3.5.2 e Failsafe 3.5.2;
- Spotless com Google Java Format;
- GitHub Actions na branch `main` e em pull requests para `main`.

```bash
java -version
mvn -version
mvn clean verify
```

`mvn clean verify` confere a formatação, compila o projeto, executa os testes pelo Surefire, empacota o JAR e executa os testes do JAR pelo Failsafe. O artefato resultante é `target/jackcompiler.jar`.

Se somente a formatação estiver incorreta:

```bash
mvn spotless:apply
mvn clean verify
```

## Uso da Parte 1

```bash
java -jar target/jackcompiler.jar --help
java -jar target/jackcompiler.jar tokenize Main.jack
java -jar target/jackcompiler.jar tokenize projetos/Square
java -jar target/jackcompiler.jar tokenize projetos/Square --output-dir resultados
```

`tokenize` recebe **um arquivo `.jack` ou um diretório**. Para diretórios, seleciona apenas arquivos regulares `.jack` diretamente contidos nele, em ordem alfabética de nome; a busca **não é recursiva** e a extensão diferencia maiúsculas de minúsculas. Entradas ausentes, caminhos inválidos e diretórios sem fontes elegíveis produzem erro.

### Destinos e prevenção de sobrescrita

- **Sem `--output-dir`:** a saída é gravada em `target/generated-tokens/`, relativo ao diretório de trabalho do processo. O diretório é criado se necessário. Um arquivo regular já existente com o nome de saída correspondente **pode ser substituído** (`REPLACE_EXISTING`).
- **Com `--output-dir`:** a saída é gravada no diretório informado, criado se necessário. A existência de um destino com o mesmo nome causa erro, **sem sobrescrevê-lo** (`CREATE_NEW`).
- Em ambos os casos, `Main.jack` produz `MainT.xml`. Os fontes `.jack` não são modificados. Há verificações contra aliases do fonte, links simbólicos nos caminhos de saída e gravação em diretórios de código ou de fixtures do projeto.
- O conteúdo completo do XML é produzido antes da publicação do arquivo. A gravação usa arquivo temporário e tenta publicação atômica onde houver suporte. Não há garantia de transação para um lote inteiro.

Exemplo de execução a partir da raiz do projeto, sem diretório personalizado:

```text
target/
├── jackcompiler.jar
└── generated-tokens/
    └── MainT.xml
```

**Atenção:** os arquivos `*T.xml` dos exemplos oficiais estão em `src/test/resources/nand2tetris/` e não devem ser usados como destinos de saída. Trabalhe sobre cópias ou use um diretório personalizado separado.

### Ajuda, comandos e códigos de saída

| Código | Significado |
|---|---|
| `0` | Sucesso ou apresentação de `--help` |
| `1` | Falha de processamento, leitura, tokenização, serialização ou escrita |
| `2` | Argumentos ou subcomando inválidos |

A execução normal e a ajuda usam `stdout`; os erros usam `stderr`. Falhas léxicas informam arquivo, linha e coluna; erros de I/O e serialização informam o arquivo, sem posição inventada. O processamento de diretórios é **fail-fast**: os resultados já concluídos permanecem, o arquivo que falha não publica um resultado novo parcial e os arquivos seguintes não são processados. Não há rollback do lote.

**O comando `parse` não está implementado nem anunciado nesta versão**: tentar utilizá-lo produz erro de argumentos (`2`). Ele pertence exclusivamente à Parte 2.

## Linguagem e formato XML

O analisador reconhece **21 palavras reservadas** e **19 símbolos** oficiais da linguagem Jack, além de identificadores, constantes inteiras e strings. As cinco categorias de tokens são `keyword`, `symbol`, `integerConstant`, `stringConstant` e `identifier`.

**Símbolos reconhecidos:** `{ } ( ) [ ] . , ; + - * / & | < > = ~`.

**Esclarecimento sobre `[` e `]`:** os dois colchetes integram expressamente o conjunto oficial de **19 símbolos Jack**, sendo usados, por exemplo, no acesso a arrays (`a[i]`). Sua inclusão é necessária para compatibilidade com a especificação e com os exemplos oficiais, incluindo `ArrayTest`. Não são símbolos adicionais criados pelo projeto.

Regras relevantes: inteiros de `0` a `32767`, preservando o lexema; identificadores com início em letra ASCII ou `_`, seguidos por letras ASCII, dígitos ou `_`; strings delimitadas por aspas duplas, sem quebra de linha; espaços e comentários `//`, `/* ... */` e `/** ... */` descartados. O cursor trata LF, CRLF e CR como uma quebra lógica de linha e acompanha posições a partir de 1, por pontos de código Unicode.

Dado o arquivo `Main.jack`:

```java
class Main {}
```

A saída `MainT.xml` é:

```xml
<tokens>
<keyword> class </keyword>
<identifier> Main </identifier>
<symbol> { </symbol>
<symbol> } </symbol>
</tokens>
```

O XML usa UTF-8, linhas terminadas em LF, os nomes de elementos do nand2tetris e os espaços convencionais dentro de cada elemento. Caracteres como `<`, `>` e `&` são escapados (`&lt;`, `&gt;`, `&amp;`). Caracteres incompatíveis com XML 1.0 provocam erro explícito durante a serialização, sem substituição silenciosa.

## Organização do projeto

```text
src/main/java/jackcompiler/
├── cli/          # JackCompiler, CliArguments, CliArgumentParser
├── application/  # TokenizeCommand, SourceProcessingException
├── io/           # resolução, leitura, destino e escrita segura
├── lexer/        # scanner, cursor, regras, reconhecedores, trivia
├── token/        # tipos e objetos de valor imutáveis
└── xml/          # formato dos terminais, escape e serialização

src/test/java/jackcompiler/
├── acceptance/   # comparação com XMLs oficiais
├── application/  # testes do caso de uso
├── cli/          # argumentos, CLI e programas oficiais
├── contract/     # API pública e limites de dependências
├── integration/  # execução do JAR empacotado
├── io/
├── lexer/
├── token/
└── xml/

src/test/resources/nand2tetris/
├── LICENSE
├── Square/
├── ExpressionLessSquare/
└── ArrayTest/
```

A separação entre pacotes e seus contratos está descrita em [docs/architecture.md](docs/architecture.md).

## Testes e referências oficiais

Após a correção de auditoria do contrato público, a execução local de `mvn clean verify` passou em **625 testes Surefire e 10 testes Failsafe**, sem falhas. A CI confirmou o mesmo conjunto de verificações no commit [`5f68a6d`](https://github.com/0zzmandias/jackcompiler/commit/5f68a6dd6e1393bb60fa578e85495e292484ed68), pela [execução 38322081939](https://github.com/0zzmandias/jackcompiler/actions/runs/38322081939). Essas evidências não substituem a validação do futuro snapshot final nem a auditoria de originalidade.

Os recursos contêm sete pares `.jack`/`*T.xml` (programas `Main`, `Square` e `SquareGame` em `Square` e `ExpressionLessSquare`, além de `ArrayTest/Main`), mais `LICENSE`. Origem: [nand2tetris/projects](https://github.com/nand2tetris/projects), revisão `6097b770073025891fb8b7a22aa3b72f61f81277`. Os arquivos de referência são preservados sem modificações de conteúdo. As comparações de XML admitem **somente normalização de terminadores de linha** da referência, mantendo a ordem dos tokens, os lexemas, os espaços e os escapes.

Para reproduzir testes e compreender as verificações, consulte [docs/testing.md](docs/testing.md). Para rastrear requisitos e evidências, consulte [docs/acceptance.md](docs/acceptance.md).

## Marcos das entregas no Git

| Entrega | Delimitação no histórico | Versão |
|---|---|---|
| Parte 1 — Analisador léxico | Será delimitada pelo commit apontado pela tag `v1.0.0-lexer`, **após a auditoria e antes da submissão** | Tag ainda não criada |
| Parte 2 — Analisador sintático | Começará no primeiro commit **posterior** à tag, **após confirmação da submissão da Parte 1** | Ainda não iniciada |

A numeração planejada é de 01 a 22 para a Parte 1 e 23 a 38 para a Parte 2; os marcos definitivos serão os hashes e tags reais. **Não se registra aqui o hash do próprio commit final**, pois alterá-lo dentro do arquivo mudaria o commit. O hash definitivo será informado nas notas da versão e/ou no comentário da submissão.

## Pendências antes da entrega formal da Parte 1

Apesar de os testes automatizados e a CI da correção de auditoria terem sido aprovados, **a versão final ainda requer** auditoria integral dos requisitos, segurança de saída, documentação, compatibilidade com fixtures, arquitetura, empacotamento, histórico e originalidade/autoria. Somente após aprovação da auditoria será criada a tag `v1.0.0-lexer`, preparado o ZIP do snapshot correspondente e realizada a submissão no SIGAA com o link deste repositório. Nenhuma dessas etapas é considerada concluída por este README.
