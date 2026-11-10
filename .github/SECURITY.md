# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.3.x   | ✅ |
| < 1.3   | ❌ |

## Reporting a Vulnerability

**Please do not open a public issue for security problems.**

Report privately via **GitHub Security Advisories**:

👉 https://github.com/titanxdevz/OpenTV/security/advisories/new

Or email **inkmcontop@gmail.com** with the subject `[OpenTV] Security report`.

### What to include

- Type of issue (e.g. injection, permission overreach, information leak)
- Affected version / commit
- Steps to reproduce
- Impact — what an attacker gains
- Proof of concept if you have one

### What to expect

| Stage | Timeline |
|-------|----------|
| Acknowledgement | within 72 hours |
| Triage & severity assessment | within 7 days |
| Fix released | prioritized by severity |
| Public advisory | after the fix ships |

Please give us a reasonable window to ship a fix before disclosing publicly.
We will credit you in the advisory unless you prefer to stay anonymous.

---

## Security model of OpenTV

OpenTV is deliberately minimal, and its attack surface is small by design.

### Permissions requested

| Permission | Why |
|---|---|
| `ACCESS_NETWORK_STATE` | Read whether the TV is on Wi-Fi or Ethernet |
| `ACCESS_WIFI_STATE` | Read the SSID of the connected network |

**No `INTERNET` permission.** The launcher never makes a network request.
No analytics, no crash reporting, no ad SDK, no telemetry of any kind.

### What the app does not do

- ❌ No background service or `WorkManager` job
- ❌ No `WebView` — no remote content is ever rendered
- ❌ No shell command execution
- ❌ No file-system writes outside its own sandbox
- ❌ No `ContentProvider` exported to other apps
- ❌ No cleartext HTTP traffic
- ❌ No runtime permission prompts (all permissions are normal-level)

### Third-party dependencies

| Library | Purpose | Risk surface |
|---|---|---|
| `androidx.appcompat` | Activity & theming | None — Google-maintained |
| `androidx.recyclerview` | Lists | None — Google-maintained |
| `androidx.palette` | Colour extraction | None — Google-maintained |
| `androidx.constraintlayout` | Layout | None — Google-maintained |
| `com.github.bumptech.glide` | Image loading | Local-only, no network calls |

Every dependency is pinned to an **exact version**. CI runs OWASP
Dependency-Check, Trivy, CodeQL, Gitleaks and a licence allow-list on every
pull request, so a compromised or vulnerable transitive dependency is caught
before merge.

---

## Verifying a release

Every GitHub Release ships a `SHA256SUMS.txt`. Verify before installing:

```bash
sha256sum -c SHA256SUMS.txt
```

And confirm the app's signing certificate matches the published fingerprint
before trusting an APK from a mirror.
