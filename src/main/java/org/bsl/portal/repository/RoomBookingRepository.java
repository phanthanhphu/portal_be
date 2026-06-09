package org.bsl.portal.repository;

import org.bsl.portal.model.RoomBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface RoomBookingRepository extends MongoRepository<RoomBooking, String> {

    Page<RoomBooking> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<RoomBooking> findByPeopleInChargeContainingIgnoreCase(String peopleInCharge, Pageable pageable);

    Page<RoomBooking> findByBasedLocationContainingIgnoreCase(String basedLocation, Pageable pageable);

    Page<RoomBooking> findByRoomId(String roomId, Pageable pageable);

    List<RoomBooking> findByRoomId(String roomId);

    List<RoomBooking> findByShowOnIndexRoom(Boolean showOnIndexRoom, Sort sort);

    // Giữ lại method này để nếu project vẫn còn RoomBookingDisplayConfigService.java cũ
    // thì không bị lỗi compile. Nếu đã xóa module DisplayConfig cũ thì method này cũng không ảnh hưởng.
    List<RoomBooking> findByCheckInDateLessThanEqualAndCheckOutDateGreaterThanEqual(
            LocalDate endDate,
            LocalDate startDate,
            Sort sort
    );
}
