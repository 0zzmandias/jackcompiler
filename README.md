
# JackCompiler

Compilador da linguagem Jack, desenvolvido em **Java 21** para a disciplina de Compiladores, com base na linguagem e na máquina virtual utilizadas no curso [nand2tetris](https://www.nand2tetris.org/).

O JackCompiler disponibiliza três operações:

- **`tokenize`:** análise léxica de código Jack e geração de XML de tokens.
- **`parse`:** análise sintática descendente recursiva e geração de XML da árvore sintática.
- **`compile`:** tradução de programas Jack para instruções da máquina virtual Hack (VM).

As operações compartilham os componentes de análise léxica e sintática, preservando contratos independentes para geração de XML e compilação VM.

**Repositório:** [github.com/0zzmandias/jackcompiler](https://github.com/0zzmandias/jackcompiler)

## Integrante

**Helton Alves Sá**  
Matrícula: 20260009631

## 1. Requisitos e construção

### 1.1. Requisitos

- JDK 21;
- Apache Maven;
- Sistema operacional compatível com Java 21.

Para verificar o ambiente:

```bash
java -version
mvn -version
```

### 1.2. Construção

Na raiz do repositório, execute:

```bash
mvn clean verify
```

O comando executa a verificação de formatação Java, a compilação, os testes unitários, o empacotamento e os testes de integração.

O arquivo executável é produzido em:

```text
target/jackcompiler.jar
```

Caso seja necessário corrigir a formatação do código Java:

```bash
mvn spotless:apply
mvn clean verify
```

O projeto utiliza JUnit Jupiter, Maven Surefire, Maven Failsafe e Spotless com Google Java Format.

## 2. Interface de linha de comando

O JackCompiler disponibiliza três subcomandos e a opção de ajuda:

```bash
java -jar target/jackcompiler.jar --help

java -jar target/jackcompiler.jar tokenize Main.jack
java -jar target/jackcompiler.jar parse Main.jack
java -jar target/jackcompiler.jar compile Main.jack
```

A sintaxe geral é:

```text
jackcompiler tokenize <file.jack|directory> [--output-dir <directory>]
jackcompiler parse <file.jack|directory> [--output-dir <directory>]
jackcompiler compile <file.jack|directory> [--output-dir <directory>]
jackcompiler --help
```

Os três subcomandos aceitam arquivos individuais ou diretórios. A descoberta de fontes e a resolução de destinos dependem da operação selecionada.

### 2.1. Tokenização e análise sintática

Para processar um diretório:

```bash
java -jar target/jackcompiler.jar tokenize projetos/Square
java -jar target/jackcompiler.jar parse projetos/Square
```

Para utilizar diretórios de saída personalizados:

```bash
java -jar target/jackcompiler.jar tokenize Main.jack \
  --output-dir resultados/tokens

java -jar target/jackcompiler.jar parse Main.jack \
  --output-dir resultados/parser
```

Os comandos `tokenize` e `parse` utilizam descoberta **não recursiva**. Ao receber um diretório, selecionam somente os arquivos regulares com extensão `.jack` diretamente contidos nele, em ordem alfabética.

A extensão `.jack` diferencia maiúsculas de minúsculas.

### 2.2. Compilação Jack para VM

Para compilar um arquivo individual:

```bash
java -jar target/jackcompiler.jar compile Main.jack
```

O destino padrão é `Main.vm`, no mesmo diretório de `Main.jack`, desde que a escrita seja permitida.

Para compilar um diretório:

```bash
java -jar target/jackcompiler.jar compile projetos/Square
```

O comando `compile` pesquisa arquivos `.jack` **recursivamente** e os processa em ordem determinística de caminho relativo.

Para definir um diretório de saída:

```bash
java -jar target/jackcompiler.jar compile projetos/Square \
  --output-dir resultados/vm/Square
```

Cada arquivo Jack produz um arquivo VM com o mesmo nome-base.

Quando a entrada é um diretório com subdiretórios, a compilação com destino personalizado preserva a estrutura relativa das fontes.

A descoberta recursiva não percorre links simbólicos de diretórios. Diretórios sem arquivos Jack elegíveis e entradas inválidas são rejeitados.

### 2.3. Arquivos gerados

| Operação | Formato de saída | Destino padrão |
|---|---|---|
| `tokenize` | `NomeT.xml` | `target/generated-tokens/` |
| `parse` | `NomeP.xml` | `target/generated-parser/` |
| `compile` | `Nome.vm` | Diretório do arquivo `.jack` |

Os destinos XML padrão são relativos ao diretório de trabalho da aplicação.

A opção `--output-dir` permite definir destinos personalizados para as três operações.

### 2.4. Políticas de escrita e processamento

O processamento não modifica os arquivos-fonte.

A publicação dos resultados utiliza `TextFileWriter`, com codificação UTF-8, arquivo temporário e verificação de destinos, incluindo caminhos simbólicos e diretórios protegidos de código e recursos de teste.

São utilizadas duas políticas de escrita:

| Política | Comportamento |
|---|---|
| `REPLACE_EXISTING` | Permite substituir um arquivo regular existente em destinos padrão autorizados |
| `CREATE_NEW` | Exige destino desocupado e impede a sobrescrita de arquivos existentes |

O conteúdo de cada arquivo de saída é produzido integralmente antes da publicação.

O processamento em lote é sequencial e utiliza a política **fail-fast**: uma falha interrompe as operações restantes. Arquivos já publicados são preservados, sem transação global ou rollback.

Para compilar os programas oficiais armazenados em `src/test/resources`, deve-se utilizar um diretório de saída personalizado, preferencialmente em `target/`, preservando os recursos de teste.

### 2.5. Códigos de saída e diagnósticos

| Código | Significado |
|---|---|
| `0` | Execução bem-sucedida ou exibição da ajuda |
| `1` | Falha de processamento |
| `2` | Argumentos inválidos |

A ajuda e os caminhos dos arquivos gerados são apresentados em `stdout`. Os diagnósticos de erro são apresentados em `stderr`.

A aplicação distingue erros léxicos, sintáticos, de compilação e de entrada/saída. Quando a informação está disponível, os diagnósticos incluem linha e coluna da origem.

A leitura dos arquivos utiliza UTF-8 estrito e rejeita sequências inválidas.

## 3. Analisador léxico

O analisador léxico reconhece as cinco categorias de tokens da linguagem Jack:

- `keyword`;
- `symbol`;
- `integerConstant`;
- `stringConstant`;
- `identifier`.

São reconhecidas as 21 palavras reservadas e os 19 símbolos oficiais da linguagem.

**Símbolos Jack:** `{ } ( ) [ ] . , ; + - * / & | < > = ~`.

O analisador também implementa:

- constantes inteiras de 0 a 32767;
- identificadores e strings;
- descarte de espaços em branco;
- comentários `//`, `/* ... */` e `/** ... */`;
- preservação dos valores e posições originais dos tokens;
- identificação de entradas léxicas inválidas.

A serialização XML utiliza UTF-8, terminadores de linha LF e escape dos caracteres reservados.

### 3.1. Exemplo de tokenização

Considere `Main.jack`:

```jack
class Main {}
```

Execute:

```bash
java -jar target/jackcompiler.jar tokenize Main.jack
```

O arquivo `MainT.xml` conterá:

```xml
<tokens>
<keyword> class </keyword>
<identifier> Main </identifier>
<symbol> { </symbol>
<symbol> } </symbol>
</tokens>
```

A análise léxica pode ser utilizada independentemente da análise sintática e da compilação VM.

## 4. Analisador sintático

O analisador sintático utiliza análise descendente recursiva e recebe diretamente o `TokenizationResult` produzido pelo scanner.

A gramática reconhecida abrange:

- classes e declarações de variáveis estáticas e de campo;
- funções, métodos e construtores;
- parâmetros e variáveis locais;
- comandos `let`, `if`, `while`, `do` e `return`;
- expressões e termos;
- chamadas de sub-rotinas;
- acesso indexado a arrays;
- expressões agrupadas por parênteses.

O resultado é uma árvore sintática concreta (CST), que pode ser serializada em XML no formato do Project 10.

### 4.1. Exemplo de análise sintática

Utilizando o mesmo `Main.jack`:

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

### 4.2. Expressões e ordem de avaliação

A gramática Jack define:

```text
expression ::= term (op term)*
```

Os operadores binários possuem a mesma precedência na gramática Jack, e as operações são representadas na sequência em que aparecem na expressão.

Assim, `2 + 3 * 4` é reconhecida como uma sequência de termos e operadores, sem aplicação automática da precedência aritmética convencional.

Agrupamentos explícitos com parênteses são preservados como estruturas aninhadas.

Na compilação VM, as operações são emitidas conforme a sequência sintática representada pela CST.

Referência: Nisan e Schocken, *The Elements of Computing Systems*, capítulo 9, seção "Operator Priority and Order of Evaluation".

### 4.3. Representação XML

O XML sintático utiliza as quinze categorias gramaticais serializáveis do Project 10.

Produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são reconhecidas pelos componentes sintáticos, mas não exigem elementos XML próprios.

Por exemplo, um operador é representado por `<symbol>` dentro de `<expression>`, sem uma tag intermediária `<op>`.

A estrutura gerada preserva a hierarquia, a ordem e o conteúdo significativo dos terminais definidos pelo formato oficial.

## 5. Compilador Jack para VM

O compilador transforma a árvore sintática concreta em instruções da máquina virtual Hack, utilizando os contratos léxicos e sintáticos do projeto.

A compilação não depende de XML intermediário.

O fluxo de processamento é:

```text
Arquivo Jack (.jack)
       |
       v
JackSourceReader
       |
       v
JackTokenizer
       |
       v
TokenizationResult
       |
       v
JackParser
       |
       v
GrammarNode (CST)
       |
       v
JackVmCompiler
       |
       v
Instruções VM
       |
       v
TextFileWriter
       |
       v
Arquivo VM (.vm)
```

### 5.1. Tabela de símbolos

O compilador mantém escopos de classe e de sub-rotina.

As declarações são classificadas em quatro categorias, associadas aos segmentos correspondentes da VM:

| Categoria Jack | Segmento VM |
|---|---|
| `static` | `static` |
| `field` | `this` |
| `argument` | `argument` |
| `var` | `local` |

Os índices são independentes por categoria.

O início de uma sub-rotina reinicia o escopo local e os contadores correspondentes.

Nos métodos, `argument 0` é reservado para a referência implícita ao objeto receptor.

A resolução de símbolos consulta primeiro o escopo da sub-rotina e depois o escopo de classe.

### 5.2. Sub-rotinas e objetos

A compilação de sub-rotinas contempla:

- declaração `function Classe.rotina nLocals`;
- inicialização de `this` em métodos;
- alocação de objetos por `Memory.alloc` em construtores;
- passagem do receptor nas chamadas de métodos;
- argumentos explícitos em chamadas de funções e construtores;
- retorno de valores;
- retorno `void` mediante o valor convencional zero.

As chamadas de sub-rotinas distinguem referências locais, chamadas qualificadas por classe e chamadas sobre objetos.

Dependências externas, incluindo serviços do Jack OS, podem ser referenciadas sem que todas as classes correspondentes sejam compiladas no mesmo diretório.

### 5.3. Expressões e comandos

O compilador gera instruções VM para:

- constantes inteiras, strings e constantes de palavra-chave;
- leitura e atribuição de variáveis;
- operadores binários e unários;
- expressões aninhadas;
- chamadas de sub-rotinas;
- leitura e escrita de elementos de arrays;
- comandos `let`, `do`, `return`, `if`, `if-else` e `while`.

Multiplicação e divisão utilizam `Math.multiply` e `Math.divide`.

A construção de strings utiliza `String.new` e `String.appendChar`.

O acesso a arrays utiliza cálculo de endereço e os segmentos `pointer 1` e `that 0`.

Estruturas condicionais e de repetição utilizam rótulos VM gerados de maneira determinística.

O comando `do` descarta o valor retornado pela chamada.

### 5.4. Validação semântica e limitações

O compilador verifica inconsistências identificáveis a partir da classe e de suas declarações, incluindo:

- símbolos não resolvidos;
- declarações duplicadas;
- chamadas incompatíveis com o contexto conhecido da sub-rotina;
- utilizações inválidas identificáveis pela implementação.

O escopo de validação não inclui:

- inferência global de tipos;
- análise completa de compatibilidade entre classes independentes;
- verificação integral de implementações externas;
- otimização de código VM.

A ausência dessas verificações não impede a compilação de programas Jack válidos que utilizem classes externas ou serviços do Jack OS.

## 6. Arquitetura

O código de produção está organizado nos seguintes pacotes:

```text
src/main/java/jackcompiler/
├── application/  # Casos de uso
├── cli/          # Argumentos e interface de linha de comando
├── compiler/     # Tradução da CST em instruções VM
├── io/           # Leitura, descoberta, destinos e publicação
├── lexer/        # Reconhecimento léxico
├── parser/       # Análise sintática
├── symbol/       # Símbolos e escopos
├── syntax/       # Árvore sintática concreta
├── token/        # Representação de tokens e posições
├── vm/           # Emissão de instruções VM
└── xml/          # Serialização XML
```

Os casos de uso `TokenizeCommand`, `ParseCommand` e `CompileCommand` possuem responsabilidades independentes e compartilham os serviços necessários de leitura, análise e escrita.

O componente `JackVmCompiler` recebe um `GrammarNode` e retorna o código VM correspondente. Sua interface pública não depende da CLI, do sistema de arquivos ou da serialização XML.

A arquitetura utiliza separação de responsabilidades entre reconhecimento léxico, análise sintática, gerenciamento de símbolos, tradução semântica, emissão VM e publicação de arquivos.

A descrição detalhada dos componentes, contratos, dependências e políticas de processamento encontra-se em [docs/architecture.md](docs/architecture.md).

## 7. Testes e validação

A validação do JackCompiler abrange testes unitários, testes de integração, testes do JAR executável, testes de contratos públicos e testes de aceitação com programas oficiais do nand2tetris.

### 7.1. Testes automatizados

A suíte automatizada verifica os componentes das três operações: `tokenize`, `parse` e `compile`.

Também são exercitados o processamento de arquivos, o tratamento de erros, a interface de linha de comando e a execução do JAR em processos Java independentes.

Na execução local de 11/11/2026, referente ao commit [`d77a6ba`](https://github.com/0zzmandias/jackcompiler/commit/d77a6ba441ad3969192023a3b32672ca5831d597), o comando `mvn clean verify` apresentou:

| Verificação | Resultado |
|---|---|
| Verificação Spotless | 144 arquivos conformes |
| Compilação Java 21 | Aprovada |
| Surefire | 1.799 testes aprovados |
| Failsafe | 64 testes aprovados |
| **Total** | **1.863 testes aprovados** |
| Falhas | 0 |
| Erros | 0 |
| Testes ignorados | 0 |
| Empacotamento do JAR | Aprovado |
| Maven | `BUILD SUCCESS` |

Os resultados identificam a revisão efetivamente verificada.

### 7.2. Compatibilidade XML

As saídas de tokenização e análise sintática são verificadas com recursos oficiais dos conjuntos:

- `Square`;
- `ExpressionLessSquare`;
- `ArrayTest`.

Os testes léxicos comparam o conteúdo XML com referências `*T.xml`.

Os testes sintáticos comparam a estrutura das árvores XML com referências oficiais `*.xml`, considerando hierarquia, ordem e conteúdo significativo dos terminais.

### 7.3. Validação de geração VM

A geração VM foi verificada com os seis programas oficiais do Project 11:

| Programa | Arquivos Jack | Resultado observado no VM Emulator |
|---|---:|---|
| Seven | 1 | Exibição do valor 7 |
| Average | 1 | Média 6 para as entradas 4, 6 e 8 |
| ConvertToBin | 1 | Representação binária correta de 13 em 16 bits |
| ComplexArrays | 1 | Resultados 5, 40, 0, 77 e 110 |
| Square | 3 | Desenho, movimentação, alteração de tamanho e encerramento |
| Pong | 4 | Movimento, colisões, atualização do placar e Game Over |

Os seis programas foram compilados pelo JackCompiler e executados manualmente no **Virtual Machine Emulator 2.5**.

Os testes automatizados de geração VM verificam as instruções e os arquivos produzidos, mas não executam o Virtual Machine Emulator.

Os procedimentos de reprodução e os resultados detalhados estão documentados em [docs/testing.md](docs/testing.md).

A correspondência entre requisitos, componentes e testes está disponível em [docs/acceptance.md](docs/acceptance.md).

### 7.4. Integração contínua

O repositório contém o workflow `.github/workflows/ci.yml`, configurado para Java 21 e Maven.

O workflow executa:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

A execução do GitHub Actions ficou indisponível durante a validação da Parte 3 devido a uma restrição administrativa de cobrança da plataforma. Os resultados apresentados neste documento correspondem à execução local da suíte Maven, e não a uma execução aprovada da CI.

### 7.5. Recursos oficiais

Os recursos de referência foram obtidos de [nand2tetris/projects](https://github.com/nand2tetris/projects), na revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença MIT correspondente aos recursos oficiais está preservada em:

```text
src/test/resources/nand2tetris/LICENSE
```

## 8. Desafios e aprendizados

O desenvolvimento do JackCompiler envolveu a integração progressiva de análise léxica, análise sintática e geração de código VM. Os principais desafios técnicos estiveram relacionados à reutilização de componentes, ao gerenciamento de estados e escopos, à tradução das construções Jack e à validação do comportamento dos programas gerados.

### 8.1. Reutilização dos componentes léxicos e sintáticos

A geração de código VM precisava operar sobre os resultados da análise sintática sem duplicar o scanner ou o parser e sem introduzir dependência da representação XML.

A solução adotada utiliza diretamente a árvore sintática concreta (CST) e o componente `SyntaxTreeNavigator` para acessar suas estruturas. A tradução permanece independente dos mecanismos de serialização.

**Aprendizado:** a definição de contratos estáveis entre componentes reduz duplicação, facilita a evolução do sistema e permite reutilizar funcionalidades existentes em novas etapas de processamento.

### 8.2. Gerenciamento de símbolos e escopos

A tradução de variáveis Jack exige associar declarações aos segmentos e índices corretos da VM, distinguindo variáveis estáticas, campos, argumentos e variáveis locais.

O gerenciamento de símbolos foi organizado em escopos de classe e sub-rotina. Os índices são independentes por categoria, e cada sub-rotina reinicia seu escopo local.

**Aprendizado:** a representação explícita dos escopos e das categorias de símbolos é essencial para evitar referências incorretas e manter o isolamento entre unidades de compilação.

### 8.3. Compilação de métodos, construtores e chamadas

A tradução de sub-rotinas exige tratar diferenças entre funções, métodos e construtores, particularmente a referência implícita ao objeto receptor.

Métodos associam o receptor a `pointer 0`, enquanto construtores utilizam `Memory.alloc` para reservar espaço para os campos do objeto. Chamadas de métodos incluem a referência ao receptor na pilha de argumentos.

O catálogo de sub-rotinas permite identificar declarações da própria classe antes de traduzir os corpos, inclusive quando uma chamada referencia uma rotina declarada posteriormente.

**Aprendizado:** a geração correta de chamadas depende da compreensão das convenções da máquina virtual, da organização dos objetos em memória e da distinção entre argumentos explícitos e implícitos.

### 8.4. Tradução de expressões, arrays e fluxo de controle

Expressões Jack precisam ser convertidas em operações sobre a pilha da VM, respeitando a estrutura sintática e os efeitos de cada instrução.

O acesso a arrays utiliza cálculo de endereços e os segmentos `pointer` e `that`. Strings e operações como multiplicação e divisão dependem de chamadas aos serviços do Jack OS.

Comandos condicionais e laços exigem a geração de rótulos e desvios corretos, inclusive em estruturas aninhadas.

**Aprendizado:** a tradução de linguagens baseadas em pilha exige controle rigoroso da ordem das operações, dos valores temporários e dos efeitos das instruções sobre o estado da VM.

### 8.5. Processamento de arquivos e proteção das fontes

A interface de compilação precisava aceitar arquivos individuais e diretórios recursivos, preservar a organização das fontes e impedir gravações sobre arquivos protegidos.

A solução separa descoberta de entradas, resolução de destinos e publicação de resultados. O processamento utiliza ordenação determinística, validação de colisões, políticas de escrita e interrupção na primeira falha.

**Aprendizado:** as operações de entrada e saída fazem parte da confiabilidade de um compilador. A geração correta do código não é suficiente se a aplicação puder sobrescrever fontes ou produzir saídas em destinos indevidos.

### 8.6. Validação do código gerado

A aprovação de testes unitários não garante, isoladamente, que os programas VM produzidos apresentem o comportamento esperado.

A validação combinou testes de componentes, testes do JAR executável, verificações com recursos oficiais e execução manual dos seis programas do Project 11 no Virtual Machine Emulator.

**Aprendizado:** diferentes níveis de testes oferecem evidências complementares. A verificação da geração textual das instruções deve ser acompanhada de testes de integração e de execução comportamental para identificar problemas que não aparecem em análises isoladas.

## 9. Versionamento

O repositório contém o código das três partes do JackCompiler e preserva as referências das entregas anteriores:

| Referência Git | Componente |
|---|---|
| [`v1.0.0-lexer`](https://github.com/0zzmandias/jackcompiler/tree/v1.0.0-lexer) | Analisador léxico |
| [`v2.0.0-parser`](https://github.com/0zzmandias/jackcompiler/tree/v2.0.0-parser) | Analisador sintático |

O histórico Git permite identificar as alterações dos componentes e consultar as versões anteriores do projeto.

## 10. Uso de Inteligência Artificial

O **ChatGPT, da OpenAI, incluindo o modelo GPT-6**, foi utilizado como ferramenta de apoio técnico ao desenvolvimento do JackCompiler.

O apoio compreendeu:

- esclarecimento de especificações da linguagem Jack e da VM Hack;
- análise de alternativas arquiteturais e definição de responsabilidades;
- elaboração e revisão de propostas de código e testes;
- interpretação de diagnósticos e resultados de execução;
- investigação de defeitos e avaliação de correções;
- orientação dos procedimentos de validação com programas oficiais;
- elaboração e revisão da documentação técnica.

As propostas de implementação foram integradas e verificadas pelo autor do projeto. A validação incluiu revisão dos componentes, execução local da suíte automatizada com Maven, testes de integração do JAR e execução dos programas oficiais no Virtual Machine Emulator.

O uso de IA não substituiu a verificação funcional do software. A responsabilidade pela compreensão, integração, validação e entrega do projeto permanece com o integrante identificado neste documento.
