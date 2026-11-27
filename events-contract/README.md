# events-contract

The event contract shared by both services: the sealed `OrderEvent` and its two records,
plus the `OrderEventTypes` constants. `order-service` publishes these events to the
`order-events` topic through the oncebox outbox; `notification-service` consumes them
through the oncebox inbox and stores one notification per event. Nothing else lives here:
request and response records belong to the service that owns the API.

## Events

| Event type | Record | Published when |
|---|---|---|
| `order-created` | `OrderCreatedEvent` | an order is placed (`POST /api/v1/orders`) |
| `order-cancelled` | `OrderCancelledEvent` | an order is cancelled (`POST /api/v1/orders/{id}/cancel`) |

## On the wire

Each record on the `order-events` topic carries a JSON payload and three headers. The payload
is written by oncebox with the application's Jackson `ObjectMapper`: fields appear in record
declaration order, `Instant`s are ISO-8601 strings, `BigDecimal`s keep their scale, and `null`s
are included. The event id is not part of the payload: it travels in the headers and is the id
the consumer deduplicates on.

The examples below come from one run of the [quick start](../README.md#-quick-start): the
order from step 3 (customer `1001`, `sku-1` × 2 at `10.50` and `sku-2` × 1 at `5.25`,
`X-Correlation-Id: checkout-1`), then the cancel from step 5. Ids and timestamps differ on
every run; the shapes do not.

### `order-created`

```json
{"orderId":1,"customerId":1001,"lines":[{"productId":"sku-1","quantity":2,"price":10.50},{"productId":"sku-2","quantity":1,"price":5.25}],"total":26.25,"occurredAt":"2026-11-27T09:41:00.123456Z","correlationId":"checkout-1","traceParent":null}
```

| Header | Value |
|---|---|
| `oncebox_event_id` | `2d5c8a34-c4dd-4efc-a74f-98be448e22a6` |
| `oncebox_event_type` | `order-created` |
| `oncebox_event_payload_type` | `io.github.outbox.contract.OrderCreatedEvent` |

### `order-cancelled`

```json
{"orderId":1,"customerId":1001,"occurredAt":"2026-11-27T09:42:10.654321Z","correlationId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","traceParent":null}
```

| Header | Value |
|---|---|
| `oncebox_event_id` | `c33a791c-dfdc-4261-b8af-749d160504b9` |
| `oncebox_event_type` | `order-cancelled` |
| `oncebox_event_payload_type` | `io.github.outbox.contract.OrderCancelledEvent` |

The cancel request in the quick start sends no `X-Correlation-Id`, so the service generates
one; that generated UUID is the `correlationId` in the payload.

## Nullable fields

Only `traceParent` can be `null`: it holds the W3C `traceparent` of the request that caused the
event, or `null` when the request was not traced. Every other field is required — the records
reject `null` values (and empty `lines` for `order-created`).

## Seeing a real record

Run the quick start, place and cancel an order, then read the topic with headers:

```bash
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic order-events --from-beginning --property print.headers=true
```
