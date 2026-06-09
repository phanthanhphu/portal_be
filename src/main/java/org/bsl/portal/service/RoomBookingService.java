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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoomBookingService {

    @Autowired
    private RoomBookingRepository repository;

    @Autowired
    private RoomRepository roomRepository;

    // ==================== CREATE ROOM BOOKING ====================
    public RoomBooking create(RoomBooking booking) {
        validateBooking(null, booking);

        booking.setId(null);
        booking.setTitle(booking.getTitle().trim());
        booking.setRoomId(booking.getRoomId().trim());
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

        existing.setTitle(booking.getTitle().trim());
        existing.setRoomId(booking.getRoomId().trim());
        existing.setCheckInDate(booking.getCheckInDate());
        existing.setCheckOutDate(booking.getCheckOutDate());
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
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
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
    public Page<RoomBookingDto> search(String name, String roomId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        // Danh sách mặc định: mới tạo nhất lên đầu.
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<RoomBooking> result;

        if (roomId != null && !roomId.trim().isEmpty()) {
            result = repository.findByRoomId(roomId.trim(), pageable);
        } else if (name != null && !name.trim().isEmpty()) {
            String keyword = name.trim();

            Page<RoomBooking> byTitle = repository.findByTitleContainingIgnoreCase(keyword, pageable);

            if (!byTitle.isEmpty()) {
                result = byTitle;
            } else {
                Page<RoomBooking> byPeople = repository.findByPeopleInChargeContainingIgnoreCase(keyword, pageable);

                if (!byPeople.isEmpty()) {
                    result = byPeople;
                } else {
                    result = repository.findByBasedLocationContainingIgnoreCase(keyword, pageable);
                }
            }
        } else {
            result = repository.findAll(pageable);
        }

        List<RoomBookingDto> dtoList = result.getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, result.getTotalElements());
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
                .and(Sort.by(Sort.Direction.ASC, "roomId"))
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));

        return repository.findByShowOnIndexRoom(Boolean.TRUE, sort)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // Không cho tick hiển thị nếu booking đã hết hạn.
    // Ví dụ: checkOutDate = 2026-05-03, hôm nay = 2026-05-04 => không hợp lệ.
    private void validateCanShowOnIndexRoom(RoomBooking booking) {
        if (booking.getCheckInDate() == null) {
            throw new IllegalArgumentException("Check-in date is required before showing on Index Room");
        }

        if (booking.getCheckOutDate() == null) {
            throw new IllegalArgumentException("Check-out date is required before showing on Index Room");
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(booking.getCheckOutDate())) {
            throw new IllegalArgumentException(
                    "Cannot show on Index Room because this booking already checked out on "
                            + booking.getCheckOutDate()
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
        dto.setCheckOutDate(booking.getCheckOutDate());
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

        if (booking.getCheckOutDate() == null) {
            throw new IllegalArgumentException("Check-out date is required");
        }

        if (!booking.getCheckOutDate().isAfter(booking.getCheckInDate())) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
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

    // Không được đặt cùng phòng trong khoảng ngày bị trùng.
    private void validateNoRoomDateConflict(String currentBookingId, RoomBooking booking) {
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

            boolean overlap =
                    booking.getCheckInDate().isBefore(existing.getCheckOutDate())
                            && booking.getCheckOutDate().isAfter(existing.getCheckInDate());

            if (overlap) {
                throw new IllegalArgumentException(
                        "This room is already booked from "
                                + existing.getCheckInDate()
                                + " to "
                                + existing.getCheckOutDate()
                );
            }
        }
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
