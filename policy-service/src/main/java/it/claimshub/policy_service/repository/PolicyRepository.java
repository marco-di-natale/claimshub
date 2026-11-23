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

    public PageResult<Policy> search(PolicySearchCriteria c, int page, int size,
                                     PolicySortField sortBy, boolean desc) {
        var where = new WhereClause()
                .and("policy_type = :type", "type", nameOf(c.type()))
                .and("status = :status", "status", nameOf(c.status()))
                .and("premium >= :minPremium", "minPremium", c.minPremium())
                .and("premium <= :maxPremium", "maxPremium", c.maxPremium())
                .and("UPPER(holder_name) LIKE :holder", "holder", likePattern(c.holderName()))
                .and("start_date >= :startFrom", "startFrom", c.startFrom())
                .and("start_date <= :startTo", "startTo", c.startTo());

        // la colonna di ordinamento NON arriva mai dall'utente: whitelist contro SQL injection
        String orderCol = sortBy.column();

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM policy" + where.sql(),
                where.params(), Long.class);

        var params = where.params()
                .addValue("offset", (long) page * size)
                .addValue("size", size);
        List<Policy> content = jdbc.query(
                "SELECT * FROM policy" + where.sql()
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

    // aggiorna solo se lo stato è ancora quello che il service aveva letto
    public boolean transition(long id, PolicyStatus expected, PolicyStatus newStatus, LocalDate newEndDate) {
        int rows = jdbc.update("""
        UPDATE policy SET status = :newStatus, end_date = :endDate
        WHERE id = :id AND status = :expected
        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("expected", expected.name())
                        .addValue("newStatus", newStatus.name())
                        .addValue("endDate", newEndDate));
        return rows == 1;
    }

    private static String nameOf(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String likePattern(String text) {
        return (text == null || text.isBlank()) ? null : "%" + text.toUpperCase() + "%";
    }
}