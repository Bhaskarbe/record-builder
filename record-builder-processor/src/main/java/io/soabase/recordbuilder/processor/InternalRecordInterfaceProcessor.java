/**
 * Copyright 2019 Jordan Zimmerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.soabase.recordbuilder.processor;

import com.squareup.javapoet.*;
import io.soabase.recordbuilder.core.IgnoreDefaultMethod;
import io.soabase.recordbuilder.core.RecordBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.soabase.recordbuilder.processor.ElementUtils.getBuilderName;
import static io.soabase.recordbuilder.processor.RecordBuilderProcessor.generatedRecordInterfaceAnnotation;

class InternalRecordInterfaceProcessor {
    private final ProcessingEnvironment processingEnv;
    private final String packageName;
    private final TypeSpec recordType;
    private final List<Component> recordComponents;
    private final TypeElement iface;
    private final ClassType recordClassType;
    private final List<String> alternateMethods;

    private static final String FAKE_METHOD_NAME = "__FAKE__";

    private static final Set<String> javaBeanPrefixes = Set.of("get", "is");

    private record Component(ExecutableElement element, Optional<String> alternateName) {
    }

    InternalRecordInterfaceProcessor(ProcessingEnvironment processingEnv, TypeElement iface, boolean addRecordBuilder, RecordBuilder.Options metaData, Optional<String> packageNameOpt, boolean fromTemplate) {
        this.processingEnv = processingEnv;
        packageName = packageNameOpt.orElseGet(() -> ElementUtils.getPackageName(iface));
        recordComponents = getRecordComponents(iface);
        this.iface = iface;

        ClassType ifaceClassType = ElementUtils.getClassType(iface, iface.getTypeParameters());
        recordClassType = ElementUtils.getClassType(packageName, getBuilderName(iface, metaData, ifaceClassType, metaData.interfaceSuffix()), iface.getTypeParameters());
        List<TypeVariableName> typeVariables = iface.getTypeParameters().stream().map(TypeVariableName::get).collect(Collectors.toList());

        MethodSpec methodSpec = generateArgumentList();

        TypeSpec.Builder builder = TypeSpec.classBuilder(recordClassType.name())
                .addSuperinterface(iface.asType())
                .addMethod(methodSpec)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(generatedRecordInterfaceAnnotation)
                .addTypeVariables(typeVariables);

        if (addRecordBuilder) {
            ClassType builderClassType = ElementUtils.getClassType(packageName, getBuilderName(iface, metaData, recordClassType, metaData.suffix()) + "." + metaData.withClassName(), iface.getTypeParameters());
            builder.addAnnotation(RecordBuilder.class);
            builder.addSuperinterface(builderClassType.typeName());
            if (fromTemplate) {
                builder.addAnnotation(AnnotationSpec.get(metaData));
            } else {
                var options = iface.getAnnotation(RecordBuilder.Options.class);
                if (options != null) {
                    builder.addAnnotation(AnnotationSpec.get(options));
                }
            }
        }

        alternateMethods = buildAlternateMethods(recordComponents);

        recordType = builder.build();
    }

    boolean isValid() {
        return !recordComponents.isEmpty();
    }

    TypeSpec recordType() {
        return recordType;
    }

    String packageName() {
        return packageName;
    }

    ClassType recordClassType() {
        return recordClassType;
    }

    String toRecord(String classSource) {
        // javapoet does yet support records - so a class was created and we can reshape it
        // The class will look something like this:
        /*
            // Auto generated by io.soabase.recordbuilder.core.RecordBuilder: https://github.com/Randgalt/record-builder
            package io.soabase.recordbuilder.test;

            import io.soabase.recordbuilder.core.RecordBuilder;
            import javax.annotation.processing.Generated;

            @Generated("io.soabase.recordbuilder.core.RecordInterface")
            @RecordBuilder
            public class MyRecord implements MyInterface {
                void __FAKE__(String name, int age) {
                }
            }
        */
        Pattern pattern = Pattern.compile("(.*)(implements.*)(\\{)(.*" + FAKE_METHOD_NAME + ")(\\(.*\\))(.*)", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(classSource);
        if (!matcher.find() || matcher.groupCount() != 6) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Internal error generating record. Group count: " + matcher.groupCount(), iface);
        }

        String declaration = matcher.group(1).trim().replace("class", "record");
        String implementsSection = matcher.group(2).trim();
        String argumentList = matcher.group(5).trim();

        StringBuilder fixedRecord = new StringBuilder(declaration).append(argumentList).append(' ').append(implementsSection).append(" {");
        alternateMethods.forEach(method -> fixedRecord.append('\n').append(method));
        fixedRecord.append('}');
        return fixedRecord.toString();
    }

    private MethodSpec generateArgumentList() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(FAKE_METHOD_NAME);
        recordComponents.forEach(component -> {
            String name = component.alternateName.orElseGet(() -> component.element.getSimpleName().toString());
            ParameterSpec parameterSpec = ParameterSpec.builder(ClassName.get(component.element.getReturnType()), name).build();
            builder.addTypeVariables(component.element.getTypeParameters().stream().map(TypeVariableName::get).collect(Collectors.toList()));
            builder.addParameter(parameterSpec);
        });
        return builder.build();
    }

    private List<String> buildAlternateMethods(List<Component> recordComponents) {
        return recordComponents.stream()
                .filter(component -> component.alternateName.isPresent())
                .map(component -> {
                    var method = MethodSpec.methodBuilder(component.element.getSimpleName().toString())
                            .addAnnotation(Override.class)
                            .addAnnotation(generatedRecordInterfaceAnnotation)
                            .returns(ClassName.get(component.element.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addCode("return $L();", component.alternateName.get())
                            .build();
                    return method.toString();
                })
                .collect(Collectors.toList());
    }

    private List<Component> getRecordComponents(TypeElement iface) {
        List<Component> components = new ArrayList<>();
        try {
            getRecordComponents(iface, components, new HashSet<>(), new HashSet<>());
            if (components.isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Annotated interface has no component methods", iface);
            }
        } catch (IllegalInterface e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), iface);
            components = Collections.emptyList();
        }
        return components;
    }

    private static class IllegalInterface extends RuntimeException {
        public IllegalInterface(String message) {
            super(message);
        }

    }

    private void getRecordComponents(TypeElement iface, Collection<Component> components, Set<String> visitedSet, Set<String> usedNames) {
        if (!visitedSet.add(iface.getQualifiedName().toString())) {
            return;
        }

        iface.getEnclosedElements().stream()
                .filter(element -> (element.getKind() == ElementKind.METHOD) && !(element.getModifiers().contains(Modifier.STATIC)))
                .map(element -> ((ExecutableElement) element))
                .filter(element -> {
                    if (element.isDefault()) {
                        return element.getAnnotation(IgnoreDefaultMethod.class) == null;
                    }
                    return true;
                })
                .peek(element -> {
                    if (!element.getParameters().isEmpty() || element.getReturnType().getKind() == TypeKind.VOID) {
                        throw new IllegalInterface(String.format("Non-static, non-default methods must take no arguments and must return a value. Bad method: %s.%s()", iface.getSimpleName(), element.getSimpleName()));
                    }
                    if (!element.getTypeParameters().isEmpty()) {
                        throw new IllegalInterface(String.format("Interface methods cannot have type parameters. Bad method: %s.%s()", iface.getSimpleName(), element.getSimpleName()));
                    }
                })
                .filter(element -> usedNames.add(element.getSimpleName().toString()))
                .map(element -> new Component(element, stripBeanPrefix(element.getSimpleName().toString())))
                .collect(Collectors.toCollection(() -> components));
        iface.getInterfaces().forEach(parentIface -> {
            TypeElement parentIfaceElement = (TypeElement) processingEnv.getTypeUtils().asElement(parentIface);
            getRecordComponents(parentIfaceElement, components, visitedSet, usedNames);
        });
    }

    private Optional<String> stripBeanPrefix(String name) {
        return javaBeanPrefixes.stream()
                .filter(prefix -> name.startsWith(prefix) && (name.length() > prefix.length()))
                .findFirst()
                .map(prefix -> {
                    var stripped = name.substring(prefix.length());
                    return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
                });
    }
}
