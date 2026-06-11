package org.bsl.portal.service;

import org.bsl.portal.dto.RoomBookingDto;
import org.bsl.portal.model.Room;
import org.bsl.portal.model.RoomBooking;
import org.bsl.portal.repository.RoomBookingRepository;
import org.bsl.portal.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RoomBookingService {

    @Autowired
    private RoomBookingRepository repository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    // ==================== CREATE ROOM BOOKING ====================
    public RoomBooking create(RoomBooking booking) {
        validateBooking(null, booking);

        booking.setId(null);
        booking.setTitle(trimRequired(booking.getTitle()));
        booking.setRoomId(booking.getRoomId().trim());
        booking.setCheckInTime(normalizeTime(booking.getCheckInTime()));
        booking.setCheckOutTime(normalizeTime(booking.getCheckOutTime()));
        booking.setPeopleInCharge(trimToNull(booking.getPeopleInCharge()));
        booking.setBasedLocation(trimToNull(booking.getBasedLocation()));
        booking.setRoomCharged(normalizeVndAmount(booking.getRoomCharged()));
        booking.setShowOnIndexRoom(Boolean.FALSE);
        booking.setCreatedBy(resolveCreatedBy(booking));
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        return repository.save(booking);
    }

    // ==================== UPDATE ROOM BOOKING ====================
    public RoomBooking update(String id, RoomBooking booking) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room booking id is required");
        }

        RoomBooking existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room booking not found"));

        validateBooking(id, booking);

        existing.setTitle(trimRequired(booking.getTitle()));
        existing.setRoomId(booking.getRoomId().trim());
        existing.setCheckInDate(booking.getCheckInDate());
        existing.setCheckInTime(normalizeTime(booking.getCheckInTime()));
        existing.setCheckOutDate(booking.getCheckOutDate());
        existing.setCheckOutTime(normalizeTime(booking.getCheckOutTime()));
        existing.setPeopleInCharge(trimToNull(booking.getPeopleInCharge()));
        existing.setBasedLocation(trimToNull(booking.getBasedLocation()));
        existing.setRoomCharged(normalizeVndAmount(booking.getRoomCharged()));

        if (booking.getShowOnIndexRoom() != null) {
            if (Boolean.TRUE.equals(booking.getShowOnIndexRoom())) {
                validateCanShowOnIndexRoom(existing);
            }

            existing.setShowOnIndexRoom(booking.getShowOnIndexRoom());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    // ==================== DELETE ROOM BOOKING ====================
    public void delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room booking id is required");
        }

        RoomBooking existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room booking not found"));

        repository.delete(existing);
    }

    // ==================== GET ALL ROOM BOOKINGS ====================
    public List<RoomBookingDto> getAll() {
        return repository.findAll(
                        Sort.by(Sort.Direction.DESC, "checkInDate")
                                .and(Sort.by(Sort.Direction.DESC, "checkInTime"))
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ==================== GET ROOM BOOKING BY ID ====================
    public RoomBookingDto getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room booking id is required");
        }

        RoomBooking booking = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room booking not found"));

        return toDto(booking);
    }

    // ==================== SEARCH ROOM BOOKINGS WITH PAGINATION ====================
    public Page<RoomBookingDto> search(
            String name,
            String roomId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("To date must be after or equal to from date");
        }

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "checkInDate")
                        .and(Sort.by(Sort.Direction.DESC, "checkInTime"))
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Criteria> criteriaList = new ArrayList<>();

        if (roomId != null && !roomId.trim().isEmpty()) {
            criteriaList.add(Criteria.where("roomId").is(roomId.trim()));
        }

        String keyword = trimToNull(name);

        if (keyword != null) {
            Pattern regex = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);

            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("title").regex(regex),
                    Criteria.where("peopleInCharge").regex(regex),
                    Criteria.where("basedLocation").regex(regex)
            ));
        }

        // Lọc các booking có giao với khoảng ngày tìm kiếm.
        // VD: search 2026-06-01 -> 2026-06-05 sẽ lấy booking còn nằm trong khoảng này.
        if (fromDate != null) {
            criteriaList.add(Criteria.where("checkOutDate").gte(fromDate));
        }

        if (toDate != null) {
            criteriaList.add(Criteria.where("checkInDate").lte(toDate));
        }

        Query countQuery = new Query();
        Query dataQuery = new Query();

        if (!criteriaList.isEmpty()) {
            Criteria criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
            countQuery.addCriteria(criteria);
            dataQuery.addCriteria(criteria);
        }

        long total = mongoTemplate.count(countQuery, RoomBooking.class);

        dataQuery.with(pageable);

        List<RoomBooking> bookings = mongoTemplate.find(dataQuery, RoomBooking.class);

        List<RoomBookingDto> dtoList = bookings.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, total);
    }

    // ==================== GET BOOKINGS BY ROOM ID ====================
    public List<RoomBookingDto> getByRoomId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("Room id is required");
        }

        return repository.findByRoomId(roomId.trim())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ==================== SHOW / HIDE ON INDEX ROOM ====================
    public RoomBookingDto updateIndexRoomDisplay(String id, boolean enabled) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room booking id is required");
        }

        RoomBooking booking = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room booking not found"));

        if (enabled) {
            validateCanShowOnIndexRoom(booking);
        }

        booking.setShowOnIndexRoom(enabled);
        booking.setUpdatedAt(LocalDateTime.now());

        return toDto(repository.save(booking));
    }

    public List<RoomBookingDto> getIndexRoomBookings() {
        Sort sort = Sort.by(Sort.Direction.ASC, "checkInDate")
                .and(Sort.by(Sort.Direction.ASC, "checkInTime"))
                .and(Sort.by(Sort.Direction.ASC, "roomId"))
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));

        return repository.findByShowOnIndexRoom(Boolean.TRUE, sort)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // Không cho tick hiển thị nếu booking đã hết hạn theo cả ngày + giờ.
    private void validateCanShowOnIndexRoom(RoomBooking booking) {
        if (booking.getCheckInDate() == null) {
            throw new IllegalArgumentException("Check-in date is required before showing on Index Room");
        }

        if (booking.getCheckOutDate() == null) {
            throw new IllegalArgumentException("Check-out date is required before showing on Index Room");
        }

        LocalDateTime checkOutAt = toExistingEndDateTime(booking);

        if (LocalDateTime.now().isAfter(checkOutAt)) {
            throw new IllegalArgumentException(
                    "Cannot show on Index Room because this booking already checked out on "
                            + booking.getCheckOutDate()
                            + " "
                            + formatTime(booking.getCheckOutTime())
            );
        }
    }

    // ==================== DTO MAPPER ====================
    private RoomBookingDto toDto(RoomBooking booking) {
        RoomBookingDto dto = new RoomBookingDto();

        dto.setId(booking.getId());
        dto.setTitle(booking.getTitle());
        dto.setRoomId(booking.getRoomId());
        dto.setCheckInDate(booking.getCheckInDate());
        dto.setCheckInTime(booking.getCheckInTime());
        dto.setCheckOutDate(booking.getCheckOutDate());
        dto.setCheckOutTime(booking.getCheckOutTime());
        dto.setPeopleInCharge(booking.getPeopleInCharge());
        dto.setBasedLocation(booking.getBasedLocation());
        dto.setRoomCharged(booking.getRoomCharged());
        dto.setShowOnIndexRoom(Boolean.TRUE.equals(booking.getShowOnIndexRoom()));
        dto.setCreatedBy(booking.getCreatedBy());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());

        if (booking.getRoomId() != null && !booking.getRoomId().trim().isEmpty()) {
            Optional<Room> roomOpt = roomRepository.findById(booking.getRoomId().trim());
            roomOpt.ifPresent(room -> dto.setRoomName(room.getRoomName()));
        }

        return dto;
    }

    // ==================== VALIDATE ====================
    private void validateBooking(String currentBookingId, RoomBooking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("Room booking data is required");
        }

        if (booking.getTitle() == null || booking.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }

        if (booking.getTitle().trim().length() > 200) {
            throw new IllegalArgumentException("Title must be less than or equal to 200 characters");
        }


        if (booking.getRoomId() == null || booking.getRoomId().trim().isEmpty()) {
            throw new IllegalArgumentException("Room id is required");
        }

        boolean roomExists = roomRepository.existsById(booking.getRoomId().trim());

        if (!roomExists) {
            throw new IllegalArgumentException("Selected room does not exist");
        }

        if (booking.getCheckInDate() == null) {
            throw new IllegalArgumentException("Check-in date is required");
        }

        if (booking.getCheckInTime() == null) {
            throw new IllegalArgumentException("Check-in time is required");
        }

        if (booking.getCheckOutDate() == null) {
            throw new IllegalArgumentException("Check-out date is required");
        }

        if (booking.getCheckOutTime() == null) {
            throw new IllegalArgumentException("Check-out time is required");
        }

        LocalDateTime checkInAt = toDateTime(booking.getCheckInDate(), booking.getCheckInTime());
        LocalDateTime checkOutAt = toDateTime(booking.getCheckOutDate(), booking.getCheckOutTime());

        if (!checkOutAt.isAfter(checkInAt)) {
            throw new IllegalArgumentException("Check-out date/time must be after check-in date/time");
        }

        if (booking.getPeopleInCharge() == null || booking.getPeopleInCharge().trim().isEmpty()) {
            throw new IllegalArgumentException("People in charge is required");
        }

        if (booking.getPeopleInCharge().trim().length() > 200) {
            throw new IllegalArgumentException("People in charge must be less than or equal to 200 characters");
        }

        if (booking.getBasedLocation() == null || booking.getBasedLocation().trim().isEmpty()) {
            throw new IllegalArgumentException("Based location is required");
        }

        if (booking.getBasedLocation().trim().length() > 200) {
            throw new IllegalArgumentException("Based location must be less than or equal to 200 characters");
        }

        validateVndAmount(booking.getRoomCharged());

        validateNoRoomDateConflict(currentBookingId, booking);
    }

    // ==================== VND VALIDATION ====================
    private void validateVndAmount(BigDecimal amount) {
        if (amount == null) {
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Room charged must be greater than or equal to 0 VND");
        }

        BigDecimal normalized = amount.stripTrailingZeros();

        if (normalized.scale() > 0) {
            throw new IllegalArgumentException("Room charged must be a whole number in VND, decimals are not allowed");
        }
    }

    private BigDecimal normalizeVndAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    // Không được đặt cùng phòng trong khoảng thời gian bị trùng.
    // Dùng interval [start, end): nếu booking cũ checkout 13:00,
    // booking mới checkin 13:00 hoặc 13:30 đều hợp lệ.
    private void validateNoRoomDateConflict(String currentBookingId, RoomBooking booking) {
        LocalDateTime newCheckInAt = toDateTime(
                booking.getCheckInDate(),
                normalizeTime(booking.getCheckInTime())
        );

        LocalDateTime newCheckOutAt = toDateTime(
                booking.getCheckOutDate(),
                normalizeTime(booking.getCheckOutTime())
        );

        List<RoomBooking> existingBookings = repository.findByRoomId(booking.getRoomId().trim());

        for (RoomBooking existing : existingBookings) {
            if (existing.getId() == null) {
                continue;
            }

            if (currentBookingId != null && currentBookingId.equals(existing.getId())) {
                continue;
            }

            if (existing.getCheckInDate() == null || existing.getCheckOutDate() == null) {
                continue;
            }

            LocalDateTime existingCheckInAt = toExistingStartDateTime(existing);
            LocalDateTime existingCheckOutAt = toExistingEndDateTime(existing);

            if (!existingCheckOutAt.isAfter(existingCheckInAt)) {
                continue;
            }

            boolean overlap =
                    newCheckInAt.isBefore(existingCheckOutAt)
                            && newCheckOutAt.isAfter(existingCheckInAt);

            if (overlap) {
                throw new IllegalArgumentException(
                        "This room is already booked from "
                                + existing.getCheckInDate()
                                + " "
                                + formatTime(existing.getCheckInTime())
                                + " to "
                                + existing.getCheckOutDate()
                                + " "
                                + formatTime(existing.getCheckOutTime())
                );
            }
        }
    }

    private LocalTime normalizeTime(LocalTime time) {
        if (time == null) {
            return null;
        }

        return time.withSecond(0).withNano(0);
    }

    private LocalDateTime toDateTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, normalizeTime(time));
    }

    // Dành cho dữ liệu cũ chưa có checkInTime.
    // Nếu dữ liệu cũ bị null giờ, tạm hiểu là bắt đầu từ 00:00 để tránh overbook.
    private LocalDateTime toExistingStartDateTime(RoomBooking booking) {
        LocalTime time = booking.getCheckInTime() == null
                ? LocalTime.MIN
                : normalizeTime(booking.getCheckInTime());

        return LocalDateTime.of(booking.getCheckInDate(), time);
    }

    // Dành cho dữ liệu cũ chưa có checkOutTime.
    // Nếu dữ liệu cũ bị null giờ, tạm hiểu là hết ngày 23:59:59 để tránh overbook.
    private LocalDateTime toExistingEndDateTime(RoomBooking booking) {
        LocalTime time = booking.getCheckOutTime() == null
                ? LocalTime.of(23, 59, 59)
                : normalizeTime(booking.getCheckOutTime());

        return LocalDateTime.of(booking.getCheckOutDate(), time);
    }

    private String formatTime(LocalTime time) {
        if (time == null) {
            return "--:--";
        }

        return normalizeTime(time).toString();
    }

    private String trimRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String resolveCreatedBy(RoomBooking booking) {
        if (booking.getCreatedBy() != null && !booking.getCreatedBy().trim().isEmpty()) {
            return booking.getCreatedBy().trim();
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                String name = authentication.getName();

                if (name != null && !name.trim().isEmpty() && !"anonymousUser".equalsIgnoreCase(name)) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
            // Fallback SYSTEM.
        }

        return "SYSTEM";
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
