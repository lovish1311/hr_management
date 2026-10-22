package com.example.hr_management_backend.features.banners.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Authoritative response payload returned when an employee toggles an emoji reaction.
 * Eliminates client-side state divergence and race-condition sync defects.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerReactionSummaryDto {

    private Long bannerId;
    private Map<String, Long> reactionCounts;
    private Set<String> userReactions;
    private long totalReactions;
}
