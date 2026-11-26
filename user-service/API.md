# User service API contract

Base path: `/api`. JSON is used for request and response bodies. Protected requests use
`Authorization: Bearer <accessToken>`. Tokens are HS256 JWTs containing `sub` (user id),
`role`, `iat`, and `exp`; the service re-reads the account and role for every request.
Blocked or deleted accounts therefore cannot continue using old tokens.

## Authentication

| Method and path | Access | Request | Response |
|---|---|---|---|
| `POST /auth/register` | Public | `{name,email,password}`; password 10–72 chars | `201 UserResponse` |
| `POST /auth/login` | Public | `{email,password}` | `200 {accessToken,tokenType,expiresIn,user}` |
| `POST /auth/logout` | Authenticated | none | `204`; clients discard the bearer token |

Registration always creates a `USER`; role is never accepted from a request. Login returns
`401` for unknown, blocked, or invalid-password accounts. Duplicate email returns `409`.

## Profile

| Method and path | Access | Request | Response |
|---|---|---|---|
| `GET /users/me` | USER or ADMIN | none | `200 UserResponse` |
| `PUT /users/me` | USER or ADMIN | `{name,email}` | `200 UserResponse` |
| `PUT /users/me/password` | USER or ADMIN | `{currentPassword,newPassword}` | `204` |

Profile DTOs do not contain role or blocked fields; users cannot edit either property. An
email conflict returns `409`; invalid bodies return `400`.

## Administration

Requires `ROLE_ADMIN` on every endpoint.

| Method and path | Request | Response |
|---|---|---|
| `GET /users?q=<text>` | optional name/email substring | `200 UserResponse[]` |
| `GET /users/{id}` | none | `200 UserResponse`, `404` if absent |
| `PUT /users/{id}` | `{name,email}` | `200 UserResponse` |
| `PATCH /users/{id}/status` | `{blocked:boolean}` | `200 UserResponse` |
| `DELETE /users/{id}` | none | `204`; self-delete returns `409` |

`UserResponse` is `{id,name,email,role,blocked,createdAt}`. Password hashes are never
serialized. Missing/invalid authentication returns `401`; authenticated non-admin access
returns `403`.

## Configuration

* `JWT_SECRET`: random secret of at least 32 bytes; use the same value in services that
  validate user tokens. The local default is for development only.
* `JWT_TTL_SECONDS`: token lifetime; defaults to 3600 seconds.
* `SHOPSPHERE_ADMIN_NAME`, `SHOPSPHERE_ADMIN_EMAIL`, `SHOPSPHERE_ADMIN_PASSWORD`: set
  together to provision the initial admin; password must be at least 10 characters. The
  account is inserted only when that email is absent, and the password is BCrypt-hashed.

Trusted backend services use `X-Service-Token: <SERVICE_API_KEY>` for the internal endpoints below.
The key must be configured only on backend services and never exposed to browsers. These responses
are intentionally minimal; customer details are available only to authenticated internal callers.

| Method and path | Request | Response |
|---|---|---|
| `GET /internal/users/{id}/exists` | `X-Service-Token` header | `{exists,active}`; missing users return `{false,false}` |
| `GET /internal/users/{id}` | `X-Service-Token` header | `{id,name,email,active}`; `404` if absent |

`active` is false for blocked accounts. Public user DTOs and internal responses must not be used
to bypass the JWT authorization checks on each service.
