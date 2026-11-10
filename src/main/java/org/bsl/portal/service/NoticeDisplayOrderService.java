package org.bsl.portal.service;

import org.bsl.portal.model.Notice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One ordered list of up to two approved pins, and a second ordered list of
 * approved normal notices. Status=PENDING/REJECTED never occupies a position.
 *
 * Older records are normalized on first use; titles/files/updatedAt are not
 * modified during migration or reordering. Writes are serialized in this JVM.
 */
@Service
public class NoticeDisplayOrderService {
    @Autowired
    private MongoTemplate mongoTemplate;

    private boolean initialized = false;

    private static boolean approved(Notice notice) {
        return notice != null && (notice.getStatus() == null
                || notice.getStatus().isBlank()
                || "APPROVED".equalsIgnoreCase(notice.getStatus()));
    }

    private static int positiveOrLast(Integer value) {
        return value != null && value > 0 ? value : Integer.MAX_VALUE;
    }

    private static Comparator<Notice> ordered(boolean pinned) {
        return Comparator.comparingInt((Notice n) -> positiveOrLast(pinned ? n.getPinnedOrder() : n.getDisplayOrder()))
                .thenComparing(Notice::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Notice::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private List<Notice> approvedNotices() {
        Criteria approved = new Criteria().orOperator(
                Criteria.where("status").is("APPROVED"),
                Criteria.where("status").exists(false),
                Criteria.where("status").is(null),
                Criteria.where("status").is("")
        );
        return mongoTemplate.find(new Query(approved), Notice.class);
    }

    private static boolean sameId(Notice a, Notice b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    private void setRanks(List<Notice> pins, List<Notice> normals) {
        for (int index = 0; index < pins.size(); index++) {
            persistRank(pins.get(index), true, index + 1, null);
        }
        for (int index = 0; index < normals.size(); index++) {
            persistRank(normals.get(index), false, null, index + 1);
        }
    }

    private void persistRank(Notice notice, boolean pin, Integer pinRank, Integer normalRank) {
        if (notice == null || notice.getId() == null) return;
        if (Objects.equals(notice.getPinned(), pin)
                && Objects.equals(notice.getPinnedOrder(), pinRank)
                && Objects.equals(notice.getDisplayOrder(), normalRank)) return;

        Update update = new Update()
                .set("pinned", pin)
                .set("pinnedOrder", pinRank)
                .set("displayOrder", normalRank);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(notice.getId())), update, Notice.class);
        notice.setPinned(pin);
        notice.setPinnedOrder(pinRank);
        notice.setDisplayOrder(normalRank);
    }

    /** Initialize legacy approved notices, keeping any existing manual ranks. */
    public synchronized void ensureInitialized() {
        if (initialized) return;
        List<Notice> all = approvedNotices();
        List<Notice> pins = new ArrayList<>();
        List<Notice> normals = new ArrayList<>();
        for (Notice n : all) {
            if (Boolean.TRUE.equals(n.getPinned())) pins.add(n);
            else normals.add(n);
        }
        pins.sort(ordered(true));
        normals.sort(ordered(false));
        // Excess legacy pins are moved to the top of Normal, preserving their order.
        if (pins.size() > 2) {
            List<Notice> overflow = new ArrayList<>(pins.subList(2, pins.size()));
            pins.subList(2, pins.size()).clear();
            normals.addAll(0, overflow);
        }
        setRanks(pins, normals);
        initialized = true;
    }

    public static class Result {
        private final Notice notice;
        private final List<String> demotedIds;
        public Result(Notice notice, List<String> demotedIds) {
            this.notice = notice;
            this.demotedIds = demotedIds;
        }
        public Notice getNotice() { return notice; }
        public List<String> getDemotedIds() { return demotedIds; }
    }

    /**
     * Call AFTER persisting an edited notice, and BEFORE returning its response.
     * Call ensureInitialized BEFORE the initial save (so a new pin isn't mistaken
     * for a legacy pin while migrating).
     */
    public synchronized Result reconcile(Notice previous, Notice current,
                                         Integer requestedOrder, boolean explicitMove) {
        ensureInitialized();
        List<Notice> pins = new ArrayList<>();
        List<Notice> normals = new ArrayList<>();
        for (Notice n : approvedNotices()) {
            if (sameId(n, current)) continue;
            if (Boolean.TRUE.equals(n.getPinned())) pins.add(n);
            else normals.add(n);
        }
        pins.sort(ordered(true));
        normals.sort(ordered(false));

        if (approved(current)) {
            if (Boolean.TRUE.equals(current.getPinned())) {
                boolean keepPosition = approved(previous) && Boolean.TRUE.equals(previous.getPinned())
                        && !explicitMove && Objects.equals(previous.getPinnedOrder(), current.getPinnedOrder());
                int desired = requestedOrder != null ? requestedOrder
                        : keepPosition ? positiveOrLast(previous.getPinnedOrder()) : positiveOrLast(current.getPinnedOrder());
                if (desired == Integer.MAX_VALUE) desired = 1;
                pins.add(Math.max(0, Math.min(desired - 1, pins.size())), current);
            } else {
                boolean keepPosition = approved(previous) && !Boolean.TRUE.equals(previous.getPinned()) && !explicitMove;
                int desired = requestedOrder != null ? requestedOrder
                        : keepPosition ? positiveOrLast(previous.getDisplayOrder()) : 1;
                if (desired == Integer.MAX_VALUE) desired = normals.size() + 1;
                normals.add(Math.max(0, Math.min(desired - 1, normals.size())), current);
            }
        } else {
            // Pending/Rejected notices are not part of either display list.
            current.setDisplayOrder(null);
            if (!Boolean.TRUE.equals(current.getPinned())) current.setPinnedOrder(null);
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(current.getId())),
                    new Update().set("displayOrder", null)
                            .set("pinnedOrder", current.getPinnedOrder()), Notice.class);
        }

        List<String> demoted = new ArrayList<>();
        while (pins.size() > 2) {
            Notice oldPin = pins.remove(2);
            normals.add(0, oldPin);
            demoted.add(oldPin.getId());
        }
        setRanks(pins, normals);
        // Keep response fields consistent with the persisted result.
        for (Notice p : pins) {
            if (sameId(p, current)) {
                current.setPinned(true);
                current.setPinnedOrder(p.getPinnedOrder());
                current.setDisplayOrder(null);
            }
        }
        for (Notice n : normals) {
            if (sameId(n, current)) {
                current.setPinned(false);
                current.setPinnedOrder(null);
                current.setDisplayOrder(n.getDisplayOrder());
            }
        }
        return new Result(current, demoted);
    }

    public synchronized void afterDelete() {
        ensureInitialized();
        List<Notice> pins = new ArrayList<>();
        List<Notice> normals = new ArrayList<>();
        for (Notice n : approvedNotices()) {
            if (Boolean.TRUE.equals(n.getPinned())) pins.add(n);
            else normals.add(n);
        }
        pins.sort(ordered(true));
        normals.sort(ordered(false));
        setRanks(pins, normals);
    }
}
