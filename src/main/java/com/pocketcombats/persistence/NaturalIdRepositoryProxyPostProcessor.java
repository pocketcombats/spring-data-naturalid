package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.data.jpa.repository.query.AbstractJpaQuery;
import org.springframework.data.jpa.repository.query.PartTreeJpaQuery;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.QueryCreationListener;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advises the natural id query methods of one repository, once Spring Data has reported that it derives their
 * queries from the method names.
 */
final class NaturalIdRepositoryProxyPostProcessor
        implements RepositoryProxyPostProcessor, QueryCreationListener<AbstractJpaQuery> {

    private static final Logger LOG = LoggerFactory.getLogger(NaturalIdRepositoryProxyPostProcessor.class);

    /**
     * A query method's advisor, pending Spring Data's report of the query it creates for the method.
     */
    private record Candidate(String name, ProxyFactory factory, Advisor advisor) {
    }

    private final EntityManager entityManager;
    // Keyed by Method.toString(), which is how Spring Data query identifies its method
    private final Map<String, Candidate> candidates = new HashMap<>();

    NaturalIdRepositoryProxyPostProcessor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
        if (!repositoryInformation.hasQueryMethods()) {
            return;
        }
        Class<?> domainType = repositoryInformation.getDomainType();
        EntityManagerFactory entityManagerFactory = entityManager.getEntityManagerFactory();
        SessionFactoryImplementor sessionFactory;
        try {
            sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        } catch (PersistenceException e) {
            LOG.debug(
                    "{} is left to Spring Data: its persistence unit is not Hibernate's",
                    repositoryInformation.getRepositoryInterface().getSimpleName()
            );
            return;
        }
        EntityPersister entityDescriptor = sessionFactory.getMappingMetamodel().findEntityDescriptor(domainType);
        String attributeName = entityDescriptor == null ? null : simpleNaturalIdAttribute(entityDescriptor);
        if (attributeName == null) {
            return;
        }

        for (NaturalIdQueryMethod<?> queryMethod : NaturalIdQueryMethod.resolve(entityManager, repositoryInformation, attributeName)) {
            Method method = queryMethod.method();
            Advisor advisor = new DefaultPointcutAdvisor(
                    new StaticMethodMatcherPointcut() {
                        @Override
                        public boolean matches(Method invoked, Class<?> targetClass) {
                            return invoked.equals(method);
                        }
                    },
                    new NaturalIdQueryMethodInterceptor(entityManagerFactory, entityDescriptor, queryMethod)
            );
            candidates.put(
                    method.toString(),
                    new Candidate(NaturalIdQueryMethod.describe(repositoryInformation, method), factory, advisor)
            );
        }
    }

    /**
     * Spring Data creates the queries after {@link #postProcess} and before the proxy exists, so the advisor can
     * still be added here.
     */
    @Override
    public void onCreation(AbstractJpaQuery query) {
        Candidate candidate = candidates.remove(query.getQueryMethod().toString());
        if (candidate == null) {
            return;
        }
        if (query instanceof PartTreeJpaQuery) {
            candidate.factory().addAdvisor(candidate.advisor());
            LOG.debug("{} is registered for a natural id lookup", candidate.name());
        } else {
            LOG.debug("{} is left to Spring Data: its query is not derived from the method name", candidate.name());
        }
    }

    /**
     * The name of the entity's single natural id attribute.
     *
     * @return {@code null} when it has none or a composite one.
     */
    private static @Nullable String simpleNaturalIdAttribute(EntityPersister entityDescriptor) {
        if (!entityDescriptor.hasNaturalIdentifier()) {
            return null;
        }
        List<SingularAttributeMapping> attributes = entityDescriptor.getNaturalIdMapping().getNaturalIdAttributes();
        if (attributes.size() != 1) {
            LOG.warn("{} declares a composite natural id, which is not supported", entityDescriptor.getEntityName());
            return null;
        }
        return attributes.get(0).getAttributeName();
    }
}
