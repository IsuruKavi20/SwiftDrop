package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * After V10 rebuilt the customer table (copying the rows with their ids), new customers must get ids after the
 * highest copied one, so ids are never reused. "RESTART WITH" needs a fixed number, which is why this is Java:
 * it reads the current maximum first. Works on PostgreSQL and on the tests' H2 database.
 */
public class V11__customer_ids_continue_after_copy extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            long next;
            try (ResultSet max = statement.executeQuery("SELECT COALESCE(MAX(customer_id), 0) + 1 FROM customer")) {
                max.next();
                next = max.getLong(1);
            }
            statement.execute("ALTER TABLE customer ALTER COLUMN customer_id RESTART WITH " + next);
        }
    }
}
