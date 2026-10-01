# Public MVP on Vercel

This release presents Home, Retirement and Education through advisor conversations, with inline detailed plans, optional device saves and JSON exports. The old dashboard has been removed. The public profile disables the shared scenario API. No database account, user login or AI key is needed. Inputs are processed by the server but plans are not persisted there.

## Static frontend and calculator routing

`vercel.json` defines two services in the same project and deployment:

- `frontend` publishes only `src/main/resources/static` as static files, without an install or build step. The homepage, CSS, JavaScript and SVG therefore load without starting Java. Unversioned files require revalidation so visitors receive updates after deployment.
- `calculator` builds the existing root `Dockerfile.vercel`. `/api` and `/api/*` route here with their original paths; all other paths route to the frontend. The browser continues to call same-origin `/api/*` URLs.

Security headers apply at the public routing layer, including to static files. API responses remain `no-store`, and Java retains same-origin validation and the disabled scenario API. No CORS changes or new public storage are needed. A first calculation may still wait for Java after inactivity.

## Deploy and verify

1. Push this project to your actual GitHub repository, or use the Vercel CLI from this directory. The sample `YOURUSERNAME` Git remote must be replaced before pushing.
2. Link to the **existing Dream Planner project**, not a new project, to preserve `dreamplanner-blue.vercel.app`. In **Settings → Build and Deployment**, select **Services** as the framework and use this repository directory as the project root. Clear old project-wide build/install/output overrides; service settings now live in `vercel.json`. Both the Services framework setting and the configuration are required.
3. Create a **preview** deployment. With an authenticated current CLI, use `vercel link` to select the existing project and then `vercel deploy`. Inspect the build output for a static `frontend` and a container `calculator` service.
4. Open the preview in a fresh browser session. Check Home, Retirement and Education, detailed calculations, save/reload, and JSON exports. Check that an unknown static path returns 404 and `/api/scenarios` still returns 404. Valid same-origin calculations must succeed; cross-origin POSTs must remain rejected.
5. Inspect `/`, `/styles.css`, `/dialogue.css`, `/app.js`, `/dialogue.js` and `/dream-planner-logo.svg`: all must have the security headers and revalidation cache policy. Inspect calculation responses for `Cache-Control: no-store`. Reload after a frontend update and confirm the new files appear.
6. After the backend has scaled to zero, open the homepage and choose a topic before calculating. Confirm in Vercel runtime logs that serving the page/assets does **not** invoke the calculator container. Measure page loading and the first calculation separately; a warm request comparison alone does not prove cold-start behavior.
7. Record the previous production deployment URL. Once preview checks pass, promote that preview in Vercel (or use `vercel promote <preview-deployment-url>`), retaining the existing domain. If checks fail in production, use Vercel Instant Rollback to the recorded deployment; do not delete it.

The container selects the `public` Spring profile, binds to all interfaces and uses port 8080. If configuring PORT in Vercel, Spring uses that value too. Do not set DATABASE_URL, DATABASE_PASSWORD or OPENAI_API_KEY. Keep request logging free of calculator payloads.

Vercel container deployment documentation: https://vercel.com/kb/guide/does-vercel-support-docker-deployments

Services configuration and required project setting: https://vercel.com/kb/guide/vercel-services

Local checks: `node --test src/test/js/dialogue.test.cjs` and `.\mvnw.cmd test`. To exercise the public profile locally: `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=public'`.

The hosted container build and proxy behavior still require the preview checks above; local tests do not prove deployment compatibility. This is an educational MVP with the existing modelling limitations and dated account rules, not a validated financial advice service.
