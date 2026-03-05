# Health Check Baseline — Gateway Node

## Tham số chuẩn (Nginx passive health check)

| Tham số | Giá trị | Ý nghĩa |
|---------|---------|---------|
| `max_fails` | 3 | Số lần thất bại liên tiếp trước khi đánh dấu node down |
| `fail_timeout` | 10s | Khoảng thời gian đếm failures + thời gian node bị đánh dấu down |

```nginx
upstream openig_pool {
    server openig-1:8080 max_fails=3 fail_timeout=10s;
    server openig-2:8080 max_fails=3 fail_timeout=10s;
}
```

Nginx OSS chỉ hỗ trợ **passive health check** — node bị đánh dấu down khi có request thực tế thất bại, không chủ động probe.

---

## Mapping sang F5 BIG-IP

| Nginx parameter | F5 equivalent | Giá trị mapping |
|-----------------|---------------|-----------------|
| `max_fails=3` | `fall` | 3 |
| `fail_timeout=10s` | `interval` + `timeout` | interval=5s, timeout=10s |
| _(không có)_ | `rise` | 2 (số lần probe thành công để đưa node trở lại) |
| _(passive only)_ | Monitor type | `http` active monitor (F5 chủ động probe) |

### F5 HTTP Monitor config tương đương

```
Monitor: openig-http-monitor
  Type:          HTTP
  Interval:      5s       ← tần suất probe
  Timeout:       10s      ← tương đương fail_timeout
  Send:          GET /openig/api/info HTTP/1.1\r\nHost: localhost\r\n\r\n
  Receive:       "version"
  Fall:          3        ← tương đương max_fails
  Rise:          2        ← số lần pass để đưa lại vào pool
```

Endpoint probe: `GET /openig/api/info` — OpenIG expose sẵn, trả JSON có field `version`. Dùng làm health signal.

---

## Lưu ý khi mapping sang môi trường DEV

- F5 active monitor cho phép phát hiện node down **trước khi có traffic thực** — tốt hơn Nginx passive trong production
- Nếu OpenIG đằng sau TLS trên F5: dùng HTTPS monitor, cần import cert hoặc disable cert verify ở môi trường DEV
- `rise=2` đủ để tránh flapping; tăng lên 3 nếu môi trường có latency cao
