# ADR-007: Estratégia de observabilidade local

## Situação

Aceita

## Contexto

Operadores precisam relacionar uma reserva HTTP à sua projeção assíncrona, diferenciar rejeição de negócio de falha do
sistema e avaliar PostgreSQL, Kafka, Outbox e consumidores. O exercício deve continuar executável localmente sem exigir
uma plataforma de monitoramento ou conta SaaS.

## Decisão

Usar Spring Boot Actuator para uma superfície limitada de saúde, informações e métricas; Micrometer para métricas de
negócio e pipeline de baixa cardinalidade; e MDC do SLF4J com mensagens estruturadas para investigação. Propagar um UUID
de correlação do HTTP, passando pelo envelope persistido, até o consumidor Kafka. A prontidão inclui PostgreSQL e Kafka;
a vivacidade representa apenas o processo. Expor o formato Prometheus como integração opcional, sem executar um servidor
Prometheus.

## Consequências

A aplicação responde às perguntas operacionais comuns em uma máquina local e fica pronta para um coletor externo. IDs
únicos permanecem nos logs, não nas tags de métricas. Uma consulta limitada aos metadados do Kafka torna a prontidão
significativa. A correlação explícita não fornece spans nem amostragem. Ao expor o serviço fora de uma rede confiável,
os responsáveis pelo deploy devem proteger os endpoints do Actuator.

## Alternativas consideradas

Uma stack obrigatória com Grafana, Prometheus, ELK ou Loki foi rejeitada por ser desproporcional. Um agente SaaS criaria
credenciais, custo e acoplamento a fornecedor. O tracing com OpenTelemetry foi adiado até que múltiplos serviços tornem
spans mais úteis que o ID de correlação atual.
