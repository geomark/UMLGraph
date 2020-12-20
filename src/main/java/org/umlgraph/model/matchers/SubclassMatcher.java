package org.umlgraph.model.matchers;

import java.util.Set;
import java.util.regex.Pattern;
import jdk.javadoc.doclet.DocletEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Matches every class that extends (directly or indirectly) a class
 * matched by the regular expression provided.
 */
public class SubclassMatcher implements ClassMatcher {

    protected DocletEnvironment root;
    protected Pattern pattern;

    public SubclassMatcher(DocletEnvironment root, Pattern pattern) {
	this.root = root;
	this.pattern = pattern;
    }

    public boolean matches(TypeElement cd) {
	// if it's the class we're looking for return
	if(pattern.matcher(cd.toString()).matches())
	    return true;
	
	// recurse on supeclass, if available

	return cd.getSuperclass() != null && matches(cd.getSuperclass().toString());
    }

    public boolean matches(String name) {
        Set<? extends TypeElement> found = root.getElementUtils().getAllTypeElements(name);
	return !found.isEmpty();
    }

}
