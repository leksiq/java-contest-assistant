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
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author alexei
 */
public class WizardTest {
    
    public WizardTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    void walk_files(File file, Consumer<File> action) {
        if(file.exists()) {
            if(file.isDirectory()) {
                for(File f: file.listFiles()) {
                    walk_files(f, action);
                }
            } else if(file.getName().endsWith(".java")) {
                action.accept(file);
            }
        } else {
        }
    }

    String[] scripts = new String[] {
        "?in/ia[n][m][k][]",
        "+in(n;i)",
        "+n",
        "+in/{n;ix)",
        "+in/{n;ix})",
        "+in /",
        "+in,m/(n;{m;ix}/)",
        "+in,m/(n",
        "+in,m/(n;ix",
        "+in,m/(n;ix["
    };

    /**
     * Test of main method, of class Wizard.
     */
    @Test
    public void testMain() throws Exception {
        System.out.println("main");
        String[] args = new String[]{"-stdout", "A", null};
//        File in = new File("F:\\leksi\\contests\\codeforces.com\\archive_problems\\src");
        File in = new File("C:\\Users\\leksi\\Documents\\leksi\\contests\\codeforces.com\\archive_problems\\src");
        walk_files(in, f -> {
            try(
                    FileReader fr = new FileReader(f);
                    BufferedReader br = new BufferedReader(fr);
            ) {
                for(String line = br.readLine(); line != null; line = br.readLine()) {
                    line = line.trim();
                    if(line.startsWith("/*")) {
                        int pos = line.indexOf("$script$:");
                        if(pos >= 0) {
                            line = line.substring(pos + "$script$:".length()).trim();
                            pos = line.indexOf("*/");
                            if(pos >= 0) {
                                line = line.substring(0, pos).trim();
                            }
                            args[2] = line;
                            try {
                                System.out.println(line);
                                Wizard.main(args);
                            } catch (Exception ex){
                                System.err.println(line);
                                System.err.println(ex);
                            }
                            break;
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(WizardTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        for(String script: scripts) {
            args[2] = script;
            try {
                System.out.println(script);
                Wizard.main(args);
            } catch (Exception ex){
                System.err.println(script);
                System.err.println(ex);
            }
        }
    }
    
}
