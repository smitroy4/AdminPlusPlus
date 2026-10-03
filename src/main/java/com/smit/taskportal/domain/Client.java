package com.smit.taskportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An external customer the portal delivers work for.
 *
 * <p>The record is deliberately split in three visibility tiers, mirroring what
 * the DTOs hand out (see {@code ClientDto}):
 *
 * <ul>
 *   <li><b>Identity</b> — {@code name}, {@code status}, {@code createdAt}. Public
 *       to every authenticated role: the name is stamped on tasks.</li>
 *   <li><b>Commercial</b> — contact block, location, business profile and billing
 *       summary. Managers/admins see all of it; a CLIENT account sees it for its
 *       own record.</li>
 *   <li><b>Internal</b> — {@code notes} is account-manager commentary and stays
 *       with MANAGER/ADMIN only.</li>
 * </ul>
 *
 * <p><b>No payment credentials are modelled at all.</b> The billing block holds
 * commercial facts only (method name, cycle, amounts, references); no card
 * numbers, CVVs, bank details, tokens or secrets are stored, so there is nothing
 * sensitive to leak.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 128)
    private String name;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Account standing: {@code ACTIVE}, {@code PROSPECT}, {@code ON_HOLD}, {@code CLOSED}. */
    @Column(name = "status", length = 32)
    private String status;

    // ------------------------------------------------------------- location

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 128)
    private String city;

    /** State, province or region — whatever the customer's country uses. */
    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "country", length = 128)
    private String country;

    @Column(name = "postal_code", length = 32)
    private String postalCode;

    // ------------------------------------------------------------- business

    /** Legal trading name, when it differs from the portal record name. */
    @Column(name = "organization_name", length = 255)
    private String organizationName;

    @Column(name = "industry", length = 128)
    private String industry;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "company_size", length = 64)
    private String companySize;

    // -------------------------------------------------------------- billing
    /* Commercial billing facts only. Payment instruments themselves are never
       stored: no card numbers, expiry dates, bank coordinates or API tokens. */

    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "billing_status", length = 32)
    private String billingStatus;

    /** Method label only, e.g. {@code BANK_TRANSFER}, {@code CARD}, {@code INVOICE}. */
    @Column(name = "payment_method", length = 64)
    private String paymentMethod;

    /** {@code MONTHLY}, {@code QUARTERLY}, {@code ANNUAL}, … */
    @Column(name = "billing_cycle", length = 32)
    private String billingCycle;

    @Column(name = "last_payment_at")
    private Instant lastPaymentAt;

    @Column(name = "next_payment_due_at")
    private Instant nextPaymentDueAt;

    @Column(name = "outstanding_amount", precision = 14, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(name = "total_paid", precision = 14, scale = 2)
    private BigDecimal totalPaid;

    /** Human-readable reference of the most recent transaction (e.g. an invoice id). */
    @Column(name = "last_payment_reference", length = 64)
    private String lastPaymentReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
