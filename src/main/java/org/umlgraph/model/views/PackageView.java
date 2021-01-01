package org.umlgraph.model.views;

import org.umlgraph.model.OptionProvider;
import org.umlgraph.model.Options;
import org.umlgraph.model.matchers.ClassMatcher;
import org.umlgraph.model.matchers.PackageMatcher;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * A view designed for UMLDoc, filters out everything that it's not contained in
 * the specified package.
 * <p>
 * As such, can be viewed as a simplified version of a {@linkplain View} using a
 * single {@linkplain ClassMatcher}, and provides some extra configuration such
 * as output path configuration (and it is specified in code rather than in
 * javadoc comments).
 * @author wolf
 * 
 */
public class PackageView implements OptionProvider {

    private static final String[] HIDE = new String[] { "hide" };
    private PackageElement pd;
    private OptionProvider parent;
    private ClassMatcher matcher;
    private String outputPath;
    private Options opt;

    public PackageView(String outputFolder, PackageElement pd, OptionProvider parent) {
	this.parent = parent;
	this.pd = pd;
	this.matcher = new PackageMatcher(pd);
	this.opt = parent.getGlobalOptions();
//	this.opt.setOptions(pd.toString());
	this.outputPath = pd.getSimpleName().toString().replace('.', '/') + "/" + pd.getSimpleName().toString() + ".dot";
    }

    public String getDisplayName() {
	return "Package view for package " + pd;
    }

    public Options getGlobalOptions() {
	Options go = parent.getGlobalOptions();

	go.setOption(new String[] { "output", outputPath });
	go.setOption(HIDE);

	return go;
    }

    public Options getOptionsFor(TypeElement cd) {
	Options go = parent.getGlobalOptions();
	overrideForClass(go, cd);
	return go;
    }

    public Options getOptionsFor(String name) {
	Options go = parent.getGlobalOptions();
	overrideForClass(go, name);
	return go;
    }

    public void overrideForClass(Options opt, TypeElement cd) {
//	opt.setOptions(cd);
	boolean inPackage = matcher.matches(cd);
	if (inPackage)
	    opt.setShowQualified(false);
	boolean included = inPackage || this.opt.matchesIncludeExpression(cd.getQualifiedName().toString());
	if (!included || this.opt.matchesHideExpression(cd.getQualifiedName().toString()))
	    opt.setOption(HIDE);
    }

    public void overrideForClass(Options opt, String className) {
		opt.setShowQualified(false);
	boolean inPackage = matcher.matches(className);
	if (inPackage)
		opt.setShowQualified(false);
	boolean included = inPackage || this.opt.matchesIncludeExpression(className);
	if (!included || this.opt.matchesHideExpression(className))
	    opt.setOption(HIDE);
    }

}