package com.example.hr_management_backend.features.scribbil.repository;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrawGuessWordRepository extends JpaRepository<DrawGuessWord, Long> {

    List<DrawGuessWord> findByCategory(String category);

    @Query("SELECT DISTINCT w.category FROM DrawGuessWord w")
    List<String> findDistinctCategories();

    @Query(value = "SELECT * FROM draw_guess_words WHERE (:category IS NULL OR category = :category) ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<DrawGuessWord> findRandomWordsByCategory(@Param("category") String category, @Param("limit") int limit);
}
