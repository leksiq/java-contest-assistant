/*
 * MIT License
 * 
 * Copyright (c) 2020 Alexey Zakharov <leksi@leksi.net>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.leksi.contest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 *
 * @author alexei
 */
public class WizardOld {
    
    static public void main(final String[] args) throws IOException {
        new WizardOld().run(args);
//        new Wizard().run(new String[]{"A", "*in,m/ia[n]/ss/(m;lb[]/)ic[m]"});
//        new Wizard().run(new String[]{"-stdout", "A", "?in,h,m/{m;il,r,x/)"});
//        new Wizard().run(new String[]{"-stdout", "A", "?{2;ss}"});
//        new Wizard().run(new String[]{"-stdout", "A", "?in/ia[n]/ib[a[3]]/ic[]"});
    }

    private static void usage() {
        System.out.println("Usage: java java_options -jar net.leksi.contest.compiler.jar wizard_options class-name script");
        System.out.println("    java_options:           java options like -classpath;");
        System.out.println("    wizard_options:");
        System.out.println("        -stdout                 - write to stdout (default creates file <class-name>.java);");
        System.out.println("        -src <directory>        - the directory to generate source into (default .);");
        System.out.println("        -in <directory>         - the directory to generate input file into (default .);");
        System.out.println("        -infile <name>          - generate input file <name> (default <class-name>.in);");
        System.out.println("        -package package        - the package of class to generate (default empty);");
        System.out.println("        -force                  - overwrite existing files (default throws exception for source file) and leaves input file;");
        System.out.println("        -version                - shows current version and checks if it is latest, then returns;");
        System.out.println("        -usage, -help, ?        - shows this info, then returns;");
        System.out.println("    class-name:             name of class to generate;");
        System.out.println("    script:                 input script;");
    }

    private void version() {
        try {
            String[] version = new String[]{null, null};
            Enumeration<URL> resources = getClass().getClassLoader()
                    .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                    Manifest manifest = new Manifest(resources.nextElement().openStream());
                    Map<String, Attributes> entries = manifest.getEntries();
                    entries.keySet().stream().allMatch(k -> {
                        return entries.get(k).entrySet().stream().allMatch(e -> {
//                            System.out.println(">" + e.getKey() + "<");
                            if("Implementation-Version".equals(e.getKey().toString())) {
                                version[0] = e.getValue().toString();
                                return false;
                            }
                            return true;
                        });
                    });
            }
            System.out.println("Version");
            System.out.println("current: " + version[0]);
            final String path = "/leksiq/java-contest-assistant/releases/tag/";
            try (
                Reader r = new InputStreamReader((InputStream)new URL("https://github.com/leksiq/java-contest-assistant").getContent());
                BufferedReader br = new BufferedReader(r);
            ) {
                String line;
                while((line = br.readLine()) != null) {
                    if(line.contains("href=\"" + path)) {
                        version[1] = line.substring(line.indexOf(path) + path.length(), line.indexOf("\">"));
                        break;
                    }
                }
            }
            System.out.println("latest release: " + version[1]);
            int res = version[0].compareTo(version[1]);
            if(res < 0) {
                System.out.println("A newer version is released. To download visit:");
                System.out.println("https://github.com/leksiq/java-contest-assistant/releases/download/" + version[1] + "/net.leksi.contest.assistant.jar");
            } else if(res == 0) {
                System.out.println("The latest release version is installed.");
            } else {
                System.out.println("Your version is not a release yet.");
            }
        } catch (IOException E) {
            System.out.println("error occured: " + E.toString());
        }
    }
    
    interface IVariable {}
    static class Variable implements IVariable {
        String type;
        String name;
        String length;
        @Override
        public String toString() {
            return String.format("{V:%s;%s;%s}", type == null ? "-" : type, 
                name == null ? "-" : name, length == null ? "-" : length
            );
        }
    }
    static class Cycle implements IVariable {
        String count;
        ArrayList<IVariable> variables = new ArrayList<>();
        StringBuilder sb_class;
        String base;
        Cycle parent = null;
        String class_name;
        int field_gen = 0;
        Variable simple = null;
        boolean action = false;
        @Override
        public String toString() {
            return String.format("{C:%s;[%s]}", 
                count == null ? "-" : count, 
                variables.stream().map(Object::toString).collect(Collectors.joining(","))
            );
        }
    }
    static final String DELIMS = "/,;()[]{}";
    static final String TYPES = "ildstc";
    
    Stack<Cycle> cycles = new Stack<>();
    Stack<Cycle> data_cycles = new Stack<>();
    ArrayList<Cycle> all_cycles = new ArrayList<>();
    TreeSet<String> var_names = new TreeSet<>();

    private void run(String[] args) throws IOException {
        String pkg = null;
        String src = null;
        String in_dir = null;
        String in_name = null;
        String outfile = null;
        String class_name = null;
        String script = null;
        boolean stdout = false;
        boolean force = false;
        
        for(int i = 0 ; i < args.length; i++) {
            if(args[i].startsWith("-")) {
                if("-src".equals(args[i])) {
                    i++;
                    src = args[i];
                } else if("-package".equals(args[i])) {
                    i++;
                    pkg = args[i];
                } else if("-in".equals(args[i])) {
                    i++;
                    in_dir = args[i];
                } else if("-infile".equals(args[i])) {
                    i++;
                    in_name = args[i];
                } else if("-outfile".equals(args[i])) {
                    i++;
                    outfile = args[i];
                } else if("-stdout".equals(args[i])) {
                    stdout = true;
                } else if("-force".equals(args[i])) {
                    force = true;
                } else if("-version".equals(args[i])) {
                    version();
                    return;
                } else if("-usage".equals(args[i]) || "-help".equals(args[i]) || "-?".equals(args[i])) {
                    usage();
                    return;
                }
            } else if(class_name == null) {
                class_name = args[i];
            } else if(script == null) {
                script = args[i];
            } else {
                usage();
                return;
            }
        }
        if(class_name == null || script == null) {
            usage();
            return;
        }
        
        if(stdout) {
            if(force) {
                System.out.println("Warning: -stdout applied, -force ignored.");
            }
            if(in_dir != null) {
                in_dir = null;
                System.out.println("Warning: -stdout applied, -in ignored.");
            }
        } else {
            if(src == null) {
                src = ".";
            }
            if(in_dir == null) {
                in_dir = ".";
            }
            if(in_name == null) {
                in_name = class_name + ".in";
            }
        }
        
        Variable var = null;
        Cycle last_cycle = null;
        
        
        last_cycle = new Cycle();
        last_cycle.sb_class = new StringBuilder();
        last_cycle.action = true;
        cycles.push(last_cycle);
        last_cycle.count = "";
        all_cycles.add(last_cycle);
        
        
        boolean[] singleTest = new boolean[]{true};
        boolean[] localMultiTest = new boolean[]{false};
        int i = 0;
        if(script.charAt(0) == '+') {
            singleTest[0] = false;
            i++;
        } else if(script.charAt(0) == '?') {
            singleTest[0] = true;
            localMultiTest[0] = true;
            i++;
        }
        
        String wf = TYPES + "({";
        
        String type = null;
        boolean wait_for_name = false;
        boolean wait_for_wf = false;
        boolean wait_for_var = false;
        int[] class_gen = new int[]{0};
        
        for(; i < script.length(); i++) {
            char c = script.charAt(i);
            if(!Character.isSpaceChar(c)) {
//                System.out.println(c);
                if(!wait_for_name) {
                    if(wf.indexOf(c) < 0) {
                        throw new RuntimeException("Unexpected symbol: '" + c + 
                                "', expecting: " + Arrays.stream(wf.split("")).
                                        map(v -> "'" + v.charAt(0) + "'").
                                        collect(Collectors.joining(", ")) + ".");
                    }
                    if(TYPES.indexOf(c) >= 0) {
                        type = String.valueOf(c);
                        wf = "[/,;(){}";
                        wait_for_name = true;
                        wait_for_var = true;
                    } else {
                        switch(c) {
                            case '/':
                                var = new Variable();
                                var.type = "/";
                                cycles.peek().variables.add(var);
                                last_cycle = cycles.peek();
                                var = null;
                                wf = TYPES + "({" + (cycles.size() > 1 ? ")}" : "");
                                wait_for_name = false;
                                break;
                            case ',':
                                var = null;
                                wf = "[/,;(){}";
                                wait_for_name = true;
                                wait_for_var = true;
                                break;
                            case ';':
                                var = null;
                                wf = TYPES + "({";
                                wait_for_name = false;
                                break;
                            case '[':
                                if(var != null) {
                                    var.type += "[";
                                }
                                wf = "]";
                                wait_for_name = true;
                                wait_for_wf = true;
                                break;
                            case ']':
                                wf = "/,;(){}";
                                wait_for_name = false;
                                break;
                            case '(':
                            case '{':
                                last_cycle = new Cycle();
                                last_cycle.parent = cycles.peek();
                                last_cycle.action = c == '{';
                                cycles.peek().variables.add(last_cycle);
                                cycles.push(last_cycle);
                                if(!last_cycle.action) {
                                    data_cycles.push(last_cycle);
                                } else if(!data_cycles.isEmpty()) {
                                    throw new RuntimeException("An action cycle inside a data cycle (\"(...{...}...)\") is not allowed!");
                                }
                                all_cycles.add(last_cycle);
                                var = null;
                                wf = ";";
                                wait_for_name = true;
                                wait_for_wf = true;
                                break;
                            case ')':
                            case '}':
                                Cycle cy = cycles.pop();
                                if(!cy.action) {
                                    data_cycles.pop();
                                }
                                var = null;
                                wf = TYPES + "(){}/";
                                wait_for_name = false;
                                break;
                        }
                    }
                } else {
                    int j = i;
                    if(wait_for_wf) {
                        int brak = 0;
                        for(; j < script.length() && (script.charAt(j) != wf.charAt(0) || brak > 0); j++){
                            if(script.charAt(j) == '[') {
                                brak++;
                            } else if(script.charAt(j) == ']') {
                                brak--;
                            }
                        }
                        wait_for_wf = false;
                    } else {
                        for(; j < script.length() && DELIMS.indexOf(script.charAt(j)) < 0 && !Character.isSpaceChar(script.charAt(j)); j++){}
                    }
                    String name = script.substring(i, j);
//                    System.out.println(name);
                    if(wait_for_var) {
                        var = new Variable();
                        cycles.peek().variables.add(var);
                        last_cycle = cycles.peek();
                        if("".equals(name)) {
                            throw new RuntimeException("Unexpected char: '" + c + "', variable expected\n    \"" + script + "\"\n      " + String.format("%" + (i == 0 ? "" : i) + "s", "^"));
                        }
                        var.name = name;
                        var.type = type;
                        var_names.add(name);
                        wait_for_var = false;
                    } else if(";".equals(wf)) {
                        if("".equals(name)) {
                            throw new RuntimeException("Unexpected char: '" + c + "', variable or number expected\n    \"" + script + "\"\n      " + String.format("%" + (i == 0 ? "" : i) + "s", "^"));
                        }
                        cycles.peek().count = name;
                    } else if("]".equals(wf)) {
                        var.length = name;
                    }
                    for(; j < script.length() && Character.isSpaceChar(script.charAt(j)); j++){}
                    if(j < script.length() && wf.indexOf(script.charAt(j)) < 0) {
                        throw new RuntimeException("Unexpected symbol: '" + script.charAt(j) + "', expecting: \"" + wf + "\"");
                    }
                    i = j - 1;
                    wait_for_name = false;
                }
            }
        }
        IVariable last_var = last_cycle.variables.get(last_cycle.variables.size() - 1);
        if(!(last_var instanceof Variable) || !"/".equals(((Variable)last_var).type)) {
            var = new Variable();
            var.type = "/";
            cycles.get(0).variables.add(var);
        }
//        System.out.println(cycles);
        
        class Reenter {
            Consumer<Cycle> process;
        }
        
        Reenter reenter = new Reenter();

        StringBuilder sb0 = new StringBuilder();
        String banner = "!Please, Don't change or delete this comment!";
        String script1 = "$script$:" + script;
        int max_len = Math.max(script.length(), banner.length());
        if(banner.length() < max_len) {
            banner = String.format("%" + (max_len - banner.length()) / 2 + "s", "") + banner;
            banner += String.format("%" + (max_len - banner.length()) + "s", "");
        } else if(script1.length() < max_len) {
            script1 = String.format("%" + (max_len - script1.length()) / 2 + "s", "") + script1;
            script1 += String.format("%" + (max_len - script1.length()) + "s", "");
        }
        String stars = String.format("%" + max_len + "s", "").replace(" ", "*");
        sb0.append("/*").append(stars).append("*/\n");
        sb0.append("/*").append(banner).append("*/\n");
        sb0.append("/*").append(script1).append("*/\n");
        sb0.append("/*").append(stars).append("*/\n");
        if(pkg != null) {
            sb0.append("package ").append(pkg).append(";\n");
        }
        sb0.append("import java.io.IOException;\n");
        sb0.append("import net.leksi.contest.Solver;\n");
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        sb1.append("public class ").append(class_name).append(" extends Solver {\n");
        sb1.append("    public ").append(class_name).append("() {\n");
        if(singleTest[0]) {
            sb1.append("        singleTest = ").append(singleTest[0]).append(";\n");
        }
        if(!stdout || localMultiTest[0]) {
            sb1.append("        /*+Preprocess-DONOTCOPY*/\n");
            if(!stdout) {
                sb1.append("        localNameIn = \"").append(new File(in_dir, in_name).getPath().replace("\\", "/")).append("\";\n");
            }
            if(localMultiTest[0]) {
                sb1.append("        localMultiTest = true;\n");
            }
            sb1.append("        /*-Preprocess-DONOTCOPY*/\n");
        }
        sb1.append("    }\n");
        
        int[] indention = new int[]{1};
        
        Supplier<String> indent = () -> String.format("%" + (indention[0] * 4) + "s", "");
        UnaryOperator<String> type2 = s -> {
            String type1 = "int";
            switch (s.charAt(0)) {
                case 'c':
                    type1 = "char";
                    break;
                case 'i':
                    type1 = "int";
                    break;
                case 'l':
                    type1 = "long";
                    break;
                case 'd':
                    type1 = "double";
                    break;
                case 's':
                    type1 = "String";
                    break;
                case 't':
                    type1 = "Token";
                    break;
                default:
                    type1 = "int";
                    break;
            }
            return type1;
        };
        UnaryOperator<String> next2 = s -> {
            if(s.charAt(0) != 's') {
                String type1 = type2.apply(s);
                if(!"Token".equals(type1)) {
                    return type1.substring(0, 1).toUpperCase() + type1.substring(1);
                }
            }
            return "";
        };
        
        BiFunction<Cycle, Variable, String> find_variable = (cy, vv) -> {
            try {
                int len = Integer.valueOf(vv.length);
                return Integer.toString(len);
            } catch(NumberFormatException e) {
                Variable res = null;
                Cycle cyc = cy;
                while(true) {
                    res = (Variable)cy.variables.stream().filter(v -> v instanceof Variable && vv.length.equals(((Variable)v).name)).findFirst().orElse(null);
                    if(res != null) {
                        break;
                    }
                    cy = cy.parent;
                    if(cy == null) {
                        break;
                    }
                }
                return res != null ? (cy.base != null ? cy.base + "." : "") + res.name : "";
            }
        };
        
        BiConsumer<String, StringBuilder> write_code = (ind, sb) -> {
            sb.append(ind).append("/**************************/\n");
            sb.append(ind).append("/* Write your code below. */\n");
            sb.append(ind).append("/**************************/\n");
            sb.append(ind).append("\n");
            sb.append(ind).append("/**************************/\n");
        };
        
        boolean[] line_read = new boolean[]{false};
        
        reenter.process = (cycle) -> {
            String[] indent1 = new String[]{"    "};
            if(cycle.parent != null && cycle.simple == null && !cycle.action) {
                cycle.sb_class.append("    static class ").append(cycle.class_name).append(" {\n");
                indent1[0] += indent1[0];
            }
            indention[0]++;
            cycle.variables.forEach(v -> {
                if(v instanceof Variable) {
                    Variable vv = (Variable)v;
                    if(!"/".equals(vv.type)) {
                        String nextType;
                        String type1 = type2.apply(vv.type);
                        if("Token".equals(type1)) {
                            type1 = "String";
                        } else if("char".equals(type1)) {
                            type1 = "int";
                        }
                        String next = next2.apply(vv.type);
                        if(vv.type.endsWith("[")) {
                            type1 += "[]";
                        }
                        if(cycle.simple == null && !cycle.action) {
                            cycle.sb_class.append(indent1[0]).append(type1).append(" ").append(vv.name).append(";\n");
                        }
                        
                        if(cycle.simple == null || vv.length == null || "".equals(vv.length)) {
                            sb2.append(indent.get());
                            if(cycle.base != null) {
                                sb2.append(cycle.base);
                                if(cycle.simple == null) {
                                    sb2.append(".");
                                }
                            }
                            if(cycle.simple == null) {
                                if(cycle.action) {
                                    sb2.append(type1).append(" ");
                                }
                                sb2.append(vv.name);
                            }
                        }
                        if(vv.length != null) {
                            String count =  vv.length;
//                            String count =  find_variable.apply(cycle, vv);
                            if(!"".equals(vv.length)) {
                                if(cycle.simple == null || vv.length == null) {
                                    sb2.append(" = new ").append(type1.replace("[]", "")).append("[");
                                    sb2.append(count).append("]").append(";\n");
                                }
                                sb2.append(indent.get()).append("for(int ").append("_i_").append(vv.name).append(" = 0; _i_").
                                        append(vv.name).append(" < ").append(count).append("; _i_").append(vv.name).append("++) {\n");
                                indention[0]++;
                                sb2.append(indent.get());
                                if (cycle.base != null) {
                                    sb2.append(cycle.base);
                                    if (cycle.simple == null) {
                                        sb2.append(".");
                                    }
                                }
                                if (cycle.simple == null) {
                                    sb2.append(vv.name);
                                }
                                sb2.append("[").append("_i_").append(vv.name).append("] = sc.next").
                                        append(next).append("();\n");
                                indention[0]--;
                                sb2.append(indent.get()).append("}\n");
                            } else {
                                line_read[0] = true;
                                if(vv.type.charAt(0) == 's') {
                                    sb2.append(" = sc.nextLine().trim().split(\"\\\\s+\")");
                                } else {
                                    sb2.append(" = lineTo").append(next).append("Array()");
                                }
                                sb2.append(";\n");
                            }
                        } else {
                            if(vv.type.charAt(0) != 's') {
                                sb2.append(" = sc.next").append(next).append("();\n");
                                line_read[0] = false;
                            } else {
                                sb2.append(" = sc.nextLine().trim();\n");
                                line_read[0] = true;
                            }
                        }
                    } else {
                        if(!line_read[0]) {
                            sb2.append(indent.get()).append("sc.nextLine();\n");
                        }
                        line_read[0] = false;
                    }
                } else {
                    Cycle cy = (Cycle)v;
                    String field_name = null;
                    if(!cy.action) {
                        if(!cy.variables.stream().anyMatch(v1 -> (v1 instanceof Cycle)) && cy.variables.stream().filter(v1 -> !"/".equals(((Variable)v1).type)).count() <= 1) {
                            cy.simple = (Variable)cy.variables.stream().filter(v1 -> !"/".equals(((Variable)v1).type)).findFirst().get();
                            field_name = cy.simple.name;
                            cy.class_name = type2.apply(cy.simple.type);
    //                        cy.base = cycle.base;
                        } else {
                            do {
                                field_name = "f" + (cycle.field_gen++);
                            } while (var_names.contains(field_name));
                            cy.class_name = "Cy" + class_gen[0]++;
                            cy.sb_class = new StringBuilder();
                        }
                        sb2.append(indent.get());
                        String type1 = cy.simple != null ? type2.apply(cy.simple.type) : cy.class_name;
                        if("Token".equals(type1)) {
                            type1 = "String";
                        } else if("char".equals(type1)) {
                            type1 = "int";
                        }
                        String type3 = type1;
                        type1 += "[]";
                        sb2.append(type1);
                        if(cy.simple != null && cy.simple.length != null) {
                            sb2.append("[]");
                        }
                        sb2.append(" ");
                        cy.base = (cycle.base != null ? cycle.base + "." : "") + field_name + "[" + "_i" + field_name + "]";
                        if (cycle.base != null) {
                            sb2.append(cycle.base).append(".");
                        }
                        sb2.append(field_name).append(" = new ").append(type3).append("[").append(cy.count).append("]");
                        if (cy.simple != null && cy.simple.length != null) {
//                            sb2.append("[").append(find_variable.apply(cy, cy.simple)).append("]");
                            sb2.append("[").append(cy.simple.length).append("]");
                        }
                        sb2.append(";\n");
                    }
                    if(field_name == null) {
                        do {
                            field_name = "f" + (cycle.field_gen++);
                        } while (var_names.contains(field_name));
                        var_names.add(field_name);
                    }
                    if(cy.action) {
                        write_code.accept(indent.get(), sb2);
                    }
                    sb2.append(indent.get()).append("for(int ").append("_i").append(field_name).append(" = 0; ");
                    if(!"+".equals(cy.count)) {
                        sb2.append("_i").append(field_name).append(" < ").append(cy.count);
                    } else {
                        sb2.append("!sc.eof()");
                    }
                    sb2.append("; _i").append(field_name).append("++) {\n");
                    if(cy.simple == null && !cy.action) {
                        sb2.append(indent.get()).append("    ").append(field_name).append("[_i").append(field_name).append("").append("] = new ").append(cy.class_name).append("();\n");
                    }
                    reenter.process.accept(cy);
                    if(cy.action) {
                        write_code.accept(indent.get() + "    ", sb2);
                    }
                    sb2.append(indent.get()).append("}\n");
                    if(!cycle.action) {
                        cycle.sb_class.append(indent1[0]).append(cy.class_name);
                        if(cy.simple != null && cy.simple.length != null) {
                            cycle.sb_class.append("[]");
                        }
                        cycle.sb_class.append("[] ").append(field_name).append(";\n");
                    }
                }
            });
            if(cycle.parent != null && cycle.simple == null && !cycle.action) {
                cycle.sb_class.append("    ").append("}\n");
            }
            indention[0]--;
        };
        
        reenter.process.accept(all_cycles.get(0));

        sb1.append(all_cycles.stream().skip(1).filter(cy -> cy.simple == null && !cy.action).map(cy -> cy.sb_class).collect(Collectors.joining()));
        sb1.append("    @Override\n");
        sb1.append("    public void solve() throws IOException {\n");
        sb1.append(all_cycles.get(0).sb_class.toString().replace("    ", "        "));
        sb1.append(sb2);
        write_code.accept("        ", sb1);
        int saved_code_pos = sb1.length();
        sb1.append("    }\n");
        sb1.append("    static public void main(String[] args) throws IOException {\n");
        sb1.append("        new ").append(class_name).append("().run();\n");
        sb1.append("    }\n");
        sb1.append("}\n");
        sb1.insert(0, sb0);
        saved_code_pos += sb0.length();
        if(!stdout) {
            File src_file = null;
            if(outfile == null) {
                src_file = new File(src);
                if(pkg != null) {
                    for(String part: pkg.split("\\.")) {
                        src_file = new File(src_file, part);
                    }
                }
        //        System.out.println(src_file);
                if(!src_file.exists()) {
                    src_file.mkdirs();
                }
                src_file = new File(src_file, class_name + ".java");
                if(!force && src_file.exists()) {
                    throw new IOException("File exists: " + src_file + "! Use -force to overwrite.");
                }

                if(src_file.exists()) {
                    File save = null;
                    for(int j = 1; ; j++) {
                        save = new File(src_file.getPath() + ";" + Integer.toString(j));
                        if(!save.exists()) {
                            break;
                        }
                    }
                    src_file.renameTo(save);
                    String script2 = null;
                    try(
                        FileReader fr = new FileReader(save);
                        BufferedReader br = new BufferedReader(fr);
                    ) {
                        String line;
                        while((line = br.readLine()) != null) {
                            if(line.contains("$script$:")) {
                                script2 = line.trim();
                                script2 = script2.substring(script2.indexOf("/*"));
                                script2 = script2.substring(0, script2.lastIndexOf("*/"));
                                script2 = script2.trim();
                                script2 = script2.substring(script2.indexOf("$script$:") + "$script$:".length());
                                script2 = script2.trim();
                                break;
                            }
                        }
                    }
                    if (script2 != null) {
                        String[] args1 = new String[args.length + 2];
                        for(int j = 0; j < args.length; j++) {
                            if(args[j].equals(script)) {
                                args1[j] = script2;
                            } else {
                                args1[j] = args[j];
                            }
                        }
                        args1[args.length] = "-outfile";
                        File tmp = File.createTempFile("temp", null);
                        tmp.deleteOnExit();
                        args1[args.length + 1] = tmp.getAbsolutePath();
                        try {
                            new WizardOld().run(args1);
                            sb2.delete(0, sb2.length());
                            try (
                                    FileReader fr = new FileReader(save);
                                    BufferedReader br = new BufferedReader(fr);
                                    FileReader fr_tmp = new FileReader(tmp);
                                    BufferedReader br_tmp = new BufferedReader(fr_tmp);
                            ) {
                                String line;
                                String line_tmp = null;
                                boolean stopped = false;
                                boolean stopped_tmp = false;
                                ArrayList<String> saved_code = new ArrayList<>();
                                while (!stopped) {
                                    if(!stopped_tmp) {
                                        String line1 = br_tmp.readLine();
                                        if(line1 == null) {
                                            stopped_tmp = true;
                                        } else {
                                            if("".equals(line1.trim())) {
                                                continue;
                                            }
                                            line_tmp = line1;
                                        }
                                    }
                                    while(!stopped) {
                                        String line1 = br.readLine();
                                        if(line1 == null) {
                                            stopped = true;
                                            break;
                                        }
                                        line = line1;
                                        if(line.trim().equals(line_tmp.trim())) {
                                            if(sb2.toString().trim().length() > 0) {
                                                saved_code.add(sb2.toString());
                                                sb2.delete(0, sb2.length());
                                            }
                                            break;
                                        }
                                        sb2.append(line).append("\n");
                                    }
                                }
                                if(sb2.toString().trim().length() > 0) {
                                    saved_code.add(sb2.toString());
                                    sb2.delete(0, sb2.length());
                                }
                                for(int j = 0; j < saved_code.size(); j++) {
                                    sb2.append("\n");
                                    sb2.append("        /**** begin of piece #").append(j + 1).append("/").append(saved_code.size()).append(" of saved code ****/\n");
                                    sb2.append(saved_code.get(j));
                                    sb2.append("        /**** end of piece #").append(j + 1).append("/").append(saved_code.size()).append(" of saved code ****/\n");
                                    sb2.append("\n");
                                }
                                if(sb2.length() > 0) {
                                    sb1.insert(saved_code_pos, sb2.toString());
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Can not find changes. See previous version at " + save);
                        }
                    }
                }

                File in_file = new File(in_dir);
                if(!in_file.exists()) {
                    in_file.mkdirs();
                }
                in_file = new File(in_dir, in_name);
                if(!in_file.exists()) {
                    try (
                        FileWriter fw = new FileWriter(in_file);
                    ) {
                        fw.write("");
                    }
                }
            } else {
                src_file = new File(outfile);
            }
            try (
                FileWriter fw = new FileWriter(src_file);
            ) {
                fw.write(sb1.toString());
            }

        } else {
            System.out.println(sb1);
        }
    }
}