# Summit disclaimer review

Research date: September 29, 2026. Scope: Canadian educational calculators, with Ontario as the initial jurisdiction. This is implementation research, not a legal opinion or confirmation of compliance.

## Implemented wording

> Summit provides educational tools and information, not financial, investment, tax or legal advice. Projections are illustrative, depend on your inputs and assumptions, and are not guaranteed. Consult a qualified professional about your circumstances before making financial decisions.

The footer is visible on every goal page. Expandable limitations explain simplified calculations, changing rules, and checking investment professionals’ registration. Explain plan is now a deterministic local calculator explainer, not a generative AI chatbot. This wording describes intended use; it is not presented as a liability waiver or a statutory safe harbour.

## What the sources establish

- [CSA/CIRO Staff Notice 31-369, December 11, 2025](https://www.securities-administrators.ca/wp-content/uploads/2025/12/CSA-Finfluencer-Staff-Notice_ENG.pdf), page 4: disclaimers do not themselves avoid securities registration requirements. The notice concerns finfluencers; its warning is relevant context, not a ruling about Summit. General advice exemptions concern advice that is not tailored to recipients and can require conflict disclosures. The notice points to NI 31-103 section 8.25 and Ontario Securities Act section 34.
- [NI 31-103, section 8.25, BC consolidation](https://www.bclaws.gov.bc.ca/civix/document/id/lc/statreg/226a_2009): sets out the non-tailored advice exemption and concurrent interest disclosures for covered recommendations. Section 8.25 does not apply in Ontario. Summit must not assume that a personalized calculator or AI explanation qualifies for an exemption.
- [FSRA title-protection FAQ](https://www.fsrao.ca/industry/financial-planners-and-financial-advisors/frequently-asked-questions-faq): Ontario protects Financial Planner, Financial Advisor and confusingly similar titles used by individuals. This is distinct from securities registration. Do not infer that the software itself requires a professional credential solely because it is a calculator.

## Review before public release

Canadian counsel should assess the actual calculator, personalized AI explanations, advertising, monetization and provinces served; determine whether registration, exemptions or additional disclosures apply; and prepare appropriate terms and privacy notices. Do not claim that adding this footer prevents lawsuits or confirms compliance. If recommendations, sponsorships or referral arrangements are introduced, assess their obligations separately.

## Implemented protective changes

- `/api/chat` accepts only summary, assumptions, accounts and scenarios. It returns fixed explanations and calculator facts, with no recommendations or proposed changes. Unsupported topics and unnecessary histories/documents are rejected before processing. Provider credentials do not enable AI on this endpoint. This is a product boundary, not a claimed registration exemption.
- Removed free-text chat and external AI transmission. General explanations need no personal data; summaries use only retirement calculator inputs. Removed claims that the app provides advice and changed the monthly figure to an estimate, with a visible example-input label. User-chosen scenario controls remain.
- Added storage disclosures and browser-data removal. Named database copies remain separately removable. This computer still needs normal device security; copies and backups outside the app are not automatically erased.
- Added a loopback/same-origin request boundary to the unauthenticated app, no-store API caching, anti-framing CSP and no-referrer headers. Public access and typical external container networking are intentionally blocked until authentication and per-user isolation are designed.

Additional primary sources:

- [Canadian privacy regulators’ generative AI principles](https://www.priv.gc.ca/en/privacy-topics/technology/artificial-intelligence/gd_principles_ai/): consider necessity, less privacy-intrusive alternatives, appropriate purposes, transparency and safeguards. Summit uses a non-generative alternative and removes the provider disclosure path. This does not settle which privacy legislation applies to a future commercial deployment.
- [OPC meaningful-consent guidance](https://www.priv.gc.ca/en/privacy-topics/privacy-for-businesses/appropriate-handling-of-personal-information/collecting-personal-information-and-consent/consent/gl_omc_201805/): explain collection, use, recipients and significant consequences. The footer now distinguishes browser storage, database storage and the local calculation server.
- [Competition Bureau misleading representations guidance](https://competition-bureau.canada.ca/en/deceptive-marketing-practices/types-deceptive-marketing-practices/false-or-misleading-representations-and-deceptive-marketing-practices): consider the overall impression, and substantiate performance claims. Summit labels outputs as estimates and examples, explains assumptions and makes no promised-return or retirement-suitability claims.

Remaining public-launch work: authentication and per-user authorization; a deployment-specific privacy policy with the real operator/contact, retention, access/correction and complaint process; hosting/security and breach response; and province-specific review of services, titles, referrals and any future fees. The current footer is a notice, not an accepted contract or blanket liability release.
