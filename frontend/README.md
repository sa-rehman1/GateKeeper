# GateKeeper Console

React + TypeScript management UI for GateKeeper's Spring Boot API Management Service.

## Run locally

Requires Node.js 22+ and the API Management Service running on port 8081 with MySQL configured.

```bash
cd frontend
npm install
npm run dev
```

Visit http://localhost:5173. The UI uses the existing `/api/auth/login` endpoint and all requests to the backend use JWT Bearer authentication.

Run `npm run build` to type-check and build production assets.

Copy `.env.example` to `.env` to change `VITE_API_BASE_URL` (default: http://localhost:8081).

The management service allows http://localhost:5173 and http://127.0.0.1:5173 via Spring Security CORS by default. Edit `app.cors.allowed-origins` when deploying to another frontend origin.

Platform administrators can manage organizations and tenant admins. Tenant administrators can manage routes, inspect version history and audit records, and update their organization. Gateway service accounts are not allowed to use the browser console.

The UI uses actual backend data (no mock API responses). Tokens are kept in sessionStorage and cleared at expiration or sign-out. The backend remains responsible for authorization.

The console covers the management APIs implemented to date. Smart Gateway and Analytics UI features can be added when their backend APIs are available.

## Visual design

Industrial Control Room: process-instrumentation inspired layout with graphite surfaces, volt-lime control states, IBM Plex Sans Condensed typography and JetBrains Mono operational labels. The UI keeps the existing management API endpoints and permissions unchanged.
