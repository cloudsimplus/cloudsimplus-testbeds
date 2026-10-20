# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

CloudSim Plus Testbeds: more complex simulation scenarios that run an experiment many times with Pseudo Random Number Generators (PRNGs) to produce scientifically valid results (means, standard deviations, confidence intervals). It can apply the antithetic variates technique and the Batch Means Method to reduce variance and correlation. Unlike the `cloudsimplus-examples` module, the goal is valid research results, not showing framework features.

## Commands

Requires JDK 25 (`maven-compiler-plugin` uses `<release>25</release>`). The `.sdkmanrc` file sets Amazon Corretto 25 for [SDKMAN!](https://sdkman.io) users (`sdk env`).

```bash
./mvnw clean install        # build (CI runs: mvn -B package)
```

There are no tests and no Maven profiles or exec plugin. Each testbed is run through the `main` method of its `*Runner` class (or of an `*Experiment` class for a single isolated run), usually from the IDE. From the terminal, after a build:

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" org.cloudsimplus.testbeds.hostfaultinjection.HostFaultInjectionRunner
```

The jar manifest adds dependencies to the classpath using `~/.m2/repository/` paths, so `java -cp target/*.jar ...` also works on the machine that built it.

`license-maven-plugin` (`mvn license:check` / `license:format`) uses the header file `../COPYRIGHT`, which only resolves when run from a directory where that file exists (it is in `../cloudsimplus/COPYRIGHT`, not in the parent directory).

## Dependency on CloudSim Plus

- The **module version is the CloudSim Plus version** used (currently `<version>9.0.0-SNAPSHOT</version>`, and the dependency uses `${project.version}`). Change both together by changing only the project version.
- The base abstractions (`Experiment`, `ExperimentRunner`, `AbstractRunnable`, `ConfidenceInterval`, `ResultTable` in package `org.cloudsimplus.testbeds`) are **not in this repo**: they live in the framework (`../cloudsimplus/src/main/java/org/cloudsimplus/testbeds/`). Only `package-info.java` of that package is here.
- SNAPSHOT versions are resolved from the local Maven repository or from the Maven Central snapshots repository (declared in `pom.xml`). To use changes made in the sibling `../cloudsimplus` project, run `mvn install` there first.
- Only use libraries this project declares in `pom.xml`, not the ones that come indirectly from CloudSim Plus: they may be removed from the framework (e.g. Apache Commons Math 3 was replaced by Commons Statistics in 9.0.0).
- Logging is through Logback (`logback-classic`).

## Architecture

Each testbed is a package under `org.cloudsimplus.testbeds` with a pair of classes:

- `XxxExperiment extends Experiment<XxxExperiment>`: one simulation run. Template-method style: it implements `createBroker`, `createHost`, `createVm`, `createCloudlet(s)` and `printResults`; `Experiment.build()`/`run()` (final) wire the scenario. It is created with an index and its runner, and gets its PRNG seed from the runner.
- `XxxRunner extends ExperimentRunner<XxxExperiment>`: runs the experiment `simulationRuns` times. The constructor sets `baseSeed`, number of runs, number of batches (Batch Means) and whether to apply antithetic variates (e.g. `super(baseSeed, 400, 5, true)`). It implements `createExperimentInternal(index)` and `printSimulationParameters()`.

How a runner works (see `ExperimentRunner.run()` in the framework):

1. All experiments are created up front (needed because seeds are a shared list and runs may be parallel).
2. Seeds come from `getSeed(index)` / `createRandomGen(index, ...)`. With antithetic variates, the second half of the runs reuses the seeds of the first half (`isToReuseSeedFromFirstHalfOfExperiments`) to produce antithetic samples.
3. Each experiment registers a callback with `setAfterExperimentFinish(...)`; the runner collects per-run values with `addMetricValue(metricName, value)` into a metrics map keyed by metric name.
4. At the end, for each metric, `computeFinalStatistics` applies antithetic means and/or batch means (only if runs > batches), then builds a `ConfidenceInterval`; `ResultTable` prints the results as LaTeX and CSV tables.

Per-broker metrics are keyed by broker **name** (not instance), since each run creates new broker instances that must be matched across runs.

Current testbeds:

- `hostfaultinjection`: host failure injection and VM availability vs. SLA (based on Raysa Oliveira's MSc thesis).
- `sla/taskcompletiontime`: Cloudlet task completion time with and without a minimization policy, also with real workloads. Variants share `AbstractCloudletTaskCompletionTimeExperiment`.

Resources (`src/main/resources`, loaded via `ResourceLoader`/`SwfWorkloadFileReader`): SLA contracts in JSON (`SlaCustomerN.json`, `SlaMetrics.json`), `sla-files.txt` listing which SLA files are used (one broker per enabled file; lines starting with `#` are ignored), and SWF workload traces (`METACENTRUM-2009-2.swf`, `UniLu-Gaia-2014-2.swf`).
