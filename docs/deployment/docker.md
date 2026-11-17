# Docker deployment

Run commands from the repository root. Docker Engine with Compose is required on
Linux. Windows uses Docker Desktop with the WSL 2 Linux backend. The existing WSL
distributions do not need to be deleted or imported.

## Start with the included MySQL

Linux:

```sh
sh deploy/deploy.sh init
sh deploy/deploy.sh up
```

Windows PowerShell:

```powershell
.\deploy\deploy.ps1 init
.\deploy\deploy.ps1 up
```

Open `http://127.0.0.1:18080`. Only nginx publishes a host port. The backend and
MySQL are accessible only on the Compose network. On first launch, complete
installation in the browser using the host-generated one-time installation code.
Choose the managed database option. Secrets are generated locally without
printing them and are never bundled in images. Preserve `deploy/secrets` along
with the volumes; changing these files does not reset an existing MySQL password.

The Java launcher provides fixed host management commands. Run these only in a
private host terminal: their one-time output is a credential and must not be
copied into logs or screenshots. Managed mode uses:

```sh
docker compose -f compose.yaml -f compose.mysql.yaml exec backend java -jar /app/backend.jar --installation-code
docker compose -f compose.yaml -f compose.mysql.yaml exec backend java -jar /app/backend.jar --recovery-code
```

External mode omits `-f compose.mysql.yaml`. Generate a recovery code only when
recovering the current instance. Codes are one-time, expiring credentials.

## Use an external MySQL

Linux:

```sh
sh deploy/deploy.sh up external
```

Windows:

```powershell
.\deploy\deploy.ps1 up -ExternalDatabase
```

This uses only `compose.yaml`, so no local db container or database secrets are
required. Choose external MySQL in the browser and supply structured connection
fields. `localhost` inside a container refers to that container. Use the database
server DNS name; Docker Desktop host services can use `host.docker.internal`.
For a Linux host database, configure a deliberate host gateway or routable
private address. Require TLS for remote databases.

For deliberate local model HTTP endpoints, set
`INSTALLATION_MODEL_HTTP_ALLOWLIST` in the host environment before starting
Compose, for example `model.internal,host.docker.internal`. This is a comma-separated
list of trusted hosts. It is deployment policy and cannot be changed by a browser
request. Leave it empty for the default restricted policy.

The equivalent explicit commands are:

```sh
docker compose -f compose.yaml -f compose.mysql.yaml up -d --build
docker compose -f compose.yaml up -d --build
```

Keep using the same file set and project name for subsequent operations.
Switching modes changes the connection target; it does not move application data.

## Ports, proxy, and TLS

`WEB_PORT` defaults to `18080`, and `WEB_BIND_ADDRESS` defaults to `127.0.0.1`.
`AI_INSTALLATION_TRUSTED_ORIGINS` defaults to
`http://localhost:18080,http://127.0.0.1:18080`. Set an exact comma-separated list
of browser origins when changing the port, address, hostname, or TLS termination.
For example, a TLS deployment can set
`AI_INSTALLATION_TRUSTED_ORIGINS=https://learning.example.edu`. Wildcard hosts
are not supported.
For a remote server, forward a local port over SSH or place a trusted TLS reverse
proxy in front of nginx. Do not send remote installation credentials over public
plain HTTP. When adding a TLS proxy, configure the application's trusted origins
and proxy policy deliberately; client-supplied forwarded headers are discarded
by the shipped nginx configuration.

Browser `/backend/` requests are proxied to the backend with the prefix removed
once, including `/backend/auth/*`, setup APIs, existing business APIs, SSE, and
uploads. Legacy `/uploads/` URLs are also proxied to the application.
SSE buffering is disabled and stream timeouts are 600 seconds. Uploads are limited
to 100 MiB. Downloads retain Range, Content-Disposition, and backend authorization.
RAG and upload volumes are never exposed as nginx static directories.

## Persistence and maintenance

The `eduloop` project owns separate named volumes:

| Volume | Container path | Purpose |
|---|---|---|
| `private-config` | `/app/config/private` | Instance identity, revisions, lifecycle, and private configuration |
| `uploads` | `/app/data/uploads` | Application uploads |
| `rag` | `/app/data/rag` | Native RAG originals and indexes |
| `mysql` | `/var/lib/mysql` | Included database, managed mode only |

The backend runs as UID/GID `10001:10001`; its private directory is mode `0700`.
Replacing named volumes with host bind mounts requires matching ownership and
permissions. Private configuration is mounted only into the backend.

Use `deploy.sh status` / `deploy.ps1 status` for container status, and `down` to
stop the project. These commands retain volumes. Do not use `down -v` for routine
maintenance. Stop writes before coordinated backups and preserve all four volumes
plus database credential files together. Use a database-aware logical backup for
online MySQL; copying a live MySQL directory is not a consistent backup.
Test restoration into a separate project before relying on a backup.

Apply settings in the administration page. The Java parent launcher manages the
application child lifecycle; no Docker socket is mounted and the web UI cannot
execute shell commands. A short reconnect interval during apply is expected.
Never run a second backend against the same private configuration volume.

## Upgrade and recover safely

Before upgrading, retain a coordinated backup of the database, instance private
configuration, uploads, RAG, and managed database secret files. Test the target
version and restoration in an isolated project first. Preserve the existing
Compose project name and volume set, then use the same `up` command to rebuild
and recreate services. Do not import `database/ai_academy_schema.sql` into an
existing instance: that development script can recreate its database.

After startup, confirm the existing installation identity, accounts, data, and
operation status. A database connection failure does not make the instance new.
Do not delete the private configuration volume, rerun setup against a blank
replacement database, or create a replacement administrator to bypass an
unavailable existing database.

For `RECOVERY_REQUIRED`, retain the failed operation and active revision. Use the
operation's recovery session or the fixed host `--recovery-code` command above,
then correct the configuration and explicitly apply it or choose the available
previous revision. No automatic provider switch or rollback is promised.
`APPLIED` must match the actual target revision after the worker is ready.
Code/schema upgrade restoration may require restoring the coordinated backup;
model configuration rollback alone does not revert a database schema or data.

This upgrade/recovery procedure is part of the current acceptance scope.
See the [implementation plan](../implementation/2026-11-05-installation-ui/PLAN.md)
and [validation boundary](../COLLABORATION_AND_VALIDATION.md) for pending checks;
this guide is not a completed acceptance report.

## Restricted-network source builds

A cold backend image build must download dependencies from Maven Central. If
the build container fails TLS/network access, that cold online build remains
blocked even if the host can reach the repository. Do not substitute a host
application JAR for the source build. A trusted existing Maven dependency cache
can instead seed BuildKit, followed by compilation and packaging inside Docker.

On Windows, prepare a local ignored context containing only dependency JAR/POM
files, excluding Maven settings and this application's installed artifacts:

```powershell
$seed = .\deploy\prepare-maven-seed.ps1
docker build --progress=plain -f deploy/maven-cache.Dockerfile $seed.Context
docker compose --progress=plain -f compose.yaml -f compose.mysql.yaml build --build-arg MAVEN_OFFLINE=true backend
```

The seed and application build must use the same Docker builder, because they
share its `eduloop-maven-repository` cache. Frontend construction still requires
its own npm network access/cache. Offline Maven packaging fails explicitly if
the dependency/plugin cache is incomplete. The normal Dockerfile default remains
online; offline success does not establish cold online build success.
After an offline backend build, start the same Compose file set with
`up -d --no-build` to avoid triggering a new online build.

Use only your trusted dependency cache. It is temporary local build input, not
an application backup or a published artifact. Do not send `.m2/settings.xml`,
proxy credentials, private environment files, or an existing product JAR as the
build context. No third-party mirror is required by this workaround.

## Image provenance and verification

Dockerfiles and Compose pin image tags and manifest digests. On 2026-11-05, the
Docker Hub official tag API confirmed Node `24.11.0-alpine3.22`, Maven
`3.9.11-eclipse-temurin-21`, Temurin `21.0.9_10-jdk-noble`, nginx `1.28.0-alpine`,
and MySQL `8.4.7`. Tag existence does not establish that this project builds or
runs: see the separate acceptance evidence for actual build and runtime results.

Official installation references:

- https://docs.docker.com/desktop/setup/install/windows-install/
- https://docs.docker.com/engine/install/
- https://docs.docker.com/compose/how-tos/use-secrets/
- https://hub.docker.com/_/node
- https://hub.docker.com/_/maven
- https://hub.docker.com/_/eclipse-temurin
- https://hub.docker.com/_/nginx
- https://hub.docker.com/_/mysql
