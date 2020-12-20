/*
 * Create a graphviz graph based on the classes in the specified java
 * source files.
 *
 * (C) Copyright 2002-2010 Diomidis Spinellis
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

package org.umlgraph.doclet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

//import com.sun.javadoc.ClassDoc;
import javax.lang.model.element.TypeElement;

//import com.sun.javadoc.Doc;
import 	javax.lang.model.element.Element;

//import com.sun.javadoc.RootDoc;
import jdk.javadoc.doclet.DocletEnvironment;

import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Reporter;
import org.umlgraph.model.*;
import org.umlgraph.model.views.View;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;

/**
 * Doclet API implementation
 * @depend - - - OptionProvider
 * @depend - - - Options
 * @depend - - - View
 * @depend - - - ClassGraph
 * @depend - - - Version
 *
 * @version $Revision$
 * @author <a href="http://www.spinellis.gr">Diomidis Spinellis</a>
 */
public class UmlGraph {
//
//    private static final String programName = "UmlGraph";
//    private static final String docletName = "org.umlgraph.doclet.UmlGraph";
//
//
//    /** Options used for commenting nodes */
//    private static Options commentOptions;
//
//	/**
//	 * Provides error, warning and notice reporting.
//	 */
//	Reporter reporter;
//
//	private String overviewfile;
//
//	@Override
//	public void init(Locale locale, Reporter reporter) {
//		reporter.print(Diagnostic.Kind.NOTE, "Doclet using locale: " + locale);
//		this.reporter = reporter;
//	}
//
//	@Override
//	public String getName() {
//		return programName;
//	}
//
//	@Override
//	public Set<? extends Option> getSupportedOptions() {
//		Option[] options = {
//				new Option() {
//					private final List<String> someOption = Arrays.asList(
//							"-overviewfile",
//							"--overview-file",
//							"-o"
//					);
//
//					@Override
//					public int getArgumentCount() {
//						return 1;
//					}
//
//					@Override
//					public String getDescription() {
//						return "an option with aliases";
//					}
//
//					@Override
//					public Option.Kind getKind() {
//						return Option.Kind.STANDARD;
//					}
//
//					@Override
//					public List<String> getNames() {
//						return someOption;
//					}
//
//					@Override
//					public String getParameters() {
//						return "file";
//					}
//
//					@Override
//					public boolean process(String opt, List<String> arguments) {
//						overviewfile = arguments.get(0);
//						return true;
//					}
//				}
//		};
//		return new HashSet<>(Arrays.asList(options));
//	}
//
//	@Override
//	public SourceVersion getSupportedSourceVersion() {
//		// support the latest release
//		return SourceVersion.latest();
//	}
//
//	/** Entry point through javadoc */
//	@Override
//	public boolean run(DocletEnvironment environment) {
//
//		Set<? extends Element> els = environment.getSpecifiedElements();
//		DocTrees doctrees = environment.getDocTrees();
//
//
//		Options opt = buildOptions(getSupportedOptions());
////	    root.printNotice("UMLGraph doclet version " + Version.VERSION + " started");
//
//	   View[] views = buildViews(opt, root, root);
//	   if(views == null)
//	    return false;
//	   if (views.length == 0)
//	      buildGraph(root, opt, null);
//	   else
//	      for (int i = 0; i < views.length; i++)
//		    buildGraph(root, views[i], null);
//	return true;
//	}
//
////    public static boolean start(RootDoc root) throws IOException {
////	Options opt = buildOptions(root);
////	root.printNotice("UMLGraph doclet version " + Version.VERSION + " started");
////
////	View[] views = buildViews(opt, root, root);
////	if(views == null)
////	    return false;
////	if (views.length == 0)
////	    buildGraph(root, opt, null);
////	else
////	    for (int i = 0; i < views.length; i++)
////		buildGraph(root, views[i], null);
////	return true;
////    }
//
//    public static void main(String args[]) {
//	var err = new PrintWriter(System.err);
//        com.sun.tools.javadoc.Main.execute(programName,
//	  err, err, err, docletName, args);
//    }
//
//    public static Options getCommentOptions() {
//    	return commentOptions;
//    }
//
//	/**
//	 * Creates the base Options object.
//	 * This contains both the options specified on the command
//	 * line and the ones specified in the UMLOptions class, if available.
//	 * Also create the globally accessible commentOptions object.
//	 */
//	public static Options buildOptions(Set<? extends Option> options) {
//		commentOptions = new Options();
//
////		options.stream().map(Option::getDescription).collect();
//
////		commentOptions.setOptions(root.options());
////		commentOptions.setOptions(findClass(root, "UMLNoteOptions"));
//		commentOptions.shape = Shape.NOTE;
//
//		Options opt = new Options();
////		opt.setOptions(root.options());
////		opt.setOptions(findClass(root, "UMLOptions"));
//		return opt;
//	}
//
//
//	/**
//	 //     * Creates the base Options object.
//	 //     * This contains both the options specified on the command
//	 //     * line and the ones specified in the UMLOptions class, if available.
//	 //     * Also create the globally accessible commentOptions object.
//	 //     */
////    public static Options buildOptions(RootDoc root) {
////	commentOptions = new Options();
////	commentOptions.setOptions(root.options());
////	commentOptions.setOptions(findClass(root, "UMLNoteOptions"));
////	commentOptions.shape = Shape.NOTE;
////
////	Options opt = new Options();
////	opt.setOptions(root.options());
////	opt.setOptions(findClass(root, "UMLOptions"));
////	return opt;
////    }
//
//
//
//
//    /** Return the TypeElement for the specified class; null if not found. */
//    private static Optional<TypeElement> findClass(DocletEnvironment env, String name) {
//		Set<? extends TypeElement> els = env.getElementUtils().getAllTypeElements(name);
//
//		TypeElement[] tp = els.toArray(new TypeElement[els.size()]);
//
//		if(tp.length > 0){
//			return Optional.of(tp[0]);
//		}
//		else{
//			return Optional.empty();
//		}
//    }
//
//    /**
//     * Builds and outputs a single graph according to the view overrides
//     */
//    public static void buildGraph(DocletEnvironment env, OptionProvider op, Element contextDoc) throws IOException {
//	if(getCommentOptions() == null)
//	    buildOptions(root);
//	Options opt = op.getGlobalOptions();
//	root.printNotice("Building " + op.getDisplayName());
//		TypeElement[] classes = root.classes();
//
//	ClassGraph c = new ClassGraph(root, op, contextDoc);
//	c.prologue();
//	for (ClassDoc cd : classes)
//	    c.printClass(cd, true);
//	for (ClassDoc cd : classes)
//	    c.printRelations(cd);
//	if(opt.inferRelationships)
//	    for (ClassDoc cd : classes)
//		c.printInferredRelations(cd);
//        if(opt.inferDependencies)
//	    for (ClassDoc cd : classes)
//		c.printInferredDependencies(cd);
//
//	c.printExtraClasses(root);
//	c.epilogue();
//    }
//
//    /**
//     * Builds the views according to the parameters on the command line
//     * @param opt The options
//     * @param srcRootDoc The RootDoc for the source classes
//     * @param viewRootDoc The RootDoc for the view classes (may be
//     *                different, or may be the same as the srcRootDoc)
//     */
////    public static View[] buildViews(DocletEnvironment env ,Options opt, RootDoc srcRootDoc, RootDoc viewRootDoc) {
//    public static View[] buildViews(DocletEnvironment env ,Options opt) {
//
//		TypeElement viewclass = findClass(env, opt.viewName).orElseThrow();
//
//	if (opt.viewName != null) {
//		TypeElement viewClass = viewRootDoc.classNamed(opt.viewName);
//	    if(viewClass == null) {
//		System.out.println("View " + opt.viewName + " not found! Exiting without generating any output.");
//		return null;
//	    }
//		viewclass.
//	    if(viewclass.tags("view").length == 0) {
//		System.out.println(viewClass + " is not a view!");
//		return null;
//	    }
//	    if(viewClass.isAbstract()) {
//		System.out.println(viewClass + " is an abstract view, no output will be generated!");
//		return null;
//	    }
//	    return new View[] { buildView(srcRootDoc, viewClass, opt) };
//	} else if (opt.findViews) {
//	    List<View> views = new ArrayList<>();
//	    ClassDoc[] classes = viewRootDoc.classes();
//
//	    // find view classes
//	    for (int i = 0; i < classes.length; i++)
//		if (classes[i].tags("view").length > 0 && !classes[i].isAbstract())
//		    views.add(buildView(srcRootDoc, classes[i], opt));
//
//	    return views.toArray(new View[views.size()]);
//	} else
//	    return new View[0];
//    }
//
//    /**
//     * Builds a view along with its parent views, recursively
//     */
//    private static View buildView(RootDoc root, ClassDoc viewClass, OptionProvider provider) {
//	ClassDoc superClass = viewClass.superclass();
//	if(superClass == null || superClass.tags("view").length == 0)
//	    return new View(root, viewClass, provider);
//
//	return new View(root, viewClass, buildView(root, superClass, provider));
//    }
//
//
//
//
//

}
