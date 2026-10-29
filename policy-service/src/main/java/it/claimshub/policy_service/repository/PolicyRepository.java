package it.claimshub.policy_service.repository;

import it.claimshub.policy_service.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PolicyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<Policy> MAPPER = (rs, i) -> new Policy(
            rs.getLong("id"),
            rs.getString("policy_number"),
            rs.getString("holder_name"),
            PolicyType.valueOf(rs.getString("policy_type")),
            PolicyStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("premium"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class));

    public List<Policy> findAll() {
        return jdbc.query("SELECT * FROM policy ORDER BY id", MAPPER);
    }

    public Optional<Policy> findById(long id) {
        return jdbc.query("SELECT * FROM policy WHERE id = :id",
                Map.of("id", id), MAPPER).stream().findFirst();
    }

    public Policy insert(Policy p) {
        Long id = jdbc.queryForObject("SELECT policy_seq.NEXTVAL FROM DUAL", Map.of(), Long.class);
        jdbc.update("""
                        INSERT INTO policy (id, policy_number, holder_name, policy_type, status, premium, start_date, end_date)
                        VALUES (:id, :number, :holder, :type, :status, :premium, :start, :end)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("number", p.policyNumber())
                        .addValue("holder", p.holderName())
                        .addValue("type", p.type().name())
                        .addValue("status", p.status().name())
                        .addValue("premium", p.premium())
                        .addValue("start", p.startDate())
                        .addValue("end", p.endDate()));
        return findById(id).orElseThrow();
    }

    private static final Map<String, String> SORTABLE = Map.of(
            "id", "id", "premium", "premium",
            "startDate", "start_date", "holderName", "holder_name");

    public PageResult<Policy> search(PolicySearchCriteria c, int page, int size,
                                     String sortBy, boolean desc) {
        var params = new MapSqlParameterSource();
        var where = new StringBuilder(" WHERE 1=1");

        if (c.type() != null) {
            where.append(" AND policy_type = :type");
            params.addValue("type", c.type().name());
        }
        if (c.status() != null) {
            where.append(" AND status = :status");
            params.addValue("status", c.status().name());
        }
        if (c.minPremium() != null) {
            where.append(" AND premium >= :minPremium");
            params.addValue("minPremium", c.minPremium());
        }
        if (c.maxPremium() != null) {
            where.append(" AND premium <= :maxPremium");
            params.addValue("maxPremium", c.maxPremium());
        }
        if (c.holderName() != null && !c.holderName().isBlank()) {
            where.append(" AND UPPER(holder_name) LIKE :holder");
            params.addValue("holder", "%" + c.holderName().toUpperCase() + "%");
        }
        if (c.startFrom() != null) {
            where.append(" AND start_date >= :startFrom");
            params.addValue("startFrom", c.startFrom());
        }
        if (c.startTo() != null) {
            where.append(" AND start_date <= :startTo");
            params.addValue("startTo", c.startTo());
        }

        // la colonna di ordinamento NON arriva mai dall'utente: whitelist contro SQL injection
        String orderCol = SORTABLE.getOrDefault(sortBy, "id");

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM policy" + where, params, Long.class);

        params.addValue("offset", (long) page * size);
        params.addValue("size", size);
        List<Policy> content = jdbc.query(
                "SELECT * FROM policy" + where
                        + " ORDER BY " + orderCol + (desc ? " DESC" : " ASC") + ", id"
                        + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                params, MAPPER);

        return new PageResult<>(content, page, size, total == null ? 0 : total);
    }

    public List<PolicyStats> stats() {
        return jdbc.query("""
                SELECT policy_type, status, COUNT(*) AS cnt, SUM(premium) AS total
                FROM policy
                GROUP BY policy_type, status
                ORDER BY policy_type, status
                """, (rs, i) -> new PolicyStats(
                PolicyType.valueOf(rs.getString("policy_type")),
                PolicyStatus.valueOf(rs.getString("status")),
                rs.getLong("cnt"),
                rs.getBigDecimal("total")));
    }
}