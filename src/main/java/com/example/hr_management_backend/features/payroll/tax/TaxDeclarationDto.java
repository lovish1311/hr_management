package com.example.hr_management_backend.features.payroll.tax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxDeclarationDto {
    @Builder.Default
    private TaxRegime regime = TaxRegime.NEW;

    @Builder.Default
    private BigDecimal section80C = BigDecimal.ZERO; // Max 1,50,000 for OLD regime

    @Builder.Default
    private BigDecimal section80D = BigDecimal.ZERO; // Medical insurance (Max 25k/50k)

    @Builder.Default
    private BigDecimal hraExemption = BigDecimal.ZERO; // Rent paid exemption for OLD regime

    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    private BigDecimal monthlyTdsOverride; // Optional manual override if specified
}
