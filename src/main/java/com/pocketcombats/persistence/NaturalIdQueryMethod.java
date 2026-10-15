package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.LockMode;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.RootGraph;
import org.hibernate.metamodel.mapping.NaturalIdMapping;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Meta;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.query.Jpa21Utils;
import org.springframework.data.jpa.repository.query.JpaEntityGraph;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record NaturalIdQueryMethod<T>(
        Method method,
        Class<T> entityType,
        List<String> attributeNames,
        @Nullable LockMode lockMode,
        @Nullable LoadGraph<T> loadGraph
) {

    private static final Logger LOG = LoggerFactory.getLogger(NaturalIdQueryMethod.class);

    boolean returnsOptional() {
        return method.getReturnType() == Optional.class;
    }

    /**
     * The query methods named {@code findByX} or {@code getByX} after the natural id attributes,
     * joined by {@code And} in any order, that take one argument per attribute and return the
     * entity or an {@link Optional} of it.
     */
    static Set<NaturalIdQueryMethod<?>> resolve(
            EntityManager entityManager,
            RepositoryInformation repositoryInformation,
            NaturalIdMapping naturalIdMapping
    ) {
        List<String> attributeNames = naturalIdMapping.getNaturalIdAttributes().stream()
                .map(SingularAttributeMapping::getAttributeName)
                .toList();
        Map<String, List<String>> parameterAttributesByName = parameterAttributesByName(attributeNames);
        Set<NaturalIdQueryMethod<?>> methods = new HashSet<>();
        for (Method method : repositoryInformation.getQueryMethods()) {
            List<String> parameterAttributes = parameterAttributesByName.get(method.getName());
            if (parameterAttributes == null) {
                continue;
            }
            if (AnnotatedElementUtils.hasAnnotation(method, Query.class)) {
                LOG.debug(
                        "{} is left to Spring Data: it declares its own query",
                        describe(repositoryInformation, method)
                );
                continue;
            }
            if (AnnotatedElementUtils.hasAnnotation(method, QueryHints.class)
                    || AnnotatedElementUtils.hasAnnotation(method, Meta.class)) {
                LOG.debug(
                        "{} is left to Spring Data: a natural id lookup cannot apply query hints",
                        describe(repositoryInformation, method)
                );
                continue;
            }
            if (method.getParameterCount() != parameterAttributes.size()
                    || !returnsEntity(repositoryInformation, method)) {
                LOG.debug(
                        "{} is left to Spring Data: a natural id lookup takes one argument per natural id attribute "
                                + "and returns the entity or an Optional",
                        describe(repositoryInformation, method)
                );
                continue;
            }
            methods.add(of(entityManager, repositoryInformation.getDomainType(), method, parameterAttributes));
        }
        return methods;
    }

    /**
     * The natural id attribute each parameter supplies, by the accepted method names:
     * {@code findByXAndY}, {@code getByXAndY}, {@code findByYAndX}, {@code getByYAndX}.
     */
    private static Map<String, List<String>> parameterAttributesByName(List<String> attributeNames) {
        Map<String, List<String>> parameterAttributesByName = new HashMap<>();
        for (List<String> permutation : permutations(attributeNames)) {
            String capitalizedAttributeNames = permutation.stream()
                    .map(StringUtils::capitalize)
                    .collect(Collectors.joining("And"));
            parameterAttributesByName.put("findBy" + capitalizedAttributeNames, permutation);
            parameterAttributesByName.put("getBy" + capitalizedAttributeNames, permutation);
        }
        return parameterAttributesByName;
    }

    private static List<List<String>> permutations(List<String> attributeNames) {
        if (attributeNames.isEmpty()) {
            return List.of(List.of());
        }
        List<List<String>> permutations = new ArrayList<>();
        for (String first : attributeNames) {
            List<String> others = attributeNames.stream().filter(name -> !name.equals(first)).toList();
            for (List<String> permutation : permutations(others)) {
                permutations.add(Stream.concat(Stream.of(first), permutation.stream()).toList());
            }
        }
        return permutations;
    }

    static String describe(RepositoryInformation repositoryInformation, Method method) {
        String parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return repositoryInformation.getRepositoryInterface().getSimpleName()
                + "." + method.getName() + "(" + parameterTypes + ")";
    }

    private static boolean returnsEntity(RepositoryInformation repositoryInformation, Method method) {
        // Against the repository interface that binds the type variables of a generic base interface
        ResolvableType returnType = ResolvableType.forMethodReturnType(method, repositoryInformation.getRepositoryInterface());
        if (method.getReturnType() == Optional.class) {
            returnType = returnType.getGeneric(0);
        }
        return returnType.isAssignableFrom(repositoryInformation.getDomainType());
    }

    private static <T> NaturalIdQueryMethod<T> of(
            EntityManager entityManager,
            Class<T> entityType,
            Method method,
            List<String> attributeNames
    ) {
        return new NaturalIdQueryMethod<>(
                method,
                entityType,
                attributeNames,
                lockMode(method),
                loadGraph(entityManager, entityType, method)
        );
    }

    private static @Nullable LockMode lockMode(Method method) {
        Lock lock = AnnotatedElementUtils.findMergedAnnotation(method, Lock.class);
        return lock == null ? null : LockMode.fromJpaLockMode(lock.value());
    }

    /**
     * The graph of the method's {@code @EntityGraph}, built as Spring Data builds it.
     */
    private static <T> @Nullable LoadGraph<T> loadGraph(
            EntityManager entityManager,
            Class<T> entityType,
            Method method
    ) {
        EntityGraph annotation = AnnotatedElementUtils.findMergedAnnotation(method, EntityGraph.class);
        if (annotation == null) {
            return null;
        }

        // Spring Data's fallback name
        String nameFallback = entityType.getSimpleName() + "." + method.getName();
        JpaEntityGraph entityGraph = new JpaEntityGraph(annotation, nameFallback);
        Map<String, Object> hint = new HashMap<>();
        Jpa21Utils.getFetchGraphHint(entityManager, entityGraph, entityType).forEach(hint::put);
        String hintName = entityGraph.getType().getKey();
        @SuppressWarnings("unchecked")
        RootGraph<T> graph = (RootGraph<T>) hint.get(hintName);
        return new LoadGraph<>(graph, GraphSemantic.fromHintName(hintName));
    }

    record LoadGraph<T>(RootGraph<T> graph, GraphSemantic semantic) {
    }
}
