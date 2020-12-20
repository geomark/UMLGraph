package org.umlgraph.model.views;

import java.io.IOException;
import java.util.regex.Pattern;


import jdk.javadoc.doclet.DocletEnvironment;
import org.umlgraph.model.OptionProvider;
import org.umlgraph.model.Options;
import org.umlgraph.model.matchers.ContextMatcher;

import javax.lang.model.element.TypeElement;

/**
 * A view designed for UMLDoc, filters out everything that it's not directly
 * connected to the center class of the context.
 * <p>
 * As such, can be viewed as a simplified version of a {@linkplain View} using a
 * single {@linkplain ContextMatcher}, but provides some extra configuration
 * such as context highlighting and output path configuration (and it is
 * specified in code rather than in javadoc comments).
 * @author wolf
 * 
 */
public class ContextView implements OptionProvider {

    private TypeElement cd;
    private ContextMatcher matcher;
    private Options globalOptions;
    private Options myGlobalOptions;
    private Options hideOptions;
    private Options centerOptions;
    private Options packageOptions;
    private static final String[] HIDE_OPTIONS = new String[] { "hide" };

    public ContextView(String outputFolder, TypeElement cd, DocletEnvironment root, Options parent)
	    throws IOException {
	this.cd = cd;


	String outputPath = cd.getEnclosingElement().getSimpleName().toString().replace('.', '/') + "/" + cd.getSimpleName().toString()
		+ ".dot";

	// setup options statically, so that we won't need to change them so
	// often
	this.globalOptions = parent.getGlobalOptions();
	
	this.packageOptions = parent.getGlobalOptions();  
	this.packageOptions.showQualified = false;

	this.myGlobalOptions = parent.getGlobalOptions();
	this.myGlobalOptions.setOption(new String[] { "output", outputPath });
	this.myGlobalOptions.setOption(HIDE_OPTIONS);

	this.hideOptions = parent.getGlobalOptions();
	this.hideOptions.setOption(HIDE_OPTIONS);

	this.centerOptions = parent.getGlobalOptions();
	this.centerOptions.nodeFillColor = "lemonChiffon";
	this.centerOptions.showQualified = false;

	this.matcher = new ContextMatcher(root, Pattern.compile(Pattern.quote(cd.toString())),
		myGlobalOptions, true);

    }

    public void setContextCenter(TypeElement contextCenter) {
	this.cd = contextCenter;
	String outputPath = cd.getEnclosingElement().getSimpleName().toString().replace('.', '/') + "/" + cd.getSimpleName().toString()
		+ ".dot";
	this.myGlobalOptions.setOption(new String[] { "output", outputPath });
	matcher.setContextCenter(Pattern.compile(Pattern.quote(cd.toString())));
    }

    public String getDisplayName() {
	return "Context view for class " + cd;
    }

    public Options getGlobalOptions() {
	return myGlobalOptions;
    }

    public Options getOptionsFor(TypeElement cd) {
	Options opt;
	if (globalOptions.matchesHideExpression(cd.getQualifiedName().toString())
		|| !(matcher.matches(cd) || globalOptions.matchesIncludeExpression(cd.getQualifiedName().toString()))) {
		opt = hideOptions;
	} else if (cd.equals(this.cd)) {
		opt = centerOptions;
	} else if(cd.getEnclosingElement().getSimpleName().toString()
			.equals(this.cd.getEnclosingElement().getSimpleName().toString())){
		opt = packageOptions;
	} else {
		opt = globalOptions;
	}
	Options optionClone = (Options) opt.clone();
	overrideForClass(optionClone, cd);
	return optionClone;
    }

    public Options getOptionsFor(String name) {
	Options opt;
	if (!matcher.matches(name))
		opt = hideOptions;
	else if (name.equals(cd.getSimpleName().toString()))
		opt = centerOptions;
	else
		opt = globalOptions;
	Options optionClone = (Options) opt.clone();
	overrideForClass(optionClone, name);
	return optionClone;
    }

    public void overrideForClass(Options opt, TypeElement cd) {
	opt.setOptions(cd);
	if (opt.matchesHideExpression(cd.getQualifiedName().toString())
		|| !(matcher.matches(cd) || opt.matchesIncludeExpression(cd.getQualifiedName().toString())))
	    opt.setOption(HIDE_OPTIONS);
	if (cd.equals(this.cd))
	    opt.nodeFillColor = "lemonChiffon";
    }

    public void overrideForClass(Options opt, String className) {
	if (!(matcher.matches(className) || opt.matchesIncludeExpression(className)))
	    opt.setOption(HIDE_OPTIONS);
    }

}
