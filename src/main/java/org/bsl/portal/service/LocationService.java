package org.bsl.portal.service;

import org.bsl.portal.model.Location;
import org.bsl.portal.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LocationService {

    @Autowired
    private LocationRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Location create(String locationName, String createdByEmail) {
        String normalizedLocation = normalize(locationName);
        String normalizedCreatedByEmail = normalize(createdByEmail);

        if (normalizedLocation == null) {
            throw new IllegalArgumentException("Location is required");
        }

        if (normalizedCreatedByEmail == null) {
            throw new IllegalArgumentException("Created By email is required");
        }

        if (existsByLocationIgnoreCase(normalizedLocation, null)) {
            throw new IllegalArgumentException("Location '" + normalizedLocation + "' already exists");
        }

        LocalDateTime now = LocalDateTime.now();

        Location item = new Location();
        item.setId(UUID.randomUUID().toString());
        item.setLocation(normalizedLocation);
        item.setUserIdCreate(normalizedCreatedByEmail);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return repository.save(item);
    }

    public Location update(String id, String locationName) {
        String normalizedId = normalize(id);
        String normalizedLocation = normalize(locationName);

        if (normalizedId == null) {
            throw new IllegalArgumentException("Location ID is required");
        }

        if (normalizedLocation == null) {
            throw new IllegalArgumentException("Location is required");
        }

        Location existing = getById(normalizedId);

        if (existing == null) {
            return null;
        }

        boolean locationChanged = existing.getLocation() == null
                || !existing.getLocation().trim().equalsIgnoreCase(normalizedLocation);

        if (locationChanged && isLocationUsed(existing)) {
            /*
             * Vì RoomBooking hiện thường lưu basedLocation bằng text.
             * Nếu đổi tên location đã được dùng, dữ liệu cũ có thể bị lệch.
             * Nếu sau này bạn lưu bằng locationId, có thể bỏ rule này.
             */
            throw new IllegalStateException("Location is already used and cannot be renamed");
        }

        if (existsByLocationIgnoreCase(normalizedLocation, normalizedId)) {
            throw new IllegalArgumentException("Location '" + normalizedLocation + "' already exists");
        }

        existing.setLocation(normalizedLocation);
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    public Location getById(String id) {
        String normalizedId = normalize(id);

        if (normalizedId == null) {
            return null;
        }

        return repository.findById(normalizedId).orElse(null);
    }

    public List<Location> getAll() {
        Query query = new Query();
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC,
                "location"
        ));

        return mongoTemplate.find(query, Location.class);
    }

    public Page<Location> search(String keyword, Pageable pageable) {
        String normalizedKeyword = normalize(keyword);

        Query query = new Query();

        if (normalizedKeyword != null) {
            query.addCriteria(Criteria.where("location").regex(
                    Pattern.quote(normalizedKeyword),
                    "i"
            ));
        }

        long total = mongoTemplate.count(query, Location.class);

        query.with(pageable);

        List<Location> content = mongoTemplate.find(query, Location.class);

        return new PageImpl<>(content, pageable, total);
    }

    public void delete(String id) {
        String normalizedId = normalize(id);

        if (normalizedId == null) {
            throw new IllegalArgumentException("Location ID is required");
        }

        Location existing = getById(normalizedId);

        if (existing == null) {
            return;
        }

        if (isLocationUsed(existing)) {
            throw new IllegalStateException("Location is already used and cannot be deleted");
        }

        repository.deleteById(normalizedId);
    }

    public boolean isLocationUsed(Location location) {
        if (location == null) {
            return false;
        }

        String id = normalize(location.getId());
        String locationName = normalize(location.getLocation());

        if (id == null && locationName == null) {
            return false;
        }

        Criteria criteria = new Criteria().orOperator(
                Criteria.where("locationId").is(id),
                Criteria.where("basedLocationId").is(id),
                Criteria.where("basedLocation").is(locationName),
                Criteria.where("location").is(locationName)
        );

        Query query = new Query(criteria);

        /*
         * Dự án Room Booking thường dùng một trong các collection này.
         * Nếu RoomBooking của bạn có @Document(collection = "...") khác,
         * chỉ cần thêm tên collection vào đây.
         */
        boolean usedInRoomBookings = mongoTemplate.exists(query, "room_bookings")
                || mongoTemplate.exists(query, "roomBookings")
                || mongoTemplate.exists(query, "room_booking")
                || mongoTemplate.exists(query, "roomBooking");

        return usedInRoomBookings;
    }

    private boolean existsByLocationIgnoreCase(String locationName, String excludeId) {
        String normalizedLocation = normalize(locationName);

        if (normalizedLocation == null) {
            return false;
        }

        Criteria criteria = Criteria.where("location").regex(
                "^" + Pattern.quote(normalizedLocation) + "$",
                "i"
        );

        Query query = new Query(criteria);

        String normalizedExcludeId = normalize(excludeId);

        if (normalizedExcludeId != null) {
            query.addCriteria(Criteria.where("_id").ne(normalizedExcludeId));
        }

        return mongoTemplate.exists(query, Location.class);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
