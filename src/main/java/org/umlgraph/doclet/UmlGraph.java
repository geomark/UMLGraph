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
import java.util.*;
import java.util.stream.Collectors;

//import com.sun.javadoc.ClassDoc;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

//import com.sun.javadoc.Doc;
import 	javax.lang.model.element.Element;

//import com.sun.javadoc.RootDoc;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.DocletEnvironment;

import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Reporter;
import org.umlgraph.model.*;
import org.umlgraph.model.views.View;

import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
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
public class UmlGraph implements Doclet{

    private static final String programName = "UmlGraph";
    private static final String docletName = "org.umlgraph.doclet.UmlGraph";


    /** Options used for commenting nodes */
    private static Options commentOptions;

	/**
	 * Provides error, warning and notice reporting.
	 */
	Reporter reporter;

	private String overviewfile;

	@Override
	public void init(Locale locale, Reporter reporter) {
		reporter.print(Diagnostic.Kind.NOTE, "Doclet using locale: " + locale);
		this.reporter = reporter;
	}

	@Override
	public String getName() {
		return programName;
	}

	@Override
	public Set<? extends Option> getSupportedOptions() {
		Option[] options = {
				new Option() {
					private final List<String> someOption = Arrays.asList(
							"-overviewfile",
							"--overview-file",
							"-o"
					);

					@Override
					public int getArgumentCount() {
						return 1;
					}

					@Override
					public String getDescription() {
						return "an option with aliases";
					}

					@Override
					public Option.Kind getKind() {
						return Option.Kind.STANDARD;
					}

					@Override
					public List<String> getNames() {
						return someOption;
					}

					@Override
					public String getParameters() {
						return "file";
					}

					@Override
					public boolean process(String opt, List<String> arguments) {
						overviewfile = arguments.get(0);
						return true;
					}
				}
		};
		return new HashSet<>(Arrays.asList(options));
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		// support the latest release
		return SourceVersion.latest();
	}

	/** Entry point through javadoc */
	@Override
	public boolean run(DocletEnvironment environment) {


		Set<? extends Element> els = environment.getSpecifiedElements();
		DocTrees doctrees = environment.getDocTrees();


		Options opt = buildOptions(environment,getSupportedOptions());
//	    root.printNotice("UMLGraph doclet version " + Version.VERSION + " started");

        try{


	   View[] views = buildViews(environment,opt);
	   if(views == null)
	    return false;
	   if (views.length == 0)
	      buildGraph(environment, opt, null);
	   else
	      for (int i = 0; i < views.length; i++)
		    buildGraph(environment, views[i], null);
	      return true;
        }
        catch (Exception e){
            return false;
        }
	}

//    public static boolean start(RootDoc root) throws IOException {
//	Options opt = buildOptions(root);
//	root.printNotice("UMLGraph doclet version " + Version.VERSION + " started");
//
//	View[] views = buildViews(opt, root, root);
//	if(views == null)
//	    return false;
//	if (views.length == 0)
//	    buildGraph(root, opt, null);
//	else
//	    for (int i = 0; i < views.length; i++)
//		buildGraph(root, views[i], null);
//	return true;
//    }

//    public static void main(String args[]) {
//	var err = new PrintWriter(System.err);
//        com.sun.tools.javadoc.Main.execute(programName,
//	  err, err, err, docletName, args);
//    }
//


	/**
	 * Creates the base Options object.
	 * This contains both the options specified on the command
	 * line and the ones specified in the UMLOptions class, if available.
	 * Also create the globally accessible commentOptions object.
	 */
    /**
     * Creates the base Options object.
     * This contains both the options specified on the command
     * line and the ones specified in the UMLOptions class, if available.
     * Also create the globally accessible commentOptions object.
     */
    public  Options buildOptions(DocletEnvironment environment,Set<? extends Option> options) {
        commentOptions = new Options(environment);

//		options.stream().map(Option::getDescription).collect();

//		commentOptions.setOptions(root.options());
//		commentOptions.setOptions(findClass(root, "UMLNoteOptions"));
//		commentOptions.shape = Shape.NOTE;

        Options opt = new Options(environment);
        opt.setOutputDirectory("testdata/dot-out");
//		opt.setOptions(root.options());
//		opt.setOptions(findClass(root, "UMLOptions"));
        return opt;
    }
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
    /** Return the TypeElement for the specified class; null if not found. */
    private static Optional<TypeElement> findClass(DocletEnvironment env, String name) {
		Set<? extends TypeElement> els = env.getElementUtils().getAllTypeElements(name);

		TypeElement[] tp = els.toArray(new TypeElement[els.size()]);

		if(tp.length > 0){
			return Optional.of(tp[0]);
		}
		else{
			return Optional.empty();
		}
    }

    /**
     * Builds and outputs a single graph according to the view overrides
     */
    public  void buildGraph(DocletEnvironment env, OptionProvider op, Element contextDoc) throws IOException {
        if(getCommentOptions() == null)
            buildOptions(env,getSupportedOptions());
        Options opt = op.getGlobalOptions();
        reporter.print(Diagnostic.Kind.NOTE,"Building " + op.getDisplayName());

        Set<? extends Element> classes = env.getIncludedElements().stream()
                .filter(cd ->!cd.getKind().equals(ElementKind.PACKAGE)).collect(Collectors.toSet());

        ClassGraph c = new ClassGraph(env, op, contextDoc);
        c.prologue();
        for (Element cd : classes)
            if(!cd.getKind().equals(ElementKind.PACKAGE)){
                c.printClass((TypeElement) cd, true);
            }
        for (Element cd : classes)
            c.printRelations((TypeElement) cd);
        if(opt.inferRelationships)
            for (Element cd : classes)
                c.printInferredRelations((TypeElement) cd);
        if(opt.inferDependencies)
            for (Element cd : classes)
                c.printInferredDependencies((TypeElement) cd);

        c.printExtraClasses(env);
        c.epilogue();
    }

    /**
     * Builds the views according to the parameters on the command line
     * @param opt The options
     *                different, or may be the same as the srcRootDoc)
     */
    public static View[] buildViews(DocletEnvironment env ,Options opt) {

		DocTrees docTrees = env.getDocTrees();



	if (opt.getViewName() != null) {
		TypeElement viewClass = findClass(env, opt.getViewName()).orElseThrow();
	    if(viewClass == null) {
		System.out.println("View " + opt.getViewName() + " not found! Exiting without generating any output.");
		return null;
	    }

	    if(retrieveTags( env,  viewClass, "view").size() == 0) {
		System.out.println(viewClass + " is not a view!");
		return null;
	    }
	    if(viewClass.getModifiers().contains(Modifier.ABSTRACT)) {
		System.out.println(viewClass + " is an abstract view, no output will be generated!");
		return null;
	    }
	    return new View[] { buildView(env, viewClass, opt) };
	} else if (opt.isFindViews()) {
	    List<View> views = new ArrayList<>();


		Set<? extends Element> classes = env.getSpecifiedElements()
				.stream()
				.filter(el-> el instanceof TypeElement)
				.collect(Collectors.toSet());

	    // find view classes

		for(Element clasz : classes ) {
			if( retrieveTags( env,  clasz, "view").size()>0 &&
					clasz.getModifiers().contains(Modifier.ABSTRACT)){
				views.add(buildView(env, (TypeElement) clasz, opt));
			}

		}

	    return views.toArray(new View[views.size()]);
	} else
	    return new View[0];
    }

    /**
     * Builds a view along with its parent views, recursively
     */
    private static View buildView(DocletEnvironment env, TypeElement viewClass, OptionProvider provider) {
		TypeMirror superTMClass = viewClass.getSuperclass();

		TypeElement superClass = (TypeElement) env.getTypeUtils().asElement(superTMClass);

	if(superClass == null || retrieveTags(env,superClass, "view").size() == 0)
	    return new View(env, viewClass, provider);

	return new View(env, viewClass, buildView(env, superClass, provider));
    }



    private static List<? extends DocTree> retrieveTags(DocletEnvironment env, Element el, String tagname){
		DocTrees docTrees = env.getDocTrees();
		return docTrees.getDocCommentTree(el).getBlockTags()
				.stream()
				.filter(elm -> elm.toString().contains(tagname)).collect(Collectors.toList());
	}

    public static Options getCommentOptions() {
        return commentOptions;
    }


}
