# F5 BIG-IP Subpath Proxying cho Legacy Apps

## Bối cảnh

Trong triển khai thực tế với F5 BIG-IP (thay vì nginx), legacy app không hỗ trợ subpath natively cần được proxy qua subpath (ví dụ: `/app4/`). Câu hỏi đặt ra là F5 có xử lý được các vấn đề phát sinh như broken links hay path stripping không?

## Câu hỏi 1: F5 có hỗ trợ path stripping không?

Trả lời: **Có**, qua 2 cách chính:
- **iRules (Tcl):** Xử lý HTTP_REQUEST event, strip prefix URI trước khi gửi về backend.
- **Local Traffic Policies (GUI, v11.4+):** Định nghĩa điều kiện URI starts with + hành động Replace path.

**Ví dụ iRule:**
```tcl
when HTTP_REQUEST {
    if { [HTTP::uri] starts_with "/app4" } {
        HTTP::uri [string map {"/app4" ""} [HTTP::uri]]
    }
}
```

## Câu hỏi 2: Nginx path stripping bị broken links — F5 giải quyết được không?

**Vấn đề:** App generate internal absolute URL (href='/login') → sau khi path stripping ở proxy, browser resolve thành `/login` thay vì `/app4/login` → link chết.

Nginx đơn thuần không giải quyết được vấn đề này nếu không có module phức tạp. F5 giải quyết bằng:

### Stream Profile:
- Quét toàn bộ HTML/JS/CSS response theo realtime.
- Find-and-replace chuỗi: `href='/'` → `href='/app4/'`.
- Cấu hình trên Virtual Server → Profiles → Stream.

### Rewrite Profile (URI Translation):
- Dịch URI có hệ thống cả 2 chiều request/response.
- Tự động rewrite `Location` header trong HTTP redirect.
- Thiết kế chuyên cho bài toán giấu cấu trúc backend.

→ Đây là điểm F5 vượt trội so với nginx cho bài toán legacy app subpath.

## Câu hỏi 3: Các điều kiện bắt buộc để Stream/Rewrite Profile hoạt động

| Điều kiện | Lý do |
|-----------|-------|
| **SSL Offload trên F5** | F5 phải giải mã HTTPS thành plaintext mới đọc và sửa được nội dung (payload). |
| **Tắt Gzip phía backend** | F5 không rewrite được nội dung đã nén. Cần xóa `Accept-Encoding` header hoặc thực hiện decompression trên F5. |

## Câu hỏi 4: Gotchas và giới hạn

- **JavaScript generate URL động:** Stream Profile khó bắt hết các chuỗi phức tạp được nối trong JS → có thể cần iRules nâng cao hoặc APM Portal Access.
- **URL hardcode (IP/Domain):** Nếu app hardcode cả domain, Stream Profile phải quét và sửa cả domain/IP, cấu hình sẽ phức tạp hơn.
- **CPU Overhead:** Stream Profile tiêu tốn tài nguyên CPU hơn so với việc chỉ xử lý HTTP header.

## Câu hỏi 5: F5 APM Portal Access là gì trong bối cảnh này?

Nếu tổ chức đã có **F5 APM (Access Policy Manager)**, module **Portal Access** sẽ tự động hóa toàn bộ:
- Patch HTML/JS/CSS tự động (Reverse Proxy engine mạnh mẽ).
- Rewrite redirect và cookies.
- Thiết kế chuyên biệt cho việc tích hợp legacy app mà không cần sửa code app.

Đây là giải pháp enterprise tương đương với bộ OpenIG + nginx trong lab SSO này.

## Tóm tắt so sánh

| Tính năng | Nginx | F5 BIG-IP |
|------------|-------|-----------|
| **Path stripping** | Có (trailing slash logic) | Có (iRules / LTP) |
| **Fix broken links (href)** | Không (mặc định) | Có (Stream/Rewrite Profile) |
| **Fix JS dynamic URL** | Không | Một phần (iRules / APM) |
| **SSL bắt buộc** | Không | Có (để dùng Stream/Rewrite) |
| **Gzip backend** | OK | Phải tắt hoặc decompress trên F5 |

## Kết luận

Trong thực tế với F5 BIG-IP, vấn đề "app không hỗ trợ subpath" không còn là blocker kỹ thuật như với nginx. Tuy nhiên, quản trị viên cần đảm bảo **SSL Offload** và xử lý **Gzip** đúng cách để các tính năng rewrite nội dung hoạt động ổn định.
