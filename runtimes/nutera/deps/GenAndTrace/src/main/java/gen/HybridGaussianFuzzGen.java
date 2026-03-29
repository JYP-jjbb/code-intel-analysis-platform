package gen;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Hybrid strategy: first warm up with MultivariateGaussian to seed a corpus,
 * then switch to FuzzingGen which reuses & mutates these seeds.
 */
public class HybridGaussianFuzzGen implements InputGenerator {

    private final Random rnd;
    private final MultivariateGaussian gaussian;
    private final FuzzingGen fuzz;

    // 配置：暖启动样本数（或比例），语料池上限
    private final int warmupCount;       // 先用高斯产这么多样本作为“种子”
    private final int corpusLimit;       // 语料池最多保存的样本条数

    // 内部状态
    private int produced = 0;
    private final Deque<Object[]> corpus = new ArrayDeque<>();

    /**
     * @param seed           随机种子（可为 null 表示不设定）
     * @param warmupCount    暖启动数量（建议：samples 的 30% 左右或固定 64~128）
     * @param corpusLimit    语料池容量（建议：64~256）
     */
    public HybridGaussianFuzzGen(Long seed, int warmupCount, int corpusLimit) {
        if (seed != null) {
            this.rnd = new Random(seed);
            this.gaussian = new MultivariateGaussian(seed);
            this.fuzz = new FuzzingGen(seed);
        } else {
            this.rnd = new Random();
            this.gaussian = new MultivariateGaussian();
            this.fuzz = new FuzzingGen();
        }
        this.warmupCount = Math.max(0, warmupCount);
        this.corpusLimit = Math.max(8, corpusLimit);
    }

    public HybridGaussianFuzzGen() {
        this(null, 96, 128); // 默认：暖启动 96，语料池 128
    }

    @Override
    public Object[] nextSampleArguments(Type[] paramTypes) {
        // 阶段1：warm-up，用高斯直接产样，并放入语料池
        if (produced < warmupCount) {
            produced++;
            Object[] args = gaussian.nextSampleArguments(paramTypes);
            fuzz.pushSeed(args);
            return args;
        }

        return fuzz.nextSampleArguments(paramTypes);
    }

    private void pushCorpus(Object[] sample) {
        // 复制一份放入语料池
        Object[] cloned = sample.clone();
        corpus.addFirst(cloned);
        while (corpus.size() > corpusLimit) {
            corpus.removeLast();
        }
        // 可选：把本地 corpus 也同步给 fuzz（如果你的 FuzzingGen 暴露了 setCorpus 接口）
        // 这里示例一个“弱同步”：以一定概率把最近样本塞回 fuzz 内部流程（若你添加了该方法）
        // fuzz.pushSeed(cloned); // 若你在 FuzzingGen 里加了这个公开方法
    }
}
