# Deploying FridgeMate

The app ships as a **single deployable**: the Angular build is bundled into the Spring Boot jar,
so one service serves both the UI and the API on one URL. No CORS, no mixed content, and the
barcode scanner works out of the box because the deployment is served over HTTPS.

- **Local development** uses the embedded H2 file database (nothing to install).
- **Production** uses managed PostgreSQL, selected by the `prod` Spring profile.

---

## 1. Create the database (Neon)

1. Sign up at [neon.tech](https://neon.tech) and create a project.
2. Open **Connection Details** and copy the connection string. It looks like:

   ```
   postgresql://my_user:my_password@ep-cool-name-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require
   ```

3. **Split it into three parts** — this is the step that trips people up, because Spring needs
   the JDBC form (`jdbc:postgresql://`), without the username and password embedded:

   | Variable                 | Value from the example above                                                     |
   | ------------------------ | -------------------------------------------------------------------------------- |
   | `JDBC_DATABASE_URL`      | `jdbc:postgresql://ep-cool-name-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require` |
   | `JDBC_DATABASE_USERNAME` | `my_user`                                                                          |
   | `JDBC_DATABASE_PASSWORD` | `my_password`                                                                      |

   Note the `jdbc:` prefix, and that the `my_user:my_password@` part is removed from the URL.

---

## 2. Deploy the app (Render)

1. Sign up at [render.com](https://render.com) and connect your GitHub account.
2. **New → Web Service**, and pick the `FridgeMate` repository.
3. Render reads [`render.yaml`](render.yaml) and preconfigures the service (Docker runtime, free plan).
4. When prompted, fill in the three environment variables from step 1.
5. Deploy. The first build takes a few minutes (it compiles the frontend and the backend).

The schema is created automatically on first boot (`ddl-auto=update`), so the app starts with an
empty, working database.

---

## Environment variables

| Variable                 | Required | Purpose                                                  |
| ------------------------ | -------- | -------------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE` | yes      | Must be `prod` to use PostgreSQL instead of H2. Preset in `render.yaml`. |
| `JDBC_DATABASE_URL`      | yes      | JDBC connection URL for the PostgreSQL database.          |
| `JDBC_DATABASE_USERNAME` | yes      | Database user.                                            |
| `JDBC_DATABASE_PASSWORD` | yes      | Database password.                                        |
| `PORT`                   | no       | Injected by Render; defaults to `8080` locally.           |

Nothing sensitive is stored in the repository — all credentials come from the environment.

---

## Good to know

- **Cold starts.** On Render's free plan the service sleeps after ~15 minutes of inactivity, so the
  first request after a pause can take up to a minute. Later requests are fast.
- **The H2 console is disabled in production** (`spring.h2.console.enabled=false`) — it must never be
  exposed publicly.
- **Local development is unaffected.** Without `SPRING_PROFILES_ACTIVE=prod` the app keeps using the
  local H2 file at `Java/FridgeCalories/data/`, and the Angular dev server keeps calling
  `http://<host>:8080/api` as before.

---

## Testing the production setup locally

To run exactly what gets deployed (one jar, one URL) without Docker:

```bash
cd Front/Angular && npm run build && cd ../..
cp -r Front/Angular/dist/fridgemate/browser/. Java/FridgeCalories/src/main/resources/static/
cd Java/FridgeCalories && ./mvnw clean package -DskipTests
java -jar target/FridgeCalories-0.0.1-SNAPSHOT.jar
```

Then open `http://localhost:8080` — the whole app is served from the backend, API included.

Or build the real container image:

```bash
docker build -t fridgemate .
```
