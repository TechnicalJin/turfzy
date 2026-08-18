# Turfzy

Turfzy is a sports turf booking platform. It provides turf discovery, time-slot management, customer bookings, owner workflows, authentication, real-time updates, and administrative approval flows.

## Project Structure

```text
.
├── turfzy-backend/     Spring Boot REST API
├── turfzy-frontend/    React + Vite web application
├── booking-request.json Example booking request
├── create-turf.json    Example turf creation request
├── login-admin.json    Example admin login request
├── login-owner.json    Example owner login request
├── register-customer.json
├── register-owner.json
└── turfzy-backend/sql/ Database schema and seed SQL
```

## Technology Stack

### Backend

- Java 17
- Spring Boot 3.2.4
- Spring MVC and Spring Data JPA
- Spring Security with JWT and Google OAuth2
- MySQL 8
- Redis 7 with Redisson
- WebSocket messaging
- Cloudinary image storage
- Maven

### Frontend

- React 19
- TypeScript 6
- Vite 8
- React Router
- TanStack Query
- Zustand
- Axios
- Tailwind CSS
- React Hook Form and Zod
- STOMP/SockJS for real-time communication

## Prerequisites

Install the following before running the project:

- Java 17 or later
- Docker Desktop with Docker Compose
- Node.js 20 or later and npm
- Git

## Local Development

### 1. Start infrastructure

From the repository root:

```bash
cd turfzy-backend
docker compose up -d
```

This starts:

| Service | Address | Purpose |
| --- | --- | --- |
| MySQL | `localhost:3307` | Application database |
| Redis | `localhost:6379` | Caching and distributed locking |
| Redis Commander | `http://localhost:8081` | Development Redis UI |

The MySQL container creates the `turfzy_db` database and loads `sql/init.sql` the first time its data volume is created.

### 2. Run the backend

In `turfzy-backend`:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell or Command Prompt, use:

```powershell
.\mvnw.cmd spring-boot:run
```

The API is available at `http://localhost:8080`.

Verify that it is running:

```text
GET http://localhost:8080/api/health
```

### 3. Run the frontend

In a second terminal:

```bash
cd turfzy-frontend
npm install
npm run dev
```

The Vite development server is normally available at `http://localhost:5173`.

## Configuration

The backend uses the `dev` Spring profile by default. Local database and Redis settings are defined in:

- `turfzy-backend/src/main/resources/application.yml`
- `turfzy-backend/src/main/resources/application-dev.yml`

Before deploying or sharing this repository:

1. Move JWT, OAuth2, Cloudinary, database, and other credentials to environment variables or a secret manager.
2. Rotate any credentials that have previously been committed to configuration files.
3. Use a production profile with schema validation instead of `ddl-auto: update`.
4. Restrict CORS origins to the deployed frontend URL.

The frontend API base URL is configured in `turfzy-frontend/src/api/axiosClient.ts`.

## API Overview

The backend currently exposes routes in the following areas:

### Authentication

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/refresh`

### Turfs

- `GET /api/turfs`
- `GET /api/turfs/{id}`
- `POST /api/owner/turfs`
- `PUT /api/owner/turfs/{id}`
- `GET /api/owner/turfs`
- `POST /api/owner/turfs/{id}/images`
- `DELETE /api/owner/turfs/{turfId}/images/{imageId}`
- `POST /api/admin/turfs/{id}/approve`
- `POST /api/admin/turfs/{id}/reject`
- `GET /api/admin/turfs/pending`

### Slots

- `GET /api/slots/{turfId}`
- `GET /api/slots/{turfId}/available`
- `GET /api/slots/{turfId}/week`
- `POST /api/owner/slots/{slotId}/block`
- `POST /api/owner/slots/{slotId}/unblock`
- `POST /api/owner/turfs/{turfId}/generate-slots`

### Bookings

- `POST /api/bookings`
- `GET /api/bookings`
- `GET /api/bookings/{id}`
- `GET /api/bookings/reference/{ref}`
- `POST /api/bookings/{id}/cancel`
- `GET /api/owner/bookings`

### Real-time and diagnostics

- `GET /api/health`
- `POST /api/ws-test/broadcast/{slotId}`

Request examples are available in the JSON files at the repository root. Authenticated routes require the access token returned by login or registration, usually sent as:

```http
Authorization: Bearer <access-token>
```

## WebSocket

The backend includes WebSocket support for real-time booking and slot updates. The frontend uses STOMP over SockJS. Check the realtime package and frontend API/realtime code for the current connection and destination configuration.

## Commands

### Backend

```bash
cd turfzy-backend
.\mvnw.cmd test
.\mvnw.cmd clean package
```

### Frontend

```bash
cd turfzy-frontend
npm run lint
npm run build
npm run preview
```

## Database Reset

To remove local database and Redis data and recreate the services:

```bash
cd turfzy-backend
docker compose down -v
docker compose up -d
```

The `-v` option deletes the Docker volumes, including local application data.

## Roadmap

The planned development phases are documented in [Plan.md](Plan.md). They cover authentication, turf and booking workflows, payments, dashboards, real-time features, caching, notifications, testing, and production deployment.

## License

No license has been specified yet.
