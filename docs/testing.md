# Testes e reprodução das evidências — Parte 1

## Ambiente

Requer JDK 21 e Maven. A construção usa Spotless 2.43.0, Maven Compiler 3.14.0, Surefire 3.5.2, JUnit Jupiter 5.11.4, Maven JAR Plugin 3.4.2 e Failsafe 3.5.2. O pipeline `.github/workflows/ci.yml` executa `mvn --batch-mode --no-transfer-progress clean verify` com Java 21 (Temurin).

## Comando principal

Na raiz do projeto:

```bash
mvn clean verify
```

Fases verificadas:

1. `validate`: formatação Java aprovada pelo Spotless.
2. `compile` e `testCompile`: código de produção e testes compilados para Java 21.
3. `test`: **Surefire**, testes unitários, de aplicação, CLI e aceitação oficial.
4. `package`: empacotamento de `target/jackcompiler.jar`, com manifesto `Main-Class: jackcompiler.cli.JackCompiler`.
5. `integration-test` e `verify`: **Failsafe**, executando `JarExecutionIT` sobre o JAR **já empacotado**, em processos Java separados.

Se for necessário ajustar formatação: `mvn spotless:apply`, seguido de novo `mvn clean verify`.

### Relatórios gerados

```bash
ls target/surefire-reports
ls target/failsafe-reports
grep -h "Tests run:" target/failsafe-reports/*.txt
unzip -p target/jackcompiler.jar META-INF/MANIFEST.MF
```

Os logs da fase `integration-test` precisam indicar a execução real de `jackcompiler.integration.JarExecutionIT`. Somente compilar um JAR ou chamar `JackCompiler.run` diretamente **não** substitui essa verificação.

## Cobertura por responsabilidade

| Área | Testes relevantes | Evidência |
|---|---|---|
| Tipos de tokens e invariantes | `TokenTest`, `SourcePositionTest`, `TokenizationResultTest` | Validação, imutabilidade e EOF fora da lista |
| Regras e cursor | `JackLexicalRulesTest`, `SourceCursorTest` | Keywords, símbolos, identificadores, LF/CRLF/CR, Unicode |
| Reconhecedores | `IdentifierRecognizerTest`, `IntegerRecognizerTest`, `StringRecognizerTest`, `SymbolRecognizerTest` | Lexemas, limites, comentários dentro de strings, caracteres especiais |
| Trivia | `WhitespaceConsumerTest`, `LineCommentConsumerTest`, `BlockCommentConsumerTest`, `TriviaSkipperTest` | Descarte e erro de comentário sem fechamento |
| Scanner completo | `JackTokenizerTest`, `LexicalExceptionTest` | Tokenização, posição, erros, isolamento |
| XML | `TokenXmlWriterTest`, `XmlEscaperTest` | Layout oficial, escapes, XML 1.0 |
| Entrada e saída | `JackFileResolverTest`, `JackSourceReaderTest`, `OutputPathResolverTest`, `TextFileWriterTest` | Ordenação, UTF-8 estrito, caminhos, colisões, links simbólicos e publicação |
| Caso de uso | `TokenizeCommandTest`, `SourceProcessingExceptionTest` | Fail-fast, causas e arquivos responsáveis |
| Interface | `CliArgumentsTest`, `CliArgumentParserTest`, `CliIntegrationTest` | Opções, comandos, help, códigos de saída, stdout/stderr |
| Compatibilidade oficial | `ReferenceProgramsTest`, `OfficialTokenXmlCompatibilityTest` | Comparação com XML de referência e preservação do fonte |
| JAR real | `JarExecutionIT` | Manifesto, processos separados, códigos de saída e destino seguro |
| Ambiente | `BuildEnvironmentTest` | JVM compatível |

## Fixtures oficiais

Os materiais foram obtidos de [nand2tetris/projects](https://github.com/nand2tetris/projects), revisão `6097b770073025891fb8b7a22aa3b72f61f81277`, com licença preservada em `src/test/resources/nand2tetris/LICENSE`.

```text
src/test/resources/nand2tetris/
├── LICENSE
├── Square/
│   ├── Main.jack           MainT.xml
│   ├── Square.jack         SquareT.xml
│   └── SquareGame.jack     SquareGameT.xml
├── ExpressionLessSquare/
│   ├── Main.jack           MainT.xml
│   ├── Square.jack         SquareT.xml
│   └── SquareGame.jack     SquareGameT.xml
└── ArrayTest/
    └── Main.jack           MainT.xml
```

Há **sete pares** `.jack`/`*T.xml`. Os testes leem esses recursos sem gravar neles, copiam os fontes para diretórios temporários quando precisam executar o sistema e direcionam a saída a pastas temporárias. A comparação verifica o texto XML completo, normalizando **exclusivamente** os terminadores de linha da referência para LF, sem normalizar espaços de conteúdo, estrutura, escapes ou ordem de tokens. Por exemplo, os dois espaços após `HOW MANY NUMBERS?` no elemento `<stringConstant>` de `ArrayTest` devem ser mantidos.

## Reprodução manual segura

**Não execute o analisador usando o diretório de fixtures como destino personalizado.** O teste abaixo utiliza fonte e saída temporários:

```bash
mvn clean verify
TEMP_DIR="$(mktemp -d)"
printf 'class Main {}\n' > "$TEMP_DIR/Main.jack"
java -jar target/jackcompiler.jar tokenize "$TEMP_DIR/Main.jack" --output-dir "$TEMP_DIR/output"
cat "$TEMP_DIR/output/MainT.xml"
rm -r "$TEMP_DIR"
```

O XML esperado é:

```xml
<tokens>
<keyword> class </keyword>
<identifier> Main </identifier>
<symbol> { </symbol>
<symbol> } </symbol>
</tokens>
```

Para checar a ajuda e um erro de argumentos:

```bash
java -jar target/jackcompiler.jar --help
java -jar target/jackcompiler.jar parse Main.jack
echo "$?"  # esperado: 2 para comando ainda indisponível
```

## Evidência do incremento 21

- Commit: [`091f66edcb2e6bfe6dbad118ccdbd4cfdc567114`](https://github.com/0zzmandias/jackcompiler/commit/091f66edcb2e6bfe6dbad118ccdbd4cfdc567114).
- Execução local informada: **620 testes Surefire** e **10 testes Failsafe**, sem falhas, `BUILD SUCCESS`.
- [GitHub Actions 38319030380](https://github.com/0zzmandias/jackcompiler/actions/runs/38319030380): `completed/success`, associado ao SHA acima.

Essas evidências dizem respeito ao snapshot identificado, **não a um futuro commit documental ou à auditoria final**. A versão submetida deve ser validada novamente, conferindo SHA e execução da CI.

## Verificação documental antes de registrar o commit 22

Depois de salvar os quatro arquivos, conferir os caminhos citados e revisar o diff:

```bash
test -f README.md
test -f docs/architecture.md
test -f docs/testing.md
test -f docs/acceptance.md
test -f src/test/java/jackcompiler/acceptance/OfficialTokenXmlCompatibilityTest.java
test -f src/test/java/jackcompiler/integration/JarExecutionIT.java
test -f src/test/resources/nand2tetris/LICENSE
git diff --check
mvn clean verify
```

Os comandos `test -f` comprovam apenas a existência dos caminhos indicados. O conteúdo dos documentos, links internos, consistência com classes e testes e atualização do estado das entregas **também exigem leitura e conferência manual**. Nenhum teste automatizado deve ser interpretado como substituto da auditoria final de requisitos, segurança e originalidade.
