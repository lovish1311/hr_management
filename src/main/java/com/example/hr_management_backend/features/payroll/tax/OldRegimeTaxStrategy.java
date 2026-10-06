package com.example.hr_management_backend.features.payroll.tax;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class OldRegimeTaxStrategy implements TaxCalculationStrategy {

    private static final BigDecimal STANDARD_DEDUCTION = BigDecimal.valueOf(50000);
    private static final BigDecimal MAX_80C = BigDecimal.valueOf(150000);
    private static final BigDecimal MAX_80D = BigDecimal.valueOf(50000);
    private static final BigDecimal REBATE_LIMIT = BigDecimal.valueOf(500000);
    private static final BigDecimal CESS_RATE = BigDecimal.valueOf(0.04);

    @Override
    public TaxRegime getRegime() {
        return TaxRegime.OLD;
    }

    @Override
    public BigDecimal calculateAnnualTax(BigDecimal annualGrossTaxable, TaxDeclarationDto declaration) {
        if (annualGrossTaxable == null || annualGrossTaxable.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal deductions = STANDARD_DEDUCTION;
        if (declaration != null) {
            if (declaration.getSection80C() != null) {
                deductions = deductions.add(declaration.getSection80C().min(MAX_80C));
            }
            if (declaration.getSection80D() != null) {
                deductions = deductions.add(declaration.getSection80D().min(MAX_80D));
            }
            if (declaration.getHraExemption() != null) {
                deductions = deductions.add(declaration.getHraExemption());
            }
            if (declaration.getOtherDeductions() != null) {
                deductions = deductions.add(declaration.getOtherDeductions());
            }
        }

        BigDecimal taxableIncome = annualGrossTaxable.subtract(deductions);
        if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Section 87A rebate for Old Regime (up to 5,00,000 taxable)
        if (taxableIncome.compareTo(REBATE_LIMIT) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal tax = BigDecimal.ZERO;

        // Slab 1: 0 - 2,50,000 @ 0%
        // Slab 2: 2,50,001 - 5,00,000 @ 5% (2,50,000 @ 5% = 12,500)
        if (taxableIncome.compareTo(BigDecimal.valueOf(250000)) > 0) {
            BigDecimal chunk = taxableIncome.min(BigDecimal.valueOf(500000)).subtract(BigDecimal.valueOf(250000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.05)));
        }

        // Slab 3: 5,00,001 - 10,00,000 @ 20% (5,00,000 @ 20% = 1,00,000)
        if (taxableIncome.compareTo(BigDecimal.valueOf(500000)) > 0) {
            BigDecimal chunk = taxableIncome.min(BigDecimal.valueOf(1000000)).subtract(BigDecimal.valueOf(500000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.20)));
        }

        // Slab 4: Above 10,00,000 @ 30%
        if (taxableIncome.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            BigDecimal chunk = taxableIncome.subtract(BigDecimal.valueOf(1000000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.30)));
        }

        BigDecimal cess = tax.multiply(CESS_RATE);
        return tax.add(cess).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateMonthlyTds(BigDecimal monthlyTaxableGross, TaxDeclarationDto declaration) {
        if (declaration != null && declaration.getMonthlyTdsOverride() != null && declaration.getMonthlyTdsOverride().compareTo(BigDecimal.ZERO) >= 0) {
            return declaration.getMonthlyTdsOverride().setScale(2, RoundingMode.HALF_UP);
        }

        if (monthlyTaxableGross == null || monthlyTaxableGross.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal annualized = monthlyTaxableGross.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTax = calculateAnnualTax(annualized, declaration);
        return annualTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
