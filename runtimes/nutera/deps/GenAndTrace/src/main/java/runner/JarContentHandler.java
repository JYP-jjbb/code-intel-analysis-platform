package runner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

public class JarContentHandler extends URLClassLoader {

    private Map<String, List<Class<?>>> contents;

    private JarContentHandler(URL[] urls) {
        super(urls);

    }

    public JarContentHandler(File jarFile) throws IOException, ClassNotFoundException {
        super(new URL[]{jarFile.toURI().toURL()});


        contents = new HashMap<String, List<Class<?>>>();

        List<Class<?>> interfaces = new ArrayList<Class<?>>();
        List<Class<?>> clazzes = new ArrayList<Class<?>>();
        List<Class<?>> enums = new ArrayList<Class<?>>();
        List<Class<?>> annotations = new ArrayList<Class<?>>();

        contents.put("interfaces", interfaces);
        contents.put("classes", clazzes);
        contents.put("annotations", annotations);
        contents.put("enums", enums);

        // Count the classes loaded

        // Your jar file
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
        } catch (FileNotFoundException e) {
            System.out.println("Could not find file " + jarFile);
            throw e;
        }

        // Getting the files into the jar
        Enumeration<? extends JarEntry> enumeration = jar.entries();

        // Iterates into the files in the jar file
        while (enumeration.hasMoreElements()) {
            ZipEntry zipEntry = enumeration.nextElement();

            // Is this a class?
            if (zipEntry.getName().endsWith(".class")) {

                // Relative path of file into the jar.
                String className = zipEntry.getName();

                // Complete class name
                className = className.replace(".class", "").replace("/", ".");
                // Load class definition from JVM
                Class<?> clazz = this.loadClass(className);

                try {
                    // Verify the type of the "class"
                    if (clazz.isInterface()) {
                        interfaces.add(clazz);
                    } else if (clazz.isAnnotation()) {
                        annotations.add(clazz);
                    } else if (clazz.isEnum()) {
                        enums.add(clazz);
                    } else {
                        clazzes.add(clazz);
                    }

                } catch (ClassCastException ignored) {

                }
            }
        }

    }

    public Map<String, List<Class<?>>> getContents() {
        return contents;
    }

    public void setContents(Map<String, List<Class<?>>> classes) {
        this.contents = classes;
    }

    public List<Class<?>> getClasses() {
        return contents.get("classes");
    }

}
