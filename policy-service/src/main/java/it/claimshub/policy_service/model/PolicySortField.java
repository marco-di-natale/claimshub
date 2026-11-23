package it.claimshub.policy_service.model;

public enum PolicySortField {
    ID("id"),
    PREMIUM("premium"),
    START_DATE("start_date"),
    HOLDER_NAME("holder_name");

    private final String column;

    PolicySortField(String column) { this.column = column; }

    public String column() { return column; }
}