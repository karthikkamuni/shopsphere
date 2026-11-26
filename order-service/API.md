# Order service API contract

All `/api` order and cart requests require `Authorization: Bearer <JWT>` using the same
HS256 `JWT_SECRET` as user-service. User identity and role come only from validated `sub` and
`role` claims. The service checks current account existence/block state through user-service
on every operation with `GET /internal/users/{id}/exists` and `X-Service-Token`.

## Cart (USER or ADMIN, cart is scoped to JWT subject)

* `POST /api/cart/items` — `{productId,quantity}`; adds quantity, validates current product
  availability and stock; returns cart (`201`).
* `GET /api/cart` — lines include current product details, unit price, subtotal, availability,
  and total. Prices are informational and are fetched from product-service.
* `PUT /api/cart/items/{productId}` — `{quantity}` replaces quantity.
* `DELETE /api/cart/items/{productId}` and `DELETE /api/cart` remove line(s) (`204`).

## Checkout and order history

* `POST /api/orders/checkout` — checks out the caller's persisted cart. Requires UUID
  `Idempotency-Key`. Returns the created order and immutable item snapshots (`201`).
* Legacy `POST /api/orders` remains supported with `{userId,productId,quantity}` and optional
  UUID `Idempotency-Key`. `userId` must equal the JWT subject. Price and name are read from
  product-service, never from the request.
* `GET /api/orders?page=0&size=20&status=PLACED` — USER sees only own orders; ADMIN sees all.
  ADMIN may additionally filter by `userId`.
* `GET /api/orders/{id}` — owner or ADMIN only.
* `PATCH /api/orders/{id}/cancel` — owner or ADMIN; eligible before shipment.
* `PATCH /api/orders/{id}/status` — ADMIN only, body `{status}`.
* `GET /api/admin/dashboard` — ADMIN only; counts, delivered revenue, and recent orders.

ADMIN order detail, list, and dashboard responses include a minimal nullable
`customer:{id,name,email,active}` loaded from the trusted user-service endpoint. USER responses set
`customer` to `null`. Deleted customer records also produce `customer:null`; transient lookup failures return
`503` rather than inventing customer data.

`OrderResponse` keeps legacy fields (`id,userId,productId,quantity,unitPrice,totalPrice,status,createdAt`)
and adds `items[]` with `{id,productId,productName,unitPrice,quantity,subtotal}`. New statuses
are `PLACED`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`. Valid transitions:
PLACED→CONFIRMED/CANCELLED, CONFIRMED→PROCESSING/CANCELLED, PROCESSING→SHIPPED,
SHIPPED→DELIVERED. Terminal states cannot transition.

## Reservation saga

For each line, order-service calls the product internal reservation endpoint using a fresh UUID
reservation key per checkout attempt. It persists the order, snapshots, reservation IDs,
and cart deletion in one local transaction, then commits each product reservation. If a reserve
fails, earlier reservations are released. If local persistence fails, all acquired reservations
are released, and retry may reserve with new keys. If commit fails after persistence, the order
remains and a repeated checkout with the same idempotency key retries commit without creating
another order. This is a saga across
independent databases, not a distributed transaction. A scheduled order-service reconciliation retries
commit for persisted, non-cancelled orders and release for cancelled orders. product-service also reaps
unsettled stock reservations after the one-hour lease; conditional reservation state changes ensure a
retry or compensation cannot restore stock twice. A reservation that remains unsettled past the lease
can be released, so order-service recovery should remain operational within that window.

## Configuration / schema

* `JWT_SECRET`: shared 32+ byte HS256 secret; Kubernetes reads `shopsphere-app-secrets/JWT_SECRET`.
* `SERVICE_API_KEY`: internal user/product service token; Kubernetes reads
  `shopsphere-app-secrets/SERVICE_API_KEY`.
* `SERVICES_USER_URL`, `SERVICES_PRODUCT_URL`: service base URLs.

Hibernate update creates `cart_items`, `order_items`, and extends `orders` with idempotency,
status and timestamps. Product names/prices are stored on each order item, independent of later
catalog edits. User/product IDs remain scalar references; databases are independently owned.
