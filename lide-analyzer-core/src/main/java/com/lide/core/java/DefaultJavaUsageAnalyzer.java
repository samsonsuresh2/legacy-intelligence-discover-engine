package com.lide.core.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.lide.core.fs.CodebaseIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation that leverages JavaParser to inspect Struts/Spring Java sources.
 */
public class DefaultJavaUsageAnalyzer implements JavaUsageAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJavaUsageAnalyzer.class);
    private static final Set<String> REQUIRED_ANNOTATIONS = Set.of("NotNull", "NotBlank", "NotEmpty");
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of("Controller", "RestController");
    private static final Set<String> REQUEST_MAPPING_ANNOTATIONS = Set.of(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"
    );

    private final JavaParser parser;

    public DefaultJavaUsageAnalyzer() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(LanguageLevel.JAVA_17);
        this.parser = new JavaParser(configuration);
    }

    @Override
    public JavaMetadataIndex analyze(CodebaseIndex index) {
        Objects.requireNonNull(index, "index");

        Map<String, List<JavaFieldMetadata>> fieldsByClass = new LinkedHashMap<>();
        Map<String, List<JavaMetadataIndex.HandlerMethodMetadata>> handlerMethods = new LinkedHashMap<>();
        Set<String> strutsFormClasses = new LinkedHashSet<>();
        Set<String> strutsActionClasses = new LinkedHashSet<>();
        Set<String> controllerClasses = new LinkedHashSet<>();

        for (Path javaFile : index.getJavaFiles()) {
            processJavaFile(javaFile, fieldsByClass, handlerMethods, strutsFormClasses, strutsActionClasses, controllerClasses);
        }

        LOGGER.info("Java analysis complete: {} classes with field metadata, {} controllers, {} Struts forms",
                fieldsByClass.size(), controllerClasses.size(), strutsFormClasses.size());

        return new JavaMetadataIndex(fieldsByClass, handlerMethods, strutsFormClasses, strutsActionClasses, controllerClasses);
    }

    private void processJavaFile(Path javaFile,
                                 Map<String, List<JavaFieldMetadata>> fieldsByClass,
                                 Map<String, List<JavaMetadataIndex.HandlerMethodMetadata>> handlerMethods,
                                 Set<String> strutsFormClasses,
                                 Set<String> strutsActionClasses,
                                 Set<String> controllerClasses) {
        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(javaFile);
        } catch (IOException ex) {
            LOGGER.warn("Failed to read {}: {}", javaFile, ex.getMessage());
            return;
        }

        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            LOGGER.warn("Skipping {} due to parse errors: {}", javaFile, result.getProblems());
            return;
        }

        CompilationUnit unit = result.getResult().get();
        String packageName = unit.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");

        for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (declaration.isInterface()) {
                continue;
            }
            String qualifiedName = computeQualifiedName(declaration, packageName);
            Map<String, JavaFieldMetadata.Builder> builders = new LinkedHashMap<>();

            boolean isStrutsForm = declaration.getExtendedTypes().stream()
                    .map(ClassOrInterfaceType::getName)
                    .map(SimpleName::asString)
                    .anyMatch(name -> name.endsWith("ActionForm"));

            boolean isStrutsAction = declaration.getExtendedTypes().stream()
                    .map(ClassOrInterfaceType::getName)
                    .map(SimpleName::asString)
                    .anyMatch(name -> name.endsWith("Action") || name.endsWith("DispatchAction"));

            boolean isController = declaration.getAnnotations().stream()
                    .map(annotation -> annotation.getName().getIdentifier())
                    .anyMatch(CONTROLLER_ANNOTATIONS::contains);

            if (isStrutsForm) {
                strutsFormClasses.add(qualifiedName);
            }
            if (isStrutsAction) {
                strutsActionClasses.add(qualifiedName);
            }
            if (isController) {
                controllerClasses.add(qualifiedName);
            }

            processFieldDeclarations(qualifiedName, declaration, builders);
            processAccessorAnnotations(qualifiedName, declaration, builders);

            if (!builders.isEmpty()) {
                List<JavaFieldMetadata> metadata = builders.values().stream()
                        .map(JavaFieldMetadata.Builder::build)
                        .collect(Collectors.toCollection(ArrayList::new));
                fieldsByClass.put(qualifiedName, metadata);
            }

            if (isController) {
                List<JavaMetadataIndex.HandlerMethodMetadata> mappings = analyzeControllerMethods(qualifiedName, declaration);
                if (!mappings.isEmpty()) {
                    handlerMethods.put(qualifiedName, mappings);
                }
            }
        }
    }

    private void processFieldDeclarations(String qualifiedName,
                                          ClassOrInterfaceDeclaration declaration,
                                          Map<String, JavaFieldMetadata.Builder> builders) {
        for (FieldDeclaration field : declaration.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                String fieldName = variable.getNameAsString();
                JavaFieldMetadata.Builder builder = builders.computeIfAbsent(fieldName,
                        name -> new JavaFieldMetadata.Builder(qualifiedName, name));
                builder.fieldType(variable.getType().asString());
                applyAnnotations(builder, field.getAnnotations());
            }
        }
    }

    private void processAccessorAnnotations(String qualifiedName,
                                            ClassOrInterfaceDeclaration declaration,
                                            Map<String, JavaFieldMetadata.Builder> builders) {
        for (MethodDeclaration method : declaration.getMethods()) {
            if (method.getAnnotations().isEmpty()) {
                continue;
            }
            if (!method.getParameters().isEmpty()) {
                continue;
            }
            String methodName = method.getNameAsString();
            String property = null;
            if (methodName.startsWith("get") && methodName.length() > 3) {
                property = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                property = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }
            if (property == null || property.isEmpty()) {
                continue;
            }
            JavaFieldMetadata.Builder builder = builders.computeIfAbsent(property,
                    name -> new JavaFieldMetadata.Builder(qualifiedName, name).fieldType(method.getType().asString()));
            builder.fieldType(method.getType().asString());
            applyAnnotations(builder, method.getAnnotations());
        }
    }

    private List<JavaMetadataIndex.HandlerMethodMetadata> analyzeControllerMethods(String qualifiedName,
                                                                                   ClassOrInterfaceDeclaration declaration) {
        List<JavaMetadataIndex.HandlerMethodMetadata> handlerMetadata = new ArrayList<>();
        for (MethodDeclaration method : declaration.getMethods()) {
            List<AnnotationExpr> handlerAnnotations = method.getAnnotations().stream()
                    .filter(annotation -> REQUEST_MAPPING_ANNOTATIONS.contains(annotation.getName().getIdentifier()))
                    .collect(Collectors.toList());
            if (handlerAnnotations.isEmpty()) {
                continue;
            }

            List<String> httpMethods = new ArrayList<>();
            List<String> paths = new ArrayList<>();
            for (AnnotationExpr annotation : handlerAnnotations) {
                String annotationName = annotation.getName().getIdentifier();
                httpMethods.addAll(resolveHttpMethods(annotationName, annotation));
                paths.addAll(extractStringValues(annotation, "value"));
                paths.addAll(extractStringValues(annotation, "path"));
            }

            if (paths.isEmpty()) {
                paths.add("/");
            }
            if (httpMethods.isEmpty()) {
                httpMethods.add("GET");
            }

            List<JavaMetadataIndex.HandlerParameterMetadata> parameters = method.getParameters().stream()
                    .map(this::toParameterMetadata)
                    .collect(Collectors.toList());

            handlerMetadata.add(new JavaMetadataIndex.HandlerMethodMetadata(method.getNameAsString(),
                    httpMethods, paths, parameters));
        }

        if (!handlerMetadata.isEmpty()) {
            LOGGER.debug("Controller {} has {} handler methods", qualifiedName, handlerMetadata.size());
        }

        return handlerMetadata;
    }

    private JavaMetadataIndex.HandlerParameterMetadata toParameterMetadata(Parameter parameter) {
        String typeName = parameter.getType().asString();
        List<String> annotations = parameter.getAnnotations().stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .collect(Collectors.toList());
        return new JavaMetadataIndex.HandlerParameterMetadata(parameter.getNameAsString(), typeName, annotations);
    }

    private List<String> resolveHttpMethods(String annotationName, AnnotationExpr annotation) {
        switch (annotationName) {
            case "GetMapping":
                return List.of("GET");
            case "PostMapping":
                return List.of("POST");
            case "PutMapping":
                return List.of("PUT");
            case "DeleteMapping":
                return List.of("DELETE");
            case "PatchMapping":
                return List.of("PATCH");
            case "RequestMapping":
                List<String> methods = extractEnumValues(annotation, "method");
                if (!methods.isEmpty()) {
                    return methods;
                }
                return List.of();
            default:
                return List.of();
        }
    }

    private void applyAnnotations(JavaFieldMetadata.Builder builder, List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getName().getIdentifier();
            if (REQUIRED_ANNOTATIONS.contains(name)) {
                builder.addConstraint("required");
                builder.putAttribute("required", Boolean.TRUE);
            } else if ("Size".equals(name)) {
                builder.addConstraint("size");
                extractNumericAttribute(annotation, "min").ifPresent(value -> builder.putAttribute("minLength", value));
                extractNumericAttribute(annotation, "max").ifPresent(value -> builder.putAttribute("maxLength", value));
            } else if ("Min".equals(name) || "DecimalMin".equals(name)) {
                builder.addConstraint("min");
                extractNumericAttribute(annotation, "value").ifPresent(value -> builder.putAttribute("min", value));
                extractSingleValue(annotation).ifPresent(value -> builder.putAttribute("min", value));
            } else if ("Max".equals(name) || "DecimalMax".equals(name)) {
                builder.addConstraint("max");
                extractNumericAttribute(annotation, "value").ifPresent(value -> builder.putAttribute("max", value));
                extractSingleValue(annotation).ifPresent(value -> builder.putAttribute("max", value));
            } else if ("Pattern".equals(name)) {
                builder.addConstraint("pattern");
                extractStringAttribute(annotation, "regexp")
                        .or(() -> extractSingleValue(annotation))
                        .ifPresent(value -> builder.putAttribute("pattern", value));
            }
        }
    }

    private Optional<Number> extractNumericAttribute(AnnotationExpr annotation, String attribute) {
        return extractValue(annotation, attribute)
                .flatMap(this::toNumber);
    }

    private Optional<String> extractStringAttribute(AnnotationExpr annotation, String attribute) {
        return extractValue(annotation, attribute)
                .flatMap(this::toStringLiteral);
    }

    private Optional<Object> extractSingleValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return Optional.ofNullable(resolveExpression(single.getMemberValue()));
        }
        return Optional.empty();
    }

    private Optional<Expression> extractValue(AnnotationExpr annotation, String attribute) {
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getName().asString().equals(attribute)) {
                    return Optional.of(pair.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Number> toNumber(Expression expression) {
        Object value = resolveExpression(expression);
        if (value instanceof Number) {
            return Optional.of((Number) value);
        }
        if (value instanceof String stringValue) {
            try {
                if (stringValue.contains(".")) {
                    return Optional.of(Double.parseDouble(stringValue));
                }
                return Optional.of(Long.parseLong(stringValue));
            } catch (NumberFormatException ex) {
                LOGGER.debug("Unable to parse numeric constraint value '{}': {}", stringValue, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    private Optional<String> toStringLiteral(Expression expression) {
        Object value = resolveExpression(expression);
        if (value instanceof String string) {
            return Optional.of(string);
        }
        return Optional.empty();
    }

    private List<String> extractStringValues(AnnotationExpr annotation, String attribute) {
        List<String> values = new ArrayList<>();
        extractValue(annotation, attribute).ifPresent(expression -> values.addAll(resolveStrings(expression)));
        return values;
    }

    private List<String> extractEnumValues(AnnotationExpr annotation, String attribute) {
        List<String> values = new ArrayList<>();
        extractValue(annotation, attribute).ifPresent(expression -> values.addAll(resolveEnumNames(expression)));
        return values;
    }

    private List<String> resolveStrings(Expression expression) {
        Object value = resolveExpression(expression);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> objects = (List<Object>) value;
            return objects.stream().map(String::valueOf).collect(Collectors.toList());
        }
        if (value != null) {
            return List.of(String.valueOf(value));
        }
        return List.of();
    }

    private List<String> resolveEnumNames(Expression expression) {
        if (expression instanceof ArrayInitializerExpr initializer) {
            List<String> values = new ArrayList<>();
            for (Expression value : initializer.getValues()) {
                values.addAll(resolveEnumNames(value));
            }
            return values;
        }
        Object resolved = resolveExpression(expression);
        if (resolved instanceof String string) {
            int lastDot = string.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < string.length() - 1) {
                return List.of(string.substring(lastDot + 1));
            }
            return List.of(string);
        }
        return List.of();
    }

    private Object resolveExpression(Expression expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof StringLiteralExpr literal) {
            return literal.getValue();
        }
        if (expression instanceof TextBlockLiteralExpr block) {
            return block.getValue();
        }
        if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
            return Long.parseLong(integerLiteralExpr.getValue());
        }
        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return Long.parseLong(longLiteralExpr.getValue().replace("L", ""));
        }
        if (expression instanceof DoubleLiteralExpr doubleLiteralExpr) {
            return Double.parseDouble(doubleLiteralExpr.getValue());
        }
        if (expression instanceof BooleanLiteralExpr booleanLiteralExpr) {
            return booleanLiteralExpr.getValue();
        }
        if (expression instanceof CharLiteralExpr charLiteralExpr) {
            return charLiteralExpr.getValue();
        }
        if (expression instanceof ArrayInitializerExpr initializer) {
            List<Object> values = new ArrayList<>();
            for (Expression value : initializer.getValues()) {
                values.add(resolveExpression(value));
            }
            return values;
        }
        if (expression instanceof NameExpr nameExpr) {
            return nameExpr.getNameAsString();
        }
        if (expression instanceof FieldAccessExpr fieldAccess) {
            return fieldAccess.toString();
        }
        if (expression instanceof NormalAnnotationExpr || expression instanceof SingleMemberAnnotationExpr
                || expression instanceof MarkerAnnotationExpr) {
            return expression.toString();
        }
        if (expression instanceof ArrayCreationExpr arrayCreationExpr) {
            List<Object> values = new ArrayList<>();
            arrayCreationExpr.getInitializer().ifPresent(initializer ->
                    initializer.getValues().forEach(value -> values.add(resolveExpression(value))));
            return values;
        }
        if (expression instanceof EnclosedExpr enclosedExpr) {
            return resolveExpression(enclosedExpr.getInner());
        }
        if (expression instanceof NullLiteralExpr) {
            return null;
        }
        return expression.toString();
    }

    private String computeQualifiedName(ClassOrInterfaceDeclaration declaration, String packageName) {
        List<String> names = new ArrayList<>();
        Node current = declaration;
        while (current instanceof ClassOrInterfaceDeclaration) {
            names.add(0, ((ClassOrInterfaceDeclaration) current).getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        String joined = String.join(".", names);
        if (packageName == null || packageName.isBlank()) {
            return joined;
        }
        return packageName + "." + joined;
    }
}
