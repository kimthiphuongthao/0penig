# Open Questions

## Pattern Consolidation - 2026-03-16
- [ ] Does OpenIG 6.0.2 ScriptableHandler support `args` binding? — Gates parameterization approach for BackchannelLogoutHandler (Step 3) and SloHandler (Step 4). If NO, must use env vars instead.
- [ ] Should VaultCredentialFilter be consolidated in a future phase? — Currently out of scope due to structural variance (different username sources per mechanism). Revisit after Steps 2-4 complete.
- [ ] Is C-2 (app session tokens in JwtSession over HTTP) accepted as a lab limitation or should it be addressed before packaging? — CRITICAL severity per security audit, but fix requires server-side storage or HTTPS (both significant effort).
- [ ] Should Redis authentication (H-4) be added before packaging? — All 3 stacks run Redis without auth. Medium effort (docker-compose + 9 Groovy files).
- [ ] Should secrets in docker-compose.yml (H-5) be moved to .env files before packaging? — Medium effort, affects deployment workflow.
