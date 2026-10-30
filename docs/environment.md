# Environment used for the evidence

| Component | Version / value |
|---|---|
| OS | Windows 11 |
| Camunda 8 Run | 8.8.32 (bundled with Camunda Modeler 5.49.0 in `camunda-modeler-5.49.0-win-x64\c8run`) |
| Zeebe / Operate / Tasklist | 8.8.32 (single "camunda" process) |
| Connectors runtime | 8.8.15 |
| Secondary storage | Elasticsearch 8.17.3 (bundled) |
| Camunda Modeler | 5.49.0 |
| Java (lab apps) | JDK 25 runtime, compiled for Java 21 |
| Java (Camunda 8 Run) | JDK 23 bundled with Elasticsearch (`start-c8run.bat` sets `JAVA_HOME` to it, because Elasticsearch 8.17 does not support JDK 25) |
| Spring Boot | 3.5.16 |
| Camunda Spring Boot Starter | `io.camunda:camunda-spring-boot-starter:8.8.32` |
| Maven | 3.9.9 |
| Python (test runner) | 3.14 |
| Node (rendering / screenshots) | 24, with `bpmn-js` 18.31.1, `dmn-js` 18.0.1, `playwright-core` 1.64.1 driving the installed Microsoft Edge |

## Endpoints

| Endpoint | URL |
|---|---|
| Operate | http://localhost:8080/operate |
| Tasklist | http://localhost:8080/tasklist |
| Orchestration Cluster REST API | http://localhost:8080/v2/ (unprotected in the default C8 Run configuration) |
| Zeebe gRPC (workers) | localhost:26500 |
| Connectors runtime | http://localhost:8086 |
| Lab 1 app (workers + mock credit bureau) | http://localhost:8081 |
| Lab 2 app (workers + mock payment gateway) | http://localhost:8082 |
| Lab 3 app (workers + mock scheduling API) | http://localhost:8083 |

Operate and Tasklist use the local default user that Camunda 8 Run creates (`configuration/application.yaml` of the C8 Run folder).

## Starting Camunda 8 Run (Windows)

```bat
cd %USERPROFILE%\OneDrive\Documents\camunda-modeler-5.49.0-win-x64\c8run
start-c8run.bat
```

`c8run.exe stop` (in the same folder, with the same `JAVA_HOME`) shuts everything down.

## Screenshot capture

`scripts/render/capture.mjs` opens Operate and Tasklist in headless Microsoft Edge, logs in with the local user, and saves
PNGs into `<lab>/evidence/screenshots/`. `scripts/run_scenarios.py --screenshots` calls it at the moments each scenario
defines (instance waiting at a task, incident raised, after recovery, …). All screenshots therefore come from the live local stack.
