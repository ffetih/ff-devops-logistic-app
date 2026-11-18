# Backlog

This repository rebuilds `ff-devops-app` (the reference implementation, `D:\dev\workspace\ff-devops-app`) from scratch, one sprint at a time. The reference is a source to port from, not a history to replay: its later fixes are folded into the item where the code first appears, so each fix is written once, correctly.

## How the work is cut

| Unit | In Git | Rule |
| :--- | :--- | :--- |
| Sprint | branch `sprint/sNN` → pull request → tag `sNN` | Ends with something running on the local k3d cluster |
| Item | one commit, subject `SNN-MM <what it does>` | The build and its tests pass at every commit |
| Fix found later | a new item in the current sprint | Old sprints are not rewritten |

Each item lists:

- **Port** — paths in the reference repo to start from
- **Absorbs** — reference commits whose fix belongs in this item from the start
- **Done when** — the test, command or screen that proves it

## Runtime

One runtime: a single-node k3d cluster in WSL (server node runs the workloads too). No Compose. Argo CD applies everything from Git; nothing is applied by hand except the cluster and Argo CD itself (S0).

Host ports, fixed in `deploy/cluster/k3d-config.yaml`:

| Host port | NodePort | Service |
| :---: | :---: | :--- |
| 8080 | 30080 | Argo CD UI |
| 8081 | 30081 | logistics app |
| 8085 | 30085 | identity service |
| 8086 | 30086 | analytics loader |
| 8087 | 30087 | partner gateway |
| 8088 | 30088 | Superset |
| 8090 | 30090 | console |
| 8180 | 30180 | Keycloak (local only, for IDE runs) |
| 9000 | 30900 | RustFS S3 API |
| 9094 | 30094 | Kafka external listener (IDE runs) |
| 5432 | 30432 | PostgreSQL |
| 16686 | 30186 | Jaeger UI |

Memory budget for the 16 GB WSL VM, rough: Argo CD 1 GB, PostgreSQL 0.5 GB, Keycloak 1 GB, Kafka 1 GB, Superset 1.5 GB, four Spring services 0.5 GB each, console and simulators 0.5 GB. About 8 GB at the end of S11, which leaves room for the Rollouts preview pods (S12) and a small second cluster (S13).

## Left out on purpose

| Reference part | Why |
| :--- | :--- |
| `infra/local` Compose stack | One runtime only. It was also the reason realm and init files existed twice. |
| Flux (`deploy/gitops/flux`, `deploy/clusters`) | One controller: Argo CD |
| Metabase, Grafana, Prometheus | Removed from the reference cluster already. Prometheus may come back in S12 if a canary analysis needs it. |
| `test-live-apis.ps1` | Replaced by a PostSync smoke-test Job (S04) |
| Committed SFTP host key | Generated into a Secret (S11) |
| `kubectl-argo-rollouts` binary in the repo folder | Installed as a tool, ignored by Git |

---

## S0 — Foundation

Goal: an empty repository that builds, a k3d cluster, Argo CD running in it.

- [x] **S00-01 Add repository settings and Maven Wrapper**
  Port: `.gitignore`, `.gitattributes`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper`.
  Done when: `./mvnw -v` prints Maven 3.10.0.
- [x] **S00-02 Add README and sprint backlog**
  Done when: this file exists.
- [x] **S00-03 Add base BOM, parent POM and build script**
  Port: `base/ff-base-bom`, `base/ff-base-parent`, `build-all.*`. The BOM starts with Spring Boot and Spring Modulith only; internal libraries and third-party versions are added in the sprint that first uses them.
  Done when: `./build-all.sh` installs both POMs into `~/.m2`.
- [x] **S00-04 Add k3d cluster config**
  Pinned k3s image, one server, no agents, Traefik off (NodePorts only), the port table above.
  Done when: `k3d cluster create --config deploy/cluster/k3d-config.yaml` gives a `Ready` node and context `k3d-ff-logistics`.
- [x] **S00-05 Bootstrap Argo CD**
  Pinned Argo CD release through a kustomization, UI on host port 8080 over plain HTTP, Dex scaled to zero.
  Absorbs: `21a6583` (server-side apply for the large CRDs).
  Done when: `http://localhost:8080` shows the login page and `argocd login localhost:8080 --plaintext` works.

## S1 — Walking skeleton

Goal: a commit to `main` reaches the cluster through CI, an image and Argo CD, before any business code exists.

- [ ] **S01-01 Add the logistics app skeleton**
  Spring Boot app on 8081, Actuator health and info, `git.properties`, virtual threads, a context test.
  Port: `platform/ff-logistics/ff-logistics-app/pom.xml`, `LogisticsApplication.java`.
- [ ] **S01-02 Add a container image for the logistics app**
  Layered jar, Java 25 JRE, non-root user. Local path: build in WSL, `k3d image import`.
  Port: `ff-logistics-app/Dockerfile`, `.dockerignore`.
  Done when: the image runs and answers `/actuator/health`.
- [ ] **S01-03 Add a shared Helm chart for Spring services**
  `deploy/charts/ff-service`: Deployment, Service, probes on Actuator, resources, security context, `enableServiceLinks: false`. `values.yaml` holds defaults, `deploy/apps/logistics-app/values-local.yaml` overrides them.
  Absorbs: `a1f748a` (service links turn ports into strings).
  Done when: `helm template` renders, `helm lint` is clean.
- [ ] **S01-04 Add an Argo CD project and the app of apps**
  `AppProject ff-logistics` limited to this repo and the `ff-logistics` namespace. Root Application `ff-root` → `deploy/argocd/apps/`, child Application `logistics-app` with Helm `valueFiles`. Sync stays manual.
  Absorbs: `2c08413`, `a9e5782` (manual sync on the only cluster).
  Done when: syncing `ff-root` then `logistics-app` gives `Synced/Healthy` and `localhost:8081/actuator/health` is `UP`.
- [ ] **S01-05 Build and test on GitHub Actions**
  Same script as locally. On `main`, publish to `ghcr.io/ffetih/<name>:<sha>` and commit the tag into `values-local.yaml` with `[skip ci]`.
  Port: `.github/workflows/ci.yml`.
  Absorbs: `19ee090` (token for the registry login).
  Done when: a push to `main` produces a tag commit and Argo CD shows the app `OutOfSync`.
- [ ] **S01-06 Let Argo CD manage its own installation**
  Application `argocd` → `deploy/argocd`, so version bumps go through Git.
  Done when: changing a patch in `deploy/argocd` shows up as a diff in the UI.
- [ ] **S01-07 Build the documentation site in CI**
  AsciiDoc under `docs/src`, one guide per deployable, built and checked for warnings on every push.
  Port: `docs/build-docs.*`, `docs/src/index.adoc`, `_attributes.adoc`.
  Absorbs: `a87c1ec` (fail on warnings).

## S2 — Tenant, web and persistence base

Goal: the app stores tenant-scoped data in PostgreSQL running in the cluster.

- [ ] **S02-01 Add the tenant library**
  Port: `base/ff-base-tenant`.
  Absorbs: `4c37ac8` (one tenant id format).
- [ ] **S02-02 Add RFC 9457 problem responses**
  Port: `base/ff-base-web`.
  Absorbs: `42db7f4` (one 503 handler for missing dependencies).
- [ ] **S02-03 Add tenant-aware persistence**
  Hibernate `@TenantId`, database sequences, auto-configuration.
  Port: `base/ff-base-persistence`.
- [ ] **S02-04 Run PostgreSQL 17 in the cluster**
  Application `postgres` with sync wave `-1`, a PVC, init SQL for all databases from a ConfigMap, NodePort 5432 for IDE runs.
  Port: `deploy/overlays/local/platform/postgres*`.
- [ ] **S02-05 Migrate the schema with Flyway, tested on PostgreSQL**
  Testcontainers test against PostgreSQL 17; most tests stay on H2.
  Absorbs: `c3247b7`, `846888f`.
- [ ] **S02-06 Run migrations as a PreSync hook**
  A Job with the app image and Flyway only, `hook-delete-policy: BeforeHookCreation`. The app itself starts with Flyway validation only.
  Done when: a sync with a new migration shows the hook Job before the Deployment rolls.
- [ ] **S02-07 Document the IDE loop**
  App from IntelliJ, profile `local`, against the cluster's PostgreSQL on `localhost:5432`.

## S3 — Identity

Goal: users sign in through the identity service; the app trusts its tokens.

- [ ] **S03-01 Run Keycloak 26 with the logistics realm**
  Realm JSON in a ConfigMap, only the realm file mounted, sync wave `0`.
  Port: `deploy/overlays/local/platform/keycloak*`.
  Absorbs: `f05ee5a`.
- [ ] **S03-02 Add the identity service: sessions and JWKS**
  Login, refresh, logout, JWKS with cache and refresh.
  Port: `platform/ff-identity/identity-service` (`SessionController`, `JwksController`, `keycloak/*`).
- [ ] **S03-03 Add user administration inside one tenant**
  Absorbs: `c0aee29`, `648d42b`.
- [ ] **S03-04 Add the security library**
  JWT resource server, tenant claim converter, `local` header fallback.
  Port: `base/ff-base-security`.
  Absorbs: `6e9ced3` (refuse an invalid tenant claim).
- [ ] **S03-05 Add integration clients and service tokens**
  Absorbs: `be1fc9d`.
- [ ] **S03-06 Generate service Applications with an ApplicationSet**
  Git directory generator over `deploy/apps/*`; the hand-written `logistics-app` Application goes away.
  Done when: adding a folder under `deploy/apps/` creates an Application without touching Argo CD config.

## S4 — Logistics core: standards and WMS

- [ ] **S04-01 Structure the app as Spring Modulith modules**
  `ModularityTest` with `ApplicationModules.verify()`.
- [ ] **S04-02 Add the standards module**
  GS1 Modulo-10 (GLN, GTIN, SSCC), ISO 6346 Modulo-11, EN 16258.
  Port: `logistics/standards`.
- [ ] **S04-03 Add warehouses, bin locations and inbound ASNs**
  Port: `logistics/wms`, `V1__wms_init.sql`.
- [ ] **S04-04 Palletize with SSCC labels from a sequence**
- [ ] **S04-05 Smoke-test the API as a PostSync hook**
  Replaces `test-live-apis.ps1`. A failing hook marks the sync failed.

## S5 — TMS and in-process events

- [ ] **S05-01 Add shipments with e-CMR, ISO 6346 equipment and CO2e**
  Port: `logistics/tms`, `V2__tms_init.sql`.
- [ ] **S05-02 Confirm delivery once under concurrency**
  Absorbs: `b6b8378`, `V5`, `V8`.
- [ ] **S05-03 Create one shipment per customer order number**
  Absorbs: `ee69a9c`, `V9`.
- [ ] **S05-04 Record pickup and departure**
  Absorbs: `59e4bc4`, `V10`.
- [ ] **S05-05 Publish ShipmentDelivered through the event publication registry**
  Absorbs: `2019e11`, `V7`, `0f986f0` (max attempts), `b1faf04` (advisory lock), `16c7a2a` (cleanup), `59d8170` (listener concurrency).

## S6 — Geo and documents

- [ ] **S06-01 Add territories and route plans**
  Absorbs: `e9580b7` (duplicate stop codes), `c2c8c8a` (99 stops), `V3`.
- [ ] **S06-02 Estimate travel with Haversine or OSRM**
  Absorbs: `59d8170` (OSRM timeout).
- [ ] **S06-03 Order routes with time windows using Timefold**
  Absorbs: `da10b36`, `0a4b9c0`.
- [ ] **S06-04 Add the storage library**
  Port: `base/ff-base-storage`.
  Absorbs: `d0f3003` (write once), `4e28bff` (document links).
- [ ] **S06-05 Run RustFS from its Helm chart as a multi-source Application**
  Upstream chart plus `$values` from this repo.
- [ ] **S06-06 Issue presigned upload and download links**
  Absorbs: `c503898`, `67c2c56`.

## S7 — Events out and the analytics database

- [ ] **S07-01 Run single-node Kafka (KRaft)**
- [ ] **S07-02 Externalize ShipmentDelivered and ShipmentProgressed**
  Absorbs: `f4a575f`.
- [ ] **S07-03 Add the analytics loader with a dead-letter topic**
  Port: `platform/ff-analytics/analytics-loader`, `V1`–`V3`.
  Absorbs: `1857652`.
- [ ] **S07-04 Lock the analytics tables to the tenant with RLS**
  Absorbs: `0c69d59`, `V4`.
- [ ] **S07-05 Roll deliveries up per tenant and month**
  Absorbs: `85ec349`, `d2361f8`, `V5`.

## S8 — Reporting and Superset

- [ ] **S08-01 Add the reporting module**
  Port: `logistics/reporting`, `V4`, `V6`.
- [ ] **S08-02 Sign Superset guest tokens with the tenant**
  Absorbs: `b0786c7`.
- [ ] **S08-03 Run Superset**
  Absorbs: `3584bc2`, `1fb19e8`.
- [ ] **S08-04 Provision dashboards as code**
  Port: `infra/local/superset/assets`.
  Absorbs: `2d48813`, `2d2c4e2`, `8c58526`, `V6` (territories and routes).

## S9 — Console

- [ ] **S09-01 Add the console skeleton behind nginx on one origin**
  Absorbs: `2f8b93f`, `b26da7f` (nginx DNS resolution).
- [ ] **S09-02 Sign in through the identity service, with a role guard**
- [ ] **S09-03 Add warehouse and shipment pages**
- [ ] **S09-04 Show planned routes on a map**
  Absorbs: `b851603`, `f7c47d2`.
- [ ] **S09-05 Embed the Superset dashboards**
  Absorbs: `f7aea54`.
- [ ] **S09-06 Add documents, e-CMR, KPI snapshots and tenant admin**
  Absorbs: `a261271`.

## S10 — Partner gateway: Northfield

- [ ] **S10-01 Add the gateway with inbox, outbox and TMS client**
  Absorbs: `2db41d3`, `V1`, `V2`.
- [ ] **S10-02 Fetch Northfield orders over OAuth2 and REST**
- [ ] **S10-03 Report status through Northfield's SOAP service, in order**
  Absorbs: `c14927f`, `87c2ee7`.
- [ ] **S10-04 Run the partner simulator in the cluster**
  Absorbs: `222b399`.
- [ ] **S10-05 Let one gateway instance run each round**
  Absorbs: `b10bdfb`.

## S11 — Partner gateway: Corvane

- [ ] **S11-01 Read and write EDIFACT (IFTMIN, IFTSTA, CONTRL)**
- [ ] **S11-02 Exchange files over SFTP with Camel and a pinned host key**
  Absorbs: `54f622c`, `bc6c177`. Host key generated into a Secret.
- [ ] **S11-03 Count a status file as accepted only after CONTRL**
  Absorbs: `1d616e9`, `V3`, `V4`.
- [ ] **S11-04 Run the SFTP simulator in the cluster**

## S12 — Tracing and progressive delivery

- [ ] **S12-01 Trace requests across services, Kafka and the outbox**
  Absorbs: `130408e`, gateway `V5`.
- [ ] **S12-02 Install Argo Rollouts through Argo CD**
- [ ] **S12-03 Release the logistics app blue-green**
  Preview Service, manual promotion.
- [ ] **S12-04 Switch to canary with an analysis**
  AnalysisTemplate on Actuator health or a Job; abort rolls back on its own.

## S13 — Multi-cluster, Workflows and Events

- [ ] **S13-01 Add a small staging cluster**
  Second k3d cluster without Superset, registered with `argocd cluster add`.
- [ ] **S13-02 Promote by commit with a matrix ApplicationSet**
  Git directories × clusters, `values-staging.yaml`.
- [ ] **S13-03 Run the monthly rollup as an Argo CronWorkflow**
- [ ] **S13-04 Start a workflow from a Kafka event with Argo Events**
  EventSource on `shipment-delivered` → Sensor → Workflow.
