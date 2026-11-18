# Deploy

Git is the desired state. Argo CD, running inside the cluster, makes the cluster match what is committed under `deploy/`. Only two things are applied by hand: the cluster itself and the first Argo CD install.

```text
deploy/
├── cluster/k3d-config.yaml   the local cluster: k3s version, host ports
└── argocd/                   Argo CD: pinned release and local patches
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
docker build -t ghcr.io/ffetih/ff-logistics-app:local platform/ff-logistics/ff-logistics-app
k3d image import ghcr.io/ffetih/ff-logistics-app:local -c ff-logistics
```

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
