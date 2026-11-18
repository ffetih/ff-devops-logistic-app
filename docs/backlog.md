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

Memory budget for the 16 GB WSL VM, rough: Argo CD 1 GB, PostgreSQL 0.5 GB, Keycloak 1 GB, Kafka 1 GB, Superset 1.5 GB, four Spring services 0.5 GB each, console and simulators 0.5 GB. About 8 GB at the end of S12, which leaves room for the Rollouts preview pods (S02) and a small second cluster (S14).

## Left out on purpose

| Reference part | Why |
| :--- | :--- |
| `infra/local` Compose stack | One runtime only. It was also the reason realm and init files existed twice. |
| Flux (`deploy/gitops/flux`, `deploy/clusters`) | One controller: Argo CD |
| Metabase, Grafana, Prometheus | Removed from the reference cluster already. Prometheus may come back in S2 if a canary analysis needs metrics. |
| `test-live-apis.ps1` | Replaced by a PostSync smoke-test Job (S05) |
| Committed SFTP host key | Generated into a Secret (S12) |
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

- [x] **S01-01 Plan Argo Rollouts as sprint 2**
  Rollouts only needs an app that runs, so it moves up from the tracing sprint while the CAPA material is fresh.
- [x] **S01-02 Add the argocd CLI to the deploy guide**
- [x] **S01-03 Add the logistics app skeleton**
  Spring Boot app on 8081, Actuator health with probes, info with `git.properties`, virtual threads, a health test.
  Port: `platform/ff-logistics/ff-logistics-app/pom.xml`, `LogisticsApplication.java`.
- [x] **S01-04 Add a container image for the logistics app**
  Java 25 JRE, non-root user. Local path: build in WSL, `k3d image import`.
  Port: `ff-logistics-app/Dockerfile`, `.dockerignore`.
  Done when: the image runs and answers `/actuator/health`.
- [x] **S01-05 Add a shared Helm chart for Spring services**
  `deploy/charts/ff-service`: Deployment, Service, probes on Actuator, resources, security context, `enableServiceLinks: false`. The chart's `values.yaml` holds defaults; `deploy/apps/<name>/values.yaml` and `values-local.yaml` override them in that order.
  Absorbs: `a1f748a` (service links turn ports into strings).
  Done when: `helm lint` is clean and `helm template` renders.
- [x] **S01-06 Add an Argo CD project and the app of apps**
  `AppProject ff-logistics` limited to this repo and the `ff-logistics` namespace. Root Application `ff-root` → `deploy/argocd/apps/`, child Application `logistics-app` with Helm `valueFiles`. The root syncs on its own (it only holds Application objects); the apps it creates wait for a manual sync.
  Absorbs: `2c08413`, `a9e5782` (manual sync on the only cluster).
  Done when: `ff-root` creates `logistics-app`, a manual sync gives `Synced/Healthy` and `localhost:8081/actuator/health` is `UP`.
- [x] **S01-07 Let Argo CD manage its own installation**
  Application `argocd` → `deploy/argocd`, server-side apply, never pruned.
  Done when: changing a patch in `deploy/argocd` shows up as a diff in the UI.
- [x] **S01-08 Build and publish images on GitHub Actions**
  Same script as locally. On `main`, publish to `ghcr.io/ffetih/ff-devops-logistic-app/<name>:<sha>` and commit the tag into `values-local.yaml` with `[skip ci]`.
  Port: `.github/workflows/ci.yml`.
  Absorbs: `19ee090` (token for the registry login).
  Done when: a push to `main` produces a tag commit and Argo CD shows `logistics-app` `OutOfSync`.
- [x] **S01-09 Publish images under the repository's name**
  `ghcr.io/ffetih/ff-logistics-app` already belongs to the reference repository, so this repository's token got `permission_denied: write_package`. Images are now `ghcr.io/ffetih/ff-devops-logistic-app/<service>`: created by this repository's first push and linked to it.

## S2 — Progressive delivery with Argo Rollouts

Goal: a new version of the logistics app reaches users only after it proves itself, and goes back on its own when it does not.

Three pull requests, so each strategy can be watched on the cluster before the next one replaces it:

| PR | Items | What to try after merge and sync |
| :--- | :--- | :--- |
| A | S02-01 … S02-04 | Rollouts installed, the app runs as a blue-green Rollout |
| B | S02-05 | A new image arrives on the preview Service, promote it by hand |
| C | S02-06 | Canary with analysis: a healthy release goes through, a broken one aborts |

- [x] **S02-01 Note how Argo CD adopted its own installation**
  The first diff of the `argocd` Application was only `argocd.argoproj.io/tracking-id` on every object, because `kubectl apply` does not set it. One sync adds it; from then on Argo CD is upgraded through Git only.
- [x] **S02-02 Install Argo Rollouts through Argo CD**
  Pinned controller release through a kustomization, an Application in the app of apps, server-side apply for the CRDs. The `kubectl argo rollouts` plugin and its dashboard go into the deploy guide.
  Done when: `argo-rollouts` is `Synced/Healthy` and `kubectl argo rollouts version` shows the same version as the controller.
- [x] **S02-03 Let the chart render a Rollout instead of a Deployment**
  Chart option `rollout.enabled`; the Rollout keeps the same pod template, probes and Service. Off by default.
  Done when: CI renders both shapes.
- [x] **S02-04 Release the logistics app blue-green**
  Active Service `logistics-app` (NodePort 30081), preview Service `logistics-app-preview`, `autoPromotionEnabled: false`.
  Done when: after a sync with prune, the Deployment is gone and `kubectl argo rollouts get rollout logistics-app` shows one healthy revision.
- [x] **S02-05 Add a release health group that a switch can turn DOWN**
  `/actuator/health/release` answers 200 with `UP` or `DOWN`; `FF_RELEASE_HEALTHY=false` makes it `DOWN`. Liveness and readiness do not include it, so the pod stays Ready: the release is wrong, not the process.
  Done when: the new image waits on the preview Service, and `kubectl argo rollouts promote` switches the active Service.
- [x] **S02-06 Release canary with an analysis**
  Four replicas, steps 25% → analysis → 50% → 100%. The analysis reads `/actuator/health/release` through the canary Service three times. `FF_RELEASE_HEALTHY=false` in the values makes the release abort and scale back by itself.
  Done when: a healthy change reaches 100% without a hand on it, and a broken one aborts at 25%.
- [x] **S02-07 Keep the CI image tag in a file of its own**
  The bot rewrote `values-local.yaml` with `yq`, which also moved blank lines and comments, so PR C could not merge: a person and the bot had edited neighbouring lines. The tag now lives alone in `image-local.yaml`, read last, and CI rewrites that whole file. Nobody edits it by hand except to set `local`.

## S3 — Tenant, web and persistence base

Goal: the shared libraries exist and are tested; PostgreSQL runs in the cluster; the logistics app reaches it, and its schema is migrated by an Argo CD hook before new pods start. Business tables arrive with their modules (S5 on).

- [x] **S03-01 Publish an image only when its service changed**
  Every push to `main` used to build a new image, so a change to values alone started a full canary. CI now compares the push with what was there before and builds a service only when its folder, `base/` or the Maven Wrapper changed. Also moves `checkout` and `setup-java` to v5 (Node 20 is deprecated on the runners).
- [x] **S03-02 Add the tenant library**
  Port: `base/ff-base-tenant`.
  Absorbs: `4c37ac8` (one tenant id format).
- [x] **S03-03 Add RFC 9457 problem responses**
  Port: `base/ff-base-web`.
  Absorbs: `42db7f4` (one 503 handler for missing dependencies).
- [x] **S03-04 Add tenant-aware persistence**
  Hibernate `@TenantId`, database sequences, auto-configuration. Not used by the app until its first entity (S5).
  Port: `base/ff-base-persistence`.
- [x] **S03-05 Run PostgreSQL 17 in the cluster**
  Application `postgres` in the app of apps: a StatefulSet with a volume, a Secret with local-only credentials, NodePort 5432 for runs from the IDE.
  Port: `deploy/overlays/local/platform/postgres.yaml`.
  Done when: `psql -h localhost -U fflogistics ff_logistics` answers from WSL.
- [x] **S03-06 Connect the logistics app to PostgreSQL with Flyway**
  Datasource, Flyway and a first migration that only starts the schema history. A Testcontainers test runs the migrations on PostgreSQL 17 and is skipped where Docker is missing; the other tests use H2. The IDE runs against the cluster's database on `localhost:5432`.
  Absorbs: `c3247b7`, `846888f`.
- [x] **S03-07 Run migrations as a PreSync hook**
  A Job with the app image in the `migrate` profile: it migrates and exits. `hook-delete-policy: BeforeHookCreation`. The pods themselves do not run Flyway. Migrations must stay compatible with the version still running: the old pods keep serving while the hook runs.
  Done when: a sync shows the hook Job complete before the Rollout starts a new revision, and `flyway_schema_history` has the row.

## S4 — Identity

Goal: people and partner systems sign in through the identity service, and the logistics app trusts only the tokens it issues.

Three pull requests:

| PR | Items | What to try after merge and sync |
| :--- | :--- | :--- |
| A | S04-01 … S04-03 | Keycloak runs with the realm; a user signs in through the identity service and gets a token |
| fix | S04-04 | The admin console opens on `localhost:8180` |
| B | S04-05 … S04-06 | The logistics app answers 401 without a token and accepts one from the identity service |
| C | S04-07 … S04-08 | One ApplicationSet creates every service's Application; the guides build in CI |

- [x] **S04-01 Run Keycloak 26 with the logistics realm**
  Application `keycloak`: the realm and the user profile from `deploy/platform/keycloak`, imported at start. The admin console is on `localhost:8180`. Dev mode keeps its data in the container, so users created through the API are gone after a restart; the realm's own users come back.
  Port: `deploy/overlays/local/platform/keycloak*`, `infra/local/keycloak`.
  Absorbs: `f05ee5a` (only the realm file in the import folder).
- [x] **S04-02 Add the identity service**
  The only process that calls Keycloak: sign-in, refresh, sign-out, JWKS with cache and refresh, users of one tenant, integration clients with client credentials, service tokens. `KeycloakBroker` holds all of it, so it comes as one piece. Tested against an in-process fake and, where Docker runs, against Keycloak itself with the realm file.
  Port: `platform/ff-identity/identity-service` (without tracing: S13).
  Absorbs: `c0aee29`, `648d42b`, `be1fc9d`.
- [x] **S04-03 Deploy the identity service**
  Image, values for the shared chart (NodePort 8085), an Application, and CI building and publishing it.
  Done when: `curl -X POST localhost:8085/sessions` with a realm user returns an access token whose `tenant_id` is the user's tenant.
- [x] **S04-04 Give Keycloak one address for browsers and tokens**
  With `--hostname=http://keycloak:8080` the admin console loaded, but its sign-in sent the browser to `keycloak:8080`, a name that exists only inside the cluster ("Timeout when waiting for 3rd party check iframe message"). Keycloak's public address is now `http://localhost:8180` for pages, redirects and the token issuer; `--hostname-backchannel-dynamic` keeps the identity service's calls on `keycloak:8080`.
  Done when: `http://localhost:8180/admin` signs in, and a token from `POST /sessions` has `iss: http://localhost:8180/realms/ff-logistics`.
- [x] **S04-05 Add the security library**
  JWT resource server against the identity service's JWKS, tenant claim into `TenantContext`, `local` header fallback.
  Port: `base/ff-base-security`.
  Absorbs: `6e9ced3` (refuse an invalid tenant claim).
- [x] **S04-06 Protect the logistics app with tokens**
  The app uses the security, web and tenant libraries; everything but Actuator needs a token. `GET /api/v1/me` answers with the tenant, user and roles taken from the token. The `local` profile accepts `X-Tenant-Id` instead, for runs from the IDE only.
  Done when: `/api/v1/me` answers 401 without a token, and 200 with `tenantId: ff-eu-freight` with one from `POST /sessions`.
- [x] **S04-07 Generate service Applications with an ApplicationSet**
  Git directory generator over `deploy/apps/*`; the hand-written `logistics-app` and `identity` Applications go away.
  Done when: adding a folder under `deploy/apps/` creates an Application without touching Argo CD config.
- [ ] **S04-08 Build the documentation site in CI**
  AsciiDoc under `docs/src`, one guide per deployable, built and checked for warnings on every push.
  Port: `docs/build-docs.*`, `docs/src/index.adoc`, `_attributes.adoc`.
  Absorbs: `a87c1ec` (fail on warnings).

## S5 — Logistics core: standards and WMS

- [ ] **S05-01 Structure the app as Spring Modulith modules**
  `ModularityTest` with `ApplicationModules.verify()`.
- [ ] **S05-02 Add the standards module**
  GS1 Modulo-10 (GLN, GTIN, SSCC), ISO 6346 Modulo-11, EN 16258.
  Port: `logistics/standards`.
- [ ] **S05-03 Add warehouses, bin locations and inbound ASNs**
  Port: `logistics/wms`, `V1__wms_init.sql`.
- [ ] **S05-04 Palletize with SSCC labels from a sequence**
- [ ] **S05-05 Smoke-test the API as a PostSync hook**
  Replaces `test-live-apis.ps1`. A failing hook marks the sync failed.

## S6 — TMS and in-process events

- [ ] **S06-01 Add shipments with e-CMR, ISO 6346 equipment and CO2e**
  Port: `logistics/tms`, `V2__tms_init.sql`.
- [ ] **S06-02 Confirm delivery once under concurrency**
  Absorbs: `b6b8378`, `V5`, `V8`.
- [ ] **S06-03 Create one shipment per customer order number**
  Absorbs: `ee69a9c`, `V9`.
- [ ] **S06-04 Record pickup and departure**
  Absorbs: `59e4bc4`, `V10`.
- [ ] **S06-05 Publish ShipmentDelivered through the event publication registry**
  Absorbs: `2019e11`, `V7`, `0f986f0` (max attempts), `b1faf04` (advisory lock), `16c7a2a` (cleanup), `59d8170` (listener concurrency).

## S7 — Geo and documents

- [ ] **S07-01 Add territories and route plans**
  Absorbs: `e9580b7` (duplicate stop codes), `c2c8c8a` (99 stops), `V3`.
- [ ] **S07-02 Estimate travel with Haversine or OSRM**
  Absorbs: `59d8170` (OSRM timeout).
- [ ] **S07-03 Order routes with time windows using Timefold**
  Absorbs: `da10b36`, `0a4b9c0`.
- [ ] **S07-04 Add the storage library**
  Port: `base/ff-base-storage`.
  Absorbs: `d0f3003` (write once), `4e28bff` (document links).
- [ ] **S07-05 Run RustFS from its Helm chart as a multi-source Application**
  Upstream chart plus `$values` from this repo.
- [ ] **S07-06 Issue presigned upload and download links**
  Absorbs: `c503898`, `67c2c56`.

## S8 — Events out and the analytics database

- [ ] **S08-01 Run single-node Kafka (KRaft)**
- [ ] **S08-02 Externalize ShipmentDelivered and ShipmentProgressed**
  Absorbs: `f4a575f`.
- [ ] **S08-03 Add the analytics loader with a dead-letter topic**
  Port: `platform/ff-analytics/analytics-loader`, `V1`–`V3`.
  Absorbs: `1857652`.
- [ ] **S08-04 Lock the analytics tables to the tenant with RLS**
  Absorbs: `0c69d59`, `V4`.
- [ ] **S08-05 Roll deliveries up per tenant and month**
  Absorbs: `85ec349`, `d2361f8`, `V5`.

## S9 — Reporting and Superset

- [ ] **S09-01 Add the reporting module**
  Port: `logistics/reporting`, `V4`, `V6`.
- [ ] **S09-02 Sign Superset guest tokens with the tenant**
  Absorbs: `b0786c7`.
- [ ] **S09-03 Run Superset**
  Absorbs: `3584bc2`, `1fb19e8`.
- [ ] **S09-04 Provision dashboards as code**
  Port: `infra/local/superset/assets`.
  Absorbs: `2d48813`, `2d2c4e2`, `8c58526`, `V6` (territories and routes).

## S10 — Console

- [ ] **S10-01 Add the console skeleton behind nginx on one origin**
  Absorbs: `2f8b93f`, `b26da7f` (nginx DNS resolution).
- [ ] **S10-02 Sign in through the identity service, with a role guard**
- [ ] **S10-03 Add warehouse and shipment pages**
- [ ] **S10-04 Show planned routes on a map**
  Absorbs: `b851603`, `f7c47d2`.
- [ ] **S10-05 Embed the Superset dashboards**
  Absorbs: `f7aea54`.
- [ ] **S10-06 Add documents, e-CMR, KPI snapshots and tenant admin**
  Absorbs: `a261271`.

## S11 — Partner gateway: Northfield

- [ ] **S11-01 Add the gateway with inbox, outbox and TMS client**
  Absorbs: `2db41d3`, `V1`, `V2`.
- [ ] **S11-02 Fetch Northfield orders over OAuth2 and REST**
- [ ] **S11-03 Report status through Northfield's SOAP service, in order**
  Absorbs: `c14927f`, `87c2ee7`.
- [ ] **S11-04 Run the partner simulator in the cluster**
  Absorbs: `222b399`.
- [ ] **S11-05 Let one gateway instance run each round**
  Absorbs: `b10bdfb`.

## S12 — Partner gateway: Corvane

- [ ] **S12-01 Read and write EDIFACT (IFTMIN, IFTSTA, CONTRL)**
- [ ] **S12-02 Exchange files over SFTP with Camel and a pinned host key**
  Absorbs: `54f622c`, `bc6c177`. Host key generated into a Secret.
- [ ] **S12-03 Count a status file as accepted only after CONTRL**
  Absorbs: `1d616e9`, `V3`, `V4`.
- [ ] **S12-04 Run the SFTP simulator in the cluster**

## S13 — Tracing

- [ ] **S13-01 Trace requests across services, Kafka and the outbox**
  Absorbs: `130408e`, gateway `V5`.

## S14 — Multi-cluster, Workflows and Events

- [ ] **S14-01 Add a small staging cluster**
  Second k3d cluster without Superset, registered with `argocd cluster add`.
- [ ] **S14-02 Promote by commit with a matrix ApplicationSet**
  Git directories × clusters, `values-staging.yaml`.
- [ ] **S14-03 Run the monthly rollup as an Argo CronWorkflow**
- [ ] **S14-04 Start a workflow from a Kafka event with Argo Events**
  EventSource on `shipment-delivered` → Sensor → Workflow.
