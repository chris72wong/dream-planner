# Summit — Canadian planning calculator

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

The homepage offers Home, Retirement, and Education illustrated cards. Each opens an
advisor scene with authored dialogue choices. Learning needs no personal inputs;
choosing an illustration asks one question at a time. Back and Topics let you explore
at your own pace. Dialogue answers and results stay in this session, separately for
each topic; they are not automatically saved.

The conversations fill the viewport with animated SVG environments: Amara on a city
terrace for Home, Daniel on a coastal promenade for Retirement, and Mei in a university
courtyard for Education. Characters blink, breathe, and gesture while subtitles reveal
progressively. Longer explanations advance in short passages using **Continue**, keeping
the advisor visible on small screens. Responses are integrated into the scene. **Show full text** skips the
subtitle reveal; **Pause animation** stops scene motion. System reduced-motion preferences
are respected by default. **About & privacy** remains available inside each scene.
Education reuses the savings engine
and excludes RESP grants, bonds, tax effects, and eligibility/contribution-room checks.
**View detailed plan** applies the home or retirement illustration to the existing
workspace without automatically saving it. Retirement illustrations start with zero
benefits/pension income; add those in the detailed editor. Existing browser saves and
named scenarios remain available. Vacation, Car, and Something else are deferred.
Dialogue graph and request compatibility checks run with
`node --test src/test/js/dialogue.test.cjs`; Java service/API checks run with
`.\mvnw.cmd test` on Windows or `./mvnw test` on macOS/Linux.
Use **Edit plan** in the detailed workspace
to enter your profile, money and goals, retirement income, and assumptions in four steps.
The example starts at age 30 with $10,000 invested and $500/month saved; benefit
estimates start at zero. Examples are illustrations, not recommended assumptions.

- **Overview:** retirement balances, supported spending, funding target, live sliders,
  today/future-dollar chart and age inspector.
- **Inline accounts:** versioned 2026 TFSA/FHSA/RRSP eligibility, contribution-room estimates,
  history forms, verified-room overrides, planned-overage checks and CRA source links.
- **House:** home savings, emergency cash, monthly cash flow and debt payoff.
- **What if?:** pin a baseline and compare more saving, later retirement and lower returns.
- **The details:** annual savings/withdrawal table, CSV download, print report, and JSON
  plan import/export. Results and exports always use the last successful calculation.

**Save on this device** stores a plan in this browser's local storage. Saved plans reload
automatically; saving is explicit. **Remove device save** removes only that saved copy.
**Reset example** restores the example without deleting a saved plan. JSON plan files
must match this version and assessment date. Calculator inputs stay in this app. Explain plan sends only the retirement inputs needed to the local app server; no information is sent to an AI provider.

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

## Expanded workspace

The overview shows the main results and chart. Sliders and account previews are collapsed.
**Ask Summit** opens a conversational panel; **Explore** holds optional tax, market-risk and
household analyses. **What if?** includes a SQL-backed library of named plan snapshots.

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
