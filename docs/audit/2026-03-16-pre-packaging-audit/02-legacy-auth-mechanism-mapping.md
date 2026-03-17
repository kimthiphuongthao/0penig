# Task 1B: Legacy Auth Mechanisms → OpenIG 6.0.2 Mapping

**Agent:** document-specialist (Sonnet)
**Date:** 2026-03-16

> Update 2026-03-17: Pattern Consolidation Steps 1-5 are complete. The OpenIG mapping verdict is unchanged; Step 5 only closed config/deployment quick wins and Step 6 is the current document-sync pass.

---

## Part 1: Legacy Login Mechanisms Inventory

| # | Mechanism | Protocol | Where creds sent | Example apps | Prevalence |
|---|---|---|---|---|---|
| 1 | Form POST login | HTTP POST form-encoded | POST body | WordPress, Redmine, Jira, Jenkins | HIGH |
| 2 | HTTP Basic Auth | RFC 7617 `Authorization: Basic` | Header (every request) | phpMyAdmin, Jenkins API, Prometheus | HIGH |
| 3 | Header injection | Trusted proxy sets `X-WEBAUTH-USER` | HTTP request header | Grafana, SonarQube, Nexus | HIGH |
| 4 | Token/Bearer injection | RFC 6750 `Authorization: Bearer` | Header or localStorage | Jellyfin, GitLab API, Mattermost | MEDIUM |
| 5 | LDAP direct bind | RFC 4513 app→LDAP | Form POST → app → LDAP | Redmine, GitLab, Jenkins (LDAP plugin) | HIGH |
| 6 | SAML SP | OASIS SAML 2.0 | Browser redirect | Salesforce, SharePoint, ServiceNow | MEDIUM |
| 7 | Kerberos/SPNEGO | RFC 4559 `Authorization: Negotiate` | OS-managed header | IIS, SharePoint, SAP NetWeaver | MEDIUM |
| 8 | Cookie replay | HTTP cookies | Cookie header | Legacy WebSphere, Oracle EBS | LOW |
| 9 | Custom HTTP header | Vendor-specific (SM_USER, iv-user) | Proprietary header | SiteMinder, OAM, TAM apps | MEDIUM |
| 10 | Certificate (mTLS) | TLS 1.2/1.3 mutual auth | TLS handshake | Government portals, banking | LOW |
| 11 | NTLM | Microsoft challenge-response | `Authorization: NTLM` | Legacy IIS, OWA | LOW |
| 12 | OAuth2 Resource Server | RFC 6750 + RFC 7662 | `Authorization: Bearer` (validated) | Spring Security RS apps | MEDIUM |

## Part 2: OpenIG 6.0.2 Filter Mapping

| # | Mechanism | OpenIG Built-in Filter | Config-only? | Needs Groovy? | Complexity |
|---|---|---|---|---|---|
| 1 | Form POST | `PasswordReplayFilter` + `StaticRequestFilter` | Yes (static creds) | Yes (Vault-backed) | Medium |
| 2 | HTTP Basic Auth | `HttpBasicAuthFilter` | Yes — full | No (unless Vault) | Low |
| 3 | Header injection | `HeaderFilter` + `AssignmentFilter` | Yes — full | No | Low |
| 4 | Token/Bearer | `AssignmentFilter` + `HeaderFilter` + `OAuth2ClientFilter` | Partial | Yes (SPA localStorage) | High |
| 5 | LDAP bind | None directly | No | Yes (ScriptableFilter) | High |
| 6 | SAML SP | `SamlFederationHandler` | Yes — full | No | Medium |
| 7 | Kerberos/SPNEGO | None | No | Yes (impractical) | Very High |
| 8 | Cookie replay | `CookieFilter` (partial) | Yes (replay) | Yes (initial login) | Medium |
| 9 | Custom header | `HeaderFilter` (= Mechanism 3) | Yes — full | No | Low |
| 10 | Certificate (mTLS) | None for TLS | No | Partial (header forward) | High |
| 11 | NTLM | None | No | NOT APPLICABLE | Very High |
| 12 | OAuth2 RS | `OAuth2ResourceServerFilter` | Yes — full | No | Low |

## Part 3: Verdict Summary

| # | Mechanism | Verdict |
|---|---|---|
| 1 | Form POST | **PARTIAL SUPPORT** — built-in for static creds, Groovy needed for Vault |
| 2 | HTTP Basic Auth | **FULL SUPPORT** — config-only |
| 3 | Header injection | **FULL SUPPORT** — config-only |
| 4 | Token/Bearer | **PARTIAL SUPPORT** — SPA localStorage needs Groovy |
| 5 | LDAP bind | **NOT APPLICABLE** — app does LDAP, OpenIG injects creds |
| 6 | SAML SP | **FULL SUPPORT** — SamlFederationHandler |
| 7 | Kerberos/SPNEGO | **CUSTOM REQUIRED** — delegate to nginx |
| 8 | Cookie replay | **PARTIAL SUPPORT** — CookieFilter for replay, Groovy for login |
| 9 | Custom header | **FULL SUPPORT** — same as header injection |
| 10 | Certificate (mTLS) | **PARTIAL SUPPORT** — nginx terminates, header forward |
| 11 | NTLM | **NOT APPLICABLE** — incompatible with reverse proxy |
| 12 | OAuth2 RS | **FULL SUPPORT** — config-only |

## Lab Implementation Assessment

| Lab Component | Uses | Verdict |
|---|---|---|
| Stack C Grafana (HeaderFilter) | Built-in | Correct — FULL SUPPORT |
| Stack C phpMyAdmin (HttpBasicAuthFilter) | Built-in | Correct — FULL SUPPORT |
| VaultCredentialFilter.groovy | Custom | Correct — no built-in Vault client |
| BackchannelLogoutHandler.groovy | Custom | Correct — no built-in backchannel logout |
| SessionBlacklistFilter.groovy | Custom | Correct — no built-in session blacklist |
| SloHandler*.groovy | Custom | Correct — no built-in end_session support |
| CredentialInjector.groovy (WordPress) | Custom | Correct — cookie caching beyond PasswordReplayFilter |
| JellyfinResponseRewriter.groovy | Custom | Correct — no built-in response body rewriting |

**Overall: Lab is NOT over-engineering custom Groovy. All custom code addresses real OpenIG 6.0.2 gaps.**
