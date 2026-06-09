package org.bsl.portal.service;

import org.bsl.portal.dto.RoomBookingDisplayConfigDto;
import org.bsl.portal.dto.RoomBookingDto;
import org.bsl.portal.model.Room;
import org.bsl.portal.model.RoomBooking;
import org.bsl.portal.model.RoomBookingDisplayConfig;
import org.bsl.portal.repository.RoomBookingDisplayConfigRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RoomBookingDisplayConfigService {

    @Autowired
    private RoomBookingDisplayConfigRepository repository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    // ==================== CREATE DISPLAY CONFIG ====================
    public RoomBookingDisplayConfigDto create(RoomBookingDisplayConfig config) {
        validateConfig(config);

        RoomBookingDisplayConfig entity = new RoomBookingDisplayConfig();
        entity.setName(config.getName().trim());
        entity.setStartDate(config.getStartDate());
        entity.setEndDate(config.getEndDate());
        entity.setEnabled(config.isEnabled());
        entity.setCreatedBy(resolveCreatedBy(config.getCreatedBy()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.isEnabled()) {
            disableAllConfigs(null);
        }

        return toDto(repository.save(entity));
    }

    // ==================== UPDATE DISPLAY CONFIG ====================
    public RoomBookingDisplayConfigDto update(String id, RoomBookingDisplayConfig config) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Display config id is required");
        }

        RoomBookingDisplayConfig existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Display config not found"));

        validateConfig(config);

        existing.setName(config.getName().trim());
        existing.setStartDate(config.getStartDate());
        existing.setEndDate(config.getEndDate());
        existing.setEnabled(config.isEnabled());
        existing.setUpdatedAt(LocalDateTime.now());

        if (existing.isEnabled()) {
            disableAllConfigs(existing.getId());
        }

        return toDto(repository.save(existing));
    }

    // ==================== DELETE DISPLAY CONFIG ====================
    public void delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Display config id is required");
        }

        RoomBookingDisplayConfig existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Display config not found"));

        repository.delete(existing);
    }

    // ==================== GET ALL DISPLAY CONFIGS ====================
    public List<RoomBookingDisplayConfigDto> getAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public RoomBookingDisplayConfigDto getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Display config id is required");
        }

        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("Display config not found"));
    }

    // ==================== SEARCH DISPLAY CONFIGS ====================
    public Page<RoomBookingDisplayConfigDto> search(String name, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<RoomBookingDisplayConfig> result;

        if (name != null && !name.trim().isEmpty()) {
            result = repository.findByNameContainingIgnoreCase(name.trim(), pageable);
        } else {
            result = repository.findAll(pageable);
        }

        List<RoomBookingDisplayConfigDto> dtoList = result.getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, result.getTotalElements());
    }

    // ==================== ENABLE ONE CONFIG ONLY ====================
    public RoomBookingDisplayConfigDto enable(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Display config id is required");
        }

        RoomBookingDisplayConfig target = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Display config not found"));

        disableAllConfigs(target.getId());

        target.setEnabled(true);
        target.setUpdatedAt(LocalDateTime.now());

        return toDto(repository.save(target));
    }

    public RoomBookingDisplayConfigDto disable(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Display config id is required");
        }

        RoomBookingDisplayConfig target = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Display config not found"));

        target.setEnabled(false);
        target.setUpdatedAt(LocalDateTime.now());

        return toDto(repository.save(target));
    }

    public Optional<RoomBookingDisplayConfigDto> getActiveConfig() {
        List<RoomBookingDisplayConfig> activeConfigs = repository.findByEnabledTrue();

        if (activeConfigs == null || activeConfigs.isEmpty()) {
            return Optional.empty();
        }

        // Nếu dữ liệu cũ có nhiều enabled, tự giữ config mới nhất, tắt các config còn lại.
        RoomBookingDisplayConfig selected = activeConfigs.stream()
                .sorted((a, b) -> {
                    LocalDateTime ad = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    LocalDateTime bd = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();

                    if (ad == null && bd == null) return 0;
                    if (ad == null) return 1;
                    if (bd == null) return -1;

                    return bd.compareTo(ad);
                })
                .findFirst()
                .orElse(activeConfigs.get(0));

        disableAllConfigs(selected.getId());

        if (!selected.isEnabled()) {
            selected.setEnabled(true);
            selected = repository.save(selected);
        }

        return Optional.of(toDto(selected));
    }

    // ==================== ACTIVE BOOKINGS FOR INDEX ROOM ====================
    public List<RoomBookingDto> getActiveBookingsForIndexRoom() {
        Optional<RoomBookingDisplayConfigDto> activeConfigOpt = getActiveConfig();

        if (activeConfigOpt.isEmpty()) {
            return List.of();
        }

        RoomBookingDisplayConfigDto activeConfig = activeConfigOpt.get();

        return getBookingsByDisplayRange(activeConfig.getStartDate(), activeConfig.getEndDate());
    }

    public List<RoomBookingDto> getBookingsByDisplayRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }

        if (endDate == null) {
            throw new IllegalArgumentException("End date is required");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after or equal start date");
        }

        Sort sort = Sort.by(Sort.Direction.ASC, "checkInDate")
                .and(Sort.by(Sort.Direction.ASC, "roomId"));

        List<RoomBooking> bookings = roomBookingRepository
                .findByCheckInDateLessThanEqualAndCheckOutDateGreaterThanEqual(endDate, startDate, sort);

        return bookings.stream()
                .map(this::toRoomBookingDto)
                .collect(Collectors.toList());
    }

    private void disableAllConfigs(String exceptId) {
        List<RoomBookingDisplayConfig> activeConfigs = repository.findByEnabledTrue();

        if (activeConfigs == null || activeConfigs.isEmpty()) {
            return;
        }

        for (RoomBookingDisplayConfig config : activeConfigs) {
            if (config.getId() != null && config.getId().equals(exceptId)) {
                continue;
            }

            config.setEnabled(false);
            config.setUpdatedAt(LocalDateTime.now());
            repository.save(config);
        }
    }

    private void validateConfig(RoomBookingDisplayConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Display config data is required");
        }

        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Display config name is required");
        }

        if (config.getName().trim().length() > 200) {
            throw new IllegalArgumentException("Display config name must be less than or equal to 200 characters");
        }

        if (config.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }

        if (config.getEndDate() == null) {
            throw new IllegalArgumentException("End date is required");
        }

        if (config.getEndDate().isBefore(config.getStartDate())) {
            throw new IllegalArgumentException("End date must be after or equal start date");
        }
    }

    private RoomBookingDisplayConfigDto toDto(RoomBookingDisplayConfig config) {
        return new RoomBookingDisplayConfigDto(
                config.getId(),
                config.getName(),
                config.getStartDate(),
                config.getEndDate(),
                config.isEnabled(),
                config.getCreatedBy(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

    private RoomBookingDto toRoomBookingDto(RoomBooking booking) {
        RoomBookingDto dto = new RoomBookingDto();

        dto.setId(booking.getId());
        dto.setTitle(booking.getTitle());
        dto.setRoomId(booking.getRoomId());
        dto.setCheckInDate(booking.getCheckInDate());
        dto.setCheckOutDate(booking.getCheckOutDate());
        dto.setPeopleInCharge(booking.getPeopleInCharge());
        dto.setBasedLocation(booking.getBasedLocation());
        dto.setRoomCharged(booking.getRoomCharged());
        dto.setCreatedBy(booking.getCreatedBy());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());

        if (booking.getRoomId() != null && !booking.getRoomId().trim().isEmpty()) {
            Optional<Room> roomOpt = roomRepository.findById(booking.getRoomId().trim());
            roomOpt.ifPresent(room -> dto.setRoomName(room.getRoomName()));
        }

        return dto;
    }

    private String resolveCreatedBy(String fallback) {
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
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
}
