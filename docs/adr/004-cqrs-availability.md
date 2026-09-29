# ADR-004: CQRS somente para disponibilidade

## Status

Accepted

## Context
A consulta de disponibilidade é quente, mas não pode enfraquecer zero overselling.

## Decision
`GET /events/{id}` lê projeção assíncrona. Commands usam exclusivamente estado autoritativo e update condicional.

## Consequences
Leitura escala e pode estar atrasada. Inbox e versão impedem duplicação/regressão.

## Alternatives Considered
Usar a projeção para conceder ingressos foi rejeitado; duplicar todo o modelo também.
