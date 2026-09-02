package dev.duynguyen.jobtracker.application;

/**
 * Advertised or discussed compensation (SCHEMA.md §4.2). Embedded, optional as a whole.
 *
 * <p>Annual base in whole currency units — no cents, no equity modelling. If a package is
 * more complicated than a range, write it in the application's {@code notes} instead;
 * this field exists to make "what did I apply to at what level" sortable, not to be a
 * complete offer model.
 */
public class Compensation {

    private Integer min;
    private Integer max;

    /** ISO 4217, e.g. {@code USD}. Defaulted to USD by the service when min/max are set. */
    private String currency;

    public Integer getMin() { return min; }
    public void setMin(Integer min) { this.min = min; }

    public Integer getMax() { return max; }
    public void setMax(Integer max) { this.max = max; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
