package org.bsl.portal.repository;

import org.bsl.portal.model.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoomRepository extends MongoRepository<Room, String> {

    Page<Room> findByRoomNameContainingIgnoreCase(String roomName, Pageable pageable);

    boolean existsByRoomNameIgnoreCase(String roomName);
}
