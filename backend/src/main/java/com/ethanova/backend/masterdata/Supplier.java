package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", nullable = false, unique = true, length = 50)
    private String supplierCode;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_type", nullable = false, length = 30)
    private SupplierType supplierType;

    @Column(name = "state_code", nullable = false, length = 10)
    private String stateCode;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "reliability_score", precision = 5, scale = 2)
    private BigDecimal reliabilityScore;

    @Column(nullable = false)
    private Boolean active = true;
}