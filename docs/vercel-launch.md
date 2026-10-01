# Public MVP on Vercel

This release presents Home, Retirement and Education through advisor conversations, with inline detailed plans, optional device saves and JSON exports. The old dashboard has been removed. The public profile disables the shared scenario API. No database account, user login or AI key is needed. Inputs are processed by the server but plans are not persisted there.

1. Push this project to your GitHub repository.
2. In Vercel, import that repository with the project root set to this directory. Use Other if asked for a framework. Leave build/output overrides unset so Vercel detects `Dockerfile.vercel`.
3. Deploy a preview and check the homepage, each topic, detailed calculations, browser save/reload and exports.
4. Confirm `/api/scenarios` returns 404, then promote the working deployment to production and optionally attach your domain.

The container selects the `public` Spring profile, binds to all interfaces and uses port 8080. If configuring PORT in Vercel, Spring uses that value too. Do not set DATABASE_URL, DATABASE_PASSWORD or OPENAI_API_KEY. Keep request logging free of calculator payloads.

Vercel container deployment documentation: https://vercel.com/kb/guide/does-vercel-support-docker-deployments

Local checks: `node --test src/test/js/dialogue.test.cjs` and `.\mvnw.cmd test`. To exercise the public profile locally: `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=public'`.

The hosted container build and proxy behavior still require the preview checks above; local tests do not prove deployment compatibility. This is an educational MVP with the existing modelling limitations and dated account rules, not a validated financial advice service.
