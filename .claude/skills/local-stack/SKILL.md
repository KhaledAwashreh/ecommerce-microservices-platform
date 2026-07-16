---
name: local-stack
description: Bring up, inspect, and tear down the local Docker Compose stack for this platform, and debug a service that will not start or returns errors. Use when asked to run the app locally, check service health, or read service logs.
---

# Local stack

## Start

```bash
docker-compose -f docker-compose.dev.yml up -d      # dev layout
docker-compose -f docker-compose.dev.yml up -d --build   # after code changes
./start.sh -b                                        # wrapper, same thing
```

`docker-compose.yaml` is the unified single-Postgres layout that mirrors the Kubernetes
setup; `docker-compose.dev.yml` is the dev layout. They differ. Confirm which one the
task means before editing either.

Java changes need a rebuild — the images bake the jar, so `up -d` alone reuses the old
build.

## Endpoints

| What | URL |
|------|-----|
| API Gateway | http://localhost:8765 |
| Frontend | http://localhost:3000 |
| Zipkin | http://localhost:9411 |
| RedisInsight | http://localhost:5540 |
| PostgreSQL | localhost:5433 (user `postgres`, dev password in compose) |

Backend services are not published to the host. Reach them through the gateway, or
`docker compose exec` into the network.

## Inspect

```bash
docker compose -f docker-compose.dev.yml ps
docker compose -f docker-compose.dev.yml logs -f <service>
docker compose -f docker-compose.dev.yml exec <service> sh
curl http://localhost:8765/actuator/health
```

## Debugging order

1. Is the container running, or restart-looping? `ps` shows the restart count.
2. Read the startup logs to the first stack trace — Spring failures cascade, only the
   first one matters.
3. Datasource failures — confirm the target database exists. The init scripts under
   `docker/postgres/init` create the per-service databases; a stale `postgres-data`
   volume will not re-run them. `docker compose down -v` forces a re-init and destroys
   local data.
4. A 404 through the gateway is usually a missing gateway route, not a missing handler.
   Check the gateway route config before the service.
5. Cross-service failures — check Zipkin for the trace, it shows which hop broke.

## Stop

```bash
docker-compose -f docker-compose.dev.yml down       # keep data
docker-compose -f docker-compose.dev.yml down -v    # wipe volumes, forces DB re-init
```
