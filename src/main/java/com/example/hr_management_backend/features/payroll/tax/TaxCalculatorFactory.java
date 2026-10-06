package com.example.hr_management_backend.features.payroll.tax;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TaxCalculatorFactory {

    private final List<TaxCalculationStrategy> strategies;

    public TaxCalculationStrategy getStrategy(TaxRegime regime) {
        TaxRegime target = regime != null ? regime : TaxRegime.NEW;
        return strategies.stream()
                .filter(s -> s.getRegime() == target)
                .findFirst()
                .orElseGet(() -> strategies.stream()
                        .filter(s -> s.getRegime() == TaxRegime.NEW)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No tax calculation strategy registered.")));
    }
}
