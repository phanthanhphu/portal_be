package org.bsl.portal.service;

import org.bsl.portal.dto.RoomBookingDto;
import org.bsl.portal.model.Room;
import org.bsl.portal.model.Location;
import org.bsl.portal.model.RoomBooking;
import org.bsl.portal.repository.RoomBookingRepository;
import org.bsl.portal.repository.LocationRepository;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.bson.Document;
import org.bson.types.ObjectId;

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

    private static final long ROOM_BOOKING_BUFFER_MINUTES = 30L;
    private static final String INDEX_ROOM_ORDER_FIELD = "indexRoomOrder";

    @Autowired
    private RoomBookingRepository repository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    // ==================== CREATE ROOM BOOKING ====================
    public RoomBooking create(RoomBooking booking) {
        syncLocationToBooking(booking);
        validateBooking(null, booking);
        validateBookingDateNotPastForCreate(booking);

        booking.setId(null);
        booking.setTitle(trimRequired(booking.getTitle()));
        booking.setRoomId(booking.getRoomId().trim());
        booking.setCheckInTime(normalizeTime(booking.getCheckInTime()));
        booking.setCheckOutTime(normalizeTime(booking.getCheckOutTime()));
        booking.setPeopleInCharge(trimToNull(booking.getPeopleInCharge()));
        booking.setLocationId(trimToNull(booking.getLocationId()));
        booking.setBasedLocation(trimToNull(booking.getBasedLocation()));
        booking.setRoomCharged(normalizeUsdAmount(booking.getRoomCharged()));
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
        Integer previousIndexRoomOrder = getIndexRoomOrder(id);

        syncLocationToBooking(booking);
        validateBooking(id, booking);

        existing.setTitle(trimRequired(booking.getTitle()));
        existing.setRoomId(booking.getRoomId().trim());
        existing.setCheckInDate(booking.getCheckInDate());
        existing.setCheckInTime(normalizeTime(booking.getCheckInTime()));
        existing.setCheckOutDate(booking.getCheckOutDate());
        existing.setCheckOutTime(normalizeTime(booking.getCheckOutTime()));
        existing.setPeopleInCharge(trimToNull(booking.getPeopleInCharge()));
        existing.setLocationId(trimToNull(booking.getLocationId()));
        existing.setBasedLocation(trimToNull(booking.getBasedLocation()));
        existing.setRoomCharged(normalizeUsdAmount(booking.getRoomCharged()));

        if (booking.getShowOnIndexRoom() != null) {
            if (Boolean.TRUE.equals(booking.getShowOnIndexRoom())) {
                validateCanShowOnIndexRoom(existing);
            }

            existing.setShowOnIndexRoom(booking.getShowOnIndexRoom());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        RoomBooking saved = repository.save(existing);
        syncIndexRoomOrderAfterSave(saved, previousIndexRoomOrder);

        return saved;
    }

    // ==================== DELETE ROOM BOOKING ====================
    public void delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room booking id is required");
        }

        RoomBooking existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room booking not found"));

        boolean wasShownOnIndexRoom = Boolean.TRUE.equals(existing.getShowOnIndexRoom());

        repository.delete(existing);

        if (wasShownOnIndexRoom) {
            resequenceIndexRoomOrder();
        }
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
    /*
     * Search kết hợp nhiều tiêu chí:
     * - name: tìm trong title theo kiểu contains, không phân biệt hoa/thường.
     * - roomId: lọc theo phòng.
     * - locationId: lọc theo locationId, hỗ trợ dữ liệu cũ basedLocation text.
     * - fromDate/toDate: lọc booking có giao với khoảng ngày tìm kiếm.
     *
     * Logic ngày theo yêu cầu:
     * - fromDate + toDate => chỉ cần booking chạm/đi qua khoảng ngày user chọn thì lấy.
     * - Công thức overlap:
     *   checkInDate  <= toDate
     *   checkOutDate >= fromDate
     *
     * Sort theo yêu cầu:
     * - API /search trả về booking mới cập nhật nhất lên top.
     * - Dựa trên toàn bộ updatedAt: ngày + giờ + phút + giây + mili giây.
     */
    public Page<RoomBookingDto> search(
            String name,
            String roomId,
            String locationId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        validateDateRange(fromDate, toDate, "To date must be after or equal to from date");

        /*
         * API /search trả về booking mới cập nhật nhất trước.
         * updatedAt DESC là tiêu chí chính; createdAt/checkInDate chỉ dùng làm phụ để kết quả ổn định
         * khi dữ liệu cũ chưa có updatedAt hoặc nhiều dòng có cùng thời điểm cập nhật.
         */
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "updatedAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "checkInDate"))
                        .and(Sort.by(Sort.Direction.DESC, "checkInTime"))
        );

        List<Criteria> criteriaList = new ArrayList<>();

        if (roomId != null && !roomId.trim().isEmpty()) {
            criteriaList.add(Criteria.where("roomId").is(roomId.trim()));
        }

        String normalizedLocationId = trimToNull(locationId);

        if (normalizedLocationId != null) {
            String locationName = locationRepository.findById(normalizedLocationId)
                    .map(Location::getLocation)
                    .orElse(null);

            if (locationName != null && !locationName.trim().isEmpty()) {
                /*
                 * Hỗ trợ cả dữ liệu mới có locationId và dữ liệu cũ chỉ có basedLocation dạng text.
                 * Regex exact, không phân biệt hoa/thường và bỏ qua khoảng trắng đầu/cuối.
                 */
                Pattern locationRegex = Pattern.compile(
                        "^\\s*" + Pattern.quote(locationName.trim()) + "\\s*$",
                        Pattern.CASE_INSENSITIVE
                );

                criteriaList.add(new Criteria().orOperator(
                        Criteria.where("locationId").is(normalizedLocationId),
                        Criteria.where("basedLocation").regex(locationRegex)
                ));
            } else {
                criteriaList.add(Criteria.where("locationId").is(normalizedLocationId));
            }
        }

        String keyword = trimToNull(name);

        if (keyword != null) {
            /*
             * Search bằng MongoDB query, không filter bằng Java.
             * VD: search "o" sẽ match "Mr. Bao", "Mr. Ho".
             */
            String regexText = ".*" + Pattern.quote(keyword) + ".*";
            criteriaList.add(Criteria.where("title").regex(regexText, "i"));
        }

        /*
         * Search ngày theo logic booking CÓ GIAO với khoảng tìm kiếm (overlap):
         * - Có cả fromDate và toDate:
         *   checkInDate  <= toDate
         *   checkOutDate >= fromDate
         *
         * Nghĩa là booking chỉ cần chạm/đi qua khoảng ngày user chọn thì lấy.
         * Ví dụ search 06/10/2026 đến 06/12/2026:
         * - 06/11/2026 -> 06/17/2026: có lấy vì check-in nằm trong khoảng.
         * - 06/08/2026 -> 06/11/2026: có lấy vì check-out nằm trong khoảng.
         * - 06/08/2026 -> 06/17/2026: có lấy vì booking bao phủ toàn bộ khoảng.
         *
         * - Chỉ có fromDate:
         *   lấy booking chưa checkout trước fromDate
         *   checkOutDate >= fromDate
         *
         * - Chỉ có toDate:
         *   lấy booking đã check-in trước hoặc trong toDate
         *   checkInDate <= toDate
         */
        if (fromDate != null && toDate != null) {
            criteriaList.add(Criteria.where("checkInDate").lte(toDate));
            criteriaList.add(Criteria.where("checkOutDate").gte(fromDate));
        } else if (fromDate != null) {
            criteriaList.add(Criteria.where("checkOutDate").gte(fromDate));
        } else if (toDate != null) {
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

    private void validateDateRange(LocalDate fromDate, LocalDate toDate, String message) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(message);
        }
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
            resequenceIndexRoomOrder();
            setIndexRoomDisplayState(id, true, getNextIndexRoomOrder());
        } else {
            setIndexRoomDisplayState(id, false, null);
            resequenceIndexRoomOrder();
        }

        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("Room booking not found"));
    }

    public List<RoomBookingDto> getIndexRoomBookings() {
        resequenceIndexRoomOrder();

        Sort sort = Sort.by(Sort.Direction.ASC, INDEX_ROOM_ORDER_FIELD)
                .and(Sort.by(Sort.Direction.ASC, "checkInDate"))
                .and(Sort.by(Sort.Direction.ASC, "checkInTime"))
                .and(Sort.by(Sort.Direction.ASC, "roomId"))
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));

        return repository.findByShowOnIndexRoom(Boolean.TRUE, sort)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private Criteria idCriteria(String id) {
        if (ObjectId.isValid(id)) {
            return new Criteria().orOperator(
                    Criteria.where("_id").is(id),
                    Criteria.where("_id").is(new ObjectId(id))
            );
        }

        return Criteria.where("_id").is(id);
    }

    private void setIndexRoomDisplayState(String id, boolean enabled, Integer displayOrder) {
        Update update = new Update()
                .set("showOnIndexRoom", enabled)
                .set("updatedAt", LocalDateTime.now());

        if (enabled) {
            update.set(INDEX_ROOM_ORDER_FIELD, displayOrder == null ? getNextIndexRoomOrder() : displayOrder);
        } else {
            update.unset(INDEX_ROOM_ORDER_FIELD);
        }

        mongoTemplate.updateFirst(
                Query.query(idCriteria(id)),
                update,
                RoomBooking.class
        );
    }

    private int getNextIndexRoomOrder() {
        long currentShownCount = mongoTemplate.count(
                Query.query(Criteria.where("showOnIndexRoom").is(Boolean.TRUE)),
                RoomBooking.class
        );

        return (int) currentShownCount + 1;
    }

    private Integer getIndexRoomOrder(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        Query query = Query.query(idCriteria(id));
        query.fields().include(INDEX_ROOM_ORDER_FIELD);

        Document doc = mongoTemplate.findOne(
                query,
                Document.class,
                mongoTemplate.getCollectionName(RoomBooking.class)
        );

        if (doc == null) {
            return null;
        }

        Object value = doc.get(INDEX_ROOM_ORDER_FIELD);

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private void syncIndexRoomOrderAfterSave(RoomBooking saved, Integer previousIndexRoomOrder) {
        if (saved == null || saved.getId() == null) {
            return;
        }

        if (Boolean.TRUE.equals(saved.getShowOnIndexRoom())) {
            setIndexRoomDisplayState(
                    saved.getId(),
                    true,
                    previousIndexRoomOrder == null ? getNextIndexRoomOrder() : previousIndexRoomOrder
            );
        } else {
            setIndexRoomDisplayState(saved.getId(), false, null);
        }

        resequenceIndexRoomOrder();
    }

    private void resequenceIndexRoomOrder() {
        Sort sort = Sort.by(Sort.Direction.ASC, INDEX_ROOM_ORDER_FIELD)
                .and(Sort.by(Sort.Direction.ASC, "checkInDate"))
                .and(Sort.by(Sort.Direction.ASC, "checkInTime"))
                .and(Sort.by(Sort.Direction.ASC, "roomId"))
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));

        Query query = Query.query(Criteria.where("showOnIndexRoom").is(Boolean.TRUE)).with(sort);
        List<RoomBooking> shownBookings = mongoTemplate.find(query, RoomBooking.class);

        int order = 1;
        for (RoomBooking item : shownBookings) {
            if (item.getId() == null) {
                continue;
            }

            mongoTemplate.updateFirst(
                    Query.query(idCriteria(item.getId())),
                    new Update().set(INDEX_ROOM_ORDER_FIELD, order),
                    RoomBooking.class
            );
            order += 1;
        }
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
        dto.setLocationId(booking.getLocationId());
        dto.setBasedLocation(resolveLocationName(booking));
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


    // ==================== LOCATION LINK ====================
    private Location getLocationByIdOrThrow(String locationId) {
        String normalizedLocationId = trimToNull(locationId);

        if (normalizedLocationId == null) {
            return null;
        }

        return locationRepository.findById(normalizedLocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Location with ID " + normalizedLocationId + " does not exist"
                ));
    }

    /*
     * FE mới gửi locationId.
     * Backend sẽ tự lấy tên location trong master data và set vào basedLocation.
     * basedLocation vẫn được giữ lại để tương thích màn hình list/export/index-room cũ.
     */
    private void syncLocationToBooking(RoomBooking booking) {
        if (booking == null) {
            return;
        }

        String normalizedLocationId = trimToNull(booking.getLocationId());

        if (normalizedLocationId == null) {
            /*
             * Tương thích dữ liệu/FE cũ:
             * Nếu chưa gửi locationId thì vẫn cho dùng basedLocation dạng text.
             */
            booking.setLocationId(null);
            booking.setBasedLocation(trimToNull(booking.getBasedLocation()));
            return;
        }

        Location location = getLocationByIdOrThrow(normalizedLocationId);

        booking.setLocationId(location.getId());
        booking.setBasedLocation(trimToNull(location.getLocation()));
    }

    private String resolveLocationName(RoomBooking booking) {
        if (booking == null) {
            return null;
        }

        String normalizedLocationId = trimToNull(booking.getLocationId());

        if (normalizedLocationId == null) {
            return booking.getBasedLocation();
        }

        return locationRepository.findById(normalizedLocationId)
                .map(Location::getLocation)
                .filter(value -> value != null && !value.trim().isEmpty())
                .orElse(booking.getBasedLocation());
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

        validateUsdAmount(booking.getRoomCharged());

        validateNoRoomDateConflict(currentBookingId, booking);
    }

    // Chỉ áp dụng cho CREATE.
    // Dữ liệu cũ vẫn được phép EDIT lại dù check-in/check-out là ngày quá khứ.
    private void validateBookingDateNotPastForCreate(RoomBooking booking) {
        LocalDate today = LocalDate.now();

        if (booking.getCheckInDate() != null && booking.getCheckInDate().isBefore(today)) {
            throw new IllegalArgumentException("Check-in date cannot be in the past");
        }

        if (booking.getCheckOutDate() != null && booking.getCheckOutDate().isBefore(today)) {
            throw new IllegalArgumentException("Check-out date cannot be in the past");
        }
    }

    // ==================== USD VALIDATION ====================
    private void validateUsdAmount(BigDecimal amount) {
        if (amount == null) {
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Room charged must be greater than or equal to 0 USD");
        }

        BigDecimal normalized = amount.stripTrailingZeros();

        if (normalized.scale() > 2) {
            throw new IllegalArgumentException("Room charged supports up to 2 decimal places in USD");
        }
    }

    private BigDecimal normalizeUsdAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // Không được đặt cùng phòng nếu bị trùng thời gian
    // hoặc chưa cách checkout/checkin tối thiểu 30 phút.
    //
    // Quy ước:
    // - Nếu booking mới nằm sau booking cũ:
    //   newCheckInAt >= existingCheckOutAt + 30 phút
    //
    // - Nếu booking mới nằm trước booking cũ:
    //   existingCheckInAt >= newCheckOutAt + 30 phút
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

            // Khi update thì bỏ qua chính booking hiện tại
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

            LocalDateTime allowedCheckInAfterExisting =
                    existingCheckOutAt.plusMinutes(ROOM_BOOKING_BUFFER_MINUTES);

            LocalDateTime allowedExistingCheckInAfterNew =
                    newCheckOutAt.plusMinutes(ROOM_BOOKING_BUFFER_MINUTES);

            boolean newBookingIsAfterExisting =
                    !newCheckInAt.isBefore(allowedCheckInAfterExisting);

            boolean newBookingIsBeforeExisting =
                    !existingCheckInAt.isBefore(allowedExistingCheckInAfterNew);

            boolean conflict = !newBookingIsAfterExisting && !newBookingIsBeforeExisting;

            if (conflict) {
                throw new IllegalArgumentException(
                        "This room is already booked from "
                                + existing.getCheckInDate()
                                + " "
                                + formatTime(existing.getCheckInTime())
                                + " to "
                                + existing.getCheckOutDate()
                                + " "
                                + formatTime(existing.getCheckOutTime())
                                + ". New booking must be at least "
                                + ROOM_BOOKING_BUFFER_MINUTES
                                + " minutes apart from existing booking. Earliest valid check-in after this booking is "
                                + allowedCheckInAfterExisting.toLocalDate()
                                + " "
                                + formatTime(allowedCheckInAfterExisting.toLocalTime())
                );
            }
        }
    }

    private LocalTime normalizeTime(LocalTime time) {
        if (time == null) {
            return LocalTime.MIN;
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
    // Nếu dữ liệu cũ bị null giờ, dùng mặc định 00:00 theo chuẩn hiện tại.
    private LocalDateTime toExistingEndDateTime(RoomBooking booking) {
        LocalTime time = booking.getCheckOutTime() == null
                ? LocalTime.MIN
                : normalizeTime(booking.getCheckOutTime());

        return LocalDateTime.of(booking.getCheckOutDate(), time);
    }

    private String formatTime(LocalTime time) {
        if (time == null) {
            return "00:00";
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
