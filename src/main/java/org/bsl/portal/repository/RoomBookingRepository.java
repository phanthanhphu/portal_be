package org.bsl.portal.repository;

import org.bsl.portal.model.RoomBooking;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface RoomBookingRepository extends MongoRepository<RoomBooking, String> {

    List<RoomBooking> findByRoomId(String roomId);

    boolean existsByRoomId(String roomId);

    List<RoomBooking> findByShowOnIndexRoom(Boolean showOnIndexRoom, Sort sort);

    List<RoomBooking> findByCheckInDateLessThanEqualAndCheckOutDateGreaterThanEqual(
            LocalDate endDate,
            LocalDate startDate,
            Sort sort
    );
}