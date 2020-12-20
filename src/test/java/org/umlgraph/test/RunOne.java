/*
 * UmlGraph class diagram testing framework
 *
 * Contibuted by Andrea Aime
 * (C) Copyright 2005 Diomidis Spinellis
 *
 * Permission to use, copy, and distribute this software and its
 * documentation for any purpose and without fee is hereby granted,
 * provided that the above copyright notice appear in all copies and that
 * both that copyright notice and this permission notice appear in
 * supporting documentation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 * MERCHANTIBILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 *
 */

package org.umlgraph.test;

import java.io.File;
import java.io.PrintWriter;
import java.util.spi.ToolProvider;

public class RunOne {

    static String testSourceFolder = "testdata/java/";

    static String testDestFolder = "testdata/dot-out";

    static PrintWriter pw = new PrintWriter(System.out);

    public static void main(String[] args)  {
	File outFolder = new File(testDestFolder);
        if (!outFolder.exists())
            outFolder.mkdirs();
	
//        runView("gr.spinellis.views.ViewChildEmpty");
        runSingleClass("TestHiddenOp");
    }
    
    public static void runView(String viewClass)  {
	String[] options = new String[] { "UMLGraph test","org.umlgraph.doclet.UmlGraphDoc" ,"-docletpath", "build", "-private", "-d",
		testDestFolder, "-sourcepath", "testdata/java", "-subpackages",
		"gr.spinellis", "-view", viewClass};
	runDoclet(options);
    }
    
    public static void runSingleClass(String className) {
	 String[] options = new String[] { "UMLGraph test","org.umlgraph.doclet.UmlGraphDoc" ,"-docletpath", "build", "-hide", "Hidden",
		    "-private", "-d", testDestFolder, "-output", className + ".dot", testSourceFolder + className + ".java"};
	 runDoclet(options);
    }

    private static void runDoclet(String[] options) {
        ToolProvider javadoc = ToolProvider.findFirst("javadoc").orElseThrow();

        int result = javadoc.run(pw, pw, options);
    }

    
}
