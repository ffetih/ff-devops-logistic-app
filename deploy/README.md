# Deploy

Git is the desired state. Argo CD, running inside the cluster, makes the cluster match what is committed under `deploy/`. Only two things are applied by hand: the cluster itself and the first Argo CD install.

```text
deploy/
├── cluster/k3d-config.yaml   the local cluster: k3s version, host ports
├── argocd/                   Argo CD: pinned release, local patches, root Application
│   └── apps/                 what the root creates: AppProject, one Application per service
├── charts/ff-service/        one Helm chart for every Spring service
└── apps/<name>/              per-service values: values.yaml, values-local.yaml
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
docker build -t ghcr.io/ffetih/ff-logistics-app:local platform/ff-logistics/ff-logistics-app
docker run --rm -p 18081:8081 ghcr.io/ffetih/ff-logistics-app:local   # then curl localhost:18081/actuator/health
k3d image import ghcr.io/ffetih/ff-logistics-app:local -c ff-logistics
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

## Helm chart and values

Every Spring service is the same shape: a Deployment, a NodePort or ClusterIP Service, Actuator probes, a non-root read-only container. So there is one chart, `charts/ff-service`, and each service only brings values. Three files, later ones win:

```text
charts/ff-service/values.yaml          defaults for every service
apps/<name>/values.yaml                this service, every environment (image, port)
apps/<name>/values-local.yaml          this service on the local cluster (NodePort, image tag)
```

Render locally the way Argo CD will:

```bash
helm lint deploy/charts/ff-service -f deploy/apps/logistics-app/values.yaml -f deploy/apps/logistics-app/values-local.yaml
helm template logistics-app deploy/charts/ff-service -f deploy/apps/logistics-app/values.yaml -f deploy/apps/logistics-app/values-local.yaml
```

The release name (`logistics-app`) names every object; in Argo CD it is the Application name.

## App of apps

`deploy/argocd/root.yaml` is the only Application applied by hand, and it comes with the Argo CD install (`kubectl apply -k deploy/argocd ...`). It points at `deploy/argocd/apps/` and creates everything there:

```text
ff-root  (auto sync: only Argo CD objects)
├── AppProject ff-logistics     sync wave -1: this repo, namespace ff-logistics only
├── Application argocd          manual sync: Argo CD itself, from deploy/argocd
└── Application logistics-app   manual sync: chart + apps/logistics-app values
```

### Argo CD manages itself

The `argocd` Application points at `deploy/argocd`, the same folder the first install came from. An upgrade is a pull request that changes the version in `deploy/argocd/kustomization.yaml`; after merge, sync `argocd` in the UI and review the diff first. Every object in that folder is annotated `Prune=false`, so even a sync with prune selected cannot remove Argo CD. After the first install it should show `Synced`; a diff there means the cluster and Git disagree about Argo CD itself.

After this sprint's branch is merged, re-run the install once to create the root:

```bash
kubectl apply -k deploy/argocd --server-side --force-conflicts
argocd app list                     # ff-root Synced, logistics-app OutOfSync
argocd app sync logistics-app
argocd app wait logistics-app --health
curl -s localhost:8081/actuator/health
```

`logistics-app` deploys the image tag in `values-local.yaml`. Until CI has published one, that is `local`, so build and import it first (see Local images above).
