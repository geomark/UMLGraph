package org.umlgraph.model.matchers;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

public class PackageMatcher implements ClassMatcher {
    protected PackageElement packageDoc;

    public PackageMatcher(PackageElement packageDoc) {
	super();
	this.packageDoc = packageDoc;
    }

    public boolean matches(TypeElement el) {
        return el.getEnclosingElement().equals(packageDoc);
    }

    public boolean matches(String name) {
        return packageDoc.getEnclosedElements().stream().anyMatch(el -> el.getSimpleName().toString().equals(name));
    }

}
