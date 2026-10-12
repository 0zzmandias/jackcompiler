# Aceitação e rastreabilidade — Parte 1

## Escopo e interpretação

O escopo entregue até o incremento 21 é o **analisador léxico Jack**, com CLI `tokenize`, saída XML oficial e testes automatizados. O parser, a árvore sintática XML e o comando `parse` pertencem à Parte 2 e não estão implementados.

**Status das evidências:** o commit `091f66e` passou em 620 testes Surefire e 10 Failsafe localmente e teve [CI aprovada](https://github.com/0zzmandias/jackcompiler/actions/runs/38319030380). Isso prova a execução das verificações relacionadas naquele snapshot, não a conclusão da auditoria integral, da tag ou da submissão.

## Matriz de requisitos e evidências

| Requisito aprovado | Implementação | Evidência automatizada / condição |
|---|---|---|
| Cinco categorias de token | `token/TokenType.java`, `token/Token.java` | `TokenTest`, `JackTokenizerTest` |
| 21 keywords Jack | `lexer/JackLexicalRules.java`, `IdentifierRecognizer.java` | `JackLexicalRulesTest`, `IdentifierRecognizerTest` |
| 19 símbolos Jack, incluindo `[` e `]` | `JackLexicalRules.java`, `SymbolRecognizer.java` | `JackLexicalRulesTest`, `SymbolRecognizerTest`, `ArrayTest/Main` |
| Identificadores e lexemas | `JackLexicalRules.java`, `IdentifierRecognizer.java` | `IdentifierRecognizerTest`, `JackTokenizerTest` |
| Inteiros `0–32767`, limite e lexema preservado | `IntegerRecognizer.java` | `IntegerRecognizerTest`, `JackTokenizerTest` |
| Strings, conteúdo literal, EOF e quebra inválida | `StringRecognizer.java` | `StringRecognizerTest`, `JackTokenizerTest` |
| Comentários `//`, `/* ... */`, `/** ... */` e whitespace | Consumidores e `TriviaSkipper.java` | Testes individuais, `TriviaSkipperTest` |
| Unicode e CR/LF/CRLF, posições baseadas em 1 | `SourceCursor.java`, `SourcePosition.java` | `SourceCursorTest`, `SourcePositionTest` |
| Scanner sem CLI/I/O/XML, resultado imutável e sem token EOF artificial | `JackTokenizer.java`, `TokenizationResult.java` | `JackTokenizerTest`, `TokenizationResultTest`; dependências ainda sujeitas à auditoria |
| XML de terminais, espaços e escapes | `TokenXmlWriter.java`, `XmlEscaper.java` | `TokenXmlWriterTest`, `XmlEscaperTest` |
| Rejeição de caracteres XML 1.0 inválidos | `XmlEscaper.java` | `XmlEscaperTest`, `TokenizeCommandTest` |
| XML oficial de `Main`, `Square`, `SquareGame` e `ArrayTest` | Fluxo `JackTokenizer` → `TokenXmlWriter` | `ReferenceProgramsTest`, `OfficialTokenXmlCompatibilityTest` |
| Descoberta de `.jack` direta, ordenada e não recursiva | `JackFileResolver.java` | `JackFileResolverTest`, `CliIntegrationTest`, `JarExecutionIT` |
| Leitura UTF-8 estrita e preservação do fonte | `JackSourceReader.java` | `JackSourceReaderTest`, `TokenizeCommandTest`, `JarExecutionIT` |
| `Main.jack` → `MainT.xml`, destino padrão | `OutputPathResolver.java` | `OutputPathResolverTest`, `TokenizeCommandTest`, `JarExecutionIT` |
| `--output-dir` e rejeição de colisões | `CliArgumentParser.java`, `TextFileWriter.java` | `CliArgumentParserTest`, `TextFileWriterTest`, `CliIntegrationTest`, `JarExecutionIT` |
| Proteção contra symlinks, alias e locais protegidos | `OutputPathResolver.java`, `TextFileWriter.java` | `OutputPathResolverTest`, `TextFileWriterTest`, `TokenizeCommandTest` |
| Escrita por temporário, publicação controlada e limpeza | `TextFileWriter.java` | `TextFileWriterTest`, `TokenizeCommandTest`; revisar riscos residuais na auditoria |
| Lote fail-fast sem rollback, arquivo atual não parcial | `application/TokenizeCommand.java` | `TokenizeCommandTest`, `CliIntegrationTest`, `JarExecutionIT` |
| Erro contextualizado com causa original | `SourceProcessingException.java`, `JackCompiler.java` | `SourceProcessingExceptionTest`, `CliIntegrationTest` |
| `--help`, opções inválidas, `parse` rejeitado, códigos `0/1/2` | `CliArguments.java`, `CliArgumentParser.java`, `JackCompiler.java` | `CliArgumentParserTest`, `CliIntegrationTest`, `JarExecutionIT` |
| JAR realmente executável | `pom.xml`, manifesto, `JackCompiler.java` | `JarExecutionIT` (Failsafe após `package`), inspeção manual do manifesto |
| Java 21, build e CI | `pom.xml`, `.github/workflows/ci.yml` | `BuildEnvironmentTest`, Surefire, Failsafe, execução GitHub Actions |
| Documentação fiel ao código | `README.md`, `docs/*.md` | Conferência de caminhos, links, diffs e conteúdo no incremento 22; **não auditada formalmente ainda** |

## Material oficial e critério de comparação

Diretório: `src/test/resources/nand2tetris/`; sete pares de fonte e XML nas suítes `Square` (três), `ExpressionLessSquare` (três) e `ArrayTest` (um). Licença: `src/test/resources/nand2tetris/LICENSE`. Fonte upstream: [nand2tetris/projects](https://github.com/nand2tetris/projects), revisão `6097b770073025891fb8b7a22aa3b72f61f81277`.

A comparação admite somente normalização de CRLF/CR para LF **no texto da referência**, e exige correspondência de todos os tokens, ordem, elementos, escapes e espaços significativos. Os recursos originais não são modificados pelos testes. `ArrayTest/Main` verifica explicitamente o uso de colchetes e a preservação dos espaços de strings.

## Verificações já realizadas e limites da evidência

| Marco | Evidência | Situação |
|---|---|---|
| Incremento 20 | 605 testes locais, JAR manual; [CI 38317687904](https://github.com/0zzmandias/jackcompiler/actions/runs/38317687904) | Aprovado no commit `49cd96e` |
| Incremento 21 | 620 Surefire + 10 Failsafe locais; [CI 38319030380](https://github.com/0zzmandias/jackcompiler/actions/runs/38319030380) | Aprovado no commit `091f66e` |
| Incremento 22 | README e três documentos, revisão documental e nova execução do `mvn clean verify` | **Pendente até validação e CI do novo commit** |
| Auditoria final | Requisitos, arquitetura, segurança, fontes, testes, empacotamento, autoria e submissão | **Pendente** |
| Tag `v1.0.0-lexer` e ZIP | Exigem auditoria integral aprovada | **Não realizados** |
| Parte 2 | Somente após submissão da Parte 1 confirmada | **Não iniciada** |

## Checklist obrigatório antes da tag

- [ ] Conferir todos os contratos e requisitos da Parte 1 com rastreabilidade.
- [ ] Confirmar 21 keywords, 19 símbolos, cinco categorias e diagnóstico de erro.
- [ ] Verificar equivalência dos XMLs oficiais de `Main`, `Square`, `SquareGame` e `ArrayTest` no snapshot final.
- [ ] Auditar proibições de dependência, API pública, ausência do parser e reutilização futura.
- [ ] Inspecionar proteção de fontes/fixtures, colisões, links simbólicos, aliases e ausência de saída parcial.
- [ ] Reexecutar `mvn clean verify` em checkout limpo do commit final, confirmar relatórios Surefire/Failsafe e CI para o **mesmo SHA**.
- [ ] Conferir README, três arquivos em `docs/`, links, comandos, versão Java, licença e estado real do projeto.
- [ ] Revisar histórico Git e procedência dos arquivos; realizar **auditoria de originalidade/autoria por último**.
- [ ] Verificar procedimento e conteúdo do ZIP do snapshot final, link do repositório e requisitos do SIGAA.

**A liberação só ocorrerá após todos os itens aplicáveis serem verificados.** Depois da auditoria: criar a tag imutável `v1.0.0-lexer` no commit aprovado, conferir SHA, gerar o pacote daquele snapshot, realizar a entrega no SIGAA e confirmar a submissão. Somente então iniciar a Parte 2 no mesmo repositório, após a tag.
