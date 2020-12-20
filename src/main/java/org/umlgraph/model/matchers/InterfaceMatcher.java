package org.umlgraph.model.matchers;

import java.util.regex.Pattern;
import jdk.javadoc.doclet.DocletEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Matches every class that implements (directly or indirectly) an
 * interfaces matched by regular expression provided.
 */
public class InterfaceMatcher implements ClassMatcher {

    protected DocletEnvironment root;
    protected Pattern pattern;

    public InterfaceMatcher(DocletEnvironment root, Pattern pattern) {
	this.root = root;
	this.pattern = pattern;
    }

    public boolean matches(TypeElement cd) {
	// if it's the interface we're looking for, match

	if(cd.getKind().equals(ElementKind.INTERFACE) && pattern.matcher(cd.toString()).matches())
	    return true;
	
	// for each interface, recurse, since classes and interfaces 
	// are treated the same in the doclet API
	for ( TypeMirror iface : cd.getInterfaces())


	    if(matches((TypeElement) root.getTypeUtils().asElement(iface)))
		return true;
		Element superclass = root.getTypeUtils().asElement(cd.getSuperclass());
	// recurse on supeclass, if available
	return cd.getSuperclass() != null && matches((TypeElement) superclass);
    }

    public boolean matches(String name) {
    	TypeElement cd = root.getElementUtils().getTypeElement(name);
	return cd != null && matches(cd);
    }

}
