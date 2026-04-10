package com.turfzy.turf;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurfRepository extends JpaRepository<Turf, Long> {

    /** Public listing — only active turfs */
    Page<Turf> findByStatusAndCityContainingIgnoreCase(
        TurfStatus status, String city, Pageable pageable);

    /** Owner's own turfs — all statuses */
    List<Turf> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** Admin — pending approval queue */
    List<Turf> findByStatusOrderByCreatedAtAsc(TurfStatus status);

    /** Filter by city + sport type (used in search page) */
    @Query("""
        SELECT DISTINCT t FROM Turf t
        JOIN t.sportTypes s
        WHERE t.status = 'ACTIVE'
        AND (:city IS NULL OR LOWER(t.city) LIKE LOWER(CONCAT('%', :city, '%')))
        AND (:sport IS NULL OR s = :sport)
        ORDER BY t.averageRating DESC
        """)
    Page<Turf> searchTurfs(
        @Param("city") String city,
        @Param("sport") SportType sport,
        Pageable pageable);

    /** Count active turfs for admin dashboard */
    long countByStatus(TurfStatus status);
}