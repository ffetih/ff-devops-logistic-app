# Deploy

Git is the desired state. Argo CD, running inside the cluster, makes the cluster match what is committed under `deploy/`. Only two things are applied by hand: the cluster itself and the first Argo CD install.

```text
deploy/
└── cluster/k3d-config.yaml   the local cluster: k3s version, host ports
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
