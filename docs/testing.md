# Estratégia de testes

## Strategy

A suíte favorece feedback rápido sem substituir provas de infraestrutura por mocks:

* **Unit** — invariantes de `Event` e `Reservation`, limites temporais, fingerprint e contrato JSON dos eventos, sem
  Spring;
* **Application** — decisões dos casos de uso com ports substituídos, incluindo replay e conflito idempotente;
* **API** — os cinco endpoints, validação e RFC 9457 `ProblemDetail` com MockMvc isolado;
* **Architecture** — ArchUnit protege a direção das dependências, controllers e injeção por construtor;
* **Integration/Repository** — PostgreSQL real, Flyway, atomicidade de negócio/Outbox e queries específicas;
* **Concurrency** — transações e conexões independentes exercitam contenção real no PostgreSQL;
* **Messaging** — serialização, retry do publisher, Inbox, ordering e projeção. Os testes unitários isolam falhas
  determinísticas; os testes PostgreSQL não fingem transacionalidade com mocks.

Testes `*Test` pertencem à suíte rápida do Surefire. Testes `*IT` pertencem ao Failsafe e são executados por `verify`.
Containers são compartilhados dentro de cada classe, nunca criados por método.

## Commands

```bash
./mvnw clean test
./mvnw clean verify
```

Java 21 é necessário fora do container. O Maven Wrapper versionado baixa Maven 3.9.11 na primeira execução. A suíte
`verify` requer um daemon Docker acessível ao Testcontainers e não requer o Compose rodando.
Quando Docker não está disponível, testes marcados com `disabledWithoutDocker` são ignorados em vez de usar H2.

## Testcontainers e Flyway

Os testes de persistência usam `postgres:17-alpine`. O contexto Spring executa exatamente as migrations de
`src/main/resources/db/migration`; não existe schema alternativo de teste. A dependência Kafka Testcontainers está
preparada, mas a suíte atual ainda não contém um teste broker end-to-end — veja **Lacunas conhecidas**.

## Concorrência

`ReservationConcurrencyIT` comprova, consultando o banco autoritativo:

* 100 tentativas para capacidade 10, repetidas em três rodadas, resultam em exatamente 10 reservas ativas;
* quantidades variáveis preservam `available + active = capacity` e nunca tornam disponibilidade negativa;
* 50 chamadas simultâneas da mesma chave criam uma reserva e consomem capacidade uma vez;
* cancelamentos concorrentes liberam capacidade e emitem evento terminal uma vez;
* expiradores concorrentes, cancelamento versus expiração e expiração versus novas reservas preservam single-winner;
* uma exceção externa antes do commit reverte capacidade, reserva, idempotência e Outbox;
* entrega duplicada atualiza Inbox/projeção uma única vez.

Os testes usam barreira de início e futures com timeout, sem sleeps nem suposições sobre qual concorrente vence.
A expiração considera vencido `expiresAt <= now`; os testes unitários cobrem antes e exatamente no instante limite.

## Load test

Crie um evento, copie seu UUID e execute sem instalar k6 localmente:

```bash
EVENT_ID=<uuid> VUS=100 DURATION=30s QUANTITY=1 \
  docker compose --profile load-test run --rm k6
```

O cenário aceita `201` e o esgotamento esperado `422`; qualquer outro status alimenta `unexpected_errors`. k6 não é
uma prova de correção e nenhum resultado de benchmark é versionado sem uma execução observada.

## Coverage

`mvn verify` gera o relatório JaCoCo em `target/site/jacoco/index.html`. Não há threshold artificial nesta fase:
primeiro
é preciso observar o relatório completo num ambiente com Docker e aumentar testes por risco, não por getters. Serviços,
adapters, concorrência e idempotência não são excluídos.

## Lacunas conhecidas

* falta um teste end-to-end com Kafka real cobrindo producer, consumer, retry e DLT;
* o teste OpenAPI em contexto completo e a validação automatizada de todos os paths ainda não existem;
* Outbox multi-worker e Inbox concorrente precisam de provas dedicadas além dos cenários atuais.
