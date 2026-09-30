# ADR-004: CQRS somente para disponibilidade

## Situação

Aceita

## Contexto

A consulta de disponibilidade é quente, mas não pode enfraquecer zero overselling.

## Decisão

`GET /eventos/{id}` lê projeção assíncrona. Os comandos usam exclusivamente o estado autoritativo e a atualização condicional.

## Consequências

Leitura escala e pode estar atrasada. Inbox e versão impedem duplicação/regressão.

## Alternativas consideradas

Usar a projeção para conceder ingressos foi rejeitado; duplicar todo o modelo também.
