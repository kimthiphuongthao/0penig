# Log Format Standard — Gateway Node

## Vấn đề trước khi chuẩn hóa

Tomcat AccessLogValve mặc định dùng pattern `common`:
```
%h %l %u %t "%r" %s %b
```

Thiếu:
- Real client IP (chỉ thấy IP của nginx/LB, không thấy IP browser)
- Request duration (không biết request chậm bao lâu)
- Node identifier (không biết node nào xử lý request trong HA setup)

---

## Format chuẩn đã áp dụng

```
%{X-Forwarded-For}i %h %t "%r" %s %b %Dms node=${OPENIG_NODE_NAME}
```

### Giải thích từng field

| Field | Ý nghĩa | Ví dụ |
|-------|---------|-------|
| `%{X-Forwarded-For}i` | Real client IP (do nginx forward) | `192.168.1.10` |
| `%h` | IP của proxy/nginx gửi request đến Tomcat | `172.18.0.5` |
| `%t` | Timestamp | `[05/Mar/2026:10:23:45 +0000]` |
| `"%r"` | Request line | `"GET /app1/ HTTP/1.1"` |
| `%s` | HTTP status code | `200` |
| `%b` | Bytes sent | `4821` |
| `%Dms` | Request duration (milliseconds) | `142ms` |
| `node=${OPENIG_NODE_NAME}` | Node identifier từ env var | `node=openig-1` |

### Ví dụ log line

```
192.168.1.10 172.18.0.5 [05/Mar/2026:10:23:45 +0000] "GET /app1/ HTTP/1.1" 200 4821 142ms node=openig-1
```

---

## Cấu hình trong server.xml

```xml
<Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
       prefix="localhost_access_log" suffix=".txt"
       pattern="%{X-Forwarded-For}i %h %t &quot;%r&quot; %s %b %Dms node=${OPENIG_NODE_NAME}" />
```

Áp dụng cho cả 2 đơn vị: `stack-a/docker/openig/server.xml` và `stack-b/docker/openig/server.xml`.

---

## Yêu cầu đi kèm

**`OPENIG_NODE_NAME`** phải được set trong `docker-compose.yml` cho từng node:

```yaml
environment:
  - OPENIG_NODE_NAME=openig-1   # node 1
  - OPENIG_NODE_NAME=openig-2   # node 2
```

**Nginx phải forward `X-Forwarded-For`**:
```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
```

Đã cấu hình sẵn trong cả 2 nginx.conf.

---

## Mapping sang môi trường DEV (F5)

Khi F5 thay thế Nginx làm LB, header `X-Forwarded-For` vẫn được F5 forward theo mặc định nếu bật **X-Forwarded-For insertion** trong HTTP profile. Không cần thay đổi format log.

Node identifier: thay `OPENIG_NODE_NAME` bằng hostname thực hoặc IP của từng node OpenIG trong môi trường DEV.
