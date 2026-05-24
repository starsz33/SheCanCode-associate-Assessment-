# Idempotency Gateway — IgirePay Technologies

## Overview
This is a RESTful payment processing API built for IgirePay Technologies Ltd.
It implements an Idempotency Layer that ensures no customer is ever charged twice,
even when network timeouts cause clients to retry the same payment request multiple times.

## Architecture Diagram
![Idempotency Gateway Flow](diagram.svg)

## Flow Summary
Client sends POST /process-payment with Idempotency-Key header
↓
Key exists in database?
├── NO  → Save as processing → Wait 2s → Save response → Return 201 SUCCESS
└── YES →
├── Key expired (24hrs+)     → Return 410 EXPIRED
├── Different request body   → Return 409 CONFLICT
├── Still processing         → Wait → Return result
└── Same body               → Return cached response + X-Cache-Hit: true

Server starts on:http://localhost:8081

POST /api/process-payment

### Required Headers

| Key                 |Value |

| `Idempotency-Key`   | A unique string per transaction (e.g. UUID) |
| `Content-Type`      | `application/json` |

### Request Body

```json
{
  "amount": 100,
  "currency": "GHS"
}
```

### Response Body

```json
{
  "message": "Charged 100 GHS",
  "idempotencyKey": "key-001",
  "status": "SUCCESS"
}
```

### All Possible Responses

### 1. First payment processed
- Status Code: `201 Created`
- Status: `SUCCESS`
- Message: `Charged 100 GHS`

---

### 2. Duplicate request with same key and same body
- Status Code: `201 Created`
- Status: `ALREADY_PROCESSED`
- Message: `Charged 100 GHS`

---

### 3. Same key used with different request body
- Status Code: `409 Conflict`
- Status: `CONFLICT`
- Message: `Idempotency key already used for a different request body.`

---

### 4. Idempotency key expired after 24 hours
- Status Code: `410 Gone`
- Status: `EXPIRED`
- Message: `Idempotency key has expired. Please use a new key.`

---

### 5. Payment still processing
- Status Code: `202 Accepted`
- Status: `PROCESSING`
- Message: `Payment is still processing. Please try again shortly.`
### Special Response Header (Duplicate Requests)

When a duplicate request is detected the response includes:
X-Cache-Hit: true

## Design Decisions

### 1. H2 In-Memory Database
I chose H2 as the database because it requires zero setup and works immediately
when the project starts. In a production environment this would be replaced
with PostgreSQL or Redis for persistent storage.

### 2. Store Full Request Body
I store the entire request body as a string in the database.
This allows the system to detect fraud — if someone sends the same
idempotency key with a different amount, the system immediately
rejects it with a 409 Conflict error.

### 3. `isProcessing` Flag
I added an `isProcessing` boolean field to handle race conditions.
When a request arrives and is being processed, this flag is set to true.
If a duplicate request arrives during that window, it waits and polls
every 500ms until the first request finishes, then returns its result.
This prevents double charging in concurrent scenarios.

### 4. ObjectMapper for Request Comparison
I used Jackson's ObjectMapper to serialize the request body to a JSON string
before storing and comparing it. This ensures consistent comparison
regardless of field ordering in the incoming request.

## Developer's Choice Feature — Idempotency Key Expiry (410 Gone)

### What I Added
Idempotency keys automatically expire after **24 hours**.
If a client tries to reuse a key that is older than 24 hours,
the system returns a `410 Gone` response with the message:

### Why I Added This
In real-world fintech systems, allowing idempotency keys to live forever
creates serious risks:

1. **Security risk** — an attacker who intercepts an old key could
   attempt to replay old transactions indefinitely.

2. **Data integrity** — very old keys may refer to payment contexts
   that no longer exist or have changed (different pricing, currency rates).

3. **Storage management** — without expiry, the database grows indefinitely
   storing keys that will never be used again.

4. **Industry standard** — payment processors like Stripe expire
   idempotency keys after 24 hours for exactly these reasons.

By adding this feature the system behaves like a production-grade
payment processor and protects both the business and the customer.

## Author
**UWINGENEYE Esther**
Computer and Software Engineering final year Student
University of Rwanda
GitHub: [starsz33](https://github.com/starsz33)
