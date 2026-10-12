package com.pocketcombats.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SimpleNaturalIdLoadAccess;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

import java.util.Optional;

final class NaturalIdQueryMethodInterceptor implements MethodInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(NaturalIdQueryMethodInterceptor.class);

    private final EntityManagerFactory entityManagerFactory;
    private final EntityPersister entityDescriptor;
    private final NaturalIdQueryMethod<?> queryMethod;

    NaturalIdQueryMethodInterceptor(
            EntityManagerFactory entityManagerFactory,
            EntityPersister entityDescriptor,
            NaturalIdQueryMethod<?> queryMethod
    ) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityDescriptor = entityDescriptor;
        this.queryMethod = queryMethod;
    }

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        Object id = invocation.getArguments()[0];
        if (id == null) {
            // Left to Spring Data's "is null" query
            return invocation.proceed();
        }

        EntityManager shared = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        // No session to share: a throwaway one, as Spring's shared EntityManager uses for a query outside a transaction
        EntityManager entityManager = shared != null ? shared : entityManagerFactory.createEntityManager();
        try {
            SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
            if (!isFiltered(session)) {
                return load(session, id);
            }
        } finally {
            if (shared == null) {
                entityManager.close();
            }
        }
        LOG.trace(
                "{} with natural id {} is left to Spring Data: an enabled filter restricts the entity",
                entityDescriptor.getEntityName(),
                id
        );
        return invocation.proceed();
    }

    /**
     * Whether an enabled filter restricts the entity. A query applies it; a natural id lookup does not.
     */
    private boolean isFiltered(SessionImplementor session) {
        // Every filter counts, not only those that apply to a load by key
        return entityDescriptor.isAffectedByEnabledFilters(session.getLoadQueryInfluencers(), false);
    }

    /**
     * A miss is null or an empty Optional, as Spring Data answers its own query methods.
     * Its nullability validation, which wraps this interceptor, turns the null of a non-null return into an exception.
     */
    private @Nullable Object load(Session session, Object id) {
        LOG.trace("Loading {} with natural id {}", entityDescriptor.getEntityName(), id);
        Object entity = load(queryMethod, session, id);
        return queryMethod.returnsOptional() ? Optional.ofNullable(entity) : entity;
    }

    private static <T> @Nullable T load(NaturalIdQueryMethod<T> queryMethod, Session session, Object id) {
        SimpleNaturalIdLoadAccess<T> access = session.bySimpleNaturalId(queryMethod.entityType());
        LockMode lockMode = queryMethod.lockMode();
        if (lockMode != null) {
            access = access.with(lockMode);
        }
        NaturalIdQueryMethod.LoadGraph<T> loadGraph = queryMethod.loadGraph();
        if (loadGraph != null) {
            access = access.with(loadGraph.graph(), loadGraph.semantic());
        }
        return access.load(id);
    }
}
