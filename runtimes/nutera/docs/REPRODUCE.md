# NuTERA — Reproduction

Run the artifact via Docker. All commands below assume you are at the repository root.

**API key (once per shell):**

```bash
export OPENROUTER_API_KEY="sk..."
```

(Use `OPENAI_API_KEY` if your config points to OpenAI.)

---

## Run with Docker

**Build:**

```bash
docker build -t nutera .
```

**One-shot run** (container is created, workflow runs, then the container is removed):

```bash
docker run --rm -it -e OPENROUTER_API_KEY nutera bash -lc '
cd /home
export JAVACHECKER_JAR="/home/deps/javachecker/build/libs/javachecker-uber.jar"
export LD_LIBRARY_PATH="/home/libs/linux-x64:${LD_LIBRARY_PATH}"
export JAVA_TOOL_OPTIONS="-Djava.library.path=/home/libs/linux-x64"
python3 main.py run \
  --problem-set-csv benchmarking/problem-sets/nuTerm_advantage_set.csv \
  --config config/default.yaml
'
```

**Reuse a container** (e.g. to resume or try another CSV). Create it once:

```bash
docker run -it --name nutera-run -e OPENROUTER_API_KEY nutera bash
```

Inside the container:

```bash
cd /home
export JAVACHECKER_JAR="/home/deps/javachecker/build/libs/javachecker-uber.jar"
export LD_LIBRARY_PATH="/home/libs/linux-x64:${LD_LIBRARY_PATH}"
export JAVA_TOOL_OPTIONS="-Djava.library.path=/home/libs/linux-x64"

python3 main.py run \
  --problem-set-csv benchmarking/problem-sets/nuTerm_advantage_set.csv \
  --config config/default.yaml
```

Exit the shell when done; the container stays stopped. To run again later:

```bash
docker start nutera-run
docker exec -it nutera-run bash -lc '
cd /home
export JAVACHECKER_JAR="/home/deps/javachecker/build/libs/javachecker-uber.jar"
export LD_LIBRARY_PATH="/home/libs/linux-x64:${LD_LIBRARY_PATH}"
export JAVA_TOOL_OPTIONS="-Djava.library.path=/home/libs/linux-x64"
python3 main.py run \
  --problem-set-csv benchmarking/problem-sets/nuTerm_advantage_set.csv \
  --config config/default.yaml \
  --resume-from results/checkpoints/after_synthesis_<timestamp>.pkl
'
```

To get results on the host, add a volume when creating the container, e.g. `-v "$(pwd)/results:/home/results"`. Pipeline writes under `results/` by default.

---

## Recommended benchmark

Small run (nuTerm advantage set):

```bash
python3 main.py run --problem-set-csv benchmarking/problem-sets/nuTerm_advantage_set.csv --config config/default.yaml
```

Full benchmark:

```bash
python3 main.py run --problem-set-csv benchmarking/problem-sets/six_sets.csv --config config/default.yaml
```

In Docker, run these after `docker run ... nutera bash` or via `docker exec -it nutera-run ...`; ensure the API key is exported and passed with `-e OPENROUTER_API_KEY`.

---

## Expected results

A successful run writes under `results/`:

- `results/final_summary.txt` — aggregate counts and percentages
- `results/program_level/program_level_after_synthesis_<timestamp>.csv` — per-program status
- `results/attempt_level/attempts_<timestamp>.csv` — per-attempt data
- `results/checkpoints/after_synthesis_<timestamp>.pkl` — checkpoint for `--resume-from`

Exit code 0 on success.

---

## Resume

After an interruption, use the same CSV and the checkpoint file:

```bash
python3 main.py run \
  --problem-set-csv benchmarking/problem-sets/nuTerm_advantage_set.csv \
  --config config/default.yaml \
  --resume-from results/checkpoints/after_synthesis_<timestamp>.pkl
```

Replace `<timestamp>` with the real checkpoint filename.

---

## Checker only

To verify a given ranking function without running the full pipeline, call the manual checker:

```bash
python3 checker/manual_rank_check.py \
  --jar "benchmarking/termination-crafted-lit/AliasDarteFeautrierGonnord-SAS2010-nestedLoop-1/AliasDarteFeautrierGonnordSAS2010nestedLoop1.jar" \
  --class "AliasDarteFeautrierGonnordSAS2010nestedLoop1" \
  --method "loop" \
  --auto-assume-from-main \
  --rank "[ReLU(n-i), ReLU(m-j) + ReLU(N-1-k)]"
```

This runs the checker on the specified JAR, class, method, and rank expression and prints YES/NO.

---

## Run-stage checker

Checker can also be invoked via `main.py` with a JSON params string:

```bash
python3 main.py run-stage checker \
  --params '{"jar_path":"benchmarking/.../Program.jar","class_name":"Program","method_name":"loop","rank_expr":"ReLU(n-i)"}'
```

---

## Troubleshooting

| Problem | Fix |
|--------|-----|
| `python: command not found` | Use `python3`. The image has no `python` alias. |
| LLM auth / API errors | Export the key in your shell before `docker run` and pass it with `-e OPENROUTER_API_KEY`. |
| No such container (e.g. `nutera-run`) | Create the container first with `docker run` (without `--rm`). Then use `docker start` and `docker exec` on that container. |
| Container exits right away | With `docker run --rm ... python3 main.py run ...`, the process runs and the container is removed. For a shell you can reuse, use `docker run -it --name nutera-run -e OPENROUTER_API_KEY nutera bash` and run the workflow inside. |
