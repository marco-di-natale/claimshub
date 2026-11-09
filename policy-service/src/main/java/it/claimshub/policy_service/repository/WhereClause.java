package it.claimshub.policy_service.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class WhereClause {

    private final StringBuilder sql = new StringBuilder(" WHERE 1=1");
    private final MapSqlParameterSource params = new MapSqlParameterSource();

    // la condizione è sempre una stringa scritta nel codice, mai input dell'utente;
    // il valore passa solo come parametro. Se è null, il filtro non si applica.
    WhereClause and(String condition, String paramName, Object value) {
        if (value != null) {
            sql.append(" AND ").append(condition);
            params.addValue(paramName, value);
        }
        return this;
    }

    String sql() { return sql.toString(); }

    MapSqlParameterSource params() { return params; }
}