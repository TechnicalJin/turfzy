package com.turfzy.turf;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TurfRepository extends JpaRepository<Turf, Long> {

    Page<Turf> findByStatusAndCityContainingIgnoreCase(
            TurfStatus status, String city, Pageable pageable);

    List<Turf> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<Turf> findByStatusOrderByCreatedAtAsc(TurfStatus status);

    /**
     * Search active turfs by city and sport.
     * We use a subquery pattern to avoid MultipleBagFetchException
     * and LazyInitializationException on sportTypes in the listing.
     *
     * The LEFT JOIN on sportTypes is for WHERE filtering only —
     * we do NOT rely on it to initialize the sportTypes collection.
     * sportTypes is loaded via a separate @EntityGraph or removed from SummaryDto.
     */
    @Query(value = """
    SELECT DISTINCT t FROM Turf t
    LEFT JOIN t.sportTypes s
    WHERE t.status = 'ACTIVE'
    AND (:city IS NULL OR LOWER(t.city) LIKE LOWER(CONCAT('%', :city, '%')))
    AND (:sport IS NULL OR s = :sport)
    ORDER BY t.averageRating DESC
    """,
            countQuery = """
    SELECT COUNT(DISTINCT t) FROM Turf t
    LEFT JOIN t.sportTypes s
    WHERE t.status = 'ACTIVE'
    AND (:city IS NULL OR LOWER(t.city) LIKE LOWER(CONCAT('%', :city, '%')))
    AND (:sport IS NULL OR s = :sport)
    """)
    Page<Turf> searchTurfs(
            @Param("city") String city,
            @Param("sport") SportType sport,
            Pageable pageable);

    long countByStatus(TurfStatus status);

    /**
     * Step 1: Fetch turf with owner + sportTypes only.
     * We intentionally exclude images here — fetching two List collections
     * in one JOIN FETCH causes Hibernate's MultipleBagFetchException.
     * Images are loaded separately in Step 2 below.
     */
    @Query("""
        SELECT t FROM Turf t
        LEFT JOIN FETCH t.sportTypes
        LEFT JOIN FETCH t.owner
        WHERE t.id = :id
        """)
    Optional<Turf> findByIdWithSportTypesAndOwner(@Param("id") Long id);

    /**
     * Step 2: Fetch turf with images only (separate query).
     * Hibernate initializes the images collection on the same entity
     * instance when called after findByIdWithSportTypesAndOwner within
     * the same transaction — no extra DB round trip for the entity itself.
     */
    @Query("""
        SELECT t FROM Turf t
        LEFT JOIN FETCH t.images
        WHERE t.id = :id
        """)
    Optional<Turf> findByIdWithImages(@Param("id") Long id);
}