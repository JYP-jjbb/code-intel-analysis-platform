package main;

import analyser.CFGAnalyser;
import jarhandling.JarContentHandler;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.LinkedList;


public class CalculateLoopHeads {

    public static List<Integer> calculateLoopHeadsHelper(String jar, String klass, String method) throws IOException, ClassNotFoundException {
        File f = new File(jar);
        JarContentHandler jarhandle = new JarContentHandler(f);
        return CFGAnalyser.loopHeaders(jarhandle, klass, method);

    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Options options = new Options();
        options.addRequiredOption("c","class", true, "Class with method.");
        options.addRequiredOption("m", "method", true, "Method name.");
        options.addRequiredOption("j", "jar", true, "Jar File with class and method.");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Trace", options, true);
            System.exit(0);
        }
        String jar = cmd.getOptionValue("jar");
        String className = cmd.getOptionValue("class");
        String methodName = cmd.getOptionValue("method");

        List<Integer> lh = calculateLoopHeadsHelper(jar, className, methodName);

        System.out.println(lh);

    }

}
