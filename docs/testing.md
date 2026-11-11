
# Testes e validação do JackCompiler

Este documento descreve a estratégia de testes, os procedimentos de reprodução, os materiais oficiais e as evidências observadas nas três partes do JackCompiler.

A validação combina testes automatizados em Java, execução do JAR empacotado e execução manual dos programas oficiais do Project 11 no Virtual Machine Emulator.

## 1. Ambiente de testes

O projeto utiliza:

| Componente | Versão ou ferramenta |
|---|---|
| Linguagem de implementação | Java 21 |
| Construção | Apache Maven |
| Testes | JUnit Jupiter 5.11.4 |
| Testes unitários | Maven Surefire 3.5.2 |
| Testes de integração | Maven Failsafe 3.5.2 |
| Formatação | Spotless 2.43.0 |
| Formatador Java | Google Java Format 1.24.0 |
| Emulador VM | Virtual Machine Emulator 2.5 |
| Ambiente local utilizado | Debian 13 |
| Integração contínua | GitHub Actions |

O emulador é uma ferramenta externa ao processo Maven. Sua execução não é simulada por `OfficialVmGenerationTest`.

## 2. Execução da suíte automatizada

Na raiz do repositório:

```bash
java -version
mvn -version
mvn clean verify
```

O Maven executa as etapas de formatação, compilação, testes unitários, empacotamento e testes de integração.

Se houver falha de formatação:

```bash
mvn spotless:apply
mvn clean verify
```

A execução direcionada dos testes oficiais de geração VM utiliza:

```bash
mvn '-Dtest=OfficialVmGenerationTest' test
```

### 2.1. Relatórios produzidos

Os relatórios Surefire são gerados em:

```text
target/surefire-reports/
```

Os relatórios Failsafe são gerados em:

```text
target/failsafe-reports/
```

O JAR executável é gerado em:

```text
target/jackcompiler.jar
```

O comando `mvn clean verify` remove os artefatos antigos de `target/` antes de produzir os novos resultados.

### 2.2. Critérios de aprovação

Uma execução é aprovada quando:

- a formatação Java é aceita;
- o código compila;
- os testes não apresentam falhas ou erros;
- o JAR é gerado;
- os testes de integração do executável passam;
- o Maven termina com `BUILD SUCCESS`.

A aprovação automatizada não substitui os testes comportamentais realizados no VM Emulator.

## 3. Estratégia de testes

A validação está organizada em seis grupos complementares.

### 3.1. Analisador léxico

Os testes da Parte 1 abrangem:

- reconhecimento das cinco categorias de tokens;
- palavras reservadas, símbolos e identificadores;
- constantes inteiras e strings;
- limites de valores;
- comentários e espaços;
- posições de origem;
- erros léxicos;
- preservação dos valores;
- formato XML e escape de caracteres.

Exemplos de classes:

```text
JackTokenizerTest
JackLexicalRulesTest
StringRecognizerTest
IntegerRecognizerTest
IdentifierRecognizerTest
SymbolRecognizerTest
SourceCursorTest
TokenXmlWriterTest
```

`LexerPublicApiTest` verifica aspectos do contrato público e da separação de responsabilidades.

### 3.2. Analisador sintático

Os testes da Parte 2 abrangem:

- estrutura de classes;
- declarações de variáveis;
- funções, métodos e construtores;
- parâmetros;
- comandos `let`, `if`, `while`, `do` e `return`;
- expressões e termos;
- acesso a arrays;
- chamadas de sub-rotinas;
- agrupamentos por parênteses;
- consumo integral dos tokens;
- erros sintáticos posicionados.

As responsabilidades são distribuídas entre testes de:

```text
JackParserTest
ClassStructureParserTest
StatementParserTest
ExpressionParserTest
TokenCursorTest
SyntaxExceptionTest
```

Os testes específicos de cada produção complementam essas classes principais.

### 3.3. Árvore sintática e XML

Os componentes da CST e de serialização são exercitados por:

```text
ParseNodeTest
GrammarNodeTest
TerminalNodeTest
SyntaxTagTest
ParseTreeXmlWriterTest
XmlEscaperTest
```

São verificados a imutabilidade, a ordem dos filhos, os terminais, a hierarquia e a representação XML oficial.

### 3.4. Tabela de símbolos e geração VM

Os testes da Parte 3 incluem:

```text
SymbolTest
SymbolTableTest
VmWriterTest
SyntaxTreeNavigatorTest
CompilationContextTest
CompilationExceptionTest
SubroutineCatalogTest
ExpressionCompilerTest
StatementCompilerTest
ClassCompilerTest
JackVmCompilerTest
```

Também existem testes específicos para grupos de expressões, chamadas, arrays, objetos e fluxo de controle.

Esses testes verificam:

- categorias e índices de símbolos;
- escopos e sombreamento;
- validação das instruções VM;
- estado independente por compilação;
- chamadas locais, externas e de objeto;
- argumentos implícitos de métodos;
- construtores e alocação;
- constantes, strings e operadores;
- endereçamento de arrays;
- `let`, `do`, `return`, `if`, `else` e `while`;
- rótulos determinísticos;
- diagnósticos semânticos;
- entradas inválidas e situações-limite.

`CompilerPublicApiTest` verifica aspectos do contrato público do compilador.

### 3.5. Aplicação, I/O e CLI

Os testes operacionais abrangem:

```text
TokenizeCommandTest
ParseCommandTest
CompileCommandTest
JackFileResolverTest
OutputPathResolverTest
TextFileWriterTest
CliArgumentParserTest
CliArgumentsTest
CliIntegrationTest
CliTokenizeIntegrationTest
CliParseIntegrationTest
CliCompileIntegrationTest
```

Os cenários incluem:

- entrada por arquivo e diretório;
- descoberta não recursiva e recursiva;
- extensão `.jack` sensível a maiúsculas;
- ordenação determinística;
- diretórios sem fontes elegíveis;
- leitura UTF-8 estrita;
- destinos padrão e personalizados;
- preservação de caminhos relativos;
- colisões e aliases;
- proteção contra escrita em fontes;
- caminhos e diretórios simbólicos;
- política de escrita;
- processamento `fail-fast`;
- mensagens e códigos de saída.

Os testes verificam que o comando `compile` não altera o comportamento de `tokenize` e `parse`.

### 3.6. JAR executável

Os testes de integração do JAR incluem:

| Classe | Testes na validação P3-24 |
|---|---:|
| `JarTokenizeIT` | 14 |
| `JarParseIT` | 17 |
| `JarCompileIT` | 15 |
| `JarExecutionIT` | 18 |
| **Total Failsafe** | **64** |

O JAR é executado em processos Java independentes, verificando sua utilização real pela linha de comando.

## 4. Validação com recursos oficiais

Os materiais de referência foram obtidos do repositório [nand2tetris/projects](https://github.com/nand2tetris/projects), na revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença MIT está preservada em:

```text
src/test/resources/nand2tetris/LICENSE
```

Os recursos oficiais de diferentes projetos permanecem separados.

### 4.1. Compatibilidade XML — Partes 1 e 2

Os testes utilizam os conjuntos:

| Conjunto | Fontes |
|---|---|
| Square | `Main`, `Square`, `SquareGame` |
| ExpressionLessSquare | `Main`, `Square`, `SquareGame` |
| ArrayTest | `Main` |

São sete fontes acompanhadas de referências XML.

`OfficialTokenXmlCompatibilityTest` compara os XMLs léxicos com os arquivos `*T.xml`.

A comparação é textual, exigindo correspondência de elementos, valores, ordem, espaços e escapes. Apenas os terminadores de linha das referências são normalizados.

`OfficialParserXmlCompatibilityTest` realiza comparações estruturais com as referências sintáticas oficiais.

A comparação considera:

- nomes dos elementos;
- hierarquia;
- quantidade e ordem dos filhos;
- categorias e valores dos terminais;
- espaços significativos;
- ausência de atributos inesperados.

Diferenças de indentação entre elementos gramaticais e entidades XML equivalentes não invalidam a estrutura.

Os testes exercitam a API e os casos de uso correspondentes, preservando os arquivos-fonte.

### 4.2. Aceitação de geração VM — Parte 3

`OfficialVmGenerationTest` utiliza os programas oficiais do Project 11.

| Programa | Arquivos `.jack` | Classes VM |
|---|---:|---:|
| Seven | 1 | 1 |
| Average | 1 | 1 |
| ConvertToBin | 1 | 1 |
| ComplexArrays | 1 | 1 |
| Square | 3 | 3 |
| Pong | 4 | 4 |
| **Total** | **11** | **11** |

Os testes verificam a geração dos arquivos `.vm`, a presença de instruções representativas, a ordem das saídas e a preservação dos arquivos de entrada.

Para Square e Pong, são verificadas compilações com destinos padrão e personalizados, incluindo as chamadas entre classes.

O teste automatizado não interpreta nem executa as instruções VM.

## 5. Procedimento de compilação dos programas oficiais

Após uma construção aprovada:

```bash
mvn clean verify
```

Compile os seis programas para diretórios separados:

```bash
java -jar target/jackcompiler.jar compile \
  src/test/resources/nand2tetris/project11/Seven/Main.jack \
  --output-dir target/project11-acceptance/Seven

java -jar target/jackcompiler.jar compile \
  src/test/resources/nand2tetris/project11/Average/Main.jack \
  --output-dir target/project11-acceptance/Average

java -jar target/jackcompiler.jar compile \
  src/test/resources/nand2tetris/project11/ConvertToBin/Main.jack \
  --output-dir target/project11-acceptance/ConvertToBin

java -jar target/jackcompiler.jar compile \
  src/test/resources/nand2tetris/project11/ComplexArrays/Main.jack \
  --output-dir target/project11-acceptance/ComplexArrays

java -jar target/jackcompiler.jar compile \
  src/test/resources/nand2tetris/project11/Square \
  --output-dir target/project11-acceptance/Square

java -jar target/jackcompiler.jar compile \
  src/test/resources/nand2tetris/project11/Pong \
  --output-dir target/project11-acceptance/Pong
```

**Importante:** as saídas personalizadas utilizam `CREATE_NEW`. Para repetir o procedimento, utilize diretórios de saída vazios ou remova somente os artefatos gerados anteriormente em `target/project11-acceptance/`.

Não compile diretamente sobre os recursos protegidos de `src/test/resources`.

A separação por programa impede que arquivos com nomes repetidos, como `Main.vm`, sejam misturados no mesmo diretório.

## 6. Execução no Virtual Machine Emulator

No ambiente local utilizado, o emulador pode ser iniciado com:

```bash
/home/helton/Documentos/Ferramentas/nand2tetris/tools/VMEmulator.sh
```

O caminho corresponde à instalação local utilizada na validação. Em outros ambientes, é necessário adaptar a localização da ferramenta.

Para cada programa:

1. abrir o emulador;
2. carregar o diretório contendo os arquivos `.vm`;
3. selecionar `No animation`;
4. executar o programa;
5. fornecer entradas quando necessárias;
6. observar a saída ou o estado de memória;
7. comparar o resultado efetivo com o comportamento esperado.

O VM Emulator executa os programas compilados. Essa verificação é independente dos testes de geração textual executados pelo JUnit.

### 6.1. Seven

**Entrada:** nenhuma.

**Comportamento esperado:** impressão do número `7`.

**Resultado observado:** o emulador exibiu `7`.

**Conclusão:** aprovado.

### 6.2. Average

**Entradas utilizadas:** `4`, `6` e `8`.

O programa solicita os valores e calcula sua média.

Resultado esperado:

```text
The average is 6
```

**Resultado observado:** média `6`, conforme esperado.

**Conclusão:** aprovado.

### 6.3. ConvertToBin

**Entrada utilizada:** `RAM[8000] = 13`.

O programa escreve a representação binária nas posições seguintes.

A interpretação das posições utiliza o bit menos significativo primeiro:

| Posição | Valor observado |
|---|---:|
| RAM[8001] | 1 |
| RAM[8002] | 0 |
| RAM[8003] | 1 |
| RAM[8004] | 1 |
| RAM[8005] a RAM[8016] | 0 |

Assim, a representação de 16 bits, em ordem convencional, é:

```text
0000000000001101
```

A execução foi interrompida manualmente para atualizar e inspecionar a memória, conforme o comportamento do programa no emulador.

**Resultado observado:** as dezesseis posições corresponderam à representação esperada, sem valores `-1` remanescentes.

**Conclusão:** aprovado.

### 6.4. ComplexArrays

O programa realiza operações com arrays e chamadas de funções.

Resultados esperados e observados:

| Teste | Esperado | Obtido |
|---|---:|---:|
| 1 | 5 | 5 |
| 2 | 40 | 40 |
| 3 | 0 | 0 |
| 4 | 77 | 77 |
| 5 | 110 | 110 |

**Conclusão:** cinco resultados corretos; aprovado.

### 6.5. Square

O jogo foi carregado com as três classes:

```text
Main.vm
Square.vm
SquareGame.vm
```

Foram observados:

- desenho inicial do quadrado;
- movimentação pelas quatro setas;
- aumento de tamanho com `x`;
- diminuição de tamanho com `z`;
- encerramento com `q`.

Todos os comportamentos foram confirmados durante a execução manual.

**Conclusão:** aprovado.

### 6.6. Pong

O jogo foi carregado com as quatro classes:

```text
Ball.vm
Bat.vm
Main.vm
PongGame.vm
```

Foram verificados:

- inicialização com bola, raquete e placar;
- movimentação horizontal da raquete;
- deslocamento e colisões da bola;
- rebatidas e atualização da pontuação;
- encerramento da partida com `Game Over`.

A captura final mostra:

```text
Score: 3
Game Over
```

O valor `3` evidencia que a pontuação foi atualizada durante a partida.

O autor confirmou que os controles previstos funcionaram, incluindo o encerramento solicitado pelo teclado.

**Conclusão:** aprovado.

## 7. Resultados automatizados observados

A execução local do P3-24 ocorreu em 10/11/2026.

O comando utilizado foi:

```bash
mvn clean verify
```

Os resultados registrados foram:

| Verificação | Resultado |
|---|---|
| Spotless | Aprovado |
| Compilação Java 21 | Aprovada |
| Surefire | 1.799 testes |
| Failsafe | 64 testes |
| Falhas | 0 |
| Erros | 0 |
| Testes ignorados | 0 |
| **Total** | **1.863 testes aprovados** |
| Construção Maven | **BUILD SUCCESS** |

`OfficialVmGenerationTest` executou 12 testes, todos aprovados.

A execução confirmou a regressão dos componentes anteriores, o empacotamento do JAR e os testes de integração.

Essas evidências correspondem ao estado funcional publicado no commit:

[`0d389be81a2876ca7ac804085b49493104a96886`](https://github.com/0zzmandias/jackcompiler/commit/0d389be81a2876ca7ac804085b49493104a96886)

## 8. Integração contínua

O workflow `.github/workflows/ci.yml` utiliza Java 21 (Temurin) e executa:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

O workflow foi utilizado com sucesso em entregas anteriores do projeto.

Durante o desenvolvimento da Parte 3, o GitHub impediu a inicialização de jobs por um bloqueio administrativo de cobrança da conta (*billing lock*).

A mensagem apresentada pela plataforma indicava que o job não havia sido iniciado devido ao estado de cobrança da conta. O problema foi encaminhado ao suporte do GitHub.

Como o runner não chegou a executar as etapas de construção, esse bloqueio não demonstra defeito de compilação ou falha nos testes do JackCompiler.

O desenvolvimento prosseguiu com validação local, conforme os resultados registrados na seção 7.

**Situação:** testes locais aprovados; execução da CI no estado final ainda pendente de liberação pela plataforma.

Não foram realizadas alterações arbitrárias no workflow ou no código para contornar a restrição administrativa.

## 9. Alcance e limitações das evidências

Os testes automatizados demonstram conformidade nos cenários exercitados e verificam a preservação dos contratos das entregas anteriores.

As comparações XML utilizam referências oficiais independentes da implementação.

Os testes de VM exercitam a geração de instruções, enquanto o VM Emulator confirma o comportamento dos seis programas selecionados.

A aprovação desses testes não representa prova formal de correção para todos os programas Jack possíveis.

O conjunto também não equivale a uma validação completa de tipos entre classes, otimização ou execução de todo o ecossistema Jack.

As decisões técnicas estão em [architecture.md](architecture.md), e a matriz de requisitos está em [acceptance.md](acceptance.md).

As instruções gerais de construção e utilização do programa encontram-se no [README.md](../README.md).
