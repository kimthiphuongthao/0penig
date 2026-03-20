---
title: Redis persistence for SSO session blacklist
tags:
  - redis
  - sso
  - openig
  - session-blacklist
  - persistence
date: 2026-03-15
status: proposed
---

# Redis persistence cho SSO session blacklist

## Context
- Stack hiện tại dùng Redis để lưu key `blacklist:{sid}` với TTL 3600s từ `BackchannelLogoutHandler.groovy`.
- `SessionBlacklistFilter.groovy` check Redis ở mỗi request để force re-login nếu sid đã logout.
- Redis trong các stack A/B/C đang chạy `redis:7-alpine` mặc định, chưa mount volume cho data persistence.

Liên quan: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

## Vấn đề cần giải quyết
- Nếu Redis restart/crash và không có persistence, toàn bộ blacklist key biến mất ngay.
- Hệ quả: user đã logout vẫn có thể dùng session cũ đến khi session app hết hạn hoặc bị clear thủ công.
- Với use case logout revocation, mất dữ liệu blacklist là lỗ hổng behavior/security quan trọng hơn việc mất cache thông thường.

> [!warning] Gotcha
> Không persistence = logout đã xác nhận có thể bị "quên" sau khi Redis restart.

## So sánh nhanh: `appendonly yes` vs RDB snapshots

### AOF (`appendonly yes`)
- Ghi log mỗi lệnh ghi (`SET ... EX 3600`) vào AOF.
- Độ bền cao hơn RDB snapshot vì không phụ thuộc mốc snapshot xa.
- Với `appendfsync everysec`: có thể mất tối đa ~1 giây dữ liệu khi crash đột ngột.
- Phù hợp với blacklist vì write nhỏ, đều, và yêu cầu giữ revocation gần realtime.

### RDB snapshot (`save ...`)
- Chụp ảnh dữ liệu theo chu kỳ.
- Nếu crash giữa 2 lần snapshot, mất toàn bộ thay đổi kể từ snapshot gần nhất.
- Với blacklist TTL ngắn, window mất dữ liệu này có thể làm revoke mất hiệu lực trong vài phút.
- Hợp cho backup/restore coarse-grained hơn là durability chính cho revoke.

## Khuyến nghị
> [!success] Khuyến nghị chính
> Dùng `appendonly yes` + `appendfsync everysec` làm durability chính cho blacklist; giữ thêm RDB snapshot thưa để có điểm backup.

- Mục tiêu chính: không mất revoke state khi restart thường xuyên trong lab/dev.
- Overhead thêm là chấp nhận được với scale hiện tại (<1000 session concurrent).
- Không cần Redis Cluster/Sentinel cho nhu cầu hiện tại; ưu tiên đơn giản vận hành trước.

## Cấu hình đề xuất (Docker Compose)

```yaml
redis-a:
  image: redis:7-alpine
  container_name: sso-redis-a
  command:
    - redis-server
    - --appendonly
    - "yes"
    - --appendfsync
    - everysec
    - --save
    - "900 1 300 10 60 10000"
  volumes:
    - ./redis/data:/data
  networks:
    - backend
  restart: unless-stopped
```

Áp dụng tương tự cho `redis-b`, `redis-c`.

## Vận hành/kiểm tra
- Verify persistence:
  - `redis-cli CONFIG GET appendonly` trả về `yes`
  - `redis-cli INFO persistence` kiểm tra `aof_enabled:1`
- Test crash/restart:
  - ghi key blacklist với TTL
  - restart container Redis
  - xác nhận key còn tồn tại sau restart (nếu TTL chưa hết)

> [!tip]
> Mount volume host (`./redis/data:/data`) là bắt buộc, nếu không AOF/RDB vẫn mất khi container bị recreate.

