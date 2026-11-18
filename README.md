# FF Logistics — DevOps rebuild

A multi-tenant logistics platform (Java 25, Spring Boot 4.1, Spring Modulith 2.1), rebuilt sprint by sprint from the `ff-devops-app` reference implementation. Every change reaches a local k3d cluster through Git and Argo CD.

- Plan and progress: [docs/backlog.md](docs/backlog.md)
- Cluster and Argo CD: [deploy/README.md](deploy/README.md)
- Guides (AsciiDoc): [docs/src](docs/src), built with `./docs/build-docs.sh` into `docs/build/html`, and by CI as the `docs-html` artifact of every run

## Requirements

| Tool | Where | Version |
| :--- | :--- | :--- |
| JDK | Windows or WSL | 25 |
| Maven | downloaded by `mvnw` | 3.10.0 |
| Docker Engine | WSL | any recent |
| k3d | WSL | 5.9 |
| kubectl, helm, argocd CLI | WSL | current |

## Build

```bash
./build-all.sh              # WSL / Linux
build-all.bat               # Windows
./build-all.sh -DskipTests  # extra arguments go to every project after the POMs
```

There is no root `pom.xml`. Every project is an independent Maven project that inherits from `base/ff-base-parent`, and `build-all` installs them in dependency order.

## Layout

```text
base/          build time: BOM, parent POM and shared libraries
platform/      runtime: the services that get deployed
deploy/        cluster config, Argo CD, Helm charts and per-app values
docs/          backlog and guides
```
