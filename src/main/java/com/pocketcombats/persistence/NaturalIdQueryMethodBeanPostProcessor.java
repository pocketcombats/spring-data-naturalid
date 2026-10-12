package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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

    private static final Logger LOG = LoggerFactory.getLogger(NaturalIdQueryMethodBeanPostProcessor.class);

    private final ConfigurableListableBeanFactory beanFactory;

    public NaturalIdQueryMethodBeanPostProcessor(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof JpaRepositoryFactoryBean<?, ?, ?> repositoryFactoryBean) {
            repositoryFactoryBean.addRepositoryFactoryCustomizer(
                    repositoryFactory -> customize(repositoryFactory, beanName)
            );
        }
        return bean;
    }

    private void customize(RepositoryFactorySupport repositoryFactory, String factoryBeanName) {
        EntityManager entityManager = entityManager(factoryBeanName);
        if (entityManager == null) {
            LOG.debug(
                    "{} is left to Spring Data: its factory bean has no bean reference to an EntityManager",
                    factoryBeanName
            );
            return;
        }
        var postProcessor = new NaturalIdRepositoryProxyPostProcessor(entityManager);
        repositoryFactory.addRepositoryProxyPostProcessor(postProcessor);
        repositoryFactory.addQueryCreationListener(postProcessor);
    }

    /**
     * The repository's shared {@code EntityManager}, from the {@code entityManager} bean reference that Spring Data
     * repository configuration gives the factory bean.
     *
     * @return {@code null} for a factory bean declared some other way.
     */
    private @Nullable EntityManager entityManager(String factoryBeanName) {
        if (!beanFactory.containsBeanDefinition(factoryBeanName)) {
            return null;
        }
        Object entityManager = beanFactory.getMergedBeanDefinition(factoryBeanName).getPropertyValues().get("entityManager");
        if (!(entityManager instanceof RuntimeBeanReference reference)) {
            return null;
        }
        // By type when no bean has the name
        String name = reference.getBeanName();
        return beanFactory.containsBean(name)
                ? beanFactory.getBean(name, EntityManager.class)
                : beanFactory.getBean(EntityManager.class);
    }
}
