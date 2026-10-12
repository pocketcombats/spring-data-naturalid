package com.pocketcombats.persistence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.data.jpa.repository.JpaContext;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

/**
 * A Spring {@link BeanPostProcessor} that enhances Spring Data JPA repositories with Hibernate Natural ID support.
 * <p>
 * Repository query methods that look an entity up by its natural identifier are routed to
 * {@link org.hibernate.Session#bySimpleNaturalId}, so repeated lookups are answered from the session cache
 * (or the L2 natural-id cache, if enabled) instead of running a query each time.
 * <p>
 * Supports entities with a single natural ID attribute and query methods named {@code findByX} or
 * {@code getByX}, where X is the natural ID attribute name, that take the natural id and return the entity
 * or an {@link java.util.Optional}.
 * A method with its own query or with query hints is left to Spring Data; a {@code @Lock} and an {@code @EntityGraph}
 * are passed on to the lookup.
 * A call with a {@code null} argument, or for an entity that an enabled Hibernate filter restricts, is left to
 * Spring Data.
 *
 * @see org.hibernate.annotations.NaturalId
 * @see org.hibernate.annotations.NaturalIdCache
 */
@AutoConfiguration
public class NaturalIdQueryMethodBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<JpaContext> jpaContextProvider;

    public NaturalIdQueryMethodBeanPostProcessor(ObjectProvider<JpaContext> jpaContextProvider) {
        this.jpaContextProvider = jpaContextProvider;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof JpaRepositoryFactoryBean<?, ?, ?> repositoryFactoryBean) {
            repositoryFactoryBean.addRepositoryFactoryCustomizer(this::customize);
        }
        return bean;
    }

    private void customize(RepositoryFactorySupport repositoryFactory) {
        var postProcessor = new NaturalIdRepositoryProxyPostProcessor(jpaContextProvider);
        repositoryFactory.addRepositoryProxyPostProcessor(postProcessor);
        repositoryFactory.addQueryCreationListener(postProcessor);
    }
}
