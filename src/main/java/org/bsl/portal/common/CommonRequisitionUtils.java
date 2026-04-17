//package org.bsl.portal.common;
//
//import org.bsl.portal.model.DepartmentRequisitionMonthly;
//import org.bsl.portal.model.RequisitionMonthly;
//import org.bsl.portal.model.SupplierProduct;
//import org.bsl.portal.repository.RequisitionMonthlyRepository;
//import org.bsl.portal.repository.SupplierProductRepository;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.text.Normalizer;
//import java.time.*;
//import java.util.*;
//
//@Component
//public class CommonRequisitionUtils {
//
//    private static final String UPLOAD_DIR = "./uploads/";
//
//    private final RequisitionMonthlyRepository requisitionMonthlyRepository;
//
//    private final SupplierProductRepository supplierProductRepository;
//
//    public CommonRequisitionUtils(RequisitionMonthlyRepository requisitionMonthlyRepository, SupplierProductRepository supplierProductRepository) {
//        this.requisitionMonthlyRepository = requisitionMonthlyRepository;
//        this.supplierProductRepository = supplierProductRepository;
//    }
//
//    public static String normText(String s) {
//        if (s == null) return null;
//        String t = s.trim();
//        return t.isEmpty() ? null : t;
//    }
//
//    public static String safeTrim(String s) {
//        return normText(s);
//    }
//
//    /** ✅ normCode: null/empty => null, "NEW" ignoreCase => null */
//    public static String normCode(String s) {
//        String t = normText(s);
//        if (t == null) return null;
//        return "new".equalsIgnoreCase(t) ? null : t;
//    }
//
//    /** "new"/"New"/"NEW" -> "NEW", null/blank -> null, else keep */
//    public static String normalizeNewKeepNew(String s) {
//        String t = normText(s);
//        if (t == null) return null;
//        return "new".equalsIgnoreCase(t) ? "NEW" : t;
//    }
//
//    /** usable = not null, not blank, not "NEW" (case-insensitive) */
//    public static boolean isUsableKey(String s) {
//        String t = normText(s);
//        if (t == null) return false;
//        return !"new".equalsIgnoreCase(t);
//    }
//
//    /**
//     * ✅ Build unique key for upload/check duplicate:
//     * UNIT + (SAP usable -> HANA usable -> VN -> EN)
//     *
//     * - unit: required (null/blank => null)
//     * - SAP/HANA: ignore "NEW" (case-insensitive) and blank
//     * - VN/EN: use trimmed lower-case
//     *
//     * return null if cannot build (unit missing or all keys empty/NEW)
//     */
//    public static String buildRowKeyWithUnitByPriority(
//            String unit,
//            String sapCodeNormOrRaw,
//            String hanaCodeNormOrRaw,
//            String descVN,
//            String descEN
//    ) {
//        String u = normText(unit);
//        if (u == null) return null;
//
//        // NOTE: in upload you store "NEW" sometimes.
//        // For key building, "NEW" is treated as NOT usable.
//        String sap = normText(sapCodeNormOrRaw);
//        String hana = normText(hanaCodeNormOrRaw);
//
//        if (isUsableKey(sap)) {
//            return "UNIT|" + u.toLowerCase() + "|SAP|" + sap.toUpperCase();
//        }
//        if (isUsableKey(hana)) {
//            return "UNIT|" + u.toLowerCase() + "|HANA|" + hana.toUpperCase();
//        }
//
//        String vn = normText(descVN);
//        if (vn != null) {
//            return "UNIT|" + u.toLowerCase() + "|VN|" + vn.toLowerCase();
//        }
//
//        String en = normText(descEN);
//        if (en != null) {
//            return "UNIT|" + u.toLowerCase() + "|EN|" + en.toLowerCase();
//        }
//
//        return null;
//    }
//
//    /**
//     * ✅ NEW: Build key include DEPT NAME (so duplicate check is per-department)
//     * deptName null/blank => "NONE"
//     */
//    public static String buildRowKeyWithUnitByPriority(
//            String unit,
//            String sapCodeNormOrRaw,
//            String hanaCodeNormOrRaw,
//            String descVN,
//            String descEN,
//            String deptName
//    ) {
//        String base = buildRowKeyWithUnitByPriority(unit, sapCodeNormOrRaw, hanaCodeNormOrRaw, descVN, descEN);
//        if (base == null) return null;
//
//        String d = normText(deptName);
//        String deptPart = (d == null) ? "NONE" : d.toLowerCase();
//
//        return base + "|DEPT|" + deptPart;
//    }
//
//    /** Build key from DB record (RequisitionMonthly) using same rule */
//    public static String buildExistingKeyFromDb(RequisitionMonthly rm) {
//        if (rm == null) return null;
//
//        String unit = rm.getUnit();
//
//        // DB might have stored "NEW" or real codes; treat "NEW" as not usable in key
//        String sap = rm.getOldSAPCode();
//        String hana = rm.getHanaSAPCode();
//
//        String vn = rm.getItemDescriptionVN();
//        String en = rm.getItemDescriptionEN();
//
//        // ✅ NEW: deptName (use NAME as you requested)
//        String deptName = null;
//        List<DepartmentRequisitionMonthly> drs = rm.getDepartmentRequisitions();
//        if (drs != null && !drs.isEmpty()) {
//            DepartmentRequisitionMonthly dr0 = drs.get(0);
//            if (dr0 != null) deptName = dr0.getName();
//        }
//
//        return buildRowKeyWithUnitByPriority(unit, sap, hana, vn, en, deptName);
//    }
//
//    /**
//     * ✅ Check tồn tại theo ưu tiên:
//     * SAP (usable) -> HANA (usable) -> DesVN (usable) -> DesEN (usable)
//     * Unique = groupId + unit + chosenKey
//     *
//     * currentId: exclude chính nó khi update
//     */
//    public Optional<RequisitionMonthly> checkExistsInGroupWithUnitByPriority(
//            String groupId,
//            String unit,
//            String sapCode,
//            String hanaCode,
//            String descVN,
//            String descEN,
//            String currentId
//    ) {
//        if (groupId == null || groupId.isBlank()) {
//            throw new IllegalArgumentException("groupId is required");
//        }
//        if (unit == null || unit.isBlank()) {
//            throw new IllegalArgumentException("unit is required");
//        }
//
//        String g = groupId.trim();
//        String u = unit.trim();
//
//        Optional<RequisitionMonthly> found;
//
//        if (isUsableKey(sapCode)) {
//            found = requisitionMonthlyRepository
//                    .findFirstByGroupIdAndUnitIgnoreCaseAndOldSAPCodeIgnoreCase(g, u, sapCode.trim());
//        } else if (isUsableKey(hanaCode)) {
//            found = requisitionMonthlyRepository
//                    .findFirstByGroupIdAndUnitIgnoreCaseAndHanaSAPCodeIgnoreCase(g, u, hanaCode.trim());
//        } else if (isUsableKey(descVN)) {
//            found = requisitionMonthlyRepository
//                    .findFirstByGroupIdAndUnitIgnoreCaseAndItemDescriptionVNIgnoreCase(g, u, descVN.trim());
//        } else if (isUsableKey(descEN)) {
//            found = requisitionMonthlyRepository
//                    .findFirstByGroupIdAndUnitIgnoreCaseAndItemDescriptionENIgnoreCase(g, u, descEN.trim());
//        } else {
//            return Optional.empty();
//        }
//
//        if (found.isPresent() && currentId != null && found.get().getId().equals(currentId)) {
//            return Optional.empty();
//        }
//
//        return found;
//    }
//
//    public String getImageExtension(String mimeType) {
//        return switch (mimeType) {
//            case "image/jpeg", "image/jpg" -> ".jpg";
//            case "image/png" -> ".png";
//            case "image/gif" -> ".gif";
//            case "image/bmp" -> ".bmp";
//            case "image/webp" -> ".webp";
//            default -> ".png";
//        };
//    }
//
//    public String saveImage(byte[] imageBytes, String fileName, String mimeType) throws IOException {
//        if (imageBytes == null || imageBytes.length == 0) return null;
//
//        if (!isAllowedImageMime(mimeType)) {
//            System.err.println("Skipped unsupported format: " + mimeType);
//            return null;
//        }
//
//        Path path = Paths.get(UPLOAD_DIR).resolve(fileName);
//        Files.createDirectories(path.getParent());
//        Files.write(path, imageBytes);
//
//        return "/uploads/" + fileName;
//    }
//
//    private String saveImage(MultipartFile file) throws IOException {
//        if (file == null || file.isEmpty()) {
//            return null;
//        }
//
//        String contentType = file.getContentType();
//        if (!Arrays.asList("image/jpeg", "image/png", "image/gif").contains(contentType)) {
//            throw new IOException("Only JPEG, PNG, and GIF files are allowed");
//        }
//
//        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
//        Path path = Paths.get(UPLOAD_DIR + fileName);
//
//        Files.createDirectories(path.getParent());
//
//        file.transferTo(path);
//
//        return "/uploads/" + fileName;
//    }
//
//    private boolean isAllowedImageMime(String mimeType) {
//        if (mimeType == null) return false;
//        Set<String> allowed = Set.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", "image/webp");
//        return allowed.contains(mimeType);
//    }
//
//    /* =========================
//      ✅ NEW: chỉ thêm fun để gộp phòng ban vào request
//      - không đụng logic khác
//   ========================= */
//    public void mergeDeptIntoRequest(
//            RequisitionMonthly req,
//            String deptId,
//            String deptNameFromDb,
//            BigDecimal requestQty,
//            BigDecimal buy
//    ) {
//        if (req == null) return;
//        if (deptId == null || deptId.trim().isEmpty()) return;
//
//        List<DepartmentRequisitionMonthly> list = req.getDepartmentRequisitions();
//        if (list == null) {
//            list = new ArrayList<>();
//            req.setDepartmentRequisitions(list);
//        }
//
//        // bình thường không xảy ra vì deptKey đã chặn duplicate,
//        // nhưng thêm safeguard: nếu có dept rồi thì cộng dồn
//        for (DepartmentRequisitionMonthly dr : list) {
//            if (dr != null && deptId.equals(dr.getId())) {
//                dr.setQty(nz(dr.getQty()).add(requestQty));
//                dr.setBuy(nz(dr.getBuy()).add(buy));
//                return;
//            }
//        }
//
//        DepartmentRequisitionMonthly dr = new DepartmentRequisitionMonthly();
//        dr.setId(deptId);
//        dr.setName(deptNameFromDb);
//        dr.setQty(requestQty);
//        dr.setBuy(buy);
//        list.add(dr);
//    }
//
//    public BigDecimal nz(BigDecimal v) {
//        return v == null ? BigDecimal.ZERO : v;
//    }
//
//    public RequisitionMonthly upsertMergedRequisition(
//            Map<String, RequisitionMonthly> mergedByItemKey,
//            String itemKey,
//            String groupId,
//
//            // identity fields
//            String oldSap,
//            String hanaCode,
//            String itemVN,
//            String itemEN,
//            String unit,
//
//            // numbers
//            BigDecimal requestQty,
//            BigDecimal buy,                 // bạn tự tính theo rule của API (vd buy = dailyMedInventory)
//            BigDecimal dailyMedInventory,    // giá trị M hoặc inhand tuỳ API
//
//            // dept
//            String deptId,
//            String deptNameFromDb,
//
//            // others
//            String reason,
//            List<String> imageUrls,
//
//            // optional extras
//            String currencyFromGroup // null nếu API không set currency
//    ) {
//        RequisitionMonthly req = mergedByItemKey.get(itemKey);
//
//        if (req == null) {
//            req = new RequisitionMonthly();
//            req.setGroupId(groupId);
//            req.setCreatedDate(LocalDateTime.now());
//            req.setUpdatedDate(LocalDateTime.now());
//
//            req.setOldSAPCode(oldSap);
//            req.setHanaSAPCode(hanaCode);
//            req.setItemDescriptionVN(itemVN);
//            req.setItemDescriptionEN(itemEN);
//
//            req.setUnit(unit);
//
//            // totals (init)
//            req.setTotalRequestQty(nz(requestQty));
//            req.setOrderQty(nz(buy));
//            req.setDailyMedInventory(nz(dailyMedInventory));
//
//            // currency optional
//            if (currencyFromGroup != null && !currencyFromGroup.trim().isEmpty()) {
//                req.setCurrency(currencyFromGroup.trim());
//            }
//
//            // reason
//            if (reason != null && !reason.trim().isEmpty()) {
//                req.setReason(reason);
//            }
//
//            // images
//            req.setImageUrls((imageUrls == null || imageUrls.isEmpty()) ? null : new ArrayList<>(imageUrls));
//
//            // dept
//            if (deptId != null) {
//                DepartmentRequisitionMonthly dr = new DepartmentRequisitionMonthly();
//                dr.setId(deptId);
//                dr.setName(deptNameFromDb);
//                dr.setQty(nz(requestQty));
//                dr.setBuy(nz(buy));
//                req.setDepartmentRequisitions(new ArrayList<>(List.of(dr)));
//            }
//
//            mergedByItemKey.put(itemKey, req);
//            return req;
//        }
//
//        // ===== MERGE EXISTING =====
//        mergeDeptIntoRequest(req, deptId, deptNameFromDb, requestQty, buy);
//
//        req.setTotalRequestQty(nz(req.getTotalRequestQty()).add(nz(requestQty)));
//        req.setOrderQty(nz(req.getOrderQty()).add(nz(buy)));
//        req.setDailyMedInventory(nz(req.getDailyMedInventory()).add(nz(dailyMedInventory)));
//
//        // images append
//        if (imageUrls != null && !imageUrls.isEmpty()) {
//            List<String> existing = req.getImageUrls();
//            if (existing == null) existing = new ArrayList<>();
//            existing.addAll(imageUrls);
//            req.setImageUrls(existing);
//        }
//
//        // reason: chỉ set nếu đang trống
//        if ((req.getReason() == null || req.getReason().trim().isEmpty())
//                && reason != null && !reason.trim().isEmpty()) {
//            req.setReason(reason);
//        }
//
//        // currency: nếu req chưa có currency mà có currencyFromGroup
//        if ((req.getCurrency() == null || req.getCurrency().trim().isEmpty())
//                && currencyFromGroup != null && !currencyFromGroup.trim().isEmpty()) {
//            req.setCurrency(currencyFromGroup.trim());
//        }
//
//        req.setUpdatedDate(LocalDateTime.now());
//        return req;
//    }
//
//    public List<SupplierProduct> searchSupplierProductsByPriority(
//            String sapCode,
//            String hanaCode,
//            String vnName,
//            String enName,
//            String unit,
//            String currency,
//            String selectedSupplierId // ✅ supplierId đã chọn trong requisition (có thể null)
//    ) {
//        String cur = normText(currency);
//        String reqUnit = normText(unit);
//
//        // ✅ guard
//        if (cur == null || cur.isBlank()) return Collections.emptyList();
//        if (reqUnit == null || reqUnit.isBlank()) return Collections.emptyList();
//
//        // ✅ normalize input
//        String sap = normCode(sapCode);
//        String hana = normCode(hanaCode);
//        String vn = normText(vnName);
//        String en = normText(enName);
//
//        List<SupplierProduct> found = Collections.emptyList();
//
//        // ✅ Priority search: SAP -> HANA -> VN -> EN
//        if (sap != null) {
//            found = supplierProductRepository
//                    .findBySapCodeIgnoreCaseAndUnitIgnoreCaseAndCurrencyIgnoreCase(sap, reqUnit, cur);
//        } else if (hana != null) {
//            found = supplierProductRepository
//                    .findByHanaSapCodeIgnoreCaseAndUnitIgnoreCaseAndCurrencyIgnoreCase(hana, reqUnit, cur);
//        } else if (vn != null) {
//            found = supplierProductRepository
//                    .findByItemDescriptionVNContainingIgnoreCaseAndUnitIgnoreCaseAndCurrencyIgnoreCase(vn, reqUnit, cur);
//        } else if (en != null) {
//            found = supplierProductRepository
//                    .findByItemDescriptionENContainingIgnoreCaseAndUnitIgnoreCaseAndCurrencyIgnoreCase(en, reqUnit, cur);
//        }
//
//    /*
//     ✅ Rule output:
//     - Mỗi công ty chỉ giữ record createdAt mới nhất
//     - Nếu selectedSupplierId thuộc công ty đó và KHÁC newest => giữ thêm selected
//     - Nếu selected == newest => không add thêm
//    */
//        return dedupeLatestByCompanyPreferSelected(found, selectedSupplierId);
//    }
//
//    public List<SupplierProduct> dedupeLatestByCompanyPreferSelected(
//            List<SupplierProduct> suppliers,
//            String selectedSupplierId
//    ) {
//        if (suppliers == null || suppliers.isEmpty()) return Collections.emptyList();
//
//        String selectedId = normText(selectedSupplierId);
//
//        // ✅ group theo company name (normalize mạnh)
//        Map<String, List<SupplierProduct>> byCompany = new LinkedHashMap<>();
//        for (SupplierProduct sp : suppliers) {
//            if (sp == null) continue;
//
//            String key = normalizeCompanyNameStrong(sp.getSupplierName());
//            if (key == null) key = "";
//            byCompany.computeIfAbsent(key, k -> new ArrayList<>()).add(sp);
//        }
//
//        List<SupplierProduct> result = new ArrayList<>();
//
//        for (List<SupplierProduct> companyList : byCompany.values()) {
//            if (companyList == null || companyList.isEmpty()) continue;
//
//            // 1) ✅ newest record của công ty (createdAt mới nhất)
//            SupplierProduct newest = null;
//            Instant newestTime = null;
//
//            for (SupplierProduct sp : companyList) {
//                if (sp == null) continue;
//
//                Instant t = toInstantSafe(sp.getCreatedAt());
//                if (newest == null) {
//                    newest = sp;
//                    newestTime = t;
//                    continue;
//                }
//
//                // ưu tiên có createdAt, và lấy createdAt mới hơn
//                if (newestTime == null && t != null) {
//                    newest = sp;
//                    newestTime = t;
//                } else if (newestTime != null && t != null && t.isAfter(newestTime)) {
//                    newest = sp;
//                    newestTime = t;
//                }
//            }
//
//            if (newest != null) {
//                result.add(newest); // ✅ luôn giữ newest
//            }
//
//            // 2) ✅ nếu có selectedSupplierId trong cùng công ty:
//            //    - nếu selected KHÁC newest => giữ thêm selected
//            //    - nếu selected TRÙNG newest => khỏi add
//            if (selectedId != null) {
//                SupplierProduct selected = null;
//
//                for (SupplierProduct sp : companyList) {
//                    if (sp == null) continue;
//                    if (selectedId.equals(normText(sp.getId()))) {
//                        selected = sp;
//                        break;
//                    }
//                }
//
//                if (selected != null) {
//                    boolean selectedIsNewest = newest != null
//                            && Objects.equals(normText(newest.getId()), normText(selected.getId()));
//
//                    if (!selectedIsNewest) {
//                        result.add(selected);
//                    }
//                }
//            }
//        }
//
//        // 3) ✅ fallback: đảm bảo list trả về có selectedSupplierId nếu nó tồn tại trong input list
//        // (phòng trường hợp normalize company key lệch làm selected không rơi vào group đúng)
//        if (selectedId != null) {
//            boolean hasSelected = result.stream()
//                    .filter(Objects::nonNull)
//                    .anyMatch(x -> selectedId.equals(normText(x.getId())));
//
//            if (!hasSelected) {
//                SupplierProduct selectedObj = suppliers.stream()
//                        .filter(Objects::nonNull)
//                        .filter(x -> selectedId.equals(normText(x.getId())))
//                        .findFirst()
//                        .orElse(null);
//
//                if (selectedObj != null) {
//                    result.add(selectedObj);
//                }
//            }
//        }
//
//        // 4) (Optional) ✅ sort để output giống ví dụ: createdAt DESC (mới nhất lên trước)
//        result.sort((a, b) -> {
//            Instant ta = toInstantSafe(a != null ? a.getCreatedAt() : null);
//            Instant tb = toInstantSafe(b != null ? b.getCreatedAt() : null);
//
//            if (ta == null && tb == null) return 0;
//            if (ta == null) return 1;
//            if (tb == null) return -1;
//            return tb.compareTo(ta); // DESC
//        });
//
//        return result;
//    }
//
//    public SupplierProduct pickBestSupplierProductByLatestPerCompanyThenMinPrice(List<SupplierProduct> suppliers) {
//        if (suppliers == null || suppliers.isEmpty()) return null;
//
//        // 1) Filter: bỏ null + bỏ price null
//        List<SupplierProduct> valid = new ArrayList<>();
//        for (SupplierProduct sp : suppliers) {
//            if (sp == null) continue;
//            if (sp.getPrice() == null) continue;
//            valid.add(sp);
//        }
//        if (valid.isEmpty()) return null;
//
//        // 2) DEDUPE theo tên công ty: giữ record createdAt mới nhất
//        Map<String, SupplierProduct> latestByCompany = new LinkedHashMap<>();
//
//        for (SupplierProduct sp : valid) {
//            String key = normalizeCompanyNameStrong(sp.getSupplierName());
//
//            SupplierProduct existing = latestByCompany.get(key);
//            if (existing == null) {
//                latestByCompany.put(key, sp);
//                continue;
//            }
//
//            Instant eTime = toInstantSafe(existing.getCreatedAt());
//            Instant cTime = toInstantSafe(sp.getCreatedAt());
//
//            SupplierProduct keep;
//            if (eTime == null && cTime == null) {
//                keep = existing;
//            } else if (eTime == null) {
//                keep = sp;
//            } else if (cTime == null) {
//                keep = existing;
//            } else {
//                keep = cTime.isAfter(eTime) ? sp : existing;
//            }
//
//            latestByCompany.put(key, keep);
//
//            // DEBUG dễ nhìn:
//            // System.out.println("[DEDUP] " + key
//            //         + " | existing price=" + existing.getPrice() + " createdAt=" + existing.getCreatedAt()
//            //         + " | new price=" + sp.getPrice() + " createdAt=" + sp.getCreatedAt()
//            //         + " => KEEP price=" + keep.getPrice() + " createdAt=" + keep.getCreatedAt());
//        }
//
//        // 3) So giá giữa các công ty (đã dedupe)
//        SupplierProduct best = null;
//
//        for (SupplierProduct sp : latestByCompany.values()) {
//            if (best == null) {
//                best = sp;
//                continue;
//            }
//
//            int cmpPrice = sp.getPrice().compareTo(best.getPrice());
//            if (cmpPrice < 0) {
//                best = sp;
//            } else if (cmpPrice == 0) {
//                Instant bTime = toInstantSafe(best.getCreatedAt());
//                Instant cTime = toInstantSafe(sp.getCreatedAt());
//
//                // tie giá => lấy createdAt mới nhất
//                if (bTime == null && cTime == null) {
//                    // giữ best
//                } else if (bTime == null) {
//                    best = sp;
//                } else if (cTime == null) {
//                    // giữ best
//                } else if (cTime.isAfter(bTime)) {
//                    best = sp;
//                }
//            }
//        }
//
//        return best;
//    }
//
//    /**
//     * Normalize tên công ty mạnh hơn:
//     * - xử lý NBSP
//     * - normalize unicode
//     * - gộp whitespace
//     * - lowercase
//     */
//    public String normalizeCompanyNameStrong(String supplierName) {
//        if (supplierName == null) return "";
//        String s = supplierName;
//
//        // NBSP -> space
//        s = s.replace('\u00A0', ' ');
//
//        // Unicode normalize
//        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
//
//        // trim + collapse spaces
//        s = s.trim().replaceAll("\\s+", " ");
//
//        return s.toLowerCase();
//    }
//
//    /**
//     * Convert createdAt về Instant an toàn.
//     * Bạn hãy chỉnh type theo thực tế getCreatedAt() của bạn:
//     * - Nếu getCreatedAt() là Instant => return luôn
//     * - Nếu là Date => date.toInstant()
//     * - Nếu là OffsetDateTime/ZonedDateTime => toInstant()
//     * - Nếu là LocalDateTime => assume UTC (hoặc zone của bạn)
//     */
//    public Instant toInstantSafe(Object createdAt) {
//        if (createdAt == null) return null;
//
//        if (createdAt instanceof Instant i) return i;
//        if (createdAt instanceof Date d) return d.toInstant();
//        if (createdAt instanceof OffsetDateTime odt) return odt.toInstant();
//        if (createdAt instanceof ZonedDateTime zdt) return zdt.toInstant();
//        if (createdAt instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
//
//        // Nếu lỡ createdAt là String ISO
//        if (createdAt instanceof String s) {
//            try {
//                return OffsetDateTime.parse(s).toInstant();
//            } catch (Exception ignored) {
//                // try LocalDateTime parse nếu không có offset
//                try {
//                    return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
//                } catch (Exception ignored2) {
//                    return null;
//                }
//            }
//        }
//
//        // Không biết type => chịu
//        return null;
//    }
//
//    @Transactional
//    public List<RequisitionMonthly> markCompletedAndAutoSelectBestSupplier(
//            List<String> reqIds,
//            String email,
//            String currency
//    ) {
//        if (reqIds == null || reqIds.isEmpty()) return Collections.emptyList();
//        if (email == null || email.trim().isEmpty()) {
//            throw new IllegalArgumentException("email is required and cannot be empty");
//        }
//
//        String emailNorm = email.trim().toLowerCase();
//        LocalDateTime now = LocalDateTime.now();
//
//        // ✅ fetch 1 lần
//        List<RequisitionMonthly> requisitions = requisitionMonthlyRepository.findAllById(reqIds);
//        if (requisitions.isEmpty()) return Collections.emptyList();
//
//        // ✅ currency fallback
//        if (currency == null || currency.isBlank()) {
//            currency = requisitions.stream()
//                    .map(RequisitionMonthly::getCurrency)
//                    .filter(c -> c != null && !c.isBlank())
//                    .findFirst()
//                    .orElse("VND");
//        }
//
//        // ✅ 1 vòng duy nhất trên requisitions:
//        // - FAIL FAST thiếu supplierId
//        // - set completed fields
//        // - autoSelect supplierComparisonList + statusBestPrice
//        for (RequisitionMonthly rm : requisitions) {
//            if (rm.getSupplierId() == null || rm.getSupplierId().trim().isEmpty()) {
//                String desVN = rm.getItemDescriptionVN();
//                String desEN = rm.getItemDescriptionEN();
//
//                String displayName =
//                        (desVN != null && !desVN.trim().isEmpty())
//                                ? desVN.trim()
//                                : ((desEN != null && !desEN.trim().isEmpty()) ? desEN.trim() : "(no description)");
//
//                String displayType =
//                        (desVN != null && !desVN.trim().isEmpty()) ? "Des VN" : "Des EN";
//
//                // bạn muốn trả về requisitionId + desVN/desEN như controller cũ thì ném custom exception cũng được
//                throw new IllegalArgumentException(
//                        displayType + " '" + displayName + "' does not have a supplier assigned, therefore it cannot be completed."
//                );
//            }
//
//            // ✅ set completed luôn trong fun (đỡ 1 loop ở controller)
//            rm.setIsCompleted(true);
//            rm.setCompletedDate(now);
//            rm.setUpdatedDate(now);
//
//            rm.setCompletedByEmail(emailNorm);
//            rm.setUpdatedByEmail(emailNorm);
//
//            rm.setUncompletedByEmail(null);
//
//            // ===== auto-select logic (giữ y như bạn đang có) =====
//            String oldSAPCode = rm.getOldSAPCode();
//            String sapCodeNewSAP = rm.getHanaSAPCode();
//            String itemDescriptionVN = rm.getItemDescriptionVN();
//            String itemDescriptionEN = rm.getItemDescriptionEN();
//            String unit = rm.getUnit();
//            String selectedSupplierId = rm.getSupplierId();
//
//            List<SupplierProduct> suppliers = searchSupplierProductsByPriority(
//                    oldSAPCode,
//                    sapCodeNewSAP,
//                    itemDescriptionVN,
//                    itemDescriptionEN,
//                    unit,
//                    currency,
//                    selectedSupplierId
//            );
//
//            if (suppliers == null || suppliers.isEmpty()) {
//                rm.setSupplierComparisonList(Collections.emptyList());
//                continue;
//            }
//
//            SupplierProduct bestSp = null;
//            for (SupplierProduct sp : suppliers) {
//                if (sp == null || sp.getPrice() == null) continue;
//
//                if (bestSp == null) {
//                    bestSp = sp;
//                    continue;
//                }
//
//                int cmp = sp.getPrice().compareTo(bestSp.getPrice());
//                if (cmp < 0) {
//                    bestSp = sp;
//                } else if (cmp == 0) {
//                    Instant bt = toInstantSafe(bestSp.getCreatedAt());
//                    Instant ct = toInstantSafe(sp.getCreatedAt());
//
//                    if (bt == null && ct != null) bestSp = sp;
//                    else if (bt != null && ct != null && ct.isAfter(bt)) bestSp = sp;
//                }
//            }
//
//            List<CompletedSupplierDTO> comparisonList = suppliers.stream()
//                    .filter(sp -> sp != null && sp.getPrice() != null)
//                    .map(sp -> new CompletedSupplierDTO(
//                            sp.getId(),
//                            sp.getSupplierName(),
//                            sp.getPrice(),
//                            sp.getUnit(),
//                            0,
//                            false
//                    ))
//                    .sorted(Comparator.comparing(CompletedSupplierDTO::getPrice))
//                    .toList();
//
//            if (comparisonList.isEmpty() || bestSp == null || bestSp.getPrice() == null) {
//                rm.setSupplierComparisonList(Collections.emptyList());
//                continue;
//            }
//
//            String bestId = bestSp.getId();
//            for (CompletedSupplierDTO s : comparisonList) {
//                if (s == null) continue;
//
//                String sid = s.getSupplierId();
//                if (sid != null && bestId != null && sid.equals(bestId)) {
//                    s.setIsSelected(1);
//                    s.setIsBestPrice(true);
//                    break;
//                }
//            }
//
//            String currentStatus = rm.getStatusBestPrice();
//            if (currentStatus == null || currentStatus.trim().isEmpty()) {
//                BigDecimal bestPrice = bestSp.getPrice();
//                boolean isCurrentlyBest = rm.getPrice() != null
//                        && bestPrice != null
//                        && rm.getPrice().compareTo(bestPrice) == 0;
//
//                rm.setStatusBestPrice(isCurrentlyBest ? "Yes" : "No");
//            }
//
//            rm.setSupplierComparisonList(comparisonList);
//        }
//
//        // ✅ save 1 lần
//        return requisitionMonthlyRepository.saveAll(requisitions);
//    }
//
//
//}
