package com.pocketcombats.persistence;

import org.hibernate.cfg.JdbcSettings;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Import;

import java.util.Map;

@SpringBootApplication
@Import(NaturalIdQueryMethodBeanPostProcessor.class)
class TestApplication implements HibernatePropertiesCustomizer {

    static final RecordingStatementInspector statementInspector = new RecordingStatementInspector();

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(JdbcSettings.STATEMENT_INSPECTOR, statementInspector);
    }
}
