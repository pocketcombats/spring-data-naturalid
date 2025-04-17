package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;
import org.hibernate.SimpleNaturalIdLoadAccess;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.NaturalIdMapping;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactoryCustomizer;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

/**
 * A Spring {@link BeanPostProcessor} that enhances Spring Data JPA repositories with Hibernate Natural ID support.
 * <p>
 * This processor intercepts repository methods that query entities by their natural identifiers and
 * redirects them to use Hibernate's {@code Session.bySimpleNaturalId()} method.
 * <p>
 * Currently only supports entities with a single natural ID attribute and methods named
 * {@code findByX} or {@code getByX} where X is the natural ID attribute name.
 *
 * @see org.hibernate.annotations.NaturalId
 * @see org.hibernate.annotations.NaturalIdCache
 */
@AutoConfiguration
public class NaturalIdQueryMethodBeanPostProcessor implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NaturalIdQueryMethodBeanPostProcessor.class);

    private final RepositoryFactoryCustomizer customizer;

    public NaturalIdQueryMethodBeanPostProcessor(ObjectProvider<JpaContext> jpaContextProvider) {
        this.customizer = new NaturalIdRepositoryFactoryCustomizer(jpaContextProvider);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RepositoryFactoryBeanSupport) {
            ((RepositoryFactoryBeanSupport<?, ?, ?>) bean).addRepositoryFactoryCustomizer(this.customizer);
        }
        return bean;
    }

    private static final class NaturalIdRepositoryFactoryCustomizer
            implements RepositoryFactoryCustomizer, RepositoryProxyPostProcessor {

        private final ObjectProvider<JpaContext> jpaContextProvider;

        NaturalIdRepositoryFactoryCustomizer(ObjectProvider<JpaContext> jpaContextProvider) {
            this.jpaContextProvider = jpaContextProvider;
        }

        @Override
        public void customize(RepositoryFactorySupport repositoryFactory) {
            repositoryFactory.addRepositoryProxyPostProcessor(this);
        }

        @Override
        public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
            if (!repositoryInformation.hasQueryMethods()) {
                return;
            }

            JpaContext jpaContext = jpaContextProvider.getIfAvailable();
            if (jpaContext == null) {
                LOG.error("JpaContext is not available. Natural ID query optimizations cannot be applied.");
                return;
            }

            EntityManager em = jpaContext.getEntityManagerByManagedType(repositoryInformation.getDomainType());
            Session session = em.unwrap(Session.class);
            MappingMetamodel metamodel = (MappingMetamodel) session.getSessionFactory().getMetamodel();
            EntityPersister entityDescriptor = metamodel.findEntityDescriptor(repositoryInformation.getDomainType());
            if (!entityDescriptor.hasNaturalIdentifier()) {
                return;
            }
            NaturalIdMapping naturalIdMapping = entityDescriptor.getNaturalIdMapping();
            if (naturalIdMapping.getNaturalIdAttributes().size() > 1) {
                LOG.warn(
                        "{} declares multiple natural id attributes, which is not supported",
                        entityDescriptor.getEntityName()
                );
                return;
            }
            SingularAttributeMapping naturalIdAttribute = naturalIdMapping.getNaturalIdAttributes().get(0);
            Set<String> candidateMethodNames = getCandidateMethodNames(naturalIdAttribute.getAttributeName());

            boolean hasMatchingMethods = repositoryInformation.getQueryMethods()
                    .stream()
                    .anyMatch(method -> candidateMethodNames.contains(method.getName()));
            if (hasMatchingMethods) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "{} registered for natural id query methods processing",
                            repositoryInformation.getRepositoryInterface().getSimpleName()
                    );
                }
                factory.addAdvice(
                        new NaturalIdQueryMethodInterceptor(
                                session,
                                entityDescriptor.getMappedClass(),
                                naturalIdMapping.isMutable(),
                                candidateMethodNames
                        )
                );
            }
        }

        private static Set<String> getCandidateMethodNames(String attributeName) {
            String capitalizedAttributeName = StringUtils.capitalize(attributeName);
            return Set.of(
                    "findBy" + capitalizedAttributeName,
                    "getBy" + capitalizedAttributeName
            );
        }
    }

    /**
     * Method interceptor that redirects natural ID query methods to use Hibernate's natural ID API.
     */
    private static final class NaturalIdQueryMethodInterceptor implements MethodInterceptor {

        private final Session session;
        private final Class<?> domainType;
        private final boolean synchronizationEnabled;
        private final Set<String> methodNames;

        NaturalIdQueryMethodInterceptor(
                Session session,
                Class<?> domainType,
                boolean synchronizationEnabled,
                Set<String> methodNames
        ) {
            this.session = session;
            this.domainType = domainType;
            this.synchronizationEnabled = synchronizationEnabled;
            this.methodNames = methodNames;
        }

        @Override
        public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
            if (methodNames.contains(invocation.getMethod().getName())) {
                return findByNaturalId(invocation);
            } else {
                return invocation.proceed();
            }
        }

        private @Nullable Object findByNaturalId(MethodInvocation invocation) {
            Object id = invocation.getArguments()[0];

            if (LOG.isTraceEnabled()) {
                LOG.trace("Loading {} with natural id {}", domainType.getSimpleName(), id);
            }

            SimpleNaturalIdLoadAccess<?> loader = session.bySimpleNaturalId(domainType)
                    .setSynchronizationEnabled(synchronizationEnabled);
            if (Optional.class.isAssignableFrom(invocation.getMethod().getReturnType())) {
                return loader.loadOptional(id);
            } else {
                return loader.load(id);
            }
        }
    }
}
