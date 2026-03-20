# OpenIG Heap và Session Management

## Tổng quan về OpenIG

**OpenIG** (Open Identity Gateway) là một reverse proxy server hiệu suất cao với chức năng quản lý session và credential replay chuyên biệt. Hiện nay được phát triển bởi **Ping Identity** (trước đây là ForgeRock).

- **Tên mới**: PingGateway
- **Repository**: OpenRock/OpenIG trên GitHub
- **Ngôn ngữ**: Java

## Khái niệm Heap trong OpenIG

### Heap là gì?

**Heap** là một tập hợp các objects có liên quan được tạo và khởi tạo bởi các **Heaplet** objects. Tất cả các configurable objects trong OpenIG đều là heap objects.

```
Heap = Collection of associated objects
Heaplet = Object that creates and initializes objects in the heap
```

### Cấu trúc Heap Configuration

File cấu hình chính: `$HOME/.openig/config/config.json`

```json
{
  "heap": [
    {
      "name": "uniqueObjectName",
      "type": "ClassName",
      "config": {
        // type-specific configuration
      }
    }
  ]
}
```

### Các thuộc tính của Heap Object

| Property | Mô tả |
|----------|-------|
| `name` | Tên duy nhất để tham chiếu heap object từ các object khác |
| `type` | Tên class của object cần tạo |
| `config` | Cấu hình riêng cho object (có thể bỏ qua nếu tất cả fields đều optional) |

## Session trong OpenIG - Phân biệt các khái niệm

### 1. "Session" là thuật ngữ chung

Trong OpenIG, **Session** là khái niệm chung để chỉ việc quản lý phiên làm việc của user. Có 2 loại session:

| Loại | Đặc điểm |
|------|----------|
| **Stateful Session** | Session data lưu trên server, cookie chỉ chứa session ID |
| **Stateless Session** | Session data lưu trong JWT cookie trên client |

### 2. "Session" trong heap - Tên heap object đặc biệt

Có một cấu hình đặc biệt trong OpenIG: **JwtSession object named `Session`**:

```json
{
  "heap": [
    {
      "name": "Session",      // ← Tên đặc biệt
      "type": "JwtSession",   // ← Type là JwtSession
      "config": {
        // ...
      }
    }
  ]
}
```

Khi đặt tên là `"Session"` trong heap, OpenIG sẽ sử dụng nó làm **default session manager** cho tất cả các routes.

### 3. "session" property - Tham chiếu đến heap object

Trong `config.json` hoặc Route, có property `"session"` để chỉ định session manager:

```json
{
  "session": "JwtSessionManager",  // ← Tham chiếu đến heap object name
  "heap": [
    {
      "name": "JwtSessionManager",
      "type": "JwtSessionManager",
      "config": {}
    }
  ]
}
```

### 4. Sự khác biệt giữa JwtSession và JwtSessionManager

| Thuộc tính | JwtSession | JwtSessionManager |
|------------|------------|-------------------|
| **Status** | Deprecated (không dùng nữa) | Current |
| **Type** | `"JwtSession"` | `"JwtSessionManager"` |
| **Heap name** | Có thể đặt `"Session"` | Tùy ý |
| **Cách dùng** | Trong heap với tên đặc biệt | Tham chiếu qua `"session"` property |

### JwtSessionManager

**JwtSessionManager** là heap object quản lý session dạng stateless, lưu session data trong JWT cookie trên user-agent.

#### Cấu hình cơ bản

```json
{
  "name": "JwtSessionManager",
  "type": "JwtSessionManager",
  "config": {
    "authenticatedEncryptionSecretId": "mySecret",
    "encryptionMethod": "A128CBC-HS256",
    "cookie": {
      "name": "OPENIG_JWT_SESSION",
      "domain": ".example.com",
      "httpOnly": true,
      "path": "/",
      "sameSite": "Strict",
      "secure": true
    },
    "sessionTimeout": "30 minutes",
    "persistentCookie": false,
    "secretsProvider": "SecretsProvider",
    "skewAllowance": "5 minutes",
    "useCompression": false
  }
}
```

#### Sử dụng trong config.json

```json
{
  "session": "JwtSessionManager",
  "heap": [
    {
      "name": "JwtSessionManager",
      "type": "JwtSessionManager",
      "config": {
        // ... configuration
      }
    }
  ]
}
```

### JwtSession (Legacy)

Cấu hình cũ hơn (trước khi đổi tên thành JwtSessionManager):

```json
{
  "name": "JwtSession",
  "type": "JwtSession",
  "config": {
    "keystore": "KeyStoreObjectName",
    "alias": "PrivateKeyAlias",
    "password": "KeyStorePassword",
    "cookieName": "OpenIG",
    "cookieDomain": ".example.com",
    "sessionTimeout": "30 minutes",
    "sharedSecret": "base64EncodedSecret=="
  }
}
```

## Các Heaplet quan trọng liên quan

| Heaplet | Mô tả |
|---------|-------|
| `JwtSessionManager.Heaplet` | Tạo và khởi tạo JwtSessionManager |
| `GenericHeaplet` | Base class cho các heaplet với auto-injected fields |
| `HeapImpl` | Concrete implementation của Heap |

## Package Structure

```
org.forgerock.openig.heap          # Core heap management
org.forgerock.openig.jwt           # JWT session management
org.forgerock.openig.session.jwt   # Session JWT utilities
```

## Ví dụ tích hợp đầy đủ

```json
{
  "handler": {
    "type": "Router",
    "capture": "all"
  },
  "session": "JwtSessionManager",
  "heap": [
    {
      "name": "JwtSessionManager",
      "type": "JwtSessionManager",
      "config": {
        "authenticatedEncryptionSecretId": "sessionSecret",
        "cookie": {
          "name": "MY_APP_SESSION",
          "httpOnly": true,
          "secure": true
        },
        "sessionTimeout": "1 hour"
      }
    },
    {
      "name": "sessionSecret",
      "type": "JwkSecretStore",
      "config": {
        "jwkUrl": "file:///path/to/keys.json"
      }
    }
  ]
}
```

## Tài liệu tham khảo và Nguồn code

### Tài liệu chính thức

| Nội dung | Nguồn | Phiên bản |
|----------|-------|-----------|
| **JwtSessionManager reference** | [PingGateway Docs](https://docs.pingidentity.com/pinggateway/2025.11/reference/JwtSessionManager.html) | 2025.11 |
| **JwtSession (deprecated)** | [PingGateway Docs](https://backstage.pingidentity.com/docs/ig/2023.11/reference/JwtSession.html) | 2023.11 |
| **Heap objects** | [PingGateway Docs](https://docs.pingidentity.com/pinggateway/2025.11/reference/heap-objects.html) | 2025.11 |
| **Sessions overview** | [PingGateway Docs](https://backstage.pingidentity.com/docs/ig/2023.11/about/about-sessions.html) | 2023.11 |
| **Encrypt JWT sessions** | [PingGateway Docs](https://backstage.pingidentity.com/docs/ig/2023.11/installation-guide/jwtsession-using.html) | 2023.11 |

### Source Code

| Class | Repository | File path |
|-------|------------|-----------|
| **JwtSessionManager** | [OpenIdentityPlatform/OpenIG](https://github.com/OpenIdentityPlatform/OpenIG) | `openig-core/src/main/java/org/forgerock/openig/jwt/JwtSessionManager.java` |
| **JwtSessionManager.Heaplet** | [OpenIdentityPlatform/OpenIG](https://github.com/OpenIdentityPlatform/OpenIG) | `openig-core/src/main/java/org/forgerock/openig/jwt/JwtSessionManager.java` (nested class) |
| **SessionManager interface** | [OpenIdentityPlatform/commons](https://doc.openidentityplatform.org/commons/apidocs/org/forgerock/http/session/SessionManager.html) | `org.forgerock.http.session.SessionManager` |
| **HeapImpl** | [OpenIdentityPlatform/OpenIG](https://github.com/OpenIdentityPlatform/OpenIG) | `openig-core/src/main/java/org/forgerock/openig/heap/HeapImpl.java` |

### Javadoc

| Class | URL |
|-------|-----|
| **JwtSessionManager** | [OpenIG Javadoc 6.0.3](https://doc.openidentityplatform.org/openig/apidocs/org/forgerock/openig/jwt/JwtSessionManager.html) |
| **JwtSessionManager.Heaplet** | [OpenIG Javadoc 6.0.3](https://doc.openidentityplatform.org/openig/apidocs/org/forgerock/openig/jwt/JwtSessionManager.Heaplet.html) |
| **SessionManager** | [OpenIG Javadoc 2025.3](https://docs.pingidentity.com/pinggateway/2025.3/_attachments/apidocs/org/forgerock/http/session/class-use/Session.html) |

### PDF Reference (Archive)

| Tài liệu | Phiên bản |
|----------|-----------|
| [Configuration Reference](https://cdn-docs.pingidentity.com/archive/pdf/openig/4.5/OpenIG-4.5-Reference.pdf) | OpenIG 4.5 |
| [IG 6.5 Reference](https://cdn-docs.pingidentity.com/archive/pdf/ig/6.5/IG-6.5-Reference.pdf) | ForgeRock IG 6.5 |
| [IG 7.1 Reference](https://cdn-docs.pingidentity.com/archive/pdf/ig/7.1/ig-7.1-reference.pdf) | ForgeRock IG 7.1 |

### Trích dẫn cụ thể

**1. Định nghĩa Heap object:**
> "A heap is a collection of associated objects created and initialized by heaplet objects. All configurable objects in IG are heap objects."
> - Nguồn: [PingGateway - Heap objects](https://docs.pingidentity.com/pinggateway/2025.11/reference/heap-objects.html)

**2. Cấu trúc heap:**
```json
{
  "heap": [
    {
      "name": string,      // required - A unique name for an object in the heap
      "type": string,      // required - The class name of the heap object
      "config": object     // required unless all fields are optional
    }
  ]
}
```
- Nguồn: [PingGateway - Heap objects](https://docs.pingidentity.com/pinggateway/2025.11/reference/heap-objects.html)

**3. JwtSessionManager usage:**
```json
{
  "name": string,
  "type": "JwtSessionManager",
  "config": {
    "authenticatedEncryptionSecretId": configuration expression<secret-id>,
    "encryptionMethod": configuration expression<string>,
    "cookie": { ... },
    "sessionTimeout": configuration expression<duration>,
    "secretsProvider": SecretsProvider reference
  }
}
```
- Nguồn: [PingGateway - JwtSessionManager](https://docs.pingidentity.com/pinggateway/2025.11/reference/JwtSessionManager.html)

**4. JwtSession với tên "Session":**
> "Configure a JwtSession object named `Session` in the heap of `config.json`."
> - Nguồn: [PingGateway - JwtSession](https://backstage.pingidentity.com/docs/ig/2023.11/reference/JwtSession.html)

**5. SessionManager interface methods:**
```java
public interface SessionManager {
    Session load(Request request);
    void save(Session session, Response response);
}
```
- Nguồn: [OpenIG Javadoc - SessionManager](https://doc.openidentityplatform.org/commons/apidocs/org/forgerock/http/session/SessionManager.html)
