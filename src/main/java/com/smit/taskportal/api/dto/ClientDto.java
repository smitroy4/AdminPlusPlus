package com.smit.taskportal.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smit.taskportal.domain.Client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Client record, shaped by the role of the caller. The server never serialises
 * a field the caller is not entitled to, so a restricted response is a genuinely
 * restricted payload rather than a payload the UI is trusted to hide.
 *
 * <p>Three projections, see {@link Client}:
 *
 * <ul>
 *   <li>{@link #detailed(Client)} — everything, for MANAGER / ADMIN.</li>
 *   <li>{@link #ownCustomer(Client)} — everything except the internal
 *       {@code notes}, for a CLIENT account reading its own record.</li>
 *   <li>{@link #summary(Client)} — identity only, for everybody else.</li>
 * </ul>
 *
 * <p>{@code NON_NULL} is what makes the narrower projections real: a field the
 * caller may not read is left out of the JSON instead of being sent as a null
 * placeholder, so {@code paymentStatus} being absent is the signal that this is
 * a summary rather than a detailed record with nothing to report.
 *
 * <p>No payment credentials exist on this record: the billing block carries
 * commercial facts (method name, cycle, amounts, references) only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientDto(Long id,
                        String name,
                        String status,
                        String contactName,
                        String email,
                        String phone,
                        String notes,
                        String address,
                        String city,
                        String state,
                        String country,
                        String postalCode,
                        String organizationName,
                        String industry,
                        String website,
                        String companySize,
                        String paymentStatus,
                        String billingStatus,
                        String paymentMethod,
                        String billingCycle,
                        Instant lastPaymentAt,
                        Instant nextPaymentDueAt,
                        BigDecimal outstandingAmount,
                        BigDecimal totalPaid,
                        String lastPaymentReference,
                        Instant createdAt) {

    /** Full record — managers and admins. */
    public static ClientDto detailed(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientDto(client.getId(), client.getName(), client.getStatus(),
                client.getContactName(), client.getEmail(), client.getPhone(), client.getNotes(),
                client.getAddress(), client.getCity(), client.getState(), client.getCountry(),
                client.getPostalCode(), client.getOrganizationName(), client.getIndustry(),
                client.getWebsite(), client.getCompanySize(), client.getPaymentStatus(),
                client.getBillingStatus(), client.getPaymentMethod(), client.getBillingCycle(),
                client.getLastPaymentAt(), client.getNextPaymentDueAt(),
                client.getOutstandingAmount(), client.getTotalPaid(),
                client.getLastPaymentReference(), client.getCreatedAt());
    }

    /**
     * The customer's own view of itself: everything commercial, minus the
     * internal {@code notes}. Used for the customer that a CLIENT account is
     * linked to.
     */
    public static ClientDto ownCustomer(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientDto(client.getId(), client.getName(), client.getStatus(),
                client.getContactName(), client.getEmail(), client.getPhone(), null,
                client.getAddress(), client.getCity(), client.getState(), client.getCountry(),
                client.getPostalCode(), client.getOrganizationName(), client.getIndustry(),
                client.getWebsite(), client.getCompanySize(), client.getPaymentStatus(),
                client.getBillingStatus(), client.getPaymentMethod(), client.getBillingCycle(),
                client.getLastPaymentAt(), client.getNextPaymentDueAt(),
                client.getOutstandingAmount(), client.getTotalPaid(),
                client.getLastPaymentReference(), client.getCreatedAt());
    }

    /** Identity only — the name is stamped on tasks and used by the All Tasks filter. */
    public static ClientDto summary(Client client) {
        if (client == null) {
            return null;
        }
        return new ClientDto(client.getId(), client.getName(), client.getStatus(),
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null, null,
                client.getCreatedAt());
    }
}
