package com.stripe.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.stripe.net.APIResource;
import com.stripe.net.RequestOptions;
import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Simple test to make sure stripe-java provides consistent bindings.
 */
public class StandardizationTest {
	public Collection<Class> getSubClasses(Class<?> parentClass) throws IOException {
		ClassPath classPath = ClassPath.from(parentClass.getClassLoader());
		ImmutableSet<ClassPath.ClassInfo> topLevelClasses = classPath.getTopLevelClasses(parentClass.getPackage().getName());
		List<Class> classList = Lists.newArrayListWithExpectedSize(topLevelClasses.size());
		for (ClassPath.ClassInfo classInfo : topLevelClasses) {
			Class c = classInfo.load();
			// Skip things that aren't subclasses
			if (!parentClass.isAssignableFrom(c)) {
				continue;
			}
			// Skip the class itself
			if (parentClass == c) {
				continue;
			}
			classList.add(classInfo.load());
		}
		return classList;
	}

	@Test
	public void allNonDeprecatedMethodsTakeOptions() throws IOException, NoSuchMethodException {
		for (Class<?> aClass : getSubClasses(APIResource.class)) {
			for (Method method : aClass.getMethods()) {
				// Skip methods not declared on the base class.
				if (method.getDeclaringClass() != aClass) {
					continue;
				}
				// Skip setters
				if (method.getName().startsWith("set")) {
					continue;
				}
				// Skip getters
				if (method.getName().startsWith("get")) {
					continue;
				}

				// If more than one method with the same parameter types is declared in a class, and one of these
				// methods has a return type that is more specific than any of the others, that method is returned;
				// otherwise one of the methods is chosen arbitrarily.
				Method mostSpecificMethod = aClass.getDeclaredMethod(method.getName(), method.getParameterTypes());
				if (!method.equals(mostSpecificMethod)) {
					continue;
				}

				Invokable<?, Object> invokable = Invokable.from(method);
				// Skip private methods.
				if (invokable.isPrivate()) {
					continue;
				}
				// Skip deprecated methods - we need to keep them around, but aren't asserting their type.
				if (invokable.isAnnotationPresent(Deprecated.class)) {
					continue;
				}
				ImmutableList<Parameter> parameters = invokable.getParameters();
				// Skip empty parameter lists - assume the author is using default values for the RequestOptions
				if (parameters.isEmpty()) {
					continue;
				}
				Parameter lastParam = parameters.get(parameters.size() - 1);
				Class<?> finalParamType = lastParam.getType().getRawType();

				// Skip methods that have exactly one param which is a map.
				if (Map.class.equals(finalParamType) && parameters.size() == 1) {
					continue;
				}

				// Skip `public static Foo retrieve(String id) {...` helper methods
				if (String.class.equals(finalParamType) && parameters.size() == 1 && "retrieve".equals(method.getName())) {
					continue;
				}

				// Skip the `public static Card createCard(String id) {...` helper method on Customer.
				if (String.class.equals(finalParamType) && parameters.size() == 1 && "createCard".equals(method.getName())) {
					continue;
				}

				if (RequestOptions.class.isAssignableFrom(finalParamType)) {
					continue;
				}
				Assert.assertTrue(
						String.format("Methods on %ss like %s.%s should take a final parameter as a %s parameter.%n", APIResource.class.getSimpleName(), aClass.getSimpleName(), method.getName(), RequestOptions.class.getSimpleName()),
						RequestOptions.class.isAssignableFrom(finalParamType));
			}
		}
	}

    @Test
    public void allModelsAreFinalOrAbstract() throws IOException {
        for (Class aStripeObjectClass : getSubClasses(StripeObject.class)) {
            boolean isAbstract = Modifier.isAbstract(aStripeObjectClass.getModifiers());
            boolean isFinal = Modifier.isFinal(aStripeObjectClass.getModifiers());
            // We prefer having either abstract or final methods because they give us the most flexibility in modifying
            // future versions of our API. Also, we prefer to use composition over inheritance.
            Assert.assertTrue(String.format("Class %s should be either abstract or final, but isn't.", aStripeObjectClass.getCanonicalName()), isAbstract || isFinal);
        }
    }
}