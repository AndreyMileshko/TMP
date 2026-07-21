# TMP Verification Log

## Latest result

**Date:** 2026-07-20  
**Scope:** STAGE0-012 (BLK-004 rework, Stage 0 re-verified)  
**Overall:** PASSED

---

# Verification Entry Template

## YYYY-MM-DD — `<TASK-ID or STAGE>`

| Verification | Command / Method | Result |
|---|---|---|
| Compile | `mvn ...` | PASSED/FAILED |
| Unit tests | `mvn ...` | PASSED/FAILED |
| Integration tests | `mvn ...` | PASSED/FAILED/NOT_APPLICABLE |
| Architecture tests | `mvn ...` | PASSED/FAILED/NOT_APPLICABLE |
| Static analysis | `mvn ...` | PASSED/FAILED |
| Formatting | `mvn ...` | PASSED/FAILED |
| Manual scenario | ... | PASSED/FAILED/NOT_APPLICABLE |

### Failures

- None.

## 2026-07-17 — `STAGE0-004`

| Verification | Command / Method | Result |
|---|---|---|
| Unit tests baseline | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` | FAILED |
| Unit tests with explicit JAVA_HOME JDK 17 | PowerShell env override + `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` | FAILED |
| Manual scenario | JDK/JRE availability check | FAILED |

### Failures

- Environment contains JRE 21 and JDK 17, but no JDK 21 compiler required by project Java baseline.

## 2026-07-17 — `STAGE0-004` (resolved)

| Verification | Command / Method | Result |
|---|---|---|
| Unit tests baseline | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` with JDK 21 | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-005`

| Verification | Command / Method | Result |
|---|---|---|
| Spring context smoke test | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-bootstrap-app test` | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-006`

| Verification | Command / Method | Result |
|---|---|---|
| JavaFX shell smoke tests | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-ui-shell test` | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-007`

| Verification | Command / Method | Result |
|---|---|---|
| Datasource configuration tests | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db test` | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-008`

| Verification | Command / Method | Result |
|---|---|---|
| Flyway baseline verify | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-009`

| Verification | Command / Method | Result |
|---|---|---|
| Testcontainers integration | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | FAILED |

### Failures

- Docker environment not found; Testcontainers cannot start PostgreSQL container.

## 2026-07-20 — `STAGE0-009` (resolved)

| Verification | Command / Method | Result |
|---|---|---|
| Testcontainers integration | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | PASSED |

### Failures

- None.

## 2026-07-20 — `STAGE0-010`

| Verification | Command / Method | Result |
|---|---|---|
| Architecture tests | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify -DskipITs` | PASSED |

### Failures

- None.

## 2026-07-20 — `STAGE0-011`

| Verification | Command / Method | Result |
|---|---|---|
| Packaging verify | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -Ppackage verify` | PASSED |
| Package artifact | `dist/jpackage/TMP/TMP.exe` | PRESENT |

### Failures

- None.

## 2026-07-20 — `STAGE0-012` (BLK-004 rework)

| Verification | Command / Method | Result |
|---|---|---|
| Full Stage 0 verify | `.tools/apache-maven-3.9.9/bin/mvn.cmd clean verify` | PASSED |
| Package verify | `.tools/apache-maven-3.9.9/bin/mvn.cmd clean verify -Ppackage` | PASSED |
| Bootstrap PostgreSQL IT | `TmpBootstrapPostgresIntegrationIT` (Testcontainers) | PASSED |
| Bootstrap DB smoke | `SpringContextSmokeTest` (DataSource/JdbcTemplate/Flyway) | PASSED |
| Packaging smoke | `PackagingSmokeIT` (TMP.exe, runtime, TMP.cfg, fat jar) | PASSED |
| Package artifact | `dist/jpackage/TMP/TMP.exe` | PRESENT |

### Failures

- None.

## 2026-07-20 — `STAGE0-012`

| Verification | Command / Method | Result |
|---|---|---|
| Full Stage 0 verify | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify` | PASSED |
| Package artifact | `dist/jpackage/TMP/TMP.exe` | PRESENT |
| Stage 0 exit criteria review | Manual against STAGE-0-DEVELOPMENT-FOUNDATION.md | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-003`

| Verification | Command / Method | Result |
|---|---|---|
| Static analysis and formatting gates | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify -DskipTests` | PASSED |
| Compile | Included in verify lifecycle | PASSED |
| Unit tests | Skipped by task scope (`-DskipTests`) | NOT_APPLICABLE |
| Integration tests | Not required for this task | NOT_APPLICABLE |
| Architecture tests | Not required for this task | NOT_APPLICABLE |
| Manual scenario | Verify phase includes configured quality plugins | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-002`

| Verification | Command / Method | Result |
|---|---|---|
| Build configuration validation | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests help:effective-pom` | PASSED |
| Compile | Not required for this task | NOT_APPLICABLE |
| Unit tests | Not required for this task | NOT_APPLICABLE |
| Integration tests | Not required for this task | NOT_APPLICABLE |
| Architecture tests | Not required for this task | NOT_APPLICABLE |
| Static analysis | Deferred to STAGE0-003 | NOT_APPLICABLE |
| Formatting | Deferred to STAGE0-003 | NOT_APPLICABLE |
| Manual scenario | Parent/plugin/dependency management review | PASSED |

### Failures

- None.

## 2026-07-17 — `CONTROL-001`

| Verification | Command / Method | Result |
|---|---|---|
| Required document discovery | Manual path registry from repository docs | PASSED |
| Mandatory source consistency | Manual status/intent cross-check | PASSED |
| Context map update | `docs/development-control/CONTEXT-MAP.md` review | PASSED |
| Compile | N/A for control task | NOT_APPLICABLE |
| Unit tests | N/A for control task | NOT_APPLICABLE |
| Integration tests | N/A for control task | NOT_APPLICABLE |
| Architecture tests | N/A for control task | NOT_APPLICABLE |
| Static analysis | N/A for control task | NOT_APPLICABLE |
| Formatting | N/A for control task | NOT_APPLICABLE |
| Manual scenario | CONTROL-001 acceptance checklist | PASSED |

### Failures

- None.

## 2026-07-17 — `STAGE0-001`

| Verification | Command / Method | Result |
|---|---|---|
| Compile | `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests validate` | PASSED |
| Unit tests | Not required for this task | NOT_APPLICABLE |
| Integration tests | Not required for this task | NOT_APPLICABLE |
| Architecture tests | Not required for this task | NOT_APPLICABLE |
| Static analysis | Deferred to STAGE0-003 | NOT_APPLICABLE |
| Formatting | Deferred to STAGE0-003 | NOT_APPLICABLE |
| Manual scenario | Reactor and module pom presence review | PASSED |

### Failures

- None.

## 2026-07-17 — `CONTROL-002`

| Verification | Command / Method | Result |
|---|---|---|
| Stage 0 decomposition completeness | Manual review against Stage 0 manifest planning domains | PASSED |
| Task-size compliance | Manual review against governance limits | PASSED |
| Queue ordering and dependencies | Manual review of `WORK-QUEUE.md` | PASSED |
| Compile | N/A for control task | NOT_APPLICABLE |
| Unit tests | N/A for control task | NOT_APPLICABLE |
| Integration tests | N/A for control task | NOT_APPLICABLE |
| Architecture tests | N/A for control task | NOT_APPLICABLE |
| Static analysis | N/A for control task | NOT_APPLICABLE |
| Formatting | N/A for control task | NOT_APPLICABLE |
| Manual scenario | CONTROL-002 acceptance checklist | PASSED |

### Failures

- None.

## 2026-07-21 — Stage 1 re-review fixes (`STAGE1-015`, BLK-008)

| Verification | Command / Method | Result |
|---|---|---|
| Full reactor verify | `mvn clean verify` | PASSED |
| Package profile verify | `mvn clean verify -Ppackage` | PASSED |
| Registration lifecycle guard | `DefaultPlatformCoreRegistrationTest` | PASSED |
| Shutdown listener resilience | `PlatformCoreLifecycleListenerTest` | PASSED |
| Public platform event package | `PlatformCoreIntegrationIT`, updated imports | PASSED |
| Generic API boundary ArchUnit | `Stage1PlatformCoreArchitectureTest` | PASSED |
| Static analysis | checkstyle + spotbugs in verify | PASSED |

### Failures

- None.

## 2026-07-20 — Stage 1 blocker rework (`STAGE1-014`, BLK-005..007)

| Verification | Command / Method | Result |
|---|---|---|
| Full reactor verify | `mvn clean verify` | PASSED |
| Package profile verify | `mvn clean verify -Ppackage` | PASSED |
| Event metadata stability | `SynchronousEventBusTest` | PASSED |
| Lifecycle failure/rollback | `DefaultLifecycleManagerTest` | PASSED |
| Atomic registration | `DefaultPlatformCoreRegistrationTest`, `PlatformCoreIntegrationIT` | PASSED |
| Service registry counts | `DefaultServiceRegistryTest` | PASSED |
| Architecture API boundaries | `Stage1PlatformCoreArchitectureTest` | PASSED |
| Static analysis | checkstyle + spotbugs in verify | PASSED |
| Manual TMP.exe | `dist/jpackage/TMP/TMP.exe` with PostgreSQL env vars | PASSED |

### Failures

- None (SpotBugs `CT_CONSTRUCTOR_THROW` on `AbstractDomainEvent` resolved via `@SuppressFBWarnings` and static validation helper).

## 2026-07-20 — Stage 1 verification gate (`STAGE1-013`)

| Verification | Command / Method | Result |
|---|---|---|
| Full reactor verify | `mvn clean verify` | PASSED |
| Package profile verify | `mvn clean verify -Ppackage` | PASSED |
| Platform Core unit tests | `tmp-platform-core` surefire | PASSED |
| Bootstrap integration | `PlatformCoreIntegrationIT`, `TmpBootstrapPostgresIntegrationIT` | PASSED |
| Architecture tests | `Stage0ArchitectureBaselineTest`, `Stage1PlatformCoreArchitectureTest` | PASSED |
| Static analysis | checkstyle + spotbugs in verify | PASSED |
| UI status smoke | `JavaFxShellSmokeTest.rendersPlatformStatusLabelWhenProvided` | PASSED |
| Stage 1 exit criteria | Manual review vs STAGE-1-PLATFORM-CORE.md | PASSED |

### Failures

- None.
