
# JackCompiler

Projeto acadêmico da disciplina de Compiladores para a linguagem Jack, utilizada no curso nand2tetris.

## Integrante

**Helton Alves Sá**  
Matrícula: 20260009631

## Sobre o projeto

O JackCompiler será desenvolvido em duas entregas:

- **Parte 1 — Analisador Léxico:** reconhecimento dos tokens da linguagem Jack, tratamento de espaços e comentários e geração de XML para validação.
- **Parte 2 — Analisador Sintático:** reconhecimento da gramática Jack e geração de árvores sintáticas em XML, reutilizando o analisador léxico da primeira entrega.

## Tecnologias

- Java 21
- Apache Maven
- JUnit Jupiter
- Spotless (Google Java Style)
- Git e GitHub Actions

## Construção e testes

Pré-requisitos:

- JDK 21
- Apache Maven

Na raiz do projeto:

```bash
mvn clean verify
```

O comando executa a verificação de formatação, a compilação e os testes automatizados.

Para aplicar a formatação Java:

```bash
mvn spotless:apply
```

A integração contínua utiliza GitHub Actions.

## Estado do desenvolvimento

**Parte 1:** infraestrutura inicial em desenvolvimento.

**Parte 2:** implementação ainda não iniciada.

Os comandos da aplicação e os resultados de validação serão documentados conforme as funcionalidades forem implementadas.
