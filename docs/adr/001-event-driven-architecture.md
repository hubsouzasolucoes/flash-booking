# ADR-001: Monólito modular orientado a eventos

## Status

Accepted

## Context

É necessário desacoplar projeções sem criar microservices artificiais.

## Decision

Manter um monólito modular: PostgreSQL é autoritativo e eventos confirmados alimentam integrações assíncronas.

## Consequences

Deploy continua simples e módulos podem escalar em várias instâncias; leituras projetadas são eventualmente
consistentes.

## Alternatives Considered

Microservices foram rejeitados por custo operacional sem benefício atual; chamadas síncronas não atendem tolerância a
falhas.
