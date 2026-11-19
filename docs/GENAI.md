# GenAI stretch: design (not implemented)

Status: design only. Nothing here runs in the demo.

## Use case
Natural-language member search: "viewers added this month with a +91 phone" becomes a structured filter on the existing `GET /api/v1/members` endpoint.

## Design
1. **Translate, never execute.** The LLM returns a typed object `{search?, role?, status?, sort?}` (JSON schema, validated with the same enums the API already uses). It never produces a Mongo query or free text that reaches the database.
2. **Same door as the UI.** The object is sent to the existing list endpoint with the caller's own token, so authorization, paging limits and the prefix-search rules apply unchanged. A viewer cannot obtain more than the UI gives them.
3. **No PII to the model.** Only the user's sentence is sent. Member rows are never included in the prompt.
4. **Fallback.** Invalid or empty output: show "could not understand, use the filters" and leave the list unchanged.
5. **Boundary.** One `QueryTranslator` interface with a provider implementation behind it; the service layer does not know which model is used. Calls log the model, latency and outcome through the existing API log; the prompt text is not logged.

## Other candidates
- Migration assistant: read legacy JPA entities and propose Mongo document shapes and indexes (offline tool, reviewed by a person).
- Duplicate-member detection on near-matching names and phones.

## Risks and controls
| Risk | Control |
|---|---|
| Prompt injection in the search sentence | Output is schema-validated filters only; no tools, no data in context |
| Cost and abuse | Per-user rate limit, max input length, feature flag off by default |
| Wrong filter | Result is shown with the applied filters visible and editable |
