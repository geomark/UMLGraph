package org.umlgraph.doclet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.umlgraph.model.*;
import org.umlgraph.model.views.ContextView;
import org.umlgraph.model.views.PackageView;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;

/**
 * Chaining doclet that runs the standart Javadoc doclet first, and on success,
 * runs the generation of dot files by UMLGraph
 * @author wolf
 * 
 * @depend - - - WrappedClassDoc
 * @depend - - - WrappedRootDoc
 */
public class UmlGraphDoc implements Doclet {
	private static final String programName = "UmlGraph";
	private static final String docletName = "org.umlgraph.doclet.UmlGraph";
	/** Options used for commenting nodes */
	private static Options commentOptions;
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
	@Override
	public boolean run(DocletEnvironment environment) {

		reporter.print(Diagnostic.Kind.NOTE,"UmlGraphDoc version " + Version.VERSION +  ", running the standard doclet");
		reporter.print(Diagnostic.Kind.NOTE,"UmlGraphDoc version " + Version.VERSION + ", altering javadocs");

		try {
			String outputFolder =  "testdata/dot-out";//findOutputPath(root.options());

			Options opt = buildOptions(environment,getSupportedOptions());
//			opt.setOptions(root.options());
			// in javadoc enumerations are always printed
			opt.setShowEnumerations(true);
			opt.setRelativeLinksForSourcePackages(true);
			// enable strict matching for hide expressions
			opt.setStrictMatching(true);

			reporter.print(Diagnostic.Kind.NOTE,opt.toString());

			generatePackageDiagrams(environment, opt, outputFolder);
			generateContextDiagrams(environment, opt, outputFolder);
		} catch(Throwable t) {
			reporter.print(Diagnostic.Kind.ERROR,"Error: " + t.toString());
			t.printStackTrace();
			return false;
		}
		return true;
	}

//
//    /**
//     * Option check, forwards options to the standard doclet, if that one refuses them,
//     * they are sent to UmlGraph
//     */
//    public static int optionLength(String option) {
//	int result = Standard.optionLength(option);
//	if (result != 0)
//	    return result;
//	else
//	    return UmlGrap h.optionLength(option);
//    }

//    /**
//     * Standard doclet entry point
//     * @param root
//     * @return
//     */
//    public static boolean start(RootDoc root) {
//	root.printNotice("UmlGraphDoc version " + Version.VERSION +  ", running the standard doclet");
//	Standard.start(root);
//	root.printNotice("UmlGraphDoc version " + Version.VERSION + ", altering javadocs");
//	try {
//	    String outputFolder = findOutputPath(root.options());
//
//        Options opt = UmlGraph.buildOptions(root);
//	    opt.setOptions(root.options());
//	    // in javadoc enumerations are always printed
//	    opt.showEnumerations = true;
//	    opt.relativeLinksForSourcePackages = true;
//	    // enable strict matching for hide expressions
//	    opt.strictMatching = true;
////	    root.printNotice(opt.toString());
//
//	    generatePackageDiagrams(root, opt, outputFolder);
//	    generateContextDiagrams(root, opt, outputFolder);
//	} catch(Throwable t) {
//	    root.printWarning("Error: " + t.toString());
//	    t.printStackTrace();
//	    return false;
//	}
//	return true;
//    }

//    /**
//     * Standand doclet entry
//     * @return
//     */
//    public static LanguageVersion languageVersion() {
//	return Standard.languageVersion();
//    }

    /**
     * Generates the package diagrams for all of the packages that contain classes among those 
     * returned by RootDoc.class() 
     */
    private  void generatePackageDiagrams(DocletEnvironment root, Options opt, String outputFolder)
	    throws IOException {
	Set<String> packages = new HashSet<String>();




		Set<? extends PackageElement> mels = root.getElementUtils().getAllPackageElements("test");

		Set<? extends Element> pels = new HashSet<>();

//		for(ModuleElement mel : mels){
//			Set<? extends Element> res = mel.getEnclosedElements().stream().filter(p -> {
//				return p.getKind().equals(ElementKind.PACKAGE);
//			}).collect(Collectors.toSet());
//
//			pels = res;
////			pels.add(res);

//		}


		mels.forEach(packageDoc ->{

			if(!packages.contains(packageDoc.getSimpleName().toString())) {
				packages.add(packageDoc.getSimpleName().toString());

				OptionProvider view = new PackageView(outputFolder, packageDoc, opt);
				try {
					buildGraph(root, view, packageDoc);
				} catch (IOException e) {
					e.printStackTrace();
				}
				runGraphviz(opt.getDotExecutable(), outputFolder, packageDoc.getSimpleName().toString(), packageDoc.getSimpleName().toString(), reporter);
				try {
					alterHtmlDocs(opt, outputFolder, packageDoc.getSimpleName().toString(), packageDoc.getSimpleName().toString(),
							"package-summary.html", Pattern.compile("(</[Hh]2>)|(<h1 title=\"Package\").*"), reporter);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		});
    }

    /**
     * Generates the context diagram for a single class
     */
    private  void generateContextDiagrams(DocletEnvironment root, Options opt, String outputFolder)
	    throws IOException {



		Set<? extends Element> elements = root.getIncludedElements().stream().filter(el -> {
			return !(el.getKind().equals(ElementKind.MODULE) || el.getKind().equals(ElementKind.PACKAGE));
		}).collect(Collectors.toSet());


//        Set<ClassDoc> classDocs = new TreeSet<ClassDoc>(new Comparator<ClassDoc>() {
//            public int compare(ClassDoc cd1, ClassDoc cd2) {
//                return cd1.name().compareTo(cd2.name());
//            }
//        });
//        for (ClassDoc classDoc : root.classes())
//            classDocs.add(classDoc);

	ContextView view = null;
	for (Element classDoc : elements) {
		TypeElement tel = (TypeElement) classDoc;
	    try {
		if(view == null)
		    view = new ContextView(outputFolder, tel, root, opt);
		else
		    view.setContextCenter(tel);
		buildGraph(root, view, classDoc);

		runGraphviz(opt.getDotExecutable(), outputFolder, classDoc.getEnclosingElement().getSimpleName().toString(), classDoc.getSimpleName().toString(), reporter);
		alterHtmlDocs(opt, outputFolder, classDoc.getEnclosingElement().getSimpleName().toString(),classDoc.getSimpleName().toString(),
				classDoc.getSimpleName().toString() + ".html", Pattern.compile(".*(Class|Interface|Enum) " + classDoc.getSimpleName().toString()+ ".*") , reporter);
	    } catch (Exception e) {
		throw new RuntimeException("Error generating " + classDoc.getSimpleName().toString(), e);
	    }
	}
    }

    /**
     * Runs Graphviz dot building both a diagram (in png format) and a client side map for it.
     */
    private  void runGraphviz(String dotExecutable, String outputFolder, String packageName, String name, Reporter rep) {
    if (dotExecutable == null) {
      dotExecutable = "dot";
    }
	File dotFile = new File(outputFolder, packageName.replace(".", "/") + "/" + name + ".dot");
    File svgFile = new File(outputFolder, packageName.replace(".", "/") + "/" + name + ".svg");

	try {
	    Process p = Runtime.getRuntime().exec(new String [] {
		dotExecutable,
    "-Tsvg",
		"-o",
		svgFile.getAbsolutePath(),
		dotFile.getAbsolutePath()
	    });
	    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
	    String line;
	    while((line = reader.readLine()) != null)
			rep.print(Diagnostic.Kind.WARNING,line);
	    int result = p.waitFor();
	    if (result != 0)
			rep.print(Diagnostic.Kind.WARNING,"Errors running Graphviz on " + dotFile);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.err.println("Ensure that dot is in your path and that its path does not contain spaces");
	}
    }

    //Format string for the uml image div tag.
    private static final String UML_DIV_TAG = 
	"<div align=\"center\">" +
	    "<object width=\"100%%\" height=\"100%%\" type=\"image/svg+xml\" data=\"%1$s.svg\" alt=\"Package class diagram package %1$s\" border=0></object>" +
	"</div>";
    
    private static final String UML_AUTO_SIZED_DIV_TAG = 
    "<div align=\"center\">" +
        "<object type=\"image/svg+xml\" data=\"%1$s.svg\" alt=\"Package class diagram package %1$s\" border=0></object>" +
    "</div>";
    
    private static final String EXPANDABLE_UML_STYLE = "font-family: Arial,Helvetica,sans-serif;font-size: 1.5em; display: block; width: 250px; height: 20px; background: #009933; padding: 5px; text-align: center; border-radius: 8px; color: white; font-weight: bold;";

    //Format string for the java script tag.
    private static final String EXPANDABLE_UML = 
	"<script type=\"text/javascript\">\n" + 
	"function show() {\n" + 
	"    document.getElementById(\"uml\").innerHTML = \n" + 
	"        \'<a style=\"" + EXPANDABLE_UML_STYLE + "\" href=\"javascript:hide()\">%3$s</a>\' +\n" +
	"        \'%1$s\';\n" + 
	"}\n" + 
	"function hide() {\n" + 
	"	document.getElementById(\"uml\").innerHTML = \n" + 
	"	\'<a style=\"" + EXPANDABLE_UML_STYLE + "\" href=\"javascript:show()\">%2$s</a>\' ;\n" +
	"}\n" + 
	"</script>\n" + 
	"<div id=\"uml\" >\n" + 
	"	<a href=\"javascript:show()\">\n" + 
	"	<a style=\"" + EXPANDABLE_UML_STYLE + "\" href=\"javascript:show()\">%2$s</a> \n" +
	"</div>";
    
    /**
     * Takes an HTML file, looks for the first instance of the specified insertion point, and
     * inserts the diagram image reference and a client side map in that point.
     */
    private  void alterHtmlDocs(Options opt, String outputFolder, String packageName, String className,
	    String htmlFileName, Pattern insertPointPattern,Reporter rep) throws IOException {
	// setup files
	File output = new File(outputFolder, packageName.replace(".", "/"));
	File htmlFile = new File(output, htmlFileName);
	File alteredFile = new File(htmlFile.getAbsolutePath() + ".uml");
	if (!htmlFile.exists()) {
	    System.err.println("Expected file not found: " + htmlFile.getAbsolutePath());
	    return;
	}

	// parse & rewrite
	BufferedWriter writer = null;
	BufferedReader reader = null;
	boolean matched = false;
	try {
	    writer = new BufferedWriter(new OutputStreamWriter(new
		    FileOutputStream(alteredFile), opt.getOutputEncoding()));
	    reader = new BufferedReader(new InputStreamReader(new
		    FileInputStream(htmlFile), opt.getOutputEncoding()));

	    String line;
	    while ((line = reader.readLine()) != null) {
		writer.write(line);
		writer.newLine();
		if (!matched && insertPointPattern.matcher(line).matches()) {
		    matched = true;
			
		    String tag;
		    if (opt.isAutoSize())
		        tag = String.format(UML_AUTO_SIZED_DIV_TAG, className);
		    else
                tag = String.format(UML_DIV_TAG, className);
		    if (opt.collapsibleDiagrams)
		    	tag = String.format(EXPANDABLE_UML, tag, "Show UML class diagram", "Hide UML class diagram");
		    writer.write("<!-- UML diagram added by UMLGraph version " +
		    		Version.VERSION + 
				" (http://www.spinellis.gr/umlgraph/) -->");
		    writer.newLine();
		    writer.write(tag);
		    writer.newLine();
		}
	    }
	} finally {
	    if (writer != null)
		writer.close();
	    if (reader != null)
		reader.close();
	}

	// if altered, delete old file and rename new one to the old file name
	if (matched) {
	    htmlFile.delete();
	    alteredFile.renameTo(htmlFile);
	} else {
		rep.print(Diagnostic.Kind.NOTE,"Warning, could not find a line that matches the pattern '" + insertPointPattern.pattern()
				+ "'.\n Class diagram reference not inserted");
	    alteredFile.delete();
	}
    }

    /**
     * Returns the output path specified on the javadoc options
     */
    private static String findOutputPath(String[][] options) {
	for (int i = 0; i < options.length; i++) {
	    if (options[i][0].equals("-d"))
		return options[i][1];
	}
	return ".";
    }

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

	public static Options getCommentOptions() {
		return commentOptions;
	}
}
