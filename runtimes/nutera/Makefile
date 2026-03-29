# NuTERA Research Artifact — Build and Run Makefile
#
# Quick start:
#   make build-checker   Build javachecker + Z3 (required once before first run)
#   make run             Run the full benchmark
#   make smoke           Run 3-program smoke test to verify environment
#
# Requires: OPENROUTER_API_KEY or OPENAI_API_KEY set in environment for `run` and `smoke`.

# ===========================================================================
# Core Artifact Targets
# ===========================================================================

## Build the rank checker (javachecker + Z3). Run this once.
build-checker: z3 JavaChecker

## Run the full NuTERA benchmark (207 programs, 7 sets).
run:
	python3 main.py run \
		--problem-set-csv benchmarking/problem-sets/seven_sets.csv \
		--config config/default.yaml

## Run a 3-program smoke test to verify the environment.
smoke:
	python3 main.py run \
		--problem-set-csv benchmarking/problem-sets/smoke_test.csv \
		--config config/default.yaml

# ===========================================================================
# Build Dependencies
# ===========================================================================

z3:
	wget -P libs https://github.com/Z3Prover/z3/archive/refs/tags/z3-4.11.0.zip
	unzip -o libs/z3-4.11.0.zip -d libs
	mkdir -p ${PWD}/libs/z3-z3-4.11.0/build
	cd ${PWD}/libs/z3-z3-4.11.0/build && \
	cmake -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release \
				-DZ3_BUILD_JAVA_BINDINGS=True \
				-DZ3_INSTALL_JAVA_BINDINGS=True \
				-DCMAKE_INSTALL_PREFIX=${PWD}/libs/ .. && \
	make install
	patch -d deps/javachecker/ < libs/javachecker.patch

TraceAgentSO:
	cd ${PWD}/deps/traceAgent/ && cmake .
	make -C ${PWD}/deps/traceAgent
	mv ${PWD}/deps/traceAgent/libJavaMemTraceAgent.so ${PWD}/libs/libJavaMemTraceAgent.so

GenAndTraceJar:
	${PWD}/deps/GenAndTrace/gradlew fatJar -p ${PWD}/deps/GenAndTrace
	mv ${PWD}/deps/GenAndTrace/build/libs/GenAndTrace-1.0-SNAPSHOT.jar ${PWD}/libs/GenAndTrace.jar

CalculateLoopHeadsJar:
	${PWD}/deps/CalculateLoopHeads/gradlew fatJar -p ${PWD}/deps/CalculateLoopHeads
	mv ${PWD}/deps/CalculateLoopHeads/build/libs/CalculateLoopHeads-1.0-SNAPSHOT.jar ${PWD}/libs/CalculateLoopHeads.jar

JavaChecker:
	${PWD}/deps/javachecker/gradlew uberJar -p ${PWD}/deps/javachecker
	mv ${PWD}/deps/javachecker/build/libs/javachecker-uber.jar ${PWD}/libs

deps: TraceAgentSO GenAndTraceJar CalculateLoopHeadsJar JavaChecker
	git submodule update --init --recursive

# ===========================================================================
# Benchmark Suite Targets (JAR compilation only)
# ===========================================================================

## Compile benchmark JAR suites (needed only if JAR files are not pre-built).
bm_suite:
	make -C benchmarking/termination-suite/sv-comp/termination-crafted-lit
	make -C benchmarking/termination-suite/term-comp/Aprove_09
	make -C benchmarking/termination-suite/nuTerm-advantage/Java

.PHONY: build-checker run smoke z3 deps bm_suite
