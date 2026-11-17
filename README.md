# GateKeeper

Multi-service Java 21 / Spring Boot API gateway project.

- **api-management-service** — MySQL-backed tenants, roles, JWT authentication, API routes, configuration versions and audit records (port 8081).
- **smart-gateway-service** — Gateway request processing.
- **analytics-service** — Analytics pipeline.
- **frontend** — React + TypeScript administration console (port 5173).

## Start

Run `ApiManagementServiceApplication` in IntelliJ after configuring MySQL and applying Flyway migrations. Then:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. See [frontend/README.md](frontend/README.md) for details.
