/*
 * Create a graphviz graph based on the classes in the specified java
 * source files.
 *
 * (C) Copyright 2002-2005 Diomidis Spinellis
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

package org.umlgraph.model;

import static org.umlgraph.util.StringUtil.buildRelativePathFromClassNames;
import static org.umlgraph.util.StringUtil.escape;
import static org.umlgraph.util.StringUtil.fmt;
import static org.umlgraph.util.StringUtil.guilWrap;
import static org.umlgraph.util.StringUtil.guillemize;
import static org.umlgraph.util.StringUtil.htmlNewline;
import static org.umlgraph.util.StringUtil.removeTemplate;
import static org.umlgraph.util.StringUtil.splitPackageClass;
import static org.umlgraph.util.StringUtil.tokenize;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;
import javax.lang.model.element.VariableElement;

import com.sun.jdi.ClassType;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.DocletEnvironment;
import org.umlgraph.doclet.UmlGraphDoc;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;

/**
 * Class graph generation engine
 * @depend - - - StringUtil
 * @depend - - - Options
 * @composed - - * ClassInfo
 * @has - - - OptionProvider
 *
 * @version $Revision$
 * @author <a href="http://www.spinellis.gr">Diomidis Spinellis</a>
 */
public class ClassGraph {
    enum Align {
	LEFT, CENTER, RIGHT;

	public final String lower;

	private Align() {
	    this.lower = toString().toLowerCase();
	}
    };
	private DocletEnvironment root;
    protected Map<String, ClassInfo> classnames = new HashMap<String, ClassInfo>();
    protected Set<String> rootClasses;
	protected Map<String, TypeElement> rootClassdocs = new HashMap<>();
    protected OptionProvider optionProvider;
    protected PrintWriter w;
    protected TypeElement collectionClassDoc;
    protected TypeElement mapClassDoc;
    protected String linePostfix;
    protected String linePrefix;
    
    // used only when generating context class diagrams in UMLDoc, to generate the proper
    // relative links to other classes in the image map
    protected final String contextPackageName;
      
    /**
     * Create a new ClassGraph.  <p>The packages passed as an
     * argument are the ones specified on the command line.</p>
     * <p>Local URLs will be generated for these packages.</p>
     * @param root The root of docs as provided by the javadoc API
     * @param optionProvider The main option provider
     * @param contextDoc The current context for generating relative links, may be a ClassDoc 
     * 	or a PackageDoc (used by UMLDoc)
     */
    public ClassGraph(DocletEnvironment root, OptionProvider optionProvider, Element contextDoc) {
	this.optionProvider = optionProvider;
	this.root = root;
	this.collectionClassDoc = root.getElementUtils().getAllTypeElements("java.util.Collection").stream().findFirst().orElseThrow();
	this.mapClassDoc = root.getElementUtils().getAllTypeElements("java.util.Map").stream().findFirst().orElseThrow();

	
	// to gather the packages containing specified classes, loop thru them and gather
	// package definitions. User root.specifiedPackages is not safe, since the user
	// may specify just a list of classes (human users usually don't, but automated tools do)
	rootClasses = new HashSet<String>();
		root.getIncludedElements().stream().filter(el -> el.asType() instanceof DeclaredType)
				.map(el -> (TypeElement) el)

				.forEach(el ->{
					rootClasses.add(el.getQualifiedName().toString());
			        rootClassdocs.put(el.getQualifiedName().toString(), el);
				});

	
	// determine the context path, relative to the root
	if (contextDoc instanceof TypeElement)
	    contextPackageName = ((TypeElement) contextDoc).getEnclosingElement().getSimpleName().toString();
	else if (contextDoc instanceof PackageElement)
	    contextPackageName = ((PackageElement) contextDoc).getSimpleName().toString();
	else
	    contextPackageName = null; // Not available
	
	Options opt = optionProvider.getGlobalOptions();
	linePrefix = opt.compact ? "" : "\t";
	linePostfix = opt.compact ? "" : "\n";
    }

    

    /** Return the class's name, possibly by stripping the leading path */
    private static String qualifiedName(Options opt, String r) {
	if (opt.hideGenerics)
	    r = removeTemplate(r);
	// Fast path - nothing to do:
	if (opt.showQualified && (opt.showQualifiedGenerics || r.indexOf('<') < 0))
	    return r;
	StringBuilder buf = new StringBuilder(r.length());
	qualifiedNameInner(opt, r, buf, 0, !opt.showQualified);
	return buf.toString();
    }

    private static int qualifiedNameInner(Options opt, String r, StringBuilder buf, int last, boolean strip) {
	strip = strip && last < r.length() && Character.isLowerCase(r.charAt(last));
	for (int i = last; i < r.length(); i++) {
	    char c = r.charAt(i);
	    if (c == '.' || c == '$') {
		if (strip)
		    last = i + 1; // skip dot
		strip = strip && last < r.length() && Character.isLowerCase(r.charAt(last));
		continue;
	    }
	    if (Character.isJavaIdentifierPart(c))
		continue;
	    buf.append(r, last, i);
	    last = i;
	    // Handle nesting of generics
	    if (c == '<') {
		buf.append('<');
		i = last = qualifiedNameInner(opt, r, buf, ++last, !opt.showQualifiedGenerics);
		buf.append('>');
	    } else if (c == '>')
		return i + 1;
	}
	buf.append(r, last, r.length());
	return r.length();
    }

    /**
     * Print the visibility adornment of element e prefixed by
     * any stereotypes
     */
    private String visibility(Options opt, Element e) {
	return opt.showVisibility ? Visibility.get(e).symbol : " ";
    }

    /** Print the method parameter p */
    private String parameter(Options opt, VariableElement p[]) {
	StringBuilder par = new StringBuilder(1000);
	for (int i = 0; i < p.length; i++) {
	    par.append(p[i].getSimpleName().toString()+ typeAnnotation(opt, p[i].asType()));
	    if (i + 1 < p.length)
		par.append(", ");
	}
	return par.toString();
    }

	/** Print the method parameter p */
	private String parameter(Options opt, List<? extends TypeParameterElement> p) {
		StringBuilder par = new StringBuilder(1000);

		p.forEach(param ->{
			par.append(param.getSimpleName().toString())
					.append(typeAnnotation(opt, param.asType()));
		});
		return par.toString();
	}

    /** Print a a basic type t */
    private String type(Options opt, TypeMirror t, boolean generics) {
	return ((generics ? opt.showQualifiedGenerics : opt.showQualified) ? //
			t.getKind().getDeclaringClass().getCanonicalName() : t.getKind().getDeclaringClass().getName()) //
		+ (opt.hideGenerics ? "" : typeParameters(opt, (DeclaredType) t));

    }

    /** Print the parameters of the parameterized type t */
    private String typeParameters(Options opt, DeclaredType t) {
	if (t == null)
	    return "";
	StringBuffer tp = new StringBuffer(1000).append("&lt;");
		t.getTypeArguments().forEach(type->{
			tp.append(type(opt, type, true));
		});
//	for (int i = 0; i < args.length; i++) {
//	    tp.append(type(opt, args[i], true));
//	    if (i != args.length - 1)
//		tp.append(", ");
//	}
	return tp.append("&gt;").toString();
    }

    /** Annotate an field/argument with its type t */
    private String typeAnnotation(Options opt, TypeMirror t) {

	if (t.getKind().equals(TypeKind.VOID))
	    return "";
//	return " : " + type(opt, t, false) + t.dimension();
		return " : " + type(opt, t, false) ;
    }

    /** Print the class's attributes fd */
    private void attributes(Options opt, List<? extends TypeParameterElement>  fd) {
	for (TypeParameterElement f : fd) {
	    if (hidden(f))
		continue;
	    stereotype(opt, f, Align.LEFT);
	    String att = visibility(opt, f) + f.getSimpleName().toString();
	    if (opt.showType)
		att += typeAnnotation(opt, f.asType());
	    tableLine(Align.LEFT, att);
	    tagvalue(opt, f);
	}
    }

    /*
     * The following two methods look similar, but can't
     * be refactored into one, because their common interface,
     * ExecutableMemberDoc, doesn't support returnType for ctors.
     */

    /** Print the class's constructors m */
    private boolean operations2(Options opt, ExecutableElement m[]) {
	boolean printed = false;
	for (ExecutableElement cd : m) {
	    if (hidden(cd))
		continue;
	    stereotype(opt, cd, Align.LEFT);

	    String cs = visibility(opt, cd) + cd.getSimpleName().toString() //
		    + (opt.showType ? "(" + parameter(opt, cd.getTypeParameters()) + ")" : "()");
	    tableLine(Align.LEFT, cs);
	    tagvalue(opt, cd);
	    printed = true;
	}
	return printed;
    }

    /** Print the class's operations m */
    private boolean operations(Options opt, ExecutableElement[] m) {
	boolean printed = false;
	for (ExecutableElement md : m) {
	    if (hidden(md))
		continue;
	    // Filter-out static initializer method
	    if (md.getSimpleName().toString().equals("<clinit>") && md.getKind().equals(ElementKind.STATIC_INIT) && md.getModifiers().contains(Modifier.PRIVATE))
		continue;
	    stereotype(opt, md, Align.LEFT);
	    String op = visibility(opt, md) + md.getSimpleName().toString()+ //
		    (opt.showType ? "(" + parameter(opt, md.getParameters().toArray(new VariableElement[md.getParameters().size()])) + ")" + typeAnnotation(opt, md.getReturnType())
			    : "()");

	    tableLine(Align.LEFT, (md.getModifiers().contains(Modifier.ABSTRACT) ? Font.ABSTRACT : Font.NORMAL).wrap(opt, op));
	    printed = true;

	    tagvalue(opt, md);
	}
	return printed;
    }

    /** Print the common class node's properties */
    private void nodeProperties(Options opt) {
	Options def = opt.getGlobalOptions();
	if (opt.nodeFontName != def.nodeFontName)
	    w.print(",fontname=\"" + opt.nodeFontName + "\"");
	if (opt.nodeFontColor != def.nodeFontColor)
	    w.print(",fontcolor=\"" + opt.nodeFontColor + "\"");
	if (opt.nodeFontSize != def.nodeFontSize)
	    w.print(",fontsize=" + fmt(opt.nodeFontSize));
	w.print(opt.shape.style);
	w.println("];");
    }

    /**
     * Return as a string the tagged values associated with c
     * @param opt the Options used to guess font names
     * @param c the Doc entry to look for @tagvalue
     */
    private void tagvalue(Options opt, Element c) {
		DocCommentTree dctree = root.getDocTrees().getDocCommentTree(c);

		if(dctree != null)
		dctree.getBlockTags()
				.stream()
				.filter(tg-> tg.getKind().tagName != null)
				.filter(tg-> tg.getKind().tagName.equals("tagvalue"))
		        .forEach(tg -> {
					String t[] = tokenize(tg.toString());
					if (t.length != 2) {
						System.err.println("@tagvalue expects two fields: " + tg.toString());
					}
					else{
						tableLine(Align.RIGHT, Font.TAG.wrap(opt, "{" + t[0] + " = " + t[1] + "}"));
					}
				});
    }

    /**
     * Return as a string the stereotypes associated with c
     * terminated by the escape character term
     */
    private void stereotype(Options opt, Element c, Align align) {
		DocCommentTree dctree = root.getDocTrees().getDocCommentTree(c);

		if(dctree != null){
			dctree.getBlockTags()
					.stream()
					.filter(tg-> tg.getKind().tagName != null)
					.filter(tg-> tg.getKind().tagName.equals("tagvalue"))
					.forEach(tg -> {
						String t[] = tokenize(tg.toString());
						if (t.length != 1) {
							System.err.println("@stereotype expects one field: " + tg.toString());
						}
						else{
							tableLine(align, guilWrap(opt, t[0]));
						}

					});
		}

    }

    /** Return true if c has a @hidden tag associated with it */
    private boolean hidden(Element c) {
		DocCommentTree tree = root.getDocTrees().getDocCommentTree(c);

		if(tree == null)
			return false;

		List<? extends DocTree> bltag = tree.getBlockTags();

		for(DocTree tag: bltag){
			if(tag.getKind().tagName != null && tag.getKind().tagName.contains("hidden"))
				return true;
		}

//		long telements = tree.getBlockTags()
//				.stream()
//				.filter(tg -> tg.getKind().tagName.equals("hidden"))
////				.filter(tg -> tg.getKind().tagName.equals("view"))
//				.count();
//
//	if (telements > 0)
//	    return true;
	Options opt = optionProvider.getOptionsFor(c.getSimpleName().toString());

	return opt.matchesHideExpression(c.toString()) //
		|| (opt.hidePrivateInner && c instanceof TypeElement  && c.getModifiers().contains(Modifier.PRIVATE)
			&&  c.getEnclosingElement() != null);
    }

    public ClassInfo getClassInfo(TypeElement cd, boolean create) {
	return getClassInfo(cd, cd.toString(), create);
    }

    protected ClassInfo getClassInfo(String className, boolean create) {
	return getClassInfo(null, className, create);
    }

    protected ClassInfo getClassInfo(TypeElement cd, String className, boolean create) {
	className = removeTemplate(className);
	ClassInfo ci = classnames.get(className);
	if (ci == null && create) {
	    boolean hidden = cd != null ? hidden(cd) : optionProvider.getOptionsFor(className).matchesHideExpression(className);
	    ci = new ClassInfo(hidden);
	    classnames.put(className, ci);
	}
	return ci;
    }

    /** Return true if the class name is associated to an hidden class or matches a hide expression */
    private boolean hidden(String className) {
	className = removeTemplate(className);
	ClassInfo ci = classnames.get(className);
	return ci != null ? ci.hidden : optionProvider.getOptionsFor(className).matchesHideExpression(className);
    }

    /**
     * Prints the class if needed.
     * <p>
     * A class is a rootClass if it's included among the classes returned by
     * RootDoc.classes(), this information is used to properly compute
     * relative links in diagrams for UMLDoc
     */
    public String printClass(TypeElement c, boolean rootClass) {
		ClassInfo ci = getClassInfo(c, true);
		if (ci.nodePrinted || ci.hidden)
			return ci.name;
		Options opt = optionProvider.getOptionsFor(c);
		if (c.getKind().equals(ElementKind.ENUM) && !opt.showEnumerations)
			return ci.name;
		String className = c.toString();
		// Associate classname's alias
		w.println(linePrefix + "// " + className);
		// Create label
		w.print(linePrefix + ci.name + " [label=");

		long nomethods = c.getEnclosedElements().stream().filter(element -> {
			return element.asType() instanceof ExecutableElement;
		}).count();

		long noconstructors = c.getEnclosedElements().stream().filter(element -> {
			return element.asType() instanceof ExecutableElement;
		}).count();

		long totalenumconstants = c.getEnclosedElements().stream().filter(el -> {
			return el.getKind().equals(ElementKind.ENUM_CONSTANT);
		}).count();
		boolean showMembers =
				(opt.showAttributes && c.getEnclosedElements().size() > 0) ||
						(c.getKind().equals(ElementKind.ENUM) && opt.showEnumConstants && totalenumconstants > 0) ||
						(opt.showOperations && nomethods > 0) ||
						(opt.showConstructors && noconstructors > 0);

		final String url = classToUrl(c, rootClass);

		externalTableStart(opt, c.getQualifiedName().toString(), url);

		firstInnerTableStart(opt);
		if (c.getKind().isInterface())
			tableLine(Align.CENTER, guilWrap(opt, "interface"));
		if (c.getKind().equals(ElementKind.ENUM))
			tableLine(Align.CENTER, guilWrap(opt, "enumeration"));
		stereotype(opt, c, Align.CENTER);

		Font font = c.getModifiers().contains(Modifier.ABSTRACT) && !c.getKind().isInterface() ? Font.CLASS_ABSTRACT : Font.CLASS;
		String qualifiedName = qualifiedName(opt, className);
		int idx = splitPackageClass(qualifiedName);
		if (opt.showComment) {
			DocCommentTree tree = root.getDocTrees().getDocCommentTree(c);
			String commentText = tree.getFullBody().stream().map(el -> el.toString()).reduce("", (partialString, element) -> {
				return partialString + element.toString();
			});

			tableLine(Align.LEFT, Font.CLASS.wrap(opt, htmlNewline(escape(commentText))));
		} else if (opt.postfixPackage && idx > 0 && idx < (qualifiedName.length() - 1)) {
			String packageName = qualifiedName.substring(0, idx);
			String cn = qualifiedName.substring(idx + 1);
			tableLine(Align.CENTER, font.wrap(opt, escape(cn)));
			tableLine(Align.CENTER, Font.PACKAGE.wrap(opt, packageName));
		} else {
			tableLine(Align.CENTER, font.wrap(opt, escape(qualifiedName)));
		}
		tagvalue(opt, c);
		firstInnerTableEnd(opt);

		/*
		 * Warning: The boolean expressions guarding innerTableStart()
		 * in this block, should match those in the code block above
		 * marked: "Calculate the number of innerTable rows we will emmit"
		 */
		if (showMembers) {
			if (opt.showAttributes) {
				innerTableStart();
				List<? extends TypeParameterElement> fields = c.getTypeParameters();

				// if there are no fields, print an empty line to generate proper HTML
				if (fields.size() == 0)
					tableLine(Align.LEFT, "");
				else
					attributes(opt, fields);
				innerTableEnd();
			} else if (!c.getKind().equals(ElementKind.ENUM) && (opt.showConstructors || opt.showOperations)) {
				// show an emtpy box if we don't show attributes but
				// we show operations
				innerTableStart();
				tableLine(Align.LEFT, "");
				innerTableEnd();
			}
			if (c.getKind().equals(ElementKind.ENUM) && opt.showEnumConstants) {
				innerTableStart();


				List<? extends Element> ecs = c.getEnclosedElements();
				// if there are no constants, print an empty line to generate proper HTML
				if (ecs.size() == 0) {
					tableLine(Align.LEFT, "");
				} else {
					for (Element fd : ecs) {
						tableLine(Align.LEFT, fd.getSimpleName().toString());
					}
				}
				innerTableEnd();
			}
			if (!c.getKind().equals(ElementKind.ENUM) && (opt.showConstructors || opt.showOperations)) {
				innerTableStart();
				boolean printedLines = false;
				if (opt.showConstructors)

					printedLines |= operations(opt, c.getEnclosedElements().stream()
							.filter(el -> el.getKind().equals(ElementKind.CONSTRUCTOR)).toArray(ExecutableElement[]::new));
				if (opt.showOperations)
					printedLines |= operations(opt, c.getEnclosedElements().stream()
							.filter(el -> el.getKind().equals(ElementKind.METHOD)).toArray(ExecutableElement[]::new));

				if (!printedLines)
					// if there are no operations nor constructors,
					// print an empty line to generate proper HTML
					tableLine(Align.LEFT, "");

				innerTableEnd();
			}
		}
		externalTableEnd();
		if (url != null)
			w.print(", URL=\"" + url + "\"");


		// If needed, add a note for this node
//	int ni = 0;
//	for (DocTree t : c.   tags("note")) {
		var ref = new Object() {
			int ni = 0;
		};

		if(root.getDocTrees().getDocCommentTree(c) != null)
		root.getDocTrees().getDocCommentTree(c).getBlockTags().forEach(t -> {
			String noteName = "n" + ref.ni + "c" + ci.name;
			w.print(linePrefix + "// Note annotation\n");
			w.print(linePrefix + noteName + " [label=");
			externalTableStart(UmlGraphDoc.getCommentOptions(), c.getQualifiedName().toString(), url);
			innerTableStart();
			tableLine(Align.LEFT, Font.CLASS.wrap(UmlGraphDoc.getCommentOptions(), htmlNewline(escape(t.toString()))));
			innerTableEnd();
			externalTableEnd();
			nodeProperties(UmlGraphDoc.getCommentOptions());
			ClassInfo ci1 = getClassInfo(c, true);
			w.print(linePrefix + noteName + " -> " + ci1.name + "[arrowhead=none];\n");
			ref.ni++;

		});

		ci.nodePrinted = true;
		return ci.name;
	}

    /**
     * Print all relations for a given's class's tag
     * @param from the source class
     */
    private void allRelation(Options opt, RelationType rt, TypeElement from) {
	String tagname = rt.lower;


	    if(root.getDocTrees().getDocCommentTree(from) != null)
		root.getDocTrees().getDocCommentTree(from).getBlockTags().forEach(tag -> {
//	for (Tag tag : from.tags(tagname)) {
	    String t[] = tokenize(tag.toString());    // l-src label l-dst target
	    t = t.length == 1 ? new String[] { "-", "-", "-", t[0] } : t; // Shorthand
	    if (t.length != 5) {
		System.err.println("Error in " + from + "\n" + tagname + " expects four fields (l-src label l-dst target): " + tag.toString());
		return;
	    }

			Optional<TypeElement> toel = findElementByClassName(t[4]);
	    //TODO:probably wrong
			if(toel.isPresent()){
				TypeElement to = toel.get();
					if(hidden(to))
						relation(opt, rt, from, to, t[0], t[1], t[2]);
			}
			else{
				if(hidden(t[3]))

					relation(opt, rt, from, from.toString(), null, t[3], t[0], t[1], t[2]);
			}

	});

    }

    private Optional<TypeElement> findElementByClassName(String qname) {
		return (Optional<TypeElement>) root.getIncludedElements()
				.stream()
				.filter(el -> el.getKind().equals(ElementKind.CLASS) || el.getKind().equals(ElementKind.INTERFACE))
				.filter(el -> ((TypeElement) el).getQualifiedName().toString().equals(qname)).findFirst();

	}

    /**
     * Print the specified relation
     * @param from the source class (may be null)
     * @param fromName the source class's name
     * @param to the destination class (may be null)
     * @param toName the destination class's name
     */
    private void relation(Options opt, RelationType rt, TypeElement from, String fromName,
						  TypeElement to, String toName, String tailLabel, String label, String headLabel) {
	tailLabel = (tailLabel != null && !tailLabel.isEmpty()) ? ",taillabel=\"" + tailLabel + "\"" : "";
	label = (label != null && !label.isEmpty()) ? ",label=\"" + guillemize(opt, label) + "\"" : "";
	headLabel = (headLabel != null && !headLabel.isEmpty()) ? ",headlabel=\"" + headLabel + "\"" : "";
	boolean unLabeled = tailLabel.isEmpty() && label.isEmpty() && headLabel.isEmpty();

	ClassInfo ci1 = getClassInfo(from, fromName, true), ci2 = getClassInfo(to, toName, true);
	String n1 = ci1.name, n2 = ci2.name;
	// For ranking we need to output extends/implements backwards.
	if (rt.backorder) { // Swap:
	    n1 = ci2.name;
	    n2 = ci1.name;
	    String tmp = tailLabel;
	    tailLabel = headLabel;
	    headLabel = tmp;
	}
	Options def = opt.getGlobalOptions();
	// print relation
	w.println(linePrefix + "// " + fromName + " " + rt.lower + " " + toName);
	w.println(linePrefix + n1 + " -> " + n2 + " [" + rt.style +
		(opt.edgeColor != def.edgeColor ? ",color=\"" + opt.edgeColor + "\"" : "") +
		(unLabeled ? "" :
		    (opt.edgeFontName != def.edgeFontName ? ",fontname=\"" + opt.edgeFontName + "\"" : "") +
		    (opt.edgeFontColor != def.edgeFontColor ? ",fontcolor=\"" + opt.edgeFontColor + "\"" : "") +
		    (opt.edgeFontSize != def.edgeFontSize ? ",fontsize=" + fmt(opt.edgeFontSize) : "")) +
		tailLabel + label + headLabel +
		"];");
	
	// update relation info
	RelationDirection d = RelationDirection.BOTH;
	if(rt == RelationType.NAVASSOC || rt == RelationType.DEPEND)
	    d = RelationDirection.OUT;
	ci1.addRelation(toName, rt, d);
	ci2.addRelation(fromName, rt, d.inverse());
    }

    /**
     * Print the specified relation
     * @param from the source class
     * @param to the destination class
     */
    private void relation(Options opt, RelationType rt, TypeElement from,
						  TypeElement to, String tailLabel, String label, String headLabel) {
	relation(opt, rt, from, from.toString(), to, to.toString(), tailLabel, label, headLabel);
    }


    /** Print a class's relations */
    public void printRelations(TypeElement c) {
	Options opt = optionProvider.getOptionsFor(c);
	if (hidden(c) || c.getQualifiedName().toString().equals("java.lang.Object")) // avoid phantom classes, they may pop up when the source uses annotations
	    return;
	// Print generalization (through the Java superclass)

		TypeMirror s = c.getSuperclass();



		TypeElement sc = s != null && !s.getClass().getName().equals(Object.class.getName()) ? (TypeElement) ((DeclaredType) s).asElement() : null;
	if (sc != null && !c.getKind().equals(ElementKind.ENUM)   && !hidden(sc))
	    relation(opt, RelationType.EXTENDS, c, sc, null, null, null);
	// Print generalizations (through @extends tags)
		//for (Tag tag : c.tags("extends"))
		if(root.getDocTrees().getDocCommentTree(c) != null)
		root.getDocTrees().getDocCommentTree(c).getBlockTags().stream()
				.filter(t->t.getKind()
						.equals(DocTree.Kind.INDEX)).forEach(tag ->{
			if (!hidden(tag.toString()))

				relation(opt, RelationType.EXTENDS, c, (TypeElement) c.getSuperclass(), null, null, null);

		});


	// Print realizations (Java interfaces)
	for ( TypeMirror iface : c.getInterfaces()) {

	    if (!hidden((Element) iface))
		relation(opt, RelationType.IMPLEMENTS, c, (TypeElement) iface, null, null, null);
	}
	// Print other associations
	allRelation(opt, RelationType.COMPOSED, c);
	allRelation(opt, RelationType.NAVCOMPOSED, c);
	allRelation(opt, RelationType.HAS, c);
	allRelation(opt, RelationType.NAVHAS, c);
	allRelation(opt, RelationType.ASSOC, c);
	allRelation(opt, RelationType.NAVASSOC, c);
	allRelation(opt, RelationType.DEPEND, c);
    }

    /** Print classes that were parts of relationships, but not parsed by javadoc */
    public void printExtraClasses(DocletEnvironment root) {
	Set<String> names = new HashSet<>(classnames.keySet());
	for(String className: names) {
	    ClassInfo info = getClassInfo(className, true);
	    if (info.nodePrinted)
		continue;
		TypeElement c = root.getElementUtils().getTypeElement(className);
	    if(c != null) {
		printClass(c, false);
		continue;
	    }
	    // Handle missing classes:
	    Options opt = optionProvider.getOptionsFor(className);
	    if(opt.matchesHideExpression(className))
		continue;
	    w.println(linePrefix + "// " + className);
	    w.print(linePrefix  + info.name + "[label=");
	    externalTableStart(opt, className, classToUrl(className));
	    innerTableStart();
	    String qualifiedName = qualifiedName(opt, className);
	    int startTemplate = qualifiedName.indexOf('<');
	    int idx = qualifiedName.lastIndexOf('.', startTemplate < 0 ? qualifiedName.length() - 1 : startTemplate);
	    if(opt.postfixPackage && idx > 0 && idx < (qualifiedName.length() - 1)) {
		String packageName = qualifiedName.substring(0, idx);
		String cn = qualifiedName.substring(idx + 1);
		tableLine(Align.CENTER, Font.CLASS.wrap(opt, escape(cn)));
		tableLine(Align.CENTER, Font.PACKAGE.wrap(opt, packageName));
	    } else {
		tableLine(Align.CENTER, Font.CLASS.wrap(opt, escape(qualifiedName)));
	    }
	    innerTableEnd();
	    externalTableEnd();
	    if (className == null || className.length() == 0)
		w.print(",URL=\"" + classToUrl(className) + "\"");
	    nodeProperties(opt);
	}
    }
    
    /**
     * Prints associations recovered from the fields of a class. An association is inferred only
     * if another relation between the two classes is not already in the graph.
     * @param c
     */  
    public void printInferredRelations(TypeElement c) {
	// check if the source is excluded from inference
	if (hidden(c))
	    return;

	Options opt = optionProvider.getOptionsFor(c);

	for (Element field : c.getEnclosedElements()
			.stream()
			.filter(el-> el instanceof  VariableElement)
			.collect(Collectors.toList())) {

	    if(hidden(field))
		continue;
	    // skip statics
//	    if(field.isstatic)
//		continue;
	    // skip primitives
	    FieldRelationInfo fri = getFieldRelationInfo((VariableElement) field);
	    if (fri == null)
		continue;
	    // check if the destination is excluded from inference
	    if (hidden(fri.cd))
		continue;

	    // if source and dest are not already linked, add a dependency
	    RelationPattern rp = getClassInfo(c, true).getRelation(fri.cd.toString());
	    if (rp == null) {
		String destAdornment = fri.multiple ? "*" : "";
		relation(opt, opt.inferRelationshipType, c, fri.cd, "", "", destAdornment);
            }
	}
    }

    /** Returns an array representing the imported classes of c.
     * Disables the deprecation warning, which is output, because the
     * imported classed are an implementation detail.
     */
    @SuppressWarnings( "deprecation" )
	TypeMirror[] importedClasses(TypeElement c) {

//      FIXME:  return c.getEnclosedElements();
		return null;
    }

    /**
     * Prints dependencies recovered from the methods of a class. A
     * dependency is inferred only if another relation between the two
     * classes is not already in the graph.
     */  
    public void printInferredDependencies(TypeElement c) {
	if (hidden(c))
	    return;

	Options opt = optionProvider.getOptionsFor(c);
	Set<TypeMirror> types = new HashSet<TypeMirror>();
	// harvest method return and parameter types
	for (ExecutableElement method : filterByVisibility(c.getEnclosedElements()
			.stream()
			.filter(el -> el instanceof ExecutableElement)
			.map(el-> (ExecutableElement)el )
			.collect(Collectors.toList()), opt.inferDependencyVisibility)) {

	    types.add(method.getReturnType());
	    for (VariableElement parameter : method.getParameters()) {
		types.add(parameter.asType());
	    }
	}
	// and the field types
	if (!opt.inferRelationships) {
	    for (VariableElement field :  filterByVisibility(c.getEnclosedElements()
				.stream()
				.filter(el -> el instanceof VariableElement)
				.map(el-> (VariableElement)el )
				.collect(Collectors.toList()), opt.inferDependencyVisibility)) {
		types.add(field.asType());
	    }
	}
	// see if there are some t ype parameters
	if (c.getTypeParameters() != null) {
		List<? extends TypeParameterElement> pt = c.getTypeParameters();
	    types.addAll(c.getTypeParameters().stream().map(TypeParameterElement::asType).collect(Collectors.toList()));
	}
	// see if type parameters extend something
	for(TypeParameterElement tv: c.getTypeParameters()) {

	    if(tv.getBounds().size() > 0 )
		types.addAll(tv.getBounds());
	}

	// and finally check for explicitly imported classes (this
	// assumes there are no unused imports...)
	if (opt.useImports)
	    types.addAll(Arrays.asList(importedClasses(c)));

	// compute dependencies
	for (TypeMirror type : types) {
	    // skip primitives and type variables, as well as dependencies
	    // on the source class

	    if (type.getKind().isPrimitive() || type instanceof WildcardType || type instanceof TypeVariable
		    || c.toString().equals(type.toString()))
		continue;

	    // check if the destination is excluded from inference

	    if (hidden((Element) type))
		continue;
	    
	    // check if source and destination are in the same package and if we are allowed
	    // to infer dependencies between classes in the same package

	    if(!opt.inferDepInPackage && c.getEnclosingElement().equals(((Element) type).getEnclosingElement()))
		continue;

	    // if source and dest are not already linked, add a dependency
	    RelationPattern rp = getClassInfo(c, true).getRelation(type.toString());
	    if (rp == null || rp.matchesOne(new RelationPattern(RelationDirection.OUT))) {
		relation(opt, RelationType.DEPEND, c, (TypeElement) type, "", "", "");
	    }
	    
	}
    }
    
    /**
     * Returns all program element docs that have a visibility greater or
     * equal than the specified level
     */
    private <T extends Element> List<T> filterByVisibility(List<T> docs, Visibility visibility) {
	if (visibility == Visibility.PRIVATE)
	    return docs;

	List<T> filtered = new ArrayList<T>();
	for (T doc : docs) {
	    if (Visibility.get(doc).compareTo(visibility) > 0)
		filtered.add(doc);
	}
	return filtered;
    }



    private FieldRelationInfo getFieldRelationInfo(VariableElement field) {
		TypeMirror type = field.asType();

	if(type.getKind().isPrimitive() || type instanceof WildcardType || type instanceof TypeVariable)
	    return null;

	if(type instanceof DeclaredType ){
		DeclaredType dtype = (DeclaredType) type;
//	if (type.dimension().endsWith("[]")) {
 	if (type.getKind().toString().endsWith("[]")) {
		return new FieldRelationInfo((TypeElement) dtype.asElement(), true);
	}
	Options opt = optionProvider.getOptionsFor((TypeElement) dtype.asElement());
	if (opt.matchesCollPackageExpression(((DeclaredType) type).asElement().getSimpleName().toString())) {
		TypeMirror[] argTypes = getInterfaceTypeArguments(collectionClassDoc, type);
	    if (argTypes != null && argTypes.length == 1 && !argTypes[0].getKind().isPrimitive())
		return new FieldRelationInfo((TypeElement) argTypes[0], true);

	    argTypes = getInterfaceTypeArguments(mapClassDoc, type);
	    if (argTypes != null && argTypes.length == 2 && !argTypes[1].getKind().isPrimitive())
		return new FieldRelationInfo((TypeElement) argTypes[1], true);
	}
	}
	return new FieldRelationInfo((TypeElement) type, false);
    }
    
    private TypeMirror[] getInterfaceTypeArguments(TypeElement iface, TypeMirror t) {
	if (t instanceof DeclaredType) {
		DeclaredType pt = (DeclaredType ) t;

		TypeElement ttype = (TypeElement) ((DeclaredType) t).asElement();
	    if (iface != null && iface.equals(ttype)) {

		return pt.getTypeArguments().toArray(new TypeMirror[pt.getTypeArguments().size()]);
	    } else {

		for (TypeMirror pti : pt.getTypeArguments()) {
			TypeMirror[] result = getInterfaceTypeArguments(iface, pti);
		    if (result != null)
			return result;
		}
		if (pt.getEnclosingType() != null)
		    return getInterfaceTypeArguments(iface, pt.getEnclosingType());
	    }
	} else if (t instanceof TypeElement) {
		TypeElement cd = (TypeElement) t;
	    for (TypeMirror pti : cd.getInterfaces()) {
			TypeMirror[] result = getInterfaceTypeArguments(iface, pti);
		if (result != null)
		    return result;
	    }
	    if (cd.getEnclosingElement() != null)
		return getInterfaceTypeArguments(iface, cd.getEnclosingElement().asType());
	}
	return null;
    }

    /** Convert the class name into a corresponding URL */
    public String classToUrl(TypeElement cd, boolean rootClass) {
	// building relative path for context and package diagrams
	if(contextPackageName != null && rootClass)
	    return buildRelativePathFromClassNames(contextPackageName, cd.getEnclosingElement().getSimpleName().toString()) + cd.getSimpleName()+ ".html";
	return classToUrl(cd.getQualifiedName().toString());
    }

    /** Convert the class name into a corresponding URL */
    public String classToUrl(String className) {
		TypeElement classDoc = rootClassdocs.get(className);
	if (classDoc != null) {
	    String docRoot = optionProvider.getGlobalOptions().apiDocRoot;
	    if (docRoot == null)
		return null;
	    return new StringBuilder(docRoot.length() + className.length() + 10).append(docRoot) //
		    .append(classDoc.getEnclosingElement().getSimpleName().toString().replace('.', '/')) //
		    .append('/').append(classDoc.getSimpleName().toString()).append(".html").toString();
	}
	String docRoot = optionProvider.getGlobalOptions().getApiDocRoot(className);
	    if (docRoot == null)
		return null;
	int split = splitPackageClass(className);
	StringBuilder buf = new StringBuilder(docRoot.length() + className.length() + 10).append(docRoot);
	if (split > 0) // Avoid -1, and the extra slash then.
	    buf.append(className.substring(0, split).replace('.', '/')).append('/');
	return buf.append(className, Math.min(split + 1, className.length()), className.length()) //
		.append(".html").toString();
    }

    /** Dot prologue 
     * @throws IOException */
    public void prologue() throws IOException {
	Options opt = optionProvider.getGlobalOptions();
	OutputStream os;

	if (opt.outputFileName.equals("-"))
	    os = System.out;
	else {
	    // prepare output file. Use the output file name as a full path unless the output
	    // directory is specified
	    File file = new File(opt.outputDirectory, opt.outputFileName);
	    // make sure the output directory are there, otherwise create them
	    if (file.getParentFile() != null
		&& !file.getParentFile().exists())
		file.getParentFile().mkdirs();
	    os = new FileOutputStream(file);
	}

	// print prologue
	w = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(os), opt.outputEncoding));
	w.println(
	    "#!/usr/local/bin/dot\n" +
	    "#\n" +
	    "# Class diagram \n" +
	    "# Generated by UMLGraph version " +
	    Version.VERSION + " (http://www.spinellis.gr/umlgraph/)\n" +
	    "#\n\n" +
	    "digraph G {\n" +
	    linePrefix + "graph [fontnames=\"svg\"]\n" +
	    linePrefix + "edge [fontname=\"" + opt.edgeFontName +
	    "\",fontsize=" + fmt(opt.edgeFontSize) +
	    ",labelfontname=\"" + opt.edgeFontName +
	    "\",labelfontsize=" + fmt(opt.edgeFontSize) +
	    ",color=\"" + opt.edgeColor + "\"];\n" +
	    linePrefix + "node [fontname=\"" + opt.nodeFontName +
	    "\",fontcolor=\"" + opt.nodeFontColor +
	    "\",fontsize=" + fmt(opt.nodeFontSize) +
	    ",shape=plaintext,margin=0,width=0,height=0];"
	);

	w.println(linePrefix + "nodesep=" + opt.nodeSep + ";");
	w.println(linePrefix + "ranksep=" + opt.rankSep + ";");
	if (opt.horizontal)
	    w.println(linePrefix + "rankdir=LR;");
	if (opt.bgColor != null)
	    w.println(linePrefix + "bgcolor=\"" + opt.bgColor + "\";\n");
    }

    /** Dot epilogue */
    public void epilogue() {
	w.println("}\n");
	w.flush();
	w.close();
    }
    
    private void externalTableStart(Options opt, String name, String url) {
	String bgcolor = opt.nodeFillColor == null ? "" : (" bgcolor=\"" + opt.nodeFillColor + "\"");
	String href = url == null ? "" : (" href=\"" + url + "\" target=\"_parent\"");
	w.print("<<table title=\"" + name + "\" border=\"0\" cellborder=\"" + 
	    opt.shape.cellBorder() + "\" cellspacing=\"0\" " +
	    "cellpadding=\"2\"" + bgcolor + href + ">" + linePostfix);
    }
    
    private void externalTableEnd() {
	w.print(linePrefix + linePrefix + "</table>>");
    }
    
    private void innerTableStart() {
	w.print(linePrefix + linePrefix + "<tr><td><table border=\"0\" cellspacing=\"0\" "
		+ "cellpadding=\"1\">" + linePostfix);
    }
    
    /**
     * Start the first inner table of a class.
     */
    private void firstInnerTableStart(Options opt) {
	w.print(linePrefix + linePrefix + "<tr>" + opt.shape.extraColumn() +
		"<td><table border=\"0\" cellspacing=\"0\" " +
		"cellpadding=\"1\">" + linePostfix);
    }
    
    private void innerTableEnd() {
	w.print(linePrefix + linePrefix + "</table></td></tr>" + linePostfix);
    }

    /**
     * End the first inner table of a class.
     */
    private void firstInnerTableEnd(Options opt) {
	w.print(linePrefix + linePrefix + "</table></td>" +
	    opt.shape.extraColumn() + "</tr>" + linePostfix);
    }

    private void tableLine(Align align, String text) {
	w.print(linePrefix + linePrefix //
		+ "<tr><td align=\"" + align.lower + "\" balign=\"" + align.lower + "\"> " //
		+ text // MAY contain markup!
		+ " </td></tr>" + linePostfix);
    }

    private static class FieldRelationInfo {
    	TypeElement cd;
	boolean multiple;

	public FieldRelationInfo(TypeElement cd, boolean multiple) {
	    this.cd = cd;
	    this.multiple = multiple;
	}
    }
}
