# 🧊 FridgeMate

A full-stack app for keeping track of what's in your fridge, freezer, pantry and bathroom cabinet — and making sure you never run out of the things you actually use.

Built with **Spring Boot** (Java) on the backend and **Angular** on the frontend.

## Screenshots

> _Coming soon._

| Inventory | Shopping List | Shopping Mode |
| --- | --- | --- |
| _screenshot placeholder_ | _screenshot placeholder_ | _screenshot placeholder_ |

## About

FridgeMate started as a simple fridge inventory tracker and grew into a small household-stock system:

- Register what you have, where it's stored, and when it expires.
- Set a minimum quantity you always want to have on hand for the products you use regularly (e.g. "always keep 3 packs of tortillas").
- See at a glance what's running low and needs restocking.
- Go through a dedicated **Shopping Mode** while you're actually at the store: check off what you're buying, then assign type, storage location and expiration date to everything in one batch when you get home.

## Features

- **Accounts** — register and sign in; each user only ever sees their own kitchen. Sessions are kept in a signed, HttpOnly cookie, so you stay signed in between visits without the token ever being exposed to JavaScript.
- **Inventory management** — full CRUD for ingredients/products with name, quantity, unit, type, storage location and optional expiration date.
- **Smart duplicate handling** — adding the same product (same name, unit, location and expiration date) automatically merges quantities instead of creating duplicate rows.
- **Expiring-soon alerts** — a banner on the inventory page flags anything expiring within 3 days.
- **Shopping list with stock targets** — track products you regularly buy along with the minimum quantity you want to keep in stock.
- **"What to buy" view** — automatically computed from your current inventory vs. your stock targets, with a one-click "Bought" button that restocks straight into inventory (reusing the last known type/location for that product).
- **Shopping Mode** — a two-step checkout-style flow for restocking: check off what's in the cart (including impulse buys not on the list) while shopping, then review and assign storage details for everything at once.
- **Filtering & sorting** — filter inventory by storage location, sort by expiration date.

## Tech stack

**Backend**
- Java 21, Spring Boot 4.1
- Spring Security with JWT in an HttpOnly cookie
- Spring Data JPA — embedded H2 locally, PostgreSQL in production
- Bean Validation (Jakarta Validation)
- Lombok

**Frontend**
- Angular 19 (standalone components, `@if`/`@for` control flow)
- TypeScript, RxJS
- Plain CSS with a small shared design system (CSS custom properties, no UI framework)

## Project structure

```
FridgeCalories/
├── Java/FridgeCalories/     # Spring Boot backend
│   └── src/main/java/org/example/fridgecalories/
│       ├── model/           # JPA entities & enums
│       ├── repository/      # Spring Data repositories
│       ├── service/         # Business logic
│       └── controller/      # REST controllers
└── Front/Angular/           # Angular frontend
    └── src/app/
        ├── components/      # Feature components (inventory, shopping list, shopping mode)
        ├── models/          # TypeScript interfaces matching backend DTOs
        └── services/        # HTTP clients for the API
```

## Getting started

### Prerequisites

- Java 21+
- Node.js 18+ and npm

No database server to install — the app runs on an embedded H2 database stored as a local file.

### Backend

1. From `Java/FridgeCalories/`:

   ```bash
   ./mvnw spring-boot:run
   ```

   The API starts on `http://localhost:8080`. The schema is created/updated automatically on startup (`ddl-auto=update`).

### Frontend

From `Front/Angular/`:

```bash
npm install
npm start
```

The app runs on `http://localhost:4200` and expects the API at `http://localhost:8080`.

## Deployment

The app is packaged as a single deployable — the Angular build is bundled into the Spring Boot jar,
so one service serves both the UI and the API on one URL, backed by managed PostgreSQL.

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for the full guide.

## API overview

All `/api/**` endpoints except `/api/auth/**` require a signed-in session, and only ever
return data belonging to that account.

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create an account and sign in |
| `POST` | `/api/auth/login` | Sign in |
| `POST` | `/api/auth/logout` | Sign out |
| `GET` | `/api/auth/me` | The current account, or 401 if signed out |
| `GET` | `/api/ingredients` | List ingredients (optional `location`, `direction` filters) |
| `GET` | `/api/ingredients/expiring` | Ingredients expiring within 3 days |
| `POST` | `/api/ingredients` | Add an ingredient (merges into an existing matching row) |
| `PUT` | `/api/ingredients/{id}` | Update an ingredient |
| `DELETE` | `/api/ingredients/{id}` | Remove an ingredient |
| `GET` | `/api/shopping-list` | List tracked products |
| `GET` | `/api/shopping-list/needed` | Tracked products compared against current stock |
| `POST` | `/api/shopping-list` | Track a new product |
| `PUT` | `/api/shopping-list/{id}` | Update a tracked product |
| `DELETE` | `/api/shopping-list/{id}` | Stop tracking a product |

## Roadmap

- Barcode scanning to add products straight from the camera (in progress).
- Nutrient tracking: log what gets consumed and estimate intake with AI.
- OpenAI integration for recipe suggestions from what's in stock.
