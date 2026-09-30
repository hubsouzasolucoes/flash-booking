# Arquitetura

O sistema é um monólito modular executável em múltiplas instâncias. `interfaces` adapta HTTP, `application` coordena
casos de uso e transações, `domain` contém regras sem dependências de framework, e `infrastructure` implementa
PostgreSQL, Kafka, Outbox, Inbox e schedulers. A direção permanece `interfaces -> application -> domain`.

## Fluxos

* **Escrita:** REST → caso de uso → estado relacional autoritativo + evento na Outbox, na mesma transação PostgreSQL.
* **Assíncrono:** reivindicação da Outbox → Kafka → consumidor → Inbox + projeção, na mesma transação PostgreSQL do consumidor.
* **Leitura:** REST → `event_availability_projection`, eventualmente consistente.

A capacidade é concedida apenas pelo update condicional do write model. Kafka e a projeção nunca participam da decisão
anti-oversell. Idempotência HTTP, identificador do evento, versão do agregado e Inbox são mecanismos distintos.

Esta fase escolhe estado relacional autoritativo e eventos de domínio, **sem Event Sourcing e sem Event Store**. Outbox
é uma fila transacional; Kafka é transporte/retenção operacional. Nenhum deles é apresentado como Event Store.

Detalhes operacionais, garantias e recuperação estão em [event-driven-architecture.md](event-driven-architecture.md), e
as decisões resumidas em [adr](adr/).

Os mecanismos e provas para concorrência estão detalhados em [concurrency.md](concurrency.md) e no
[ADR 006](adr/006-concurrency-control.md).

## Fluxo dos componentes

```mermaid
flowchart TD
  HTTP[Controllers HTTP] --> APP[Casos de uso da aplicação]
  APP --> DOMAIN[Objetos de domínio]
  APP --> PORTS[Portas de repositório / Outbox]
  PORTS --> DB[(Modelo de escrita PostgreSQL + Outbox)]
  DB --> PUB[Publicador da Outbox]
  PUB --> K[Kafka]
  K --> CON[Consumidor da projeção]
  CON --> IN[(Inbox + availability projection)]
  HTTP -->|consulta de evento| IN
```

A infraestrutura depende internamente das portas da aplicação e dos tipos de domínio; o domínio não importa Spring,
JPA, Kafka nem HTTP. O pacote `interfaces` traduz os contratos públicos de transporte, e a configuração conecta os
adaptadores.

## Sequência de reserva

```mermaid
sequenceDiagram
  participant Cliente
  participant API
  participant UseCase as Reservation use case
  participant PG as PostgreSQL
  participant Publisher as Publicador da Outbox
  participant Kafka
  participant Consumer
  participant Projection
  Cliente->>API: POST reserva + Idempotency-Key
  API->>UseCase: create(eventId, quantity, key)
  rect rgb(235,245,255)
    Note over UseCase,PG: uma transação PostgreSQL
    UseCase->>PG: advisory lock + idempotency lookup
    UseCase->>PG: UPDATE condicional da capacidade
    UseCase->>PG: reserva + idempotência + Outbox INSERT
  end
  UseCase-->>Cliente: 201 reserva
  Publicador->>PG: reivindicação com SKIP LOCKED
  Publicador->>Kafka: evento versionado com chave
  Kafka->>Consumer: entrega pelo menos uma vez
  rect rgb(240,255,240)
    Note over Consumer,Projection: uma transação do consumidor
    Consumer->>PG: INSERT na Inbox
    Consumer->>Projection: atualização com verificação de versão
  end
```
