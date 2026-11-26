# Product service API contract

Base path `/api`. Product reads are public (and work for authenticated USER/ADMIN JWTs).
Management writes require `ROLE_ADMIN` in a valid HS256 JWT signed with the same `JWT_SECRET`
as user-service. Role and subject are read only from validated JWT claims. `ProductResponse`
contains `{id,name,description,price,category,imageUrl,stock,available,active,createdAt}`.

## Public catalog and order compatibility

* `GET /products?q=&category=&sort=` returns a JSON array (legacy shape preserved). `sort`
  accepts `name,asc` (default), `price,asc`, or `price,desc`. Only active catalog entries are
  returned; availability is false when disabled or stock is zero.
* `GET /products/{id}` returns the product or `404` when missing/deactivated. Existing order
  service calls remain compatible and receive the original `id,name,description,price,createdAt`
  fields with additional catalog and stock fields.
* `GET /products/page?page=0&size=20&sort=name,asc` returns `{content,number,size,totalElements,totalPages}`;
  size is limited to 100.

## Admin management

* `POST /products` — `201`; request `{name,description,price,category,imageUrl,stock,available}`.
  Category defaults to `General`, stock to zero and availability to true for old request bodies.
* `PUT /products/{id}` — replace editable product fields.
* `DELETE /products/{id}` — safely deactivates the product; it is excluded from browsing and
  cannot receive new reservations. Existing rows and order references remain intact.
* `PATCH /products/{id}/stock` — `{quantity}` sets stock (including zero).
* `PATCH /products/{id}/availability` — `{available:boolean}`.
* `GET /products/admin/low-stock?threshold=5` — active products at or below threshold.

## Trusted order-service API

Internal endpoints require `X-Service-Token: <SERVICE_API_KEY>`. Use a unique UUID in
`Idempotency-Key` for each order line reservation.

* `POST /internal/products/{id}/reservations` with `{quantity}` atomically decrements stock
  only when available stock is sufficient. Returns `{reservationId,status}`; insufficient or
  unavailable stock returns `409`. Repeating the same key and payload returns the prior result.
* `POST /internal/products/reservations/{reservationId}/commit` marks a successful order
  committed. Repeated commits are safe.
* `POST /internal/products/reservations/{reservationId}/release` restores stock once for a
  failed order. Repeated releases are safe.

Order placement spans separate databases and therefore is a reservation/compensation flow,
not a distributed ACID transaction: reserve stock, persist the order, commit the reservation;
on order persistence failure release it. Expired `RESERVED` records require a future recovery
job if process crashes occur between reservation and settlement.

## Initialization and configuration

At first startup a transaction seeds 15 items with stable `seedKey` values and persists the
`default-catalog-v1` marker. Admin edits survive restart; existing products are not overwritten.
Hibernate `ddl-auto=update` adds product metadata/version, catalog marker and reservation tables.

* `JWT_SECRET`: shared random secret, at least 32 bytes. Do not use the local development
  default in production; Kubernetes loads it from `shopsphere-app-secrets/JWT_SECRET`.
* `SERVICE_API_KEY`: random internal-only shared service credential; Kubernetes loads it from
  `shopsphere-app-secrets/SERVICE_API_KEY`.
