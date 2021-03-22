package com.github.kayjamlang.web;

import com.fastcgi.FCGIInterface;
import com.github.kayjamlang.core.KayJamLexer;
import com.github.kayjamlang.core.KayJamParser;
import com.github.kayjamlang.core.containers.Container;
import com.github.kayjamlang.executor.Executor;
import com.github.kayjamlang.executor.libs.Library;
import com.github.kayjamlang.executor.libs.main.MainLibrary;
import org.apache.commons.codec.Charsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.lang.System.getProperty;
import static java.lang.System.out;

public class FastCGIApplication {

    public static void main(String[] args) {
        FCGIInterface fcgiinterface = new FCGIInterface();

        while(fcgiinterface.FCGIaccept()>=0) {
            File file = new File(getProperty("SCRIPT_FILENAME"));

            StringBuilder output = new StringBuilder();
            Map<String, String> headers = new HashMap<>();

            try {
                KayJamLexer lexer = new KayJamLexer("{"+read(file)+"}");
                KayJamParser parser = new KayJamParser(lexer);
                Container container = (Container) parser.readExpression();

                if(getProperty("REQUEST_METHOD").equals("POST")){
                    Scanner scanner = new Scanner(System.in, "UTF-8");
                    scanner.useDelimiter("[\\r\\n;]+");

                    StringBuilder postData = new StringBuilder();
                    while(scanner.hasNextLine())
                        postData.append(scanner.nextLine());

                    scanner.close();
                    container.data.put("post", postData.toString().trim());
                }else container.data.put("post", false);


                container.data.put("requestMethod", getProperty("REQUEST_METHOD"));
                container.data.put("headerPut", (WebLibrary.HeaderPut) headers::put);
                container.data.put("query", parseQuery(getProperty("QUERY_STRING")));

                Executor executor = new Executor();

                File libDir = new File(Paths.get(System.getProperty("user.dir"),
                        "./libs/").normalize().toString());
                if(libDir.exists()||libDir.createNewFile()){
                    File[] files = libDir.listFiles();
                    if(files!=null)
                        for(File libJar: files){
                            if(libJar.getName().endsWith(".jar"))
                                loadJarLibrary(executor, libJar);
                        }
                }

                executor.setUseGetFileListener(path -> {
                    File usedFile = new File(Paths.get(file.getParentFile()
                            .getAbsolutePath(), path)
                            .normalize().toString());

                    try {
                        return (Container) new KayJamParser(new KayJamLexer("{"+read(usedFile)+"}"))
                                .readExpression();
                    } catch (Exception e) {
                        output.append("<b>Error ")
                                .append(e.getClass().getSimpleName())
                                .append(":</b> ")
                                .append(e.getMessage()).append("</br>");
                    }

                    return null;
                });

                executor.addLibrary(new WebLibrary());
                executor.addLibrary(new MainLibrary(new MainLibrary.Output() {
                    @Override
                    public void print(Object value) {
                        output.append(value);
                    }

                    @Override
                    public void println(Object value) {
                        output.append(value);
                    }
                }));
                executor.execute(container);
            } catch (Throwable e) {
                output.append("<b>Error ")
                        .append(e.getClass().getSimpleName())
                        .append(":</b> ")
                        .append(e.getMessage()).append("</br>");
            }

            out.println("HTTP/1.1 200 OK");
            if(!headers.containsKey("content-type"))
                headers.put("content-type", "text/html; charset=UTF-8");

            headers.put("Content-Length", String.valueOf(output.toString()
                    .getBytes(StandardCharsets.UTF_8).length));
            for (String header_key: headers.keySet()){
                String header_value = headers.get(header_key);
                out.println(header_key+": "+header_value);
            }

            out.println();
            out.print(output.toString());
        }
    }

    public static void loadJarLibrary(Executor executor, File file) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        JarFile jarFile = new JarFile(file);
        Enumeration<JarEntry> e = jarFile.entries();

        URL[] urls = { new URL("jar:file:" + file.getPath()+"!/") };
        URLClassLoader cl = URLClassLoader.newInstance(urls);

        while (e.hasMoreElements()) {
            JarEntry je = e.nextElement();
            if(je.isDirectory() || !je.getName().endsWith(".class")){
                continue;
            }

            String className = je.getName().substring(0,je.getName().length()-6);
            className = className.replace('/', '.');
            Class<?> c = cl.loadClass(className);
            if(Library.class.isAssignableFrom(c))
                executor.addLibrary((Library) c.newInstance());
        }
    }

    private static String read(File file) throws IOException {
        Scanner reader = new Scanner(file, UniversalDetector.detectCharset(file));

        StringBuilder value = new StringBuilder();
        while (reader.hasNextLine()) {
            value.append(reader.nextLine()).append("\n");
        }
        reader.close();


        //Filter
        value = new StringBuilder(value.toString().replaceAll("\r", "")
                .replaceAll("\t", ""));
        if (value.toString().startsWith("\uFEFF")) {
            value = new StringBuilder(value.substring(1));
        }

        return value.toString();
    }

    private static Map<String, String> parseQuery(String string_query){
        List<NameValuePair> parameters = URLEncodedUtils.parse(string_query, Charsets.UTF_8);
        Map<String, String> map = new HashMap<>();

        for(NameValuePair parameter: parameters){
            map.put(parameter.getName(), parameter.getValue());
        }

        return map;
    }
}
