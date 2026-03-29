package runner;

import exceptions.TraceSizeLimitReached;
import gen.*;
import org.apache.commons.cli.*;
import org.json.*;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting");

        Options options = new Options();
        options.addRequiredOption("c","class", true, "Class with method.");
        options.addRequiredOption("m", "method", true, "Method name.");
        options.addRequiredOption("j", "jar", true, "Jar File with class and method.");
        options.addOption("s", "samples", true, "Number of samples of a specific function to take");
        options.addOption("seed", "seed", true, "Seed for random objects. If not set, the seed will be randomly generated.");
        options.addOption("strategy", "strategy", true, "Strategy for generating sampling data");
        options.addOption("cegs", "cegs", true, "Strategy for generating sampling data");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Trace", options, true);
            System.exit(0);
        }
        File f = new File(cmd.getOptionValue("jar"));
        String className = cmd.getOptionValue("class");
        String methodName = cmd.getOptionValue("method");
        int samples = 1;
        if (cmd.hasOption("s")) {
            samples = Integer.parseInt(cmd.getOptionValue("s"));
        }

        JarContentHandler jar = new JarContentHandler(f);

        List<Class<?>> t = jar
                .getClasses()
                .stream()
                .filter(claz-> claz.getCanonicalName().equals(className)).collect(Collectors.toList());

        if (t.size() != 1) {
            System.out.println("Could not find class or classname was ambiguous.");
            System.exit(0);
        }

        Class<?> clazz = t.get(0);
        List<Method> methods = Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .collect(Collectors.toList());

        if (methods.size() != 1) {
            System.out.println("Could not find class or classname was ambiguous.");
            System.exit(0);
        }

        InputGenerator gen =null;
        Long seedOpt = cmd.hasOption("seed") ? Long.parseLong(cmd.getOptionValue("seed")) : null;
        int samples = cmd.hasOption("samples") ? Integer.parseInt(cmd.getOptionValue("samples")) : 1000;
        if (cmd.hasOption("strategy")) {
            if (cmd.getOptionValue("strategy").equals("gaussian")) {
                if (cmd.hasOption("seed")) {
                    gen = new GaussianIntegersER(Long.parseLong(cmd.getOptionValue("seed")));
                } else {
                    gen = new GaussianIntegersER();
                }
            } else if (cmd.getOptionValue("strategy").equals("mvgaussian")) {
                if (cmd.hasOption("seed")) {
                    gen = new MultivariateGaussian(Long.parseLong(cmd.getOptionValue("seed")));
                } else {
                    gen = new MultivariateGaussian();
                }
            } else if (cmd.getOptionValue("strategy").equals("pairanticorr")) {
              if (cmd.hasOption("seed")) {
                gen = new PairwiseAnticorrelated(Long.parseLong(cmd.getOptionValue("seed")));
              } else {
                gen = new PairwiseAnticorrelated();
              }
            } else if (cmd.getOptionValue("strategy").equals("singlevar")) {
                if (cmd.hasOption("seed")) {
                    gen = new SingleHighVariance(Long.parseLong(cmd.getOptionValue("seed")));
                } else {
                    gen = new SingleHighVariance();
                }
            } else if (cmd.getOptionValue("strategy").equals("fuzz")) {
                if (cmd.hasOption("seed")) {
                    gen = new FuzzingGen(Long.parseLong(cmd.getOptionValue("seed")));
                } else {
                    gen = new FuzzingGen();
                }
            }  else if (cmd.getOptionValue("strategy").equals("hybrid")) {
                        // hybrid 的两个可选参数（没有就用默认）
                int warmup = Math.max(32, (int)Math.round(samples * 0.30)); // 30% 作为高斯热启动
                int hybridCorpus = 128;
                if (cmd.hasOption("hybridWarmup")) {
                    warmup = Integer.parseInt(cmd.getOptionValue("hybridWarmup"));
                }
                if (cmd.hasOption("hybridCorpus")) {
                    hybridCorpus = Integer.parseInt(cmd.getOptionValue("hybridCorpus"));
                }
                System.out.printf("[HYBRID] samples=%d warmup=%d corpus=%d seed=%s%n", samples, warmup, hybridCorpus, String.valueOf(seedOpt));
                if (cmd.hasOption("seed")) {
                    gen = new HybridGaussianFuzzGen(Long.parseLong(cmd.getOptionValue("seed")), warmup, hybridCorpus);
                } else {
                    gen = new HybridGaussianFuzzGen(null, warmup, hybridCorpus);
                }
            }
            else { // in case we have a default strategy
                if (cmd.hasOption("seed")) {
                    gen = new EasyRandomGenHandler(Long.parseLong(cmd.getOptionValue("seed")));
                } else {
                    gen = new EasyRandomGenHandler();
                }
            }
        } else {
            if (cmd.hasOption("seed")) {
                gen = new EasyRandomGenHandler(Long.parseLong(cmd.getOptionValue("seed")));
            } else {
                gen = new EasyRandomGenHandler();
            }
        }

        System.out.println("Starting");
        if(cmd.hasOption("cegs")) { // If we have a CEGIS loop
            System.out.println("CEGS strategy");
            String argumentsFile = cmd.getOptionValue("cegs");

            Method method = methods.get(0); //
            Type[] types = method.getGenericParameterTypes();

            gen = new CEGSGenerator(argumentsFile);

            Object[] p = gen.nextSampleArguments(types);



            try {
                Main.invoke(method, p);
            } catch (InvocationTargetException ex) {
                Throwable e = ex.getCause();
                if (e instanceof TraceSizeLimitReached) {
                    //System.err.println("Caught ThreadDeath exception. Tracing of sample " + i + " was interrupted.");
                } else {
                    throw ex;
                }
            }

            if (cmd.hasOption("seed")) {
                gen = new CEGSMVGaussianGen(argumentsFile,Long.parseLong(cmd.getOptionValue("seed")));
            } else {
                gen = new CEGSMVGaussianGen(argumentsFile);
            }

            for (int i = 0; i < samples; ++i) {

                p = gen.nextSampleArguments(types);
                
                try {
                    Main.invoke(method, p);
                } catch (InvocationTargetException ex) {
                    Throwable e = ex.getCause();
                    if (e instanceof TraceSizeLimitReached) {
                        //System.err.println("Caught ThreadDeath exception. Tracing of sample " + i + " was interrupted.");
                    } else {
                        throw ex;
                    }
                }
            }

        } else {

            Method method = methods.get(0); //

            Type[] types = method.getGenericParameterTypes();


            for (int i = 0; i < samples; ++i) {

                Object[] p = gen.nextSampleArguments(types);
                try {
                    Main.invoke(method, p);
                } catch (InvocationTargetException ex) {
                    Throwable e = ex.getCause();
                    if (e instanceof TraceSizeLimitReached) {
                        //System.err.println("Caught ThreadDeath exception. Tracing of sample " + i + " was interrupted.");
                    } else {
                        throw ex;
                    }
                }
            }

            /*
            gen = new PairwiseAnticorrelated();

            for (int i = 0; i < samples/2; ++i) {

                Object[] p = gen.nextSampleArguments(types);
                try {
                    Main.invoke(method, p);
                } catch (InvocationTargetException ex) {
                    Throwable e = ex.getCause();
                    if (e instanceof TraceSizeLimitReached) {
                        //System.err.println("Caught ThreadDeath exception. Tracing of sample " + i + " was interrupted.");
                    } else {
                        throw ex;
                    }
                }
            }

            Object[] p = new Object[] {300, 0, 0};
            try {
                Main.invoke(method, p);
            } catch (InvocationTargetException ex) {
                Throwable e = ex.getCause();
                if (e instanceof TraceSizeLimitReached) {
                    //System.err.println("Caught ThreadDeath exception. Tracing of sample " + i + " was interrupted.");
                } else {
                    throw ex;
                }
            }
             */


        }

    }
    

    private static void defaultSamplingStrategy(Long seed){
        if (seed == null) {

        }

    }

    public static void invoke(Method m, Object[] p) throws InvocationTargetException, IllegalAccessException {
        m.invoke(null,p);
    }
}