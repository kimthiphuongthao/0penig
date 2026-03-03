# 📍 SSO Lab — Checkpoint Registry

Mỗi khi anh muốn quay lại, chỉ cần nói:  
**"rollback về thời điểm N"** — tôi sẽ xử lý hết.

---

## Danh sách Checkpoint

| # | Tag Git | Thời điểm | Mô tả trạng thái |
|---|---------|-----------|-----------------|
| **1** | `checkpoint/2026-02-27-working-state` | 2026-02-27 10:35 +07 | OpenIG HA working state — OIDC compliance check passed (0 critical, 5 warnings). Stack: Nginx → OpenIG-1/2 → WordPress ← Keycloak. CredentialInjector.groovy hoạt động. |
| **2** | `checkpoint/2` | 2026-02-27 16:40 +07 | Single Logout (SLO) và HA Sticky Session Test PASSED. Đã cập nhật OpenIG nodes trả về Header phân biệt. Hoàn thiện Document. |

---

## Lệnh rollback thủ công (nếu cần)

```bash
# Rollback về thời điểm 1
cd /Volumes/OS/claude/openig/sso-lab
git reset --hard checkpoint/2026-02-27-working-state

# Xem diff hiện tại so với thời điểm 1
git diff checkpoint/2026-02-27-working-state

# Xem log tất cả thay đổi
git log --oneline
```

---

## Quy ước thêm checkpoint mới

Mỗi lần anh muốn ghi nhận một thời điểm mới, nói:  
**"tạo checkpoint thời điểm N — [mô tả ngắn]"**

