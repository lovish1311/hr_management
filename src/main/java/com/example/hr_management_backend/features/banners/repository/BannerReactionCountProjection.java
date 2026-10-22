package com.example.hr_management_backend.features.banners.repository;

import com.example.hr_management_backend.features.banners.model.BannerReactionType;

public interface BannerReactionCountProjection {
    Long getBannerId();
    BannerReactionType getReactionType();
    Long getCount();
}
