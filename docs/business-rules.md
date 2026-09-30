# Regras de negócio

## Evento e capacidade

Um evento tem nome não vazio (com no máximo 160 caracteres na fronteira HTTP), horário de início futuro e capacidade
positiva. Ele começa com `available_tickets = capacity`. O PostgreSQL garante
`0 <= available_tickets <= capacity`, e a quantidade da reserva deve ser positiva. A capacidade é concedida somente
pelo `UPDATE` condicional do banco de dados autoritativo.

## Ciclo de vida da reserva

```mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> CANCELLED: cancelamento pelo cliente
  PENDING --> EXPIRED: expiresAt <= horário do worker
```

Uma reserva começa como `PENDING` e, por padrão, expira dez minutos após a criação (`RESERVATION_TTL` é configurável).
Somente reservas pendentes consomem capacidade. Repetir o cancelamento de uma reserva já cancelada é seguro; cancelar
uma reserva expirada retorna conflito. Cancelamento e expiração devolvem a capacidade exatamente uma vez.

## Idempotência

`POST /eventos/{id}/reservas` exige uma `Idempotency-Key` de até 160 caracteres. A impressão digital semântica é
`eventId + quantity`. Reutilizar a chave com a mesma impressão digital retorna a reserva original; reutilizá-la com
outra impressão digital retorna HTTP 409. Os registros são persistentes e compartilhados por todas as instâncias.

## Disponibilidade

Os comandos de reserva usam a linha transacional da tabela `events`. `GET /eventos/{id}` usa a projeção
`event_availability_projection`, atualizada de forma assíncrona por eventos de domínio versionados. Ela pode ficar
brevemente desatualizada ou retornar 404 logo após a criação do evento, mas nunca autoriza uma venda.
