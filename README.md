# Dream Planner — Canadian planning calculator

## Public MVP launch

The Vercel container uses the `public` profile: public calculator access, browser saves
and exports, with the shared database scenario API disabled. No login, hosted database
or AI key is required. The named-plan UI has been removed; local scenario APIs remain
available in the default profile for existing data. Follow [Vercel launch](docs/vercel-launch.md).
The local-only descriptions below refer to the default profile, not the public release.

Java 21 / Spring Boot / Maven. The engine lives in `com.example.retirement_planner`.
Call `ProjectionService.project(ProjectionRequest)` directly or inject the Spring service.
The local web calculator and JSON endpoint reuse that same tested engine.
Named scenarios use SQL through Spring JDBC: file-backed H2 locally, or PostgreSQL via
Docker Compose. Explain plan provides fixed calculator explanations without an AI provider. There is no authentication
or frontend build step; the app binds to localhost by default.

## Run the calculator

With JDK 21 available and `JAVA_HOME` pointing to it:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1'
```

Open http://localhost:8080 in your browser. Stop the app with Ctrl+C in the terminal.
If port 8080 is already in use, append `--server.port=8081` inside a quoted
`'-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=8081'` argument.
On macOS/Linux, replace `.\mvnw.cmd` with `./mvnw`.

The homepage offers Home, Retirement, and Education illustrated cards. Each opens a
named advisor in an animated scene. Choose **Build my plan** or a short account
explanation. Home and Education ask four personal-goal questions; Retirement asks
five. Back and Topics remain available, and each topic keeps its answers in the session.

**View detailed plan** expands the current conversation bubble. It contains inputs,
extra projection metrics, annual balances, the assumption sources, and optional
device saving / JSON export. The older dashboard, workspace scripts and style layers
have been removed. No action switches to a second website design.

Inflation is fixed at 2.1%, from the [2026 FP Canada Projection Assumption Guidelines](https://www.fpcanada.ca/projection-assumption-guidelines).
Savings goals under 10 years use a 2.4% annual short-term return illustration.
Longer goals and retirement use 4.8%, derived from a 50/50 blend of bonds at 3.2%
and equities at approximately 6.4%. These are nominal illustrations before fees
and tax, not promised returns or recommended allocations. The guideline's
long-term assumptions are intended for horizons of 10+ years. Home costs may grow
faster than general inflation. Retirement plans through age 95, or one year after
retirement when retiring at 95 or later. CPP, OAS and pension income are excluded.

Scenes retain Amara's city terrace, Daniel's coastal promenade and Mei's university
courtyard. **Pause animation** and system reduced-motion preferences are supported.
**About & privacy** explains processing and can remove saved browser plans.
Education excludes RESP grants, bonds, tax effects and eligibility checks.

**Save on this device** explicitly stores a topic's answers. Returning to that topic
prefills the questions; results are recalculated using the current defaults.
**Export plan** downloads the last successful illustration as JSON. There is no
server plan storage or AI provider involved in these flows.

Frontend checks: `node src/test/js/dialogue.test.cjs` (or
`node --test src/test/js/dialogue.test.cjs` where child-process spawning is permitted).
Java checks: `.\mvnw.cmd test` on Windows or `./mvnw test` on macOS/Linux.

## Retirement, accounts and goal assumptions

Retirement investments pool TFSA, RRSP and other balances; home/FHSA and emergency cash
are separate. Returns compound monthly; retirement growth precedes month-end withdrawals.
Spending and entered pension income rise with assumed inflation. Unmet spending is
reported separately; balances do not become debt. Surplus pension income is spent,
not reinvested. The funding target discounts monthly shortfalls, and the contribution
target funds it under the selected fixed assumptions. Supported spending uses a
monotonic solver, with no estate target. Benefits received before retirement are assumed spent.

CPP/OAS are user-entered age-65 estimates. Whole-year start-age adjustments are modeled,
including the OAS increase from age 75. Actual benefit entitlement, QPP, GIS, taxes,
OAS recovery tax, fees, RRIF minimum withdrawals and account withdrawal order are not modeled.
Province is used for account opening age, not provincial tax calculations.

Account checks use **September 29, 2026** and do not extrapolate contribution limits
or enforce future account allocations in the retirement or home projection. TFSA room
uses resident-year limits, lifetime contributions and withdrawals before 2026, or verified
remaining room. FHSA checks include age, home/spouse-home history, opening/withdrawal year,
carryforward and lifetime usage; complex histories require verified room. RRSP displays
basic new-room estimates separately from actual remaining room, which must be entered.
Planned 2026 account additions are independent checks, not an allocation of monthly saving.

Rules were checked against official sources:
[TFSA room](https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/tax-free-savings-account/contributing/calculate-room.html),
[registered-plan limits](https://www.canada.ca/en/revenue-agency/services/tax/registered-plans-administrators/pspa/mp-rrsp-dpsp-tfsa-limits-ympe.html),
[FHSA eligibility](https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account/opening-your-fhsas.html),
[FHSA room](https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account/contributing-your-fhsa.html),
[FHSA closing](https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account/closing-your-fhsa.html),
[CPP start ages](https://www.canada.ca/en/services/benefits/publicpensions/cpp/when-start.html), and
[OAS start ages](https://www.canada.ca/en/services/benefits/publicpensions/old-age-security/when-start.html).

Home savings assume fixed monthly contributions, nominal returns and an inflation-adjusted
target. Debt uses APR/12, fixed month-end payments and no new borrowing or fees; payment
below interest and horizons beyond 50 years are flagged. Cash flow subtracts living costs,
retirement/home saving and the entered debt payment from take-home income.

The main services are `ProjectionService`, `RetirementPlanService`, `AccountRulesService`
and `GoalsService`. `PlanningController` exposes `POST /api/plans/retirement`, `/accounts`,
`/home`, and `/budget`; all services validate direct calls as well as API requests.
Form-encoded `POST /api/plans/retirement/csv` and `/api/plans/export` accept a JSON `plan`
parameter and return standard downloadable attachments. CSV amounts are generated from
the Java `BigDecimal` results rather than recomputed by the browser.

## JSON API

`POST /api/projections`, with `Content-Type: application/json`:

```json
{
  "currentAge": 30,
  "retirementAge": 65,
  "currentSavings": 10000,
  "monthlyContribution": 500,
  "annualReturnRate": 0.05,
  "annualInflationRate": 0.02
}
```

Unlike the form's percentages, API rates are decimals. The response is the existing
`ProjectionResult` with its annual breakdown. Invalid inputs return HTTP 400 with
an `application/problem+json` body containing `title`, `status` and a readable `detail`.
Ages must be whole numbers; monetary fields also accept decimal strings.
`POST /api/projections/csv` accepts the same fields as form-encoded parameters and
returns a CSV attachment with exact two-decimal amounts, using the same engine validation.

## Inputs and assumptions

- Ages must satisfy `0 <= currentAge < retirementAge <= 120`; all six inputs are required.
- Savings and contributions are non-negative `BigDecimal` amounts, expressed in CAD.
- Return and inflation are constant effective annual decimal rates: `0.05` means 5%.
  Each rate must be greater than `-1` and no greater than `1`.
- The engine projects exactly `(retirementAge - currentAge) * 12` months. Each month
  applies investment growth, then adds the fixed nominal contribution at month-end.
- The monthly return is `(1 + annualReturnRate)^(1/12) - 1`. This fractional power
  uses `StrictMath` double arithmetic via `log1p`/`expm1`, then converts the rate to
  `BigDecimal`. Consequently the rate is an approximation. Monetary calculations
  use DECIMAL128 (34 significant digits), without rounding monthly balances to cents.
- Annual purchasing power is the nominal balance divided by
  `(1 + annualInflationRate)^projectionYear`; negative inflation is supported.
- Annual rows use one-based projection years, with age at each year-end. Results include
  initial savings, nominal contributions, nominal investment growth, and both final balances.
  Initial savings are excluded from contributions. All reported monetary amounts use
  two decimal places and HALF_UP rounding. Independently rounded annual rows and totals
  can differ by a few cents when summed; totals come from unrounded calculations.
- The original savings-only engine excludes taxes, fees, withdrawals and account rules.
  The separate retirement and account services add the features described above.

## Run tests

With JDK 21 available and `JAVA_HOME` pointing to it:

```powershell
.\mvnw.cmd test
```

On macOS/Linux, use `./mvnw test`. The wrapper downloads Maven/dependencies on its first
run and needs network access. JUnit tests exercise direct service calls, validation,
month-end contribution timing, compounding, inflation, negative returns and annual totals.
The existing Spring application context test is also retained.
HTTP integration tests exercise the real service through Spring MVC, JSON validation,
error responses and serving the calculator assets.
Planning tests cover retirement shortfalls, contribution targets, benefit start ages,
TFSA residency/history, FHSA eligibility/limits, RRSP room, home saving and debt/cash flow.

If Maven chooses an inaccessible user repository, explicitly set the existing repository:
`.\mvnw.cmd '-Dmaven.repo.local=[local-user-directory]/.m2/repository' test`.

## Additional engine APIs

The following services remain available through APIs. They are not exposed through a separate dashboard in the advisor interface.

- `POST /api/analysis/risk`: seeded Monte Carlo runs, 100–2,000 paths, annual volatility
  0–60%. Independent lognormal annual returns, constant within each year, with the entered
  arithmetic expected return. Monthly cash flows, fixed inflation, before tax. Reports
  shortfall-free frequency and 10th/50th/90th percentiles in today's CAD. These are model
  frequencies, not predictions. Uses double arithmetic; seed makes runs repeatable.
- `POST /api/analysis/drawdown`: Ontario after-tax estimate. Separately grows TFSA,
  RRSP/RRIF and other balances; future monthly saving shares are explicit. Supports three
  withdrawal orders after RRIF minimums. RRSP deposits after age 71 go to other investments.
  Annual growth precedes annual spending/withdrawals; conversion at 71, minimums from 72.
  Surplus net income is reinvested in other investments. Uses 2026 federal/Ontario brackets,
  basic personal credits, Ontario surtax/reduction/health premium and OAS recovery. Thresholds
  are indexed by the assumed inflation, including thresholds that currently are fixed in law.
  Other-account growth is treated as fully taxable interest and withdrawals as principal.
  Excludes age/pension credits, capital-gains basis, pension splitting, GIS, fees and future
  contribution-room enforcement. Annual double arithmetic makes this an approximate model,
  separate from the monthly BigDecimal before-tax overview. Other provinces are rejected
  explicitly rather than assigned Ontario taxes.
- `POST /api/analysis/household`: two independent before-tax plans and combined supported
  spending in today's dollars. No shared withdrawals, survivor benefits or tax coordination.
- `GET/POST /api/scenarios`, `DELETE /api/scenarios/{id}`: parameterized SQL, UUID ids,
  server-side validation of every saved calculator input, 100-plan library limit. H2 data
  persists in ignored `data/`; browser device saves remain separate. The named library stores
  the primary plan, not transient analysis form settings or the partner comparison.

Tax/benefit modelling references:
[2026 brackets](https://www.canada.ca/en/revenue-agency/services/tax/individuals/tax-rates-brackets/current-year.html),
[Ontario credits, surtax and health premium](https://www.canada.ca/en/revenue-agency/services/forms-publications/payroll/t4032-payroll-deductions-tables/t4032on-jan/t4032on-january-general-information.html),
[RRIF factors](https://www.canada.ca/en/revenue-agency/services/forms-publications/publications/ic78-18/registered-retirement-income-funds.html),
[OAS recovery](https://www.canada.ca/en/services/benefits/publicpensions/old-age-security/recovery-tax.html).

## Explain plan and protective boundaries

Explain plan accepts four topics: summary, assumptions, accounts, and scenarios. Summary
recomputes factual results from the retirement inputs. Other topics require no personal
data. Unsupported free-form requests, account documents and conversation histories are
rejected. This endpoint never calls an AI provider, proposes changes or applies a plan.
The legacy gateway class is not connected to the public explanation endpoint.

The current unauthenticated app is local-only. Requests must come from loopback clients,
use a loopback/localhost Host, and have the same origin when an Origin is supplied.
Cross-site requests are rejected. API responses are not cached; framing is blocked.
This restriction also prevents accessing the container from an external host. Public or
container networking requires a deliberate authenticated deployment design.

The footer explains educational use, assumptions and storage. Browser copies can be
removed independently of named database scenarios. See docs/disclaimer-review.md for
regulatory sources, implemented controls and remaining launch requirements.

## Container and CI

Copy `.env.example` to `.env` and set a database password. With Docker installed:

```powershell
docker compose up --build
```

This builds/tests the Java application, runs it as a non-root user, starts PostgreSQL with
a health check and persists the database in a named volume. The exposed port binds only to
localhost. PostgreSQL is not exposed to the host. H2 is used when running directly with Maven.
To use an existing PostgreSQL database, set `DATABASE_URL` (a JDBC URL), `DATABASE_USERNAME`
and `DATABASE_PASSWORD`. H2 data does not automatically migrate to PostgreSQL.

GitHub Actions runs Maven verification on pushes and pull requests and uploads test reports.
Docker/PostgreSQL and hosted CI must still be exercised on their target platforms. Public
deployment is not provisioned: choose a host, add HTTPS, authentication, per-user scenario
ownership, request quotas and a backup/migration strategy before exposing personal saved plans
or a paid AI endpoint. This is currently a local single-workspace application.

## Technology showcase

| Technology | Concrete demonstration |
| --- | --- |
| Java 21 / Spring Boot | Financial services, validation, REST controllers and dependency injection |
| REST / JSON | Projection, account, goals, analysis, scenario CRUD and chat endpoints |
| SQL / JDBC | Parameterized queries, schema, durable named scenarios, H2 and PostgreSQL configuration |
| JavaScript / HTML / CSS | Responsive interface, progressive disclosure, SVG chart, async requests and chat previews |
| AI integration | Server-side Responses API, structured changes, calculator grounding, bounded history and explicit apply |
| Maven / JUnit / Mockito | Calculation, validation, SQL and HTTP tests; provider mocked in tests |
| Docker / GitHub Actions | Container/Compose packaging and CI configuration; target execution still pending |

Further modelling work: complete provincial tax coverage, capital gains and age/pension
credits, coordinated household drawdown/survivor benefits and validated return-model calibration.

The footer explains educational use and calculation/AI limitations. See [disclaimer review](docs/disclaimer-review.md) for Canadian regulatory sources and the remaining legal review before public launch.
