package it.claimshub.policy_service.repository;

import it.claimshub.policy_service.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.RoundingMode;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<PremiumByType> premiumsByType(PolicyStatus status, int minPolicies) {
        // il pezzo di SQL che cambia è una costante nostra, mai testo dell'utente
        String where = status == null ? "" : "WHERE status = :status";
        String sql = """
            SELECT policy_type,
                   COUNT(*)     AS policies,
                   SUM(premium) AS total_premium,
                   AVG(premium) AS average_premium,
                   MIN(premium) AS min_premium,
                   MAX(premium) AS max_premium
            FROM policy
            """ + where + """

            GROUP BY policy_type
            HAVING COUNT(*) >= :minPolicies
            ORDER BY total_premium DESC
            """;
        var params = new MapSqlParameterSource("minPolicies", minPolicies);
        if (status != null) {
            params.addValue("status", status.name());
        }
        return jdbc.query(sql, params, (rs, i) -> new PremiumByType(
                PolicyType.valueOf(rs.getString("policy_type")),
                rs.getLong("policies"),
                rs.getBigDecimal("total_premium"),
                rs.getBigDecimal("average_premium").setScale(2, RoundingMode.HALF_UP),
                rs.getBigDecimal("min_premium"),
                rs.getBigDecimal("max_premium")));
    }

    public List<PremiumRank> premiumRanking() {
        return jdbc.query("""
            SELECT policy_number, policy_type, premium,
                   RANK() OVER (PARTITION BY policy_type ORDER BY premium DESC) AS position,
                   ROUND(premium * 100 / SUM(premium) OVER (PARTITION BY policy_type), 2) AS share_pct
            FROM policy
            WHERE status <> 'CANCELLED'
            ORDER BY policy_type, position, policy_number
            """, (rs, i) -> new PremiumRank(
                rs.getString("policy_number"),
                PolicyType.valueOf(rs.getString("policy_type")),
                rs.getBigDecimal("premium"),
                rs.getInt("position"),
                rs.getBigDecimal("share_pct")));
    }

    public List<MonthlyNewPolicies> monthlyNewPolicies() {
        return jdbc.query("""
            SELECT EXTRACT(YEAR FROM start_date)  AS yr,
                   EXTRACT(MONTH FROM start_date) AS mon,
                   COUNT(*)     AS policies,
                   SUM(premium) AS premium,
                   SUM(SUM(premium)) OVER (
                       ORDER BY EXTRACT(YEAR FROM start_date), EXTRACT(MONTH FROM start_date)
                   ) AS cumulative
            FROM policy
            GROUP BY EXTRACT(YEAR FROM start_date), EXTRACT(MONTH FROM start_date)
            ORDER BY yr, mon
            """, (rs, i) -> new MonthlyNewPolicies(
                rs.getInt("yr"), rs.getInt("mon"), rs.getLong("policies"),
                rs.getBigDecimal("premium"), rs.getBigDecimal("cumulative")));
    }
}