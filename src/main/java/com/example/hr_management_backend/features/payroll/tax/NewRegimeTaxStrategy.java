package com.example.hr_management_backend.features.payroll.tax;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class NewRegimeTaxStrategy implements TaxCalculationStrategy {

    private static final BigDecimal STANDARD_DEDUCTION = BigDecimal.valueOf(75000);
    private static final BigDecimal REBATE_LIMIT = BigDecimal.valueOf(700000);
    private static final BigDecimal CESS_RATE = BigDecimal.valueOf(0.04);

    @Override
    public TaxRegime getRegime() {
        return TaxRegime.NEW;
    }

    @Override
    public BigDecimal calculateAnnualTax(BigDecimal annualGrossTaxable, TaxDeclarationDto declaration) {
        if (annualGrossTaxable == null || annualGrossTaxable.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Net taxable income after Standard Deduction
        BigDecimal taxableIncome = annualGrossTaxable.subtract(STANDARD_DEDUCTION);
        if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Section 87A Rebate: if taxable income <= 7,00,000 -> 0 tax
        if (taxableIncome.compareTo(REBATE_LIMIT) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal tax = BigDecimal.ZERO;

        // Slab 1: 0 - 3,00,000 @ 0%
        // Slab 2: 3,00,001 - 7,00,000 (4,00,000 @ 5% = 20,000)
        if (taxableIncome.compareTo(BigDecimal.valueOf(300000)) > 0) {
            BigDecimal chunk = taxableIncome.min(BigDecimal.valueOf(700000)).subtract(BigDecimal.valueOf(300000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.05)));
        }

        // Slab 3: 7,00,001 - 10,00,000 (3,00,000 @ 10% = 30,000)
        if (taxableIncome.compareTo(BigDecimal.valueOf(700000)) > 0) {
            BigDecimal chunk = taxableIncome.min(BigDecimal.valueOf(1000000)).subtract(BigDecimal.valueOf(700000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.10)));
        }

        // Slab 4: 10,00,001 - 12,00,000 (2,00,000 @ 15% = 30,000)
        if (taxableIncome.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            BigDecimal chunk = taxableIncome.min(BigDecimal.valueOf(1200000)).subtract(BigDecimal.valueOf(1000000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.15)));
        }

        // Slab 5: 12,00,001 - 15,00,000 (3,00,000 @ 20% = 60,000)
        if (taxableIncome.compareTo(BigDecimal.valueOf(1200000)) > 0) {
            BigDecimal chunk = taxableIncome.min(BigDecimal.valueOf(1500000)).subtract(BigDecimal.valueOf(1200000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.20)));
        }

        // Slab 6: Above 15,00,000 @ 30%
        if (taxableIncome.compareTo(BigDecimal.valueOf(1500000)) > 0) {
            BigDecimal chunk = taxableIncome.subtract(BigDecimal.valueOf(1500000));
            tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.30)));
        }

        // Add 4% Health & Education Cess
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
