package com.pocketcombats.persistence;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class CountingStatementInspector implements StatementInspector {

    private int count;

    @Override
    public String inspect(String sql) {
        count++;
        return sql;
    }

    public void reset() {
        count = 0;
    }

    public int getCount() {
        return count;
    }
}
