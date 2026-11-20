package com.swiftdrop.backend.dispatcher.repository;

import com.swiftdrop.backend.dispatcher.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    List<DeliveryZone> findAllByOrderByNameAscIdAsc();

    /** Another zone (not {@code exceptId}; pass -1 for a new zone) with this code. */
    boolean existsByCodeAndIdNot(String code, Long exceptId);

    /** Which of these districts other zones already hold, as [district, zone code] pairs. */
    @Query("""
            select d, z.code from DeliveryZone z join z.districts d
            where d in :districts and z.id <> :exceptId
            order by d
            """)
    List<Object[]> findTakenDistricts(@Param("districts") Collection<String> districts, @Param("exceptId") Long exceptId);
}
