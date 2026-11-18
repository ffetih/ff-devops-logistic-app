# Deploy

Git is the desired state. Argo CD, running inside the cluster, makes the cluster match what is committed under `deploy/`. Only two things are applied by hand: the cluster itself and the first Argo CD install.

```text
deploy/
├── cluster/k3d-config.yaml   the local cluster: k3s version, host ports
├── argocd/                   Argo CD: pinned release, local patches, root Application
│   └── apps/                 what the root creates: AppProject, one Application per service
├── platform/argo-rollouts/   Argo Rollouts: pinned controller release
├── platform/postgres/        PostgreSQL 17: Deployment, volume, local credentials, NodePort 5432
├── platform/keycloak/        Keycloak 26: the ff-logistics realm, local credentials, NodePort 8180
├── charts/ff-service/        one Helm chart for every Spring service
└── apps/<name>/              per-service values: values.yaml, values-local.yaml, image-local.yaml
```

## Cluster

One k3d cluster in WSL. The single k3s server also runs the workloads. Traefik and the service load balancer are off; every service is a NodePort published on a fixed host port (see the table in `docs/backlog.md`).

Run these in WSL, from the repository root:

```bash
k3d cluster list                                       # an older cluster still holds the host ports? delete it first
k3d cluster create --config deploy/cluster/k3d-config.yaml
kubectl config current-context                         # k3d-ff-logistics
kubectl get nodes                                      # one node, Ready
```

Stop and start without losing data:

```bash
k3d cluster stop ff-logistics
k3d cluster start ff-logistics
```

`k3d cluster delete ff-logistics` removes the cluster and every volume in it.

### Local images

Before CI publishes images, load a locally built one straight into the node:

```bash
./build-all.sh
docker build -t ghcr.io/ffetih/ff-devops-logistic-app/logistics-app:local platform/ff-logistics/ff-logistics-app
docker run --rm -p 18081:8081 ghcr.io/ffetih/ff-devops-logistic-app/logistics-app:local   # then curl localhost:18081/actuator/health
k3d image import ghcr.io/ffetih/ff-devops-logistic-app/logistics-app:local -c ff-logistics
```

Host port 8081 belongs to the cluster, so a plain `docker run` uses another one. An imported image is used when the values set `image.tag: local` (the pull policy for that tag is `IfNotPresent`).

## Argo CD

Install the CLI in WSL, at the same version as the server:

```bash
curl -sSL -o argocd https://github.com/argoproj/argo-cd/releases/download/v3.6.1/argocd-linux-amd64
sudo install -m 555 argocd /usr/local/bin/argocd && rm argocd
argocd version --client
```

Install the pinned release from `deploy/argocd`:

```bash
kubectl apply -k deploy/argocd --server-side --force-conflicts
kubectl -n argocd rollout status deploy/argocd-server
kubectl -n argocd get pods                             # dex-server has 0 replicas on purpose
```

Server-side apply is needed because the Application CRD is larger than the annotation a client-side apply keeps. The same command upgrades Argo CD after the version in `deploy/argocd/kustomization.yaml` changes. From S1 on, Argo CD manages that folder itself.

The UI is at `http://localhost:8080` over plain HTTP (`server.insecure`, local only). User `admin`, first password:

```bash
argocd admin initial-password -n argocd
# without the CLI: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
argocd login localhost:8080 --plaintext --username admin
argocd account update-password
kubectl -n argocd delete secret argocd-initial-admin-secret   # after the password is changed
```

Local patches on top of the release:

| Patch | Why |
| :--- | :--- |
| `argocd-cmd-params-cm` | `server.insecure: "true"`, no self-signed HTTPS redirect on localhost |
| `argocd-server` Service | NodePort 30080, published by k3d on host port 8080 |
| `argocd-dex-server` | 0 replicas: only the local admin account signs in, so SSO is not needed |
| `argocd-cm` | `timeout.reconciliation: 60s`, jitter 10 s: Git is polled every minute instead of every three to four |
| `argocd-application-controller`, `argocd-repo-server` | A pod template annotation with the same value, so the sync that changes the poll also restarts the two pods that read it |

Argo CD learns about a merge by polling Git; nothing tells it. A pushed commit is seen on the next poll, now within about 70 seconds, and the **Refresh** button (or `argocd app get <name> --refresh`) asks at once. The two settings are read into environment variables at start, which is why the annotation exists: changing only the ConfigMap would leave the running pods on the old value until something restarted them. A webhook from GitHub would make it instant, but GitHub cannot reach `localhost:8080`; that comes with Argo Events (S14).

## Helm chart and values

Every Spring service is the same shape: a Deployment, a NodePort or ClusterIP Service, Actuator probes, a non-root read-only container. So there is one chart, `charts/ff-service`, and each service only brings values. Three files, later ones win:

```text
charts/ff-service/values.yaml          defaults for every service
apps/<name>/values.yaml                this service, every environment (image, port)
apps/<name>/values-local.yaml          this service on the local cluster (NodePort, rollout, env)
apps/<name>/image-local.yaml           the image tag on the local cluster, written by CI only
```

Render locally the way Argo CD will:

```bash
v=(-f deploy/apps/logistics-app/values.yaml -f deploy/apps/logistics-app/values-local.yaml -f deploy/apps/logistics-app/image-local.yaml)
helm lint deploy/charts/ff-service "${v[@]}"
helm template logistics-app deploy/charts/ff-service "${v[@]}"
```

The release name (`logistics-app`) names every object; in Argo CD it is the Application name.

## App of apps

`deploy/argocd/root.yaml` is the only Application applied by hand, and it comes with the Argo CD install (`kubectl apply -k deploy/argocd ...`). It points at `deploy/argocd/apps/` and creates everything there:

```text
ff-root  (auto sync: only Argo CD objects)
├── AppProject ff-logistics     sync wave -1: this repo, namespace ff-logistics only
├── Application argocd          manual sync: Argo CD itself, from deploy/argocd
├── Application argo-rollouts   manual sync: the Rollouts controller and CRDs
├── Application postgres        manual sync, wave -1: the database the services use
├── Application keycloak        manual sync, wave -1: the identity provider behind the identity service
└── ApplicationSet ff-services   one Application per folder under deploy/apps:
    ├── Application identity        manual sync: chart + apps/identity values (Deployment)
    └── Application logistics-app   manual sync: chart + apps/logistics-app values (Rollout)
```

### Argo CD manages itself

The `argocd` Application points at `deploy/argocd`, the same folder the first install came from. An upgrade is a pull request that changes the version in `deploy/argocd/kustomization.yaml`; after merge, sync `argocd` in the UI and review the diff first. Every object in that folder is annotated `Prune=false`, so even a sync with prune selected cannot remove Argo CD. Right after the first install the `argocd` Application shows `OutOfSync` on every object. The only difference is the `argocd.argoproj.io/tracking-id` annotation: Argo CD marks the objects it owns with it, and `kubectl apply` does not know about it. One manual sync adds the annotation and changes nothing else; Argo CD has then adopted its own installation. From there on, do not run `kubectl apply -k deploy/argocd` again: upgrades go through Git and a sync, and any later diff means the cluster and Git disagree about Argo CD itself.

On a fresh cluster the one `kubectl apply -k deploy/argocd` of the first install creates the root as well; after that, sync the Applications it creates:

```bash
argocd app list
argocd app sync argo-rollouts postgres keycloak
argocd app sync identity logistics-app
curl -s localhost:8081/actuator/health
```

A service deploys the image tag in its `image-local.yaml`. Until CI has published one, that is `local`, so build and import it first (see Local images above).

### One ApplicationSet for every service

`deploy/argocd/apps/services.yaml` is an ApplicationSet with a Git directory generator: every folder under `deploy/apps/` becomes an Application of the same name, rendered with the shared chart and that folder's three values files. Adding a service means adding a folder; nothing under `deploy/argocd` changes. The generator polls Git on its own timer, set to 60 seconds with `requeueAfterSeconds` to match the Applications.

```text
deploy/apps/identity/       ──►  Application identity
deploy/apps/logistics-app/  ──►  Application logistics-app
deploy/apps/<new>/          ──►  Application <new>   (on the generator's next poll, within a minute)
```

The platform pieces (Argo CD, Argo Rollouts, PostgreSQL, Keycloak) keep hand-written Applications: each is shaped differently, and there are few of them.

The generated Applications have no automated sync, like the hand-written ones before them. `preserveResourcesOnDeletion` keeps the workloads if the ApplicationSet itself is deleted. Removing a folder deletes the generated Application but, without a resources finalizer, not what it deployed: clean that up on purpose.

When this ApplicationSet replaced the hand-written `identity` and `logistics-app` Applications, `ff-root` pruned the old Application objects and the ApplicationSet created new ones with the same names. Neither step touched a pod: an Application without a finalizer leaves its resources behind when it is deleted, and a new Application of the same name recognises them as its own through the tracking annotation.

## CI and image tags

`.github/workflows/ci.yml` runs on every push and pull request:

```text
java       ./build-all.sh with tests, keeps the jar
manifests  helm lint + template for every deploy/apps/*, kustomize build of deploy/argocd
images     main only, after both pass, for each service this push changed (its folder, base/,
           the wrapper): build, push ghcr.io/ffetih/ff-devops-logistic-app/<name>:<commit sha>,
           write the sha into deploy/apps/<name>/image-local.yaml, commit with [skip ci]
```

From commit to cluster:

```text
merge PR ──► CI on main ──► image ghcr.io/ffetih/ff-devops-logistic-app/logistics-app:<sha>
                       └──► commit "Publish image tags" (image-local.yaml)
                                      │
                   Argo CD polls Git (about every minute)
                                      ▼
                       logistics-app OutOfSync ──► manual sync ──► new pod
```

A push that changes only values, charts or docs builds no image and makes no tag commit: the running image stays, and only the changed values reach the cluster on the next sync.

Before the first image push works, check two settings once:

- **Settings → Actions → General → Workflow permissions**: the `images` job asks for `contents: write` and `packages: write` itself, but a repository set to read-only with no override still blocks the tag commit. If the push is refused, choose *Read and write permissions*.
- **The GHCR package's visibility**: a new package is private. The cluster pulls without credentials, so after the first run open *Packages → ff-devops-logistic-app/logistics-app → Package settings → Change visibility → Public*. Otherwise the pod stays in `ImagePullBackOff`.

## Argo Rollouts

The controller comes from `deploy/platform/argo-rollouts` through the `argo-rollouts` Application. Sync it before the first Rollout reaches the cluster:

```bash
argocd app sync argo-rollouts && argocd app wait argo-rollouts --health
kubectl -n argo-rollouts get pods
```

The kubectl plugin talks to the same controller from WSL. Same version as the controller:

```bash
curl -sSL -o kubectl-argo-rollouts https://github.com/argoproj/argo-rollouts/releases/download/v1.10.2/kubectl-argo-rollouts-linux-amd64
sudo install -m 555 kubectl-argo-rollouts /usr/local/bin/kubectl-argo-rollouts && rm kubectl-argo-rollouts
kubectl argo rollouts version
```

Watch a rollout live, or open the dashboard on `http://localhost:3100`:

```bash
kubectl argo rollouts get rollout logistics-app -n ff-logistics --watch
kubectl argo rollouts dashboard
```

### Blue-green: the logistics app

`apps/logistics-app/values-local.yaml` turns the Rollout on with `strategy: blueGreen`. Two Services, and Argo Rollouts moves their selectors:

```text
                      before promote                       after promote
logistics-app         ──► blue  (old, NodePort 8081)        ──► green (new)
logistics-app-preview ──► green (new, inside the cluster)   ──► green
                                                             blue stays 30 s, then scales to 0
```

The first sync after the switch from Deployment to Rollout needs prune, so the old Deployment goes; until then both serve the same Service:

```bash
argocd app sync argo-rollouts && argocd app wait argo-rollouts --health
argocd app sync logistics-app --prune
kubectl argo rollouts get rollout logistics-app -n ff-logistics
```

The first revision has nothing to replace and goes live at once. Each later image tag waits on the preview Service; Argo CD shows the app `Suspended` meanwhile:

```bash
kubectl -n ff-logistics port-forward svc/logistics-app-preview 18081:8081   # second terminal
curl -s localhost:18081/actuator/info                                       # the new commit
curl -s localhost:8081/actuator/info                                        # still the old one
kubectl argo rollouts promote logistics-app -n ff-logistics                 # switch
kubectl argo rollouts abort logistics-app -n ff-logistics                   # or: drop the new version
```

`abort` leaves the Rollout `Degraded` while Git still asks for the new tag. Either promote later, or revert the tag in Git and sync.

### Canary with analysis: the logistics app

From S02-06 on, `values-local.yaml` releases the logistics app as a canary instead. Four pods, so each weight is whole pods:

```text
setWeight 25   1 canary pod, 3 stable          users: logistics-app ──► stable only
analysis       3 × GET /actuator/health/release on logistics-app-canary, 10 s apart
                 all UP  ──► go on                 one DOWN ──► abort, canary to 0 pods
setWeight 50   2 canary, 2 stable
pause 30s
setWeight 100  the canary becomes stable
```

Without an ingress or a service mesh there is no traffic split by percent. The weights decide how many pods run the new version; the main Service keeps users on the stable pods, and the canary Service gives the analysis the new pods alone.

Switching from blue-green removes the preview Service, so that sync needs prune too:

```bash
argocd app sync logistics-app --prune
kubectl argo rollouts get rollout logistics-app -n ff-logistics --watch
kubectl -n ff-logistics get analysisrun
```

A broken release on purpose: in `values-local.yaml` set `FF_RELEASE_HEALTHY` to `"false"`, commit, push, sync. The analysis run fails at 25 %, the rollout aborts and the canary pod goes away; users never left the stable pods. Argo CD shows `Degraded` because Git still asks for the broken release. Set it back to `"true"`, push and sync to recover.

## PostgreSQL

`deploy/platform/postgres` through the `postgres` Application: one PostgreSQL 17 server in the `ff-logistics` namespace, database `ff_logistics`, data on a k3s local-path volume.

```bash
argocd app sync postgres && argocd app wait postgres --health
kubectl -n ff-logistics exec deploy/postgres -- psql -U fflogistics -d ff_logistics -c 'select version()'
```

From WSL or Windows tools (IntelliJ database view, DBeaver): `localhost:5432`, database `ff_logistics`, user `fflogistics`, password `fflogisticsDb2026`. These are local-only values from `secret.yaml`.

```bash
sudo apt install -y postgresql-client     # once, for psql in WSL
PGPASSWORD=fflogisticsDb2026 psql -h localhost -U fflogistics ff_logistics
```

The volume claim `postgres-data` is never pruned and survives deleting the Application. `k3d cluster delete` removes it with the cluster. To start over with an empty database on purpose:

```bash
kubectl -n ff-logistics scale deploy/postgres --replicas=0
kubectl -n ff-logistics delete pvc postgres-data
argocd app sync postgres
```

### The logistics app and its database

In the cluster the app reads `LOGISTICS_DB_URL`, `LOGISTICS_DB_USERNAME` and `LOGISTICS_DB_PASSWORD` from `apps/logistics-app/values-local.yaml` (the last two from the `postgres-credentials` Secret). Flyway migrates `ff_logistics` from `src/main/resources/db/migration`.

From IntelliJ, the defaults in `application.yml` already point at the cluster's database on `localhost:5432`. Host port 8081 belongs to the cluster, so give the IDE run another port:

```text
Run configuration → LogisticsApplication → Program arguments:  --server.port=18081
```

The IDE run and the pods then share one database: both see the same schema history and the same rows.

### Migrations: a PreSync hook

`values-local.yaml` turns on `migration.enabled`. Every sync of `logistics-app` then runs in this order:

```text
PreSync   Job logistics-app-migrate   same image, profile migrate: Flyway migrates, the process exits
          ├── fails → the sync fails, nothing else is applied, the running version keeps serving
          └── succeeds ↓
Sync      Rollout, Services, ...      new pods start with SPRING_FLYWAY_ENABLED=false
```

```bash
argocd app sync logistics-app
kubectl -n ff-logistics get jobs
kubectl -n ff-logistics logs job/logistics-app-migrate
kubectl -n ff-logistics exec deploy/postgres -- psql -U fflogistics -d ff_logistics -c 'select version, description, success from flyway_schema_history'
```

The Job of the last sync stays until the next one replaces it (`BeforeHookCreation`), so its log can be read afterwards. The hook runs on every sync, also when only values changed; Flyway then finds nothing to do and the Job ends in seconds.

While the hook runs, and through a whole canary, the old version keeps serving on the new schema. A migration therefore has to work for both versions: add a column or a table first, use it in the next release, remove the old one a release later (expand, then contract).

## Keycloak

`deploy/platform/keycloak` through the `keycloak` Application: Keycloak 26 in dev mode with the `ff-logistics` realm imported at start.

```bash
argocd app sync keycloak && argocd app wait keycloak --health --timeout 300
curl -s localhost:8180/realms/ff-logistics | head -c 200; echo
```

| What | Where |
| :--- | :--- |
| Issuer in every token | `http://localhost:8180/realms/ff-logistics` (Keycloak's public address) |
| Inside the cluster | `http://keycloak:8080`: the identity service calls it there |
| Admin console | `http://localhost:8180/admin`, user `admin`, password `admin` (local only) |
| Realm users | `nexus.planner` (tenant `ff-eu-freight`), `geodispatch.operator` (tenant `ff-tr-distribution`), password `fflogistics2026` |
| Integration clients | `integration-ff-eu-freight.northfield`, `integration-ff-tr-distribution.corvane` (client credentials) |

Keycloak has one public address, `http://localhost:8180`: the browser opens it, every redirect points at it, and every token names it as issuer. A browser on this machine cannot resolve `keycloak`, so that name must never reach a page. The identity service still calls `http://keycloak:8080` inside the cluster; `--hostname-backchannel-dynamic` lets Keycloak answer those calls on the address they came in on, while the issuer stays the public one. On a real cluster the public address would be the ingress host, for example `https://login.example.com`.

Only the identity service calls Keycloak; the other services never see it. The realm lives in Git (`ff-logistics-realm.json`): a change there renames the generated ConfigMap, Keycloak restarts on the next sync and imports it. Dev mode keeps everything else inside the container, so users or clients created through the API are gone after a restart.

## The identity service

`platform/ff-identity/identity-service`, deployed with the shared chart (`apps/identity`) as a plain Deployment on host port 8085. It is the only process that calls Keycloak; the other services check tokens against its JWKS.

```bash
argocd app sync identity && argocd app wait identity --health
curl -s localhost:8085/actuator/health; echo

# sign in as a realm user: an access token and a refresh token
curl -s -X POST localhost:8085/sessions -H 'Content-Type: application/json' \
  -d '{"username":"nexus.planner","password":"fflogistics2026"}' | tee /tmp/session.json; echo

# what the token says: the tenant and the roles
python3 -c "import json,base64; t=json.load(open('/tmp/session.json'))['accessToken'].split('.')[1]; c=json.loads(base64.urlsafe_b64decode(t+'==')); print(c['iss'], c['tenant_id'], c['realm_access']['roles'])"

curl -s localhost:8085/jwks | head -c 200; echo
```

| Endpoint | Who |
| :--- | :--- |
| `POST /sessions`, `/sessions/refresh`, `/logout` | anyone: sign-in with username and password, refresh, sign-out |
| `POST /sessions/client` | a partner system with its client id and secret (client credentials) |
| `GET /jwks` | anyone: the keys the services verify tokens with |
| `POST /service-tokens` | a service holding the broker key |
| `/api/v1/users`, `/api/v1/integration-clients` | `TENANT_ADMIN`, inside their own tenant only |

### Calling the logistics app with a token

The logistics app checks every request outside Actuator against the identity service's keys (`IDENTITY_JWKS_URI`) and expects Keycloak's public address as issuer (`IDENTITY_TOKEN_ISSUER`). The tenant always comes from the token.

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8081/api/v1/me          # 401: no token

TOKEN=$(curl -s -X POST localhost:8085/sessions -H 'Content-Type: application/json' \
  -d '{"username":"nexus.planner","password":"fflogistics2026"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
curl -s localhost:8081/api/v1/me -H "Authorization: Bearer $TOKEN"; echo   # tenant, user, roles from the token
```

`geodispatch.operator` gets `ff-tr-distribution` instead: same app, same endpoint, another tenant. Probes, the canary analysis (`/actuator/health/release`) and `/actuator/info` stay open.

From IntelliJ with the `local` profile, `X-Tenant-Id` stands in for a token:

```text
Run configuration → Active profiles: local    Program arguments: --server.port=18081
curl -H 'X-Tenant-Id: ff-eu-freight' -H 'X-User-Roles: LOGISTICS_PLANNER' localhost:18081/api/v1/me
```
