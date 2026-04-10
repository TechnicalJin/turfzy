package com.turfzy.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByTurfIdOrderByCreatedAtDesc(Long turfId, Pageable pageable);

    Optional<Review> findByUserIdAndTurfId(Long userId, Long turfId);

    boolean existsByUserIdAndTurfId(Long userId, Long turfId);

    /** Recalculate average rating after a new review */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.turf.id = :turfId")
    Double calculateAverageRating(@Param("turfId") Long turfId);

    long countByTurfId(Long turfId);
}