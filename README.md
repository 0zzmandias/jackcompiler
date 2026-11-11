
# JackCompiler

Projeto acadêmico da disciplina de Compiladores, desenvolvido em **Java 21** para a linguagem Jack, utilizada no curso [nand2tetris](https://www.nand2tetris.org/).

O JackCompiler integra três componentes em um único projeto:

- **Analisador léxico (`tokenize`):** reconhece os tokens Jack e produz XML no formato oficial.
- **Analisador sintático (`parse`):** reutiliza o scanner, reconhece a gramática por análise descendente recursiva e produz uma árvore sintática em XML.
- **Compilador Jack → VM (`compile`):** reutiliza scanner e parser, constrói a representação semântica necessária e gera instruções da máquina virtual Hack, compatíveis com o Project 11.

Os componentes foram desenvolvidos progressivamente no mesmo repositório, preservando os contratos das entregas anteriores.

**Repositório:** [github.com/0zzmandias/jackcompiler](https://github.com/0zzmandias/jackcompiler)

## Integrante

**Helton Alves Sá**  
Matrícula: 20260009631

## 1. Requisitos e construção

- JDK 21;
- Apache Maven;
- Sistema operacional com suporte à execução de aplicações Java.

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

Caso a verificação de formatação falhe:

```bash
mvn spotless:apply
mvn clean verify
```

O projeto utiliza Maven Surefire, Failsafe, JUnit Jupiter e Spotless com Google Java Format.

O repositório também possui um workflow GitHub Actions configurado para Java 21. Sua execução encontra-se condicionada à resolução de um bloqueio administrativo de cobrança na plataforma GitHub, que impediu a inicialização dos jobs durante a Parte 3. Essa restrição externa não impediu a validação local do projeto.

## 2. Como executar

A aplicação disponibiliza três subcomandos e uma opção de ajuda:

```bash
java -jar target/jackcompiler.jar --help

java -jar target/jackcompiler.jar tokenize Main.jack
java -jar target/jackcompiler.jar parse Main.jack
java -jar target/jackcompiler.jar compile Main.jack
```

Sintaxe:

```text
jackcompiler tokenize <file.jack|directory> [--output-dir <directory>]
jackcompiler parse <file.jack|directory> [--output-dir <directory>]
jackcompiler compile <file.jack|directory> [--output-dir <directory>]
jackcompiler --help
```

Os três comandos também aceitam diretórios. A interpretação da entrada e os destinos diferem conforme a operação.

### 2.1. Tokenização e análise sintática

Exemplos:

```bash
java -jar target/jackcompiler.jar tokenize projetos/Square
java -jar target/jackcompiler.jar parse projetos/Square

java -jar target/jackcompiler.jar tokenize Main.jack --output-dir resultados/tokens
java -jar target/jackcompiler.jar parse Main.jack --output-dir resultados/parser
```

Para `tokenize` e `parse`, a descoberta de fontes em diretórios **não é recursiva**. Somente arquivos regulares `.jack` diretamente contidos no diretório são selecionados, em ordem alfabética.

A extensão diferencia maiúsculas de minúsculas.

### 2.2. Compilação Jack para VM

Para compilar um arquivo:

```bash
java -jar target/jackcompiler.jar compile Main.jack
```

Por padrão, o arquivo `Main.vm` será gerado ao lado de `Main.jack`, desde que o destino seja permitido.

Para compilar um diretório:

```bash
java -jar target/jackcompiler.jar compile projetos/Square
```

O comando `compile` procura arquivos `.jack` **recursivamente** e os processa em ordem determinística de caminho relativo.

Para utilizar um diretório de saída específico:

```bash
java -jar target/jackcompiler.jar compile projetos/Square \
  --output-dir resultados/vm/Square
```

Cada fonte gera um arquivo VM correspondente, preservando o nome-base.

Se o diretório de entrada possuir subdiretórios, seus caminhos relativos serão reproduzidos no diretório de saída personalizado.

O comando não percorre links simbólicos de diretórios. Diretórios sem arquivos Jack elegíveis e entradas inválidas produzem erro.

### 2.3. Arquivos gerados

| Comando | Saída | Destino padrão |
|---|---|---|
| `tokenize` | `NomeT.xml` | `target/generated-tokens/` |
| `parse` | `NomeP.xml` | `target/generated-parser/` |
| `compile` | `Nome.vm` | Diretório do arquivo `.jack` |

Os diretórios padrão de XML são relativos ao diretório de trabalho de execução.

A opção `--output-dir` permite utilizar um destino personalizado. Na compilação de diretórios, ela também preserva a estrutura relativa das fontes.

### 2.4. Proteção de arquivos e processamento em lote

Os arquivos-fonte não são modificados pelo processamento.

A publicação utiliza `TextFileWriter`, com UTF-8, arquivo temporário e verificações contra destinos inseguros, incluindo caminhos simbólicos e diretórios protegidos de código e recursos de teste.

As políticas de colisão são:

- **Destino padrão:** permite substituir um arquivo de saída regular preexistente.
- **Destino personalizado:** rejeita colisões, sem sobrescrever o arquivo existente.

O conteúdo de cada saída é preparado antes de sua publicação.

O processamento em lote é **sequencial e fail-fast**: a primeira falha interrompe o processamento. Saídas já publicadas permanecem; os arquivos seguintes não são processados. Não existe rollback global.

Para compilar exemplos oficiais versionados em `src/test/resources`, utilize um diretório de saída personalizado, preferencialmente dentro de `target/`, preservando os recursos de teste.

### 2.5. Códigos de saída e diagnósticos

| Código | Significado |
|---|---|
| `0` | Sucesso ou exibição da ajuda |
| `1` | Falha de processamento |
| `2` | Argumentos inválidos |

A ajuda e os caminhos dos arquivos gerados são apresentados em `stdout`. Erros são apresentados em `stderr`.

Os diagnósticos distinguem falhas léxicas, sintáticas, de compilação e de entrada/saída. Quando há uma posição original disponível, o diagnóstico informa linha e coluna.

A leitura utiliza UTF-8 estrito, rejeitando sequências inválidas.

## 3. Analisador léxico — Parte 1

O scanner reconhece as cinco categorias oficiais de Jack:

- `keyword`;
- `symbol`;
- `integerConstant`;
- `stringConstant`;
- `identifier`.

São implementadas as 21 palavras reservadas, os 19 símbolos oficiais, constantes inteiras de 0 a 32767, identificadores, strings e o descarte de espaços e comentários `//`, `/* ... */` e `/** ... */`.

**Símbolos:** `{ } ( ) [ ] . , ; + - * / & | < > = ~`.

O scanner preserva os valores e as posições originais dos tokens. A serialização XML utiliza UTF-8, terminadores LF e escape dos caracteres reservados.

### 3.1. Exemplo de tokenização

Para `Main.jack` contendo:

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

### 3.2. Símbolos `[` e `]`

O enunciado da Parte 1 omite `[` e `]` em sua lista de símbolos. Entretanto, ambos pertencem aos 19 símbolos oficiais de Jack e são necessários para indexação de arrays.

A implementação desses símbolos segue a especificação oficial e não constitui extensão da linguagem.

## 4. Analisador sintático — Parte 2

O parser reconhece declarações de classes, campos, estáticas, variáveis, funções, métodos, construtores, parâmetros, expressões e os comandos `let`, `if`, `while`, `do` e `return`.

Também reconhece acesso indexado a arrays e chamadas de sub-rotinas.

O parser utiliza análise descendente recursiva diretamente sobre o `TokenizationResult` produzido pelo scanner.

### 4.1. Exemplo de análise sintática

Para o mesmo `Main.jack`:

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

### 4.2. Precedência e associatividade

A gramática oficial define:

```text
expression ::= term (op term)*
```

Jack não impõe uma tabela obrigatória de precedência para operadores binários.

Assim, `2 + 3 * 4` é reconhecida como sequência de termos e operadores. Os parênteses explícitos são preservados como agrupamentos aninhados.

O parser não introduz uma precedência convencional alheia à gramática. A etapa de compilação avalia as operações segundo a sequência sintática representada pela árvore.

Essa decisão mantém a compatibilidade com a representação oficial do nand2tetris.

Referência: Nisan e Schocken, *The Elements of Computing Systems*, capítulo 9, seção "Operator Priority and Order of Evaluation".

### 4.3. Tags XML e produções auxiliares

O formato do Project 10 utiliza 15 categorias de elementos gramaticais.

Produções auxiliares como `op`, `unaryOp`, `keywordConstant`, `statement` e `subroutineCall` são reconhecidas, mas não necessariamente serializadas como elementos XML próprios.

Um operador, por exemplo, aparece como `<symbol>` dentro de `<expression>`, sem introduzir uma tag `<op>`.

Essa decisão preserva a equivalência estrutural com os XMLs oficiais.

## 5. Compilador Jack → VM — Parte 3

O compilador utiliza diretamente a árvore sintática concreta (CST), sem gerar ou interpretar XML intermediário.

O fluxo é:

```text
Fonte Jack (.jack)
    ↓
JackSourceReader
    ↓
JackTokenizer
    ↓
TokenizationResult
    ↓
JackParser
    ↓
GrammarNode
    ↓
JackVmCompiler
    ↓
Instruções VM
    ↓
TextFileWriter
    ↓
Arquivo .vm
```

### 5.1. Tabela de símbolos

As declarações são organizadas em quatro categorias:

| Categoria Jack | Segmento VM |
|---|---|
| `static` | `static` |
| `field` | `this` |
| `argument` | `argument` |
| `var` | `local` |

A tabela mantém escopos de classe e sub-rotina, com índices independentes por categoria.

Cada sub-rotina recebe um novo escopo. Nos métodos, `argument 0` é reservado à referência do objeto receptor.

A resolução procura primeiro a sub-rotina e depois a classe.

### 5.2. Sub-rotinas e objetos

O compilador gera:

- `function Classe.rotina nLocals`;
- inicialização de `this` para métodos;
- alocação de objetos com `Memory.alloc` para construtores;
- passagem do receptor em chamadas de métodos;
- argumentos explícitos em chamadas de funções e construtores;
- retorno de valores e retorno `void` com valor convencional zero.

Chamadas a bibliotecas externas, como o Jack OS, são permitidas sem exigir que suas fontes estejam presentes no mesmo diretório.

### 5.3. Expressões, arrays e comandos

A geração contempla constantes, variáveis, strings, operações aritméticas e lógicas, chamadas de sub-rotinas e expressões aninhadas.

Multiplicação e divisão utilizam `Math.multiply` e `Math.divide`. Strings utilizam `String.new` e `String.appendChar`.

Arrays são traduzidos mediante cálculo de endereço e uso de `pointer 1` e `that 0`.

Os comandos `let`, `do`, `return`, `if`, `if-else` e `while` são traduzidos para instruções da VM, com rótulos de controle de fluxo.

O comando `do` descarta o valor retornado pela chamada.

### 5.4. Limites semânticos

O compilador verifica inconsistências identificáveis a partir da classe e de suas declarações, incluindo símbolos não resolvidos, duplicações e usos incompatíveis com o contexto da sub-rotina.

Não implementa inferência global de tipos, otimização de código VM ou análise interclasses completa.

As dependências externas conhecidas pelo código Jack podem ser referenciadas sem validação integral de suas implementações.

## 6. Arquitetura do projeto

O código de produção está organizado em onze pacotes:

```text
src/main/java/jackcompiler/
├── application/  # tokenize, parse e compile
├── cli/          # argumentos, comandos e diagnósticos
├── compiler/     # tradução da CST em instruções VM
├── io/           # descoberta, leitura, destinos e publicação
├── lexer/        # reconhecimento léxico
├── parser/       # análise sintática descendente recursiva
├── symbol/       # símbolos e escopos
├── syntax/       # árvore sintática concreta imutável
├── token/        # tipos, valores e posições
├── vm/           # emissão das instruções VM
└── xml/          # serialização XML
```

A arquitetura conserva a independência do scanner e do parser.

`TokenizeCommand`, `ParseCommand` e `CompileCommand` são casos de uso separados que reutilizam os componentes necessários.

A fachada `JackVmCompiler` recebe um `GrammarNode` e retorna texto VM. Ela não depende da CLI, do sistema de arquivos ou da serialização XML.

Os contratos e as dependências são detalhados em [docs/architecture.md](docs/architecture.md).

## 7. Testes e validação oficial

O projeto utiliza testes unitários, de integração, do JAR executável, de contratos públicos e de aceitação oficial.

### 7.1. Compatibilidade das Partes 1 e 2

Os testes preservam a compatibilidade com os programas oficiais:

- `Square`;
- `ExpressionLessSquare`;
- `ArrayTest`.

Os XMLs léxicos são comparados com referências `*T.xml`. Os XMLs sintáticos são comparados estruturalmente com referências oficiais `*.xml`, preservando hierarquia, ordem e conteúdo significativo dos terminais.

### 7.2. Aceitação da Parte 3

A geração VM foi testada com os seis programas oficiais do Project 11:

| Programa | Fontes Jack | Resultado observado no VM Emulator |
|---|---:|---|
| Seven | 1 | Impressão do valor 7 |
| Average | 1 | Entradas 4, 6 e 8; média 6 |
| ConvertToBin | 1 | Representação correta de 13 em 16 bits |
| ComplexArrays | 1 | Resultados 5, 40, 0, 77 e 110 |
| Square | 3 | Desenho, movimentação, tamanho e saída |
| Pong | 4 | Jogo, controles, colisões, placar e Game Over |

Os seis programas foram compilados pelo JackCompiler e executados manualmente no Virtual Machine Emulator 2.5.

Os testes automatizados de aceitação verificam a geração VM, mas não simulam a execução do emulador.

Os materiais oficiais provêm de [nand2tetris/projects](https://github.com/nand2tetris/projects), revisão:

```text
6097b770073025891fb8b7a22aa3b72f61f81277
```

A licença MIT correspondente está preservada em `src/test/resources/nand2tetris/LICENSE`.

### 7.3. Resultados automatizados

A execução local de `mvn clean verify` em 10/11/2026, antes do fechamento documental, apresentou:

| Verificação | Resultado |
|---|---|
| Spotless | Aprovado |
| Surefire | 1.799 testes aprovados |
| Failsafe | 64 testes aprovados |
| **Total** | **1.863 testes aprovados** |
| Falhas e erros | 0 |
| Maven | `BUILD SUCCESS` |

Os resultados correspondem ao estado funcional do P3-24, commit [`0d389be`](https://github.com/0zzmandias/jackcompiler/commit/0d389be81a2876ca7ac804085b49493104a96886).

### 7.4. Integração contínua

O repositório possui um workflow GitHub Actions com Java 21 e Maven.

Durante a Parte 3, o início dos jobs foi impedido por um bloqueio administrativo de cobrança da conta GitHub (*billing lock*). A situação foi encaminhada ao suporte da plataforma.

Esse bloqueio ocorre antes da execução dos testes e não constitui evidência de falha no código Java. A validação local foi realizada e aprovada independentemente dessa restrição.

A execução da integração contínua sobre o snapshot final permanece pendente até que o serviço esteja disponível.

A estratégia de testes está em [docs/testing.md](docs/testing.md) e a matriz de requisitos está em [docs/acceptance.md](docs/acceptance.md).

## 8. Desafios de implementação

A evolução do JackCompiler exigiu preservar as duas entregas anteriores e acrescentar geração de código sem introduzir uma segunda análise léxica ou sintática.

Os principais desafios foram:

- **Reutilização da CST:** interpretar a árvore existente sem convertê-la em XML nem duplicar o parser.
- **Escopos e objetos:** mapear variáveis para segmentos VM, reservar o receptor de métodos e alocar construtores corretamente.
- **Chamadas de sub-rotinas:** distinguir funções, construtores e métodos, inclusive declarações posteriores na mesma classe.
- **Arrays:** calcular endereços indiretos e preservar os valores durante leituras e atribuições.
- **Fluxo de controle:** gerar rótulos determinísticos em estruturas condicionais e laços aninhados.
- **Proteção das fontes:** implementar descoberta recursiva e novos destinos sem alterar o comportamento dos comandos antigos.
- **Aceitação comportamental:** validar programas oficiais reais no VM Emulator, além da geração textual de instruções.

As decisões detalhadas encontram-se em [docs/architecture.md](docs/architecture.md).

## 9. Marcos Git

As entregas compartilham o mesmo histórico Git.

| Entrega | Referência | Situação |
|---|---|---|
| Parte 1 — Lexer | [`v1.0.0-lexer`](https://github.com/0zzmandias/jackcompiler/tree/v1.0.0-lexer) | Concluída |
| Parte 2 — Parser | [`v2.0.0-parser`](https://github.com/0zzmandias/jackcompiler/tree/v2.0.0-parser) | Concluída |
| Parte 3 — VM | Commit funcional [`0d389be`](https://github.com/0zzmandias/jackcompiler/commit/0d389be81a2876ca7ac804085b49493104a96886) | Incrementos P3-01 a P3-24 concluídos |

A tag `v3.0.0-vm` será criada somente depois do fechamento documental, da auditoria final e da aprovação do snapshot correspondente.

## 10. Uso de Inteligência Artificial

Foi utilizado o **ChatGPT-6, da OpenAI**, como ferramenta de apoio ao desenvolvimento do projeto.

O apoio recebido incluiu:

- esclarecimento de especificações e decisões arquiteturais;
- planejamento incremental e organização de responsabilidades;
- elaboração e revisão de propostas de código e testes;
- análise de resultados de compilação e logs;
- investigação de defeitos e avaliação de correções;
- orientação dos testes oficiais no VM Emulator;
- estruturação e revisão da documentação técnica.

A utilização de IA não substituiu a validação do projeto. O desenvolvedor executou os comandos no ambiente local, realizou os testes automatizados, verificou o funcionamento dos programas oficiais e revisou os incrementos antes de seus commits.

A responsabilidade pela compreensão, integração, validação e entrega do projeto permanece com o autor identificado neste README.
