package org.bsl.portal.repository;

import org.bsl.portal.model.RoomBookingDisplayConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RoomBookingDisplayConfigRepository extends MongoRepository<RoomBookingDisplayConfig, String> {

    Page<RoomBookingDisplayConfig> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<RoomBookingDisplayConfig> findByEnabledTrue();
}
