package com.lide.core.java;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of metadata extracted from Java sources.
 */
public final class JavaMetadataIndex {

    private final Map<String, List<JavaFieldMetadata>> fieldsByClass;
    private final Map<String, List<HandlerMethodMetadata>> handlerMethodsByController;
    private final Set<String> strutsFormClasses;
    private final Set<String> strutsActionClasses;
    private final Set<String> controllerClasses;

    public JavaMetadataIndex(Map<String, List<JavaFieldMetadata>> fieldsByClass,
                             Map<String, List<HandlerMethodMetadata>> handlerMethodsByController,
                             Set<String> strutsFormClasses,
                             Set<String> strutsActionClasses,
                             Set<String> controllerClasses) {
        this.fieldsByClass = copyMap(fieldsByClass);
        this.handlerMethodsByController = copyMap(handlerMethodsByController);
        this.strutsFormClasses = Collections.unmodifiableSet(new LinkedHashSet<>(strutsFormClasses));
        this.strutsActionClasses = Collections.unmodifiableSet(new LinkedHashSet<>(strutsActionClasses));
        this.controllerClasses = Collections.unmodifiableSet(new LinkedHashSet<>(controllerClasses));
    }

    public Map<String, List<JavaFieldMetadata>> getFieldsByClass() {
        return fieldsByClass;
    }

    public List<JavaFieldMetadata> getFieldsForClass(String className) {
        return fieldsByClass.getOrDefault(className, List.of());
    }

    public Map<String, List<HandlerMethodMetadata>> getHandlerMethodsByController() {
        return handlerMethodsByController;
    }

    public Set<String> getStrutsFormClasses() {
        return strutsFormClasses;
    }

    public Set<String> getStrutsActionClasses() {
        return strutsActionClasses;
    }

    public Set<String> getControllerClasses() {
        return controllerClasses;
    }

    private static <T> Map<String, List<T>> copyMap(Map<String, List<T>> source) {
        Map<String, List<T>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Describes a Spring MVC style handler method discovered in a controller.
     */
    public static final class HandlerMethodMetadata {
        private final String methodName;
        private final List<String> httpMethods;
        private final List<String> paths;
        private final List<HandlerParameterMetadata> parameters;

        public HandlerMethodMetadata(String methodName,
                                     List<String> httpMethods,
                                     List<String> paths,
                                     List<HandlerParameterMetadata> parameters) {
            this.methodName = Objects.requireNonNull(methodName, "methodName");
            this.httpMethods = List.copyOf(httpMethods);
            this.paths = List.copyOf(paths);
            this.parameters = List.copyOf(parameters);
        }

        public String getMethodName() {
            return methodName;
        }

        public List<String> getHttpMethods() {
            return httpMethods;
        }

        public List<String> getPaths() {
            return paths;
        }

        public List<HandlerParameterMetadata> getParameters() {
            return parameters;
        }
    }

    /**
     * Metadata for a handler method parameter, including annotations that influence binding.
     */
    public static final class HandlerParameterMetadata {
        private final String parameterName;
        private final String typeName;
        private final List<String> annotations;

        public HandlerParameterMetadata(String parameterName, String typeName, List<String> annotations) {
            this.parameterName = parameterName;
            this.typeName = typeName;
            this.annotations = List.copyOf(annotations);
        }

        public String getParameterName() {
            return parameterName;
        }

        public String getTypeName() {
            return typeName;
        }

        public List<String> getAnnotations() {
            return annotations;
        }
    }
}
