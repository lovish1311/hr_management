package com.example.hr_management_backend.features.payroll.tax;

import java.math.BigDecimal;

public interface TaxCalculationStrategy {
    TaxRegime getRegime();

    /**
     * Calculates annual income tax payable after standard deductions, Chapter VI-A deductions,
     * Section 87A rebate, and 4% Health & Education cess.
     */
    BigDecimal calculateAnnualTax(BigDecimal annualGrossTaxable, TaxDeclarationDto declaration);

    /**
     * Calculates monthly TDS (Tax Deducted at Source) to be deducted in this payroll cycle.
     */
    BigDecimal calculateMonthlyTds(BigDecimal monthlyTaxableGross, TaxDeclarationDto declaration);
}
