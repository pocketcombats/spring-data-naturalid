package com.pocketcombats.persistence;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

public class RecordingStatementInspector implements StatementInspector {

    private final List<String> statements = new ArrayList<>();

    @Override
    public String inspect(String sql) {
        statements.add(sql);
        return sql;
    }

    public void reset() {
        statements.clear();
    }

    public List<String> getStatements() {
        return List.copyOf(statements);
    }
}
