# =============================================================================
# benchmarking/Makefile — Benchmark batch experiment runner
#
# This Makefile is NOT required for the core NuTERA artifact workflow.
# Core artifact entry points are in the repository root Makefile:
#   make run    (from repository root)
#   make smoke  (from repository root)
# =============================================================================

SHELL := /bin/bash

mk_folder:
	mkdir -p results

run_baseline1:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 51 -strategy anticorr_sumofrelu2 -stratargs nodes=5  --sampling pairanticorr -save results/baseline.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_sumofrelu2 -stratargs nodes=5  --sampling pairanticorr -save results/baseline.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 251 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling pairanticorr -save results/baseline.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 351 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling pairanticorr -save results/baseline.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 451 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling pairanticorr -save results/baseline.json




run_mvgaussian:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_sumofrelu2 -save results/complete_anticorr_sumofrelu2.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 251 -strategy anticorr_sumofrelu2 -save results/complete_anticorr_sumofrelu2.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 351 -strategy anticorr_sumofrelu2 -save results/complete_anticorr_sumofrelu2.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 451 -strategy anticorr_sumofrelu2 -save results/complete_anticorr_sumofrelu2.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 551 -strategy anticorr_sumofrelu2 -save results/complete_anticorr_sumofrelu2.json

run_baseline:
	./benchmark.sh

run_fuzz:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 51 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling fuzz -save results/complete_fuzzy.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling fuzz -save results/complete_fuzzy.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 251 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling fuzz -save results/complete_fuzzy.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 351 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling fuzz -save results/complete_fuzzy.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 451 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5  --sampling fuzz -save results/complete_fuzzy.json


run_pairanticorr:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 51 -strategy anticorr_sumofrelu2  --sampling pairanticorr -save results/complete_pairanticorr.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_sumofrelu2  --sampling pairanticorr -save results/complete_pairanticorr.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 251 -strategy anticorr_sumofrelu2  --sampling pairanticorr -save results/complete_pairanticorr.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 351 -strategy anticorr_sumofrelu2  --sampling pairanticorr -save results/complete_pairanticorr.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 451 -strategy anticorr_sumofrelu2  --sampling pairanticorr -save results/complete_pairanticorr.json

run_softplus_fuzz:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 51 -strategy anticorr_sumofsoftplus_dynamic_nodes -stratargs nodes=5 --sampling pairanticorr -save results/complete_soft_fuzzy1.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_sumofsoftplus_dynamic_nodes -stratargs nodes=5 --sampling pairanticorr -save results/complete_soft_fuzzy2.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 251 -strategy anticorr_sumofsoftplus_dynamic_nodes -stratargs nodes=3 --sampling fuzz -save results/complete_soft_fuzzy3.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 351 -strategy anticorr_sumofsoftplus_dynamic_nodes -stratargs nodes=4 --sampling fuzz -save results/complete_soft_fuzzy4.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 451 -strategy anticorr_sumofsoftplus_dynamic_nodes -stratargs nodes=5 --sampling fuzz -save results/complete_soft_fuzzy5.json

run_hybrid:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_sumofrelu2  --sampling hybrid -save results/complete_fuzzy1.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 251 -strategy anticorr_sumofrelu2  --sampling hybrid -save results/complete_fuzzy2.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 351 -strategy anticorr_sumofrelu2  --sampling hybrid -save results/complete_fuzzy3.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 451 -strategy anticorr_sumofrelu2  --sampling hybrid -save results/complete_fuzzy4.json
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 551 -strategy anticorr_sumofrelu2  --sampling hybrid -save results/complete_fuzzy5.json

run_pair_relu_anticorr:

	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_pair_sumofrelu2 -save results/anticorr_pair_sumofrelu2.json



run_experim: mk_folder run_mvgaussian

run_experim1: mk_folder run_fuzz

run_experim2: mk_folder run_nodes_1_10

run_experim3: mk_folder run_pairanticorr

run_experim4: mk_folder run_softplus_fuzz run_nodes_1_10

run_experim5: mk_folder run_pair_relu_anticorr

run_experim6: mk_folder
	${PYTHON3} run_benchmark.py -set ./problem-sets/three_sets.csv -seed 151 -strategy anticorr_expanded_feature_sumofrelu -save results/complete_expanded_feature_sumofrelu.json

run_experim7: mk_folder
	${PYTHON3} run_benchmark.py -set ./problem-sets/eight_sets.csv -seed 51 -strategy anticorr_sumofrelu_dynamic_nodes -stratargs nodes=5 -save results/eight_sets_benchmark.json
