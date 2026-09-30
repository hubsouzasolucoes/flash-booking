# ADR-001: Monólito modular orientado a eventos

## Situação

Aceita

## Contexto

É necessário desacoplar projeções sem criar microservices artificiais.

## Decisão

Manter um monólito modular: PostgreSQL é autoritativo e eventos confirmados alimentam integrações assíncronas.

## Consequências

Deploy continua simples e módulos podem escalar em várias instâncias; leituras projetadas são eventualmente
consistentes.

## Alternativas consideradas

Microservices foram rejeitados por custo operacional sem benefício atual; chamadas síncronas não atendem tolerância a
falhas.
