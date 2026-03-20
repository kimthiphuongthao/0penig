# Bản tổng hợp audit lõi OpenIG 6 SSO/SLO

Báo cáo này tổng hợp bốn bản audit độc lập:
- `docs/audit/2026-03-20-openig-core-audit-codex.md`
- `docs/audit/2026-03-20-openig-core-audit-gemini.md`
- `docs/audit/2026-03-20-openig-core-audit-architect.md`
- `docs/audit/2026-03-20-openig-core-audit-architect-v2.md`

Lưu ý: nội dung task ghi là "15 claims", nhưng danh sách claim được cung cấp có 16 ID. Bản tổng hợp này bao phủ đầy đủ cả 16 claim đã liệt kê.

Phương pháp dùng để hợp nhất:
- Kết luận theo đa số khi có 3 hoặc 4 agent cùng một hướng đánh giá.
- Nếu không có đa số 3 agent, tiêu chí phân xử là thứ bậc bằng chứng bắt buộc: Architect v2 > Codex > Architect v1 > Gemini.
- `C2` được chuẩn hóa ở mức hành vi, vì các bản audit nguồn dùng cách diễn đạt đảo ngược nhau nhưng vẫn thống nhất về thứ tự thực thi thực tế.

## 1. Bảng tổng hợp theo từng claim

| Claim ID | Ưu tiên | Codex | Gemini | Architect v1 | Architect v2 | Kết luận cuối | Có xung đột? |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B1 | CRITICAL | CONFIRM | CONFIRM | CONFIRM | REFUTE (partial) | CONFIRM | YES |
| B2 | CRITICAL | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| B3 | CRITICAL | REFUTE | CONFIRM | INCONCLUSIVE | REFUTE | REFUTE | YES |
| B4 | CRITICAL | REFUTE | CONFIRM | CONFIRM | REFUTE | REFUTE | YES |
| C2 | CRITICAL | CONFIRM* | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A1 | HIGH | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A2 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A3 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A4 | MED | REFUTE | CONFIRM | CONFIRM | REFUTE (partial) | REFUTE | YES |
| A5 | HIGH | INCONCLUSIVE | CONFIRM | CONFIRM | CONFIRM | CONFIRM | YES |
| D1 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| D2 | HIGH | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| E1 | MED | REFUTE | CONFIRM | INCONCLUSIVE | REFUTE | REFUTE | YES |
| E2 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| F1 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| F2 | MED | REFUTE | CONFIRM | CONFIRM | REFUTE (partial) | REFUTE | YES |

\* `C2` được chuẩn hóa theo hành vi. Codex báo `REFUTE` chỉ vì nó đánh giá cách diễn đạt đảo ngược "after JWT cookie serialization"; còn vết thực thi mà nó lần theo vẫn thống nhất rằng các thay đổi trong `.then()` được persist vì việc lưu session xảy ra muộn hơn.

## 2. Chi tiết theo từng claim

### B1
Cả bốn bản audit đều thống nhất rằng mục session OAuth2 được lưu dưới tiền tố `oauth2:` gắn với URI `clientEndpoint` đã được resolve, nên cách tiếp cận hiện tại để phát hiện key về bản chất là đúng. Điểm khác biệt duy nhất nằm ở Architect v2, vốn phản bác một phần cách diễn đạt rút gọn vì `OAuth2Utils.sessionKey()` dùng `buildUri()` và cơ chế resolve URI thay vì nối chuỗi một cách ngây thơ. Codex và Architect v1 lần theo cùng code path nhưng vẫn chấp nhận mô tả viết tắt này vì key hiệu dụng cuối cùng vẫn resolve thành full URI như mong đợi. Kết luận cuối: CONFIRM, với bằng chứng mạnh nhất đến từ `OAuth2Utils.sessionKey()` cùng `OAuth2Utils.buildUri()` như Architect v2 và Codex đã trích dẫn.

### B2
Cả bốn bản audit đều thống nhất rằng `id_token` được persist nằm tại `session[oauth2Key].atr.id_token`. Ở đây không có bất đồng đáng kể nào, chỉ khác nhau ở mức độ tường minh khi từng bản audit trích dẫn đường đi serialization. Architect v2, Architect v1 và Codex đều lần theo `OAuth2Session.toJson()` tới object `atr`, còn Gemini đi đến cùng kết luận nhưng theo cách khái quát hơn. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là `OAuth2Session.toJson()` ghi map access token response vào dưới `atr`.

### B3
Các bản audit thống nhất rằng implementation hiện tại vẫn có một cách khả thi để suy ra subject cho luồng logout của Jellyfin, nhưng họ không thống nhất rằng `user_info` được serialize vào blob session OAuth2 đã lưu. Gemini xác nhận claim này ở mức khái quát và Architect v1 để ngỏ `INCONCLUSIVE` trong khi nghiêng về hành vi runtime, còn Codex và Architect v2 đều lần theo `OAuth2Session.toJson()` và cho thấy `user_info` vắng mặt trong mục session đã persist. Hai bản audit mạnh hơn này cũng tách biệt `OAuth2ClientFilter.fillTarget()` khỏi `OAuth2Utils.saveSession()`, từ đó giải thích vì sao `attributes.openid.user_info` có thể tồn tại ngay cả khi `session[oauth2Key].user_info` không có. Kết luận cuối: REFUTE, với bằng chứng mạnh nhất nằm ở việc Architect v2 tách chính xác các đường serialization, được Codex hậu thuẫn.

### B4
Cả bốn bản audit đều đồng ý về kết quả quan sát được: dữ liệu OIDC có sẵn trong request attributes trong suốt filter chain, và dữ liệu OAuth2 nằm trong session vẫn sẵn sàng cho các handler về sau. Họ khác nhau ở cơ chế: Gemini và Architect v1 xem sự hiện diện đồng thời hiệu dụng đó là một xác nhận, còn Codex và Architect v2 phản bác claim vì `target = ${attributes.openid}` tự thân chỉ ghi vào attributes, còn việc ghi vào session đến từ một đường `saveSession()` riêng biệt. Vì vậy, bất đồng ở đây nằm ở cách diễn đạt quan hệ nhân quả, không phải ở việc cả hai vị trí đều có thể chứa dữ liệu hữu ích. Kết luận cuối: REFUTE, với Architect v2 cung cấp bằng chứng mạnh nhất khi trích dẫn cả `OAuth2ClientFilter.fillTarget()` lẫn `OAuth2Utils.saveSession()`.

### C2
Cả bốn bản audit đều thống nhất về hành vi thực sự quan trọng: các thay đổi được thực hiện trong callback Groovy `.then()` được đưa vào session lưu cuối cùng, nên cơ chế offload token-reference lên Redis vẫn hợp lệ. Bề ngoài có vẻ bất đồng chỉ vì cách diễn đạt, do Codex phản bác một phiên bản claim nói rằng `.then()` chạy sau serialization, trong khi Architect v1 và v2 nêu trực tiếp rằng `.then()` chạy trước khi `SessionFilter` lưu session. Gemini mô tả cùng hành vi ở pha response nhưng không lần theo filter chain sâu bằng. Kết luận cuối: CONFIRM ở mức hành vi, với bằng chứng mạnh nhất đến từ thứ tự thực thi `RouteBuilder` cộng với `SessionFilter` mà Codex và Architect v2 đã lần theo.

### A1
Cả bốn bản audit đều thống nhất rằng `JwtCookieSession` expose các key đã lưu thông qua `keySet()` và Groovy có thể enumerate chúng một cách an toàn. Không có khác biệt thực chất nào ngoài độ sâu của phần trích dẫn. Architect v1, Architect v2 và Codex đều trỏ trực tiếp tới `JwtCookieSession.keySet()`, còn Gemini dựa trên hành vi `Map` của class theo nghĩa khái quát hơn. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là implementation `keySet()` tường minh trả về `DirtySet` trên `super.keySet()`.

### A2
Cả bốn bản audit đều thống nhất rằng `session.clear()` làm rỗng nội dung của `JwtCookieSession`. Điểm khác nhau duy nhất là mức độ giải thích: Codex và các báo cáo Architect liên hệ `clear()` với đường lưu expired-cookie về sau, còn Gemini dừng ở mức ngữ nghĩa `Map`. Dù vậy, kết luận vẫn đồng nhất trong cả bốn bản audit. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là `JwtCookieSession.clear()` cùng với nhánh empty-session trong `save()`.

### A3
Cả bốn bản audit đều thống nhất rằng `session.remove(key)` được hỗ trợ trực tiếp trên `JwtCookieSession`. Không có bất đồng đáng kể nào. Codex và cả hai vòng Architect đều trích dẫn override `remove()` cụ thể, còn Gemini xem đây là hành vi `Map` được kế thừa. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là implementation `JwtCookieSession.remove()` tường minh.

### A4
Cả bốn bản audit đều thống nhất rằng OpenIG không truncate một JWT session cookie quá lớn và việc vượt ngưỡng 4096 ký tự là một đường lỗi. Họ khác nhau ở hành vi mà client nhìn thấy, vì Gemini và Architect v1 dừng tại `JwtCookieSession.save()` rồi suy ra đó là một HTTP 500 cứng, trong khi Codex lần theo `SessionFilter` của CHF bên dưới và cho thấy `IOException` bị bắt trước khi downstream response gốc được trả về. Architect v2 chấp nhận vết lần theo sâu hơn đó và độc lập đi đến cùng hướng với Codex, nên đây là một khác biệt về chất lượng bằng chứng chứ không phải bế tắc thực sự. Kết luận cuối: REFUTE, với bằng chứng mạnh nhất nằm ở việc Codex kiểm tra trực tiếp `org.forgerock.http.filter.SessionFilter.handleResult()`.

### A5
Cả bốn bản audit đều đồng ý trên phương diện vận hành rằng heap object phải được đặt tên là `Session` thì `JwtSession` mới được auto-wire làm session manager mặc định. Codex là ngoại lệ duy nhất vì nó đánh dấu claim là `INCONCLUSIVE` ở rìa của hành vi fallback, tách biệt giữa "heap key là `Session`" với "implementation fallback là một container brand cụ thể". Gemini và cả hai vòng Architect làm phẳng sắc thái này thành một xác nhận thẳng vì yêu cầu thực tế liên quan tới project vẫn giữ nguyên. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là cặp `Keys.SESSION_FACTORY_HEAP_KEY = "Session"` với `GatewayHttpApplication.create()` trong Architect v2.

### D1
Cả bốn bản audit đều thống nhất rằng `request.entity.getString()` hoạt động với request body dạng form-urlencoded trong scripts và handlers. Khác biệt duy nhất là độ sâu nguồn: Codex trích dẫn một framework test cụ thể, các báo cáo Architect dựa vào hành vi API của framework, còn Gemini xác nhận ở mức cao hơn kèm lưu ý sử dụng với body lớn. Không bản audit nào phủ nhận giả định implementation này. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là phần Codex trích dẫn test lan truyền form-body cùng với binding `Request` đang chạy.

### D2
Cả bốn bản audit đều thống nhất rằng `globals` được chống lưng bởi `ConcurrentHashMap` và vì vậy hỗ trợ `compute()` theo kiểu atomic. Không có bất đồng đáng kể nào. Các bản audit mạnh nhất đều trỏ tới việc `AbstractScriptableHeapObject` tạo `scriptGlobals` dưới dạng `ConcurrentHashMap`, còn Gemini đi đến cùng kết luận nhưng ngắn gọn hơn. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là `AbstractScriptableHeapObject` cùng đường binding script.

### E1
Cả bốn bản audit đều thống nhất về rào chắn thực tế là các giá trị `clientEndpoint` phải giữ tính duy nhất để tránh va chạm callback hoặc logout. Họ khác nhau ở cơ chế: Gemini mô tả một cơ chế đăng ký toàn cục, Architect v1 nói không có registry chính thức nhưng route khớp đầu tiên vẫn tạo ra cùng hiệu ứng, còn Codex với Architect v2 lần theo `OAuth2ClientFilter` như một filter instance theo từng route, hoàn toàn không có server-level registry. Điều này khiến bất đồng nằm ở cách diễn đạt và đường nguồn, chứ không phải ở kết luận vận hành về yêu cầu tính duy nhất. Kết luận cuối: REFUTE, với bằng chứng mạnh nhất là Architect v2 kiểm tra trực tiếp instance field `clientEndpoint` và chỉ ra không hề tồn tại bất kỳ bảng toàn cục nào, điều mà Codex cũng xác nhận.

### E2
Cả bốn bản audit đều thống nhất rằng thứ tự đánh giá route tuân theo route ID sinh từ filename theo thứ tự từ điển. Ở đây không có xung đột đáng kể nào. Codex và cả hai vòng Architect trích dẫn trực tiếp `RouterHandler` và `LexicographicalRouteComparator`, còn Gemini tóm tắt cùng hành vi nhưng dùng nhãn cũ `Router`. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là đường `TreeSet` cùng comparator trong `RouterHandler`.

### F1
Cả bốn bản audit đều thống nhất rằng `cookieDomain` trở thành thuộc tính cookie `Domain` trong `Set-Cookie`. Điểm khác nhau duy nhất là từng bản audit trích dẫn bao nhiêu code. Codex và Architect v2 trỏ trực tiếp tới cookie builder, Architect v1 lần theo luồng từ manager sang cookie, còn Gemini nêu mapping này trực tiếp. Kết luận cuối: CONFIRM, bằng chứng mạnh nhất là `JwtCookieSession.buildJwtCookie().setDomain(cookieDomain)`.

### F2
Cả bốn bản audit đều thống nhất rằng `sessionTimeout` điều khiển ngữ nghĩa lifetime của session, nhưng họ khác nhau về chính xác thuộc tính cookie nào được dùng để biểu đạt lifetime đó. Gemini và Architect v1 mô tả browser lifetime theo cách khái quát nên xác nhận claim, còn Codex và Architect v2 lần theo `JwtCookieSession.buildJwtCookie()` và cho thấy nó đặt `Expires`, không phải `Max-Age`, đồng thời cũng điều khiển trạng thái expiry của JWT. Đây lại là một trường hợp khác biệt về độ sâu bằng chứng, nơi các vết lần theo ở mức code mạnh hơn các phần tóm tắt rộng. Kết luận cuối: REFUTE, với bằng chứng mạnh nhất là đoạn `setExpires(...)` chính xác trong Architect v2, được Codex hỗ trợ bằng việc lần theo `JwtSessionManager` và `_ig_exp`.

## 3. Danh sách xung đột

### B1
Khác biệt: Architect v2 nói claim chỉ đúng một phần vì key được tạo ra bằng resolve URI trong `buildUri()`, còn ba bản audit còn lại chấp nhận mô tả viết tắt `oauth2:<full-request-URL>/<clientEndpoint>`. Bằng chứng mạnh nhất là Architect v2, vì nó trích dẫn cả `OAuth2Utils.sessionKey()` lẫn `OAuth2Utils.buildUri()` nên giải thích được cơ chế, không chỉ hình dạng chuỗi ở đầu ra. Hành động khuyến nghị: ACCEPTED.

### B3
Khác biệt: Gemini nói đường `user_info` được xác nhận, Architect v1 nói nó hoạt động trong thực tế nhưng chưa thể kết luận dứt khoát ở mức persist nguồn, còn Codex và Architect v2 nói `user_info` không bao giờ được serialize vào blob session OAuth2 đã lưu. Bằng chứng mạnh nhất là Architect v2, vì nó tách riêng `OAuth2Session.toJson()`, `OAuth2ClientFilter.fillTarget()` và `OAuth2Utils.saveSession()` bằng các snippet chính xác, và Codex cũng độc lập đi đến cùng kết quả. Hành động khuyến nghị: CODE-CLEANUP.

### B4
Khác biệt: Gemini nói dữ liệu OIDC được mirror vào cả session lẫn attributes, Architect v1 nói "Data IS stored to both," còn Codex và Architect v2 nói cả hai nơi được populate bởi hai đường ghi riêng biệt, chứ không phải do chính biểu thức `target`. Bằng chứng mạnh nhất là Architect v2, vì nó trích dẫn cả phần ghi vào attributes lẫn phần ghi vào session, nhờ đó giải quyết chính xác claim về quan hệ nhân quả. Hành động khuyến nghị: CODE-CLEANUP.

### A4
Khác biệt: Gemini và Architect v1 xem việc tràn quá 4096 byte là một HTTP 500 cứng, còn Codex và Architect v2 nói framework bắt exception và trả về response gốc mà không cập nhật session cookie. Bằng chứng mạnh nhất là Codex, vì đây là bản audit duy nhất kiểm tra tường minh implementation `SessionFilter` của CHF nằm ngoài repo OpenIG và lần theo exception tới hành vi mà client thật sự nhìn thấy. Hành động khuyến nghị: ACCEPTED.

### A5
Khác biệt: Codex đánh dấu claim là `INCONCLUSIVE` vì nó phân biệt giữa heap key `"Session"` đã được chứng minh với hành vi fallback theo đúng container brand chưa được chứng minh, còn Gemini và cả hai vòng Architect gom yêu cầu thực tế liên quan tới project thành một xác nhận đơn giản. Bằng chứng mạnh nhất là Architect v2, vì nó nối trực tiếp `Keys.SESSION_FACTORY_HEAP_KEY` với đường lookup trong `GatewayHttpApplication.create()`. Hành động khuyến nghị: ACCEPTED.

### E1
Khác biệt: Gemini mô tả một cơ chế đăng ký `clientEndpoint` ở phạm vi toàn server, Architect v1 nói không có registry chính thức nhưng va chạm vận hành vẫn tồn tại, còn Codex và Architect v2 mô tả hành vi này là route-local matching cộng với va chạm route-order/path. Bằng chứng mạnh nhất là Architect v2, vì nó cho thấy `clientEndpoint` là một instance field và chỉ ra không hề có cơ chế registry tĩnh hay toàn cục nào. Hành động khuyến nghị: CODE-CLEANUP.

### F2
Khác biệt: Gemini và Architect v1 nói `sessionTimeout` điều khiển cả JWT `exp` lẫn cookie `Max-Age`, còn Codex và Architect v2 nói nó điều khiển expiry của JWT và cookie `Expires`. Bằng chứng mạnh nhất là Architect v2, vì nó trích dẫn `JwtCookieSession.buildJwtCookie().setExpires(...)`, còn Codex độc lập xác nhận không có đường ghi `Max-Age`. Hành động khuyến nghị: CODE-CLEANUP.

## 4. Tóm tắt các phát hiện quan trọng

- `B3` là phát hiện phủ định quan trọng nhất: `user_info` không được persist trong blob session OAuth2, nên giả định `session[oauth2Key].user_info.sub` là một dead path. Rủi ro: LOW ở runtime hiện tại vì vẫn có fallback bằng ID token, nhưng MEDIUM về bảo trì vì việc đọc vào dead path che khuất data model thực sự.
- `A4` là rủi ro vận hành cao nhất: các JWT session quá lớn không thất bại dưới dạng hard 500 hiển nhiên, mà thất bại dưới dạng mất session-save một cách im lặng trong khi response vẫn tới client. Rủi ro: MEDIUM, vì tình huống này khó chẩn đoán hơn lỗi tường minh dù mẫu Redis offload hiện tại phần lớn đã ngăn được nó.
- `B4` là bẫy diễn đạt kiến trúc chính: `target = ${attributes.openid}` tự nó không mirror dữ liệu vào session, và việc trộn lẫn hai đường này có thể khiến các lần debug sau đi nhầm nguồn sự thật. Rủi ro: MEDIUM về thiết kế/debugging, LOW về runtime tức thời.
- `E1` quan trọng trên phương diện vận hành nhưng rủi ro thấp hơn: va chạm `clientEndpoint` đến từ route-local matching và route order, không phải từ một registry toàn cục cấp server. Rủi ro: LOW miễn là các endpoint path vẫn giữ tính duy nhất, nhưng tài liệu cần mô tả đúng cơ chế này.
- `C2` là phát hiện tích cực mạnh nhất: cả bốn bản audit đều ủng hộ cùng một thứ tự thực thi, xác nhận rằng các thay đổi Redis token-reference offload xảy ra trước khi lưu session và vì vậy được persist. Rủi ro: LOW, và điều này củng cố đáng kể mức độ tin cậy đối với pattern OpenIG 6 hiện tại.

## 5. Các việc còn mở

- Loại bỏ hoặc chú thích rõ mọi đường đọc chết `session[oauth2Key].user_info.sub` trong luồng logout của Jellyfin, và chuẩn hóa cách dùng `attributes.openid` trong khi xử lý request cộng với fallback bằng ID token ở ngoài băng.
- Cập nhật tài liệu của project để nói rõ rằng `target = ${attributes.openid}` và việc persist session OAuth2 là hai đường ghi tách biệt, không phải một lần ghi mirror duy nhất.
- Bổ sung một mục gotcha hoặc runbook note rằng tràn JWT session dẫn tới mất session-save một cách im lặng, chứ không phải chắc chắn hard 500.
- Sửa mọi tài liệu hoặc kỳ vọng test còn nhắc tới cookie `Max-Age`; OpenIG 6 dùng cookie `Expires` cho đường session này.
- Tiếp tục tài liệu hóa việc đặt tên heap `Session` và các giá trị `clientEndpoint` duy nhất như những ràng buộc cấu hình không thể thương lượng cho các route bổ sung về sau.
