package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.dto.DailyTaskCount;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// SQL que no encaja en entidades: un informe agregado y una función que ya existe en la BD
@Repository
public class TaskJdbcRepository {

    private final JdbcClient jdbcClient;

    public TaskJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DailyTaskCount> countPerDay() {
        return jdbcClient.sql("""
                        SELECT CAST(FH_ALTA AS DATE)                           AS dia,
                               COUNT(*)                                        AS creadas,
                               COUNT(*) FILTER (WHERE FL_COMPLETADA = 'S')     AS completadas
                          FROM TB_TAREA
                         GROUP BY dia
                         ORDER BY dia DESC
                        """)
                .query(DailyTaskCount.class)
                .list();
    }

    public int purgeCompleted(int days) {
        return jdbcClient.sql("SELECT FN_PURGAR_COMPLETADAS(:dias)")
                .param("dias", days)
                .query(Integer.class)
                .single();
    }
}
