package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.stat.StatCategory;

import java.util.Objects;

public record CategoryDevelopment(
        StatCategory category,
        long storedInvestment,
        long effectiveInvestment,
        long currentCapacity,
        double development
) {

    public CategoryDevelopment {

        Objects.requireNonNull(
                category,
                "Category cannot be null"
        );


        if (storedInvestment < 0L) {
            throw new IllegalArgumentException(
                    "Stored investment cannot be negative"
            );
        }


        if (effectiveInvestment < 0L) {
            throw new IllegalArgumentException(
                    "Effective investment cannot be negative"
            );
        }


        if (currentCapacity < 0L) {
            throw new IllegalArgumentException(
                    "Category capacity cannot be negative"
            );
        }


        if (!Double.isFinite(development)
                || development < 0.0
                || development > 1.0) {

            throw new IllegalArgumentException(
                    "Category development must be between 0.0 and 1.0"
            );
        }
    }
}