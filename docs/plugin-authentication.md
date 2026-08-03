# 插件自定义鉴权

插件安全能力由生态按需生长，而非由 pm-spring-pf4j 预置。API Key、JWT、Webhook 验签、外部 IAM —— 任何认证策略均可通过可插拔 SPI 接入，**无需修改框架源码**。

**设计原则**：

- **委托权限，链式裁决**。该模块在宿主已有的 Spring Security 之上构建一层路由层。多个 Provider 依次尝试，任一成功即通过。
- **只做鉴权，不做授权**。插件回答"你是谁"——宿主通过自己的 `AuthorizationFilter`、策略引擎或 RBAC 决定"你能干什么"。
- **兜底宿主，天然隔离**。插件未注册 Provider 或 Provider 拒绝认领时，请求回落到宿主标准认证链。每个插件的 Provider 从自身 ApplicationContext 发现，通过生命周期注册，卸载时自动注销。

---

## 核心规则

`@PluginAuthenticated` 决定鉴权模式：

| 注解 | 模式 | Session 用户 |
|---|---|---|
| 无 `@PluginAuthenticated` | 全量排他 | 忽略宿主 Session，完全由 `authenticate()` 接管 |
| 有 `@PluginAuthenticated` | OR 鉴权 | Session 优先放行，无 Session 才走 `authenticate()` |

`@AllowAnonymous` 优先级最高——标注后跳过所有认证（包括插件自定义认证）。

以上两个注解均可标注在 Controller **类**上（对该类所有方法生效）或单个**方法**上。方法级优先于类级。

---

## 依赖

**宿主应用** (`pom.xml`):

```xml
<dependency>
    <groupId>io.github.novohit</groupId>
    <artifactId>pm-pf4j-security-webmvc</artifactId>
    <version>${pm-spring-pf4j.version}</version>
</dependency>
```

**业务插件** (`pom.xml`, `provided`——运行时由宿主提供):

```xml
<dependency>
    <groupId>io.github.novohit</groupId>
    <artifactId>pm-pf4j-security-core</artifactId>
    <version>${pm-spring-pf4j.version}</version>
    <scope>provided</scope>
</dependency>
```

---

## 宿主集成

### MVC

`PluginSecurityConfigurer` 负责注册所有插件 Filter。宿主需在自己的 `authorizeHttpRequests` 链中**第一个**位置加入匿名路径 `permitAll`：

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class MvcSecurityConfig {

    private final PluginSecurityConfigurer pluginSecurityConfigurer;
    private final PluginAnonymousPathRegistry anonymousPaths;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.with(pluginSecurityConfigurer, Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(req -> anonymousPaths.isAnonymous(
                    PluginHttpUtils.getPathWithinApplication(req),
                    req.getMethod())).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll());
        return http.build();
    }
}
```

### WebFlux

`PluginDelegatingAuthWebFilter` 是 `WebFilter` Bean，Spring WebFlux 自动发现：

```java
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class WebFluxSecurityConfig {

    private final PluginAnonymousPathRegistry anonymousPaths;

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/**").access((auth, ctx) -> {
                    String path = ctx.getExchange().getRequest().getPath()
                            .pathWithinApplication().value();
                    String method = ctx.getExchange().getRequest().getMethod().name();
                    if (anonymousPaths.isAnonymous(path, method)) {
                        return Mono.just(new AuthorizationDecision(true));
                    }
                    return auth.map(a -> new AuthorizationDecision(a.isAuthenticated()));
                })
                .anyExchange().permitAll());
        return http.build();
    }
}
```

&gt; **请求体缓存**：MVC 认证 Filter 通过 `ContentCachingRequestWrapper` 包装请求（无内置大小限制，由 Servlet 容器控制，如 `server.tomcat.max-swallow-size` 默认 2 MB）。WebFlux Filter 通过 `DataBufferUtils.join()` 读取，受 `spring.codec.max-in-memory-size`（默认 256 KB）控制。建议由上游基础设施（反向代理、网关）负责限流。

---

## 使用场景

### 场景 1：插件所有接口全量排他鉴权

最简单的情况——Provider 继承 `AbstractPluginAuthenticationProvider`，Controller 不加任何注解。所有请求必过 `authenticate()`，宿主 Session 被忽略。

```java
@Component
public class WxPayJwtProvider extends AbstractPluginAuthenticationProvider {
    @Override
    public Authentication authenticate(HttpServletRequest request)
            throws PluginAuthenticationException {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return null; // 无 Token → 401
        }
        Claims claims = JwtUtils.verify(token.substring(7));
        return new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of());
    }
}
```

```java
@RestController
@RequestMapping("/wxpay/order")
public class WxPayOrderController {

    @PostMapping("/create")
    public Result create() { ... }      // → JWT

    @GetMapping("/{id}")
    public Result getById() { ... }     // → JWT
}
```

---

### 场景 2：部分接口 OR 鉴权（Session 或 Token）

对内用户通过宿主 Session 已登录 → 直接放行；外部 API 调用携带 JWT → 走 `authenticate()`。

```java
@Component
public class WxPayJwtProvider extends AbstractPluginAuthenticationProvider {
    @Override
    public Authentication authenticate(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null) return null;
        Claims claims = JwtUtils.verify(token.substring(7));
        return new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of());
    }
}
```

```java
@RestController
@RequestMapping("/wxpay/order")
public class WxPayOrderController {

    @PluginAuthenticated
    @PostMapping("/refund")
    public Result refund() { ... }      // OR：Session 放行 / JWT

    @GetMapping("/{id}")
    public Result getById() { ... }     // 未标注，只用宿主 Session
}
```

---

### 场景 3：混合鉴权 + 微信支付回调（匿名）

回调接口在 Filter 层做签名验证，Controller 层标记匿名跳过认证链。

```java
@Component
public class WxPayJwtProvider extends AbstractPluginAuthenticationProvider { ... }
```

```java
@RestController
@RequestMapping("/wxpay/order")
public class WxPayOrderController {

    @PluginAuthenticated
    @PostMapping("/refund")
    public Result refund() { ... }

    @AllowAnonymous(reason = "微信支付回调，Filter 层验签")
    @PostMapping("/callback")
    public Result callback() { ... }    // 匿名
}
```

WebFlux 函数式路由等价写法：

```java
return PmRouterFunctions.route()
    .GET("/wxpay/order/{id}", handler::getById)
    .POST("/wxpay/order/refund", handler::refund)
    .pluginAuthenticated()
    .POST("/wxpay/order/callback", handler::callback)
    .anonymous("/wxpay/order/callback", "POST", "微信支付回调验签")
    .build();
```

---

### 场景 4：多套鉴权策略（高级层）

回调接口用微信支付签名验证，普通接口用 JWT。各自实现 `IPluginAuthenticationProvider`，通过 `supports()` 区分。

```java
@Component
public class WxPayCallbackSignatureProvider implements IPluginAuthenticationProvider {

    @Override
    public int getOrder() { return 100; }

    @Override
    public boolean supports(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/callback");
    }

    @Override
    public Authentication authenticate(HttpServletRequest request)
            throws PluginAuthenticationException {
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");

        if (signature == null || timestamp == null || nonce == null) {
            throw new PluginBadCredentialsException("缺少微信支付签名参数");
        }

        String body = new String(request.getInputStream().readAllBytes());
        String expected = HmacUtils.hmacSha256(apiKey, timestamp + "\n" + nonce + "\n" + body);
        if (!expected.equals(signature)) {
            throw new PluginBadCredentialsException("微信支付签名验证失败");
        }

        return new UsernamePasswordAuthenticationToken("mch_" + mchId, null, List.of());
    }
}

@Component
public class WxPayJwtProvider implements IPluginAuthenticationProvider {
    @Override
    public int getOrder() { return 200; }

    @Override
    public Authentication authenticate(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) return null;
        Claims claims = JwtUtils.verify(token.substring(7));
        return new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of());
    }
}
```

Controller 不加注解，由 `supports()` 各自路由：

```java
@RestController
@RequestMapping("/wxpay/order")
public class WxPayOrderController {

    @PostMapping("/create")
    public Result create() { ... }      // → WxPayJwtProvider

    @PostMapping("/refund")
    public Result refund() { ... }      // → WxPayJwtProvider

    @PostMapping("/callback")
    public Result callback() { ... }    // → WxPayCallbackSignatureProvider (order=100 先到)
}
```

---

### 场景 5：简单层 + 高级层共存

大部分接口用 JWT（简单层），个别接口用签名验证（高级层）。**约束**：共存时必须配合 `@PluginAuthenticated` 标注范围，否则框架 WARN 并跳过简单层。

```java
// 简单层：大部分接口
@Component
public class WxPayJwtProvider extends AbstractPluginAuthenticationProvider {
    @Override
    public Authentication authenticate(HttpServletRequest request) { /* JWT */ }
}

// 高级层：回调签名验证
@Component
public class WxPayCallbackSignatureProvider implements IPluginAuthenticationProvider {
    @Override
    public int getOrder() { return 100; }

    @Override
    public boolean supports(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/callback");
    }
    @Override
    public Authentication authenticate(HttpServletRequest request) { /* 验签 */ }
}
```

```java
@RestController
@RequestMapping("/wxpay/order")
public class WxPayOrderController {

    @PluginAuthenticated
    @PostMapping("/refund")
    public Result refund() { ... }      // → 简单层 JWT

    @PostMapping("/callback")
    public Result callback() { ... }    // → 高级层签名 (order=100 先到)
}
```

---

## 快速对照

| 场景 | Provider | Controller 注解 | Session 用户 |
|---|---|---|---|
| 全量排他 | `extends AbstractPluginAuthenticationProvider` | 不加 | 忽略，必过 `authenticate()` |
| OR 鉴权 | `extends AbstractPluginAuthenticationProvider` | `@PluginAuthenticated` | 放行 |
| 混合匿名 | `extends AbstractPluginAuthenticationProvider` | `@PluginAuthenticated` + `@AllowAnonymous` | 放行 |
| 多套策略 | N × `implements IPluginAuthenticationProvider` | 不加 | 由 `supports()` 决定 |
| 简单+高级共存 | 1 × `AbstractPluginAuthenticationProvider` + N × `IPluginAuthenticationProvider` | 必须 `@PluginAuthenticated` | 放行 |

---

## 两层 API

| 层级 | 方式 | 适用场景 |
|---|---|---|
| **简单层** | 继承 `AbstractPluginAuthenticationProvider`，只写 `authenticate()` | 一套策略覆盖大部分接口，框架自动处理路由 |
| **高级层** | 实现 `IPluginAuthenticationProvider`，写 `supports()` + `authenticate()` | 多套策略、自定义路由 |

简单层约束：每个插件最多一个实例。多个 → WARN，取第一个。

---

## 认证策略

Provider 链的执行策略可插拔：

| 策略 | 行为 | 适用场景 |
|---|---|---|
| `AtLeastOneSuccessfulStrategy`（默认） | 所有 Provider 都试，任一成功即通过 | 多认证源共存 |
| `FirstSuccessfulStrategy` | 第一个成功就停 | 性能优先 |

宿主覆盖一个 Bean 即可切换全局策略。

---

## 认证事件

框架在每次认证尝试后发布事件，宿主通过 Spring `@EventListener` 监听，不侵入 Provider 代码。

### 事件类型

| 事件 | 父类 | 发布时机 |
|------|------|----------|
| `PluginAuthenticationSuccessEvent` | `PluginAuthenticationEvent` | Provider 返回有效 `Authentication` |
| `PluginAuthenticationFailureEvent` | `PluginAuthenticationEvent` | 所有 Provider 均失败，或抛出 `PluginAuthChallenge` |
| `PluginAuthenticationEvent`（抽象基类） | `ApplicationEvent` | 基类——一次监听同时覆盖成功和失败 |

### 成功事件

```java
@EventListener
public void onAuthSuccess(PluginAuthenticationSuccessEvent e) {
    log.info("插件 [{}] 认证成功 via {} (order={}), {}ms, principal={}",
            e.getPluginId(), e.getProviderName(),
            e.getProviderOrder(), e.getDurationMs(),
            e.getAuthentication().getName());
}
```

字段：`pluginId`、`providerName`、`durationMs`、`authentication`、`providerOrder`。

### 失败事件

所有 Provider 均未成功时发布，或 Provider 抛出 `PluginAuthChallenge`（OAuth2 跳转、SAML 等）时发布。`exception` 字段揭示失败原因：

| 异常子类 | 含义 |
|----------|------|
| `PluginBadCredentialsException` | API Key、Token、签名无效——客户端错误 |
| `PluginAuthServiceException` | 外部认证服务不可用——服务端错误 |
| `PluginAuthChallenge` | 正常控制流——需跳转/质询（非错误） |

```java
@EventListener
public void onAuthFailure(PluginAuthenticationFailureEvent e) {
    log.warn("插件 [{}] 认证失败 via {} (order={}), {}ms, error={}",
            e.getPluginId(), e.getProviderName(),
            e.getProviderOrder(), e.getDurationMs(),
            e.getException() != null ? e.getException().getMessage() : "无异常信息");
    // 告警、失败计数、账号锁定、安全审计
}
```

字段：`pluginId`、`providerName`、`durationMs`、`exception`（可为 null，类型 `PluginAuthenticationException`）、`providerOrder`。

### 监听基类

监听 `PluginAuthenticationEvent` 基类，在一个方法中同时处理成功和失败：

```java
@EventListener
public void onAuthEvent(PluginAuthenticationEvent e) {
    if (e instanceof PluginAuthenticationSuccessEvent s) {
        // 处理成功
    } else if (e instanceof PluginAuthenticationFailureEvent f) {
        // 处理失败
    }
}
```

### 宿主典型用法

| 事件 | 用途 |
|------|------|
| `PluginAuthenticationSuccessEvent` | 审计日志、更新最后登录时间、指标采集 |
| `PluginAuthenticationFailureEvent` | 告警（PagerDuty/Slack）、失败计数、限流、账号锁定、安全审计追踪 |
| `PluginAuthenticationEvent` | 统一审计追踪所有认证尝试 |

---

## Filter 扩展点

认证之外，插件可在 Spring Security Filter Chain 的六个固定位置注入自定义 Filter：

```
MVC Filter Chain                                   位置
══════════════════                               ══════════
SecurityContextHolderFilter
    ↓
[1] ←────────────────────────────────────────── [FIRST]
      请求预处理、防重放
    ↓
[2] ←────────────────────────────────────────── [SESSION_RESTORE]
      会话恢复后处理
UsernamePasswordAuthenticationFilter
    ↓
[3] ←────────────────────────────────────────── [FORM_LOGIN]
      自定义登录（LDAP、短信验证码）
    ↓
[4] ── 认证链：IPluginAuthenticationProvider ───── [AUTHENTICATION]（框架占用）
AnonymousAuthenticationFilter
    ↓
[5] ←────────────────────────────────────────── [ANONYMOUS]
      匿名用户追踪
AuthorizationFilter
    ↓
[6] ←────────────────────────────────────────── [PRE_AUTHORIZE]
      授权前检查
    ↓
[7] ←────────────────────────────────────────── [LAST]
      响应头注入、上下文清理
```

**六位置 × 双栈（MVC + WebFlux）= 12 个接口**，编译期类型安全。

| 位置 | MVC 接口 | WebFlux 接口 | 典型用途 |
|------|----------|-------------|----------|
| FIRST | `FirstFilterExtension` | `FirstWebFilterExtension` | 请求预处理、防重放 |
| SESSION_RESTORE | `SessionRestoreFilterExtension` | `SessionRestoreWebFilterExtension` | 会话恢复后处理 |
| FORM_LOGIN | `FormLoginFilterExtension` | `FormLoginWebFilterExtension` | 自定义登录（LDAP、短信验证码） |
| ANONYMOUS | `AnonymousFilterExtension` | `AnonymousWebFilterExtension` | 匿名用户追踪 |
| PRE_AUTHORIZE | `PreAuthorizeFilterExtension` | `PreAuthorizeWebFilterExtension` | 授权前检查 |
| LAST | `LastFilterExtension` | `LastWebFilterExtension` | 响应头注入、上下文清理 |

实现方式：实现对应接口，返回 `Filter` / `WebFilter`，`@Component` 标注即可被框架发现，可选 `getOrder()` 控制同位置内排序。

### 示例：防重放 Filter（FIRST 位置）

```java
@Component
public class ReplayAttackFilter implements FirstFilterExtension {
    @Override
    public Filter getFilter() {
        return (req, res, chain) -> {
            String path = PluginHttpUtils.getPathWithinApplication((HttpServletRequest) req);
            if (!path.endsWith("/wxpay/order/callback")) {
                chain.doFilter(req, res); return;
            }
            String ts = ((HttpServletRequest) req).getHeader("Wechatpay-Timestamp");
            String nonce = ((HttpServletRequest) req).getHeader("Wechatpay-Nonce");
            if (Math.abs(System.currentTimeMillis()/1000 - Long.parseLong(ts)) > 300) {
                ((HttpServletResponse) res).sendError(400); return;
            }
            if (redis.setIfAbsent("nonce:" + nonce, "1", Duration.ofMinutes(5))) {
                chain.doFilter(req, res);
            } else {
                ((HttpServletResponse) res).sendError(400);
            }
        };
    }
    @Override public int getOrder() { return 0; }
}
```

### 宿主控制——三层粒度

Filter 影响面是整个应用，**默认关闭**：

```yaml
pm:
  pf4j:
    security:
      filter:
        enabled: true                            # 第一层：全局开关
        allowed-positions: FIRST, LAST           # 第二层：位置白名单
      plugins:
        com.pmplugin4j.payment:                  # 第三层：插件授权
          filter:
            allowed-positions: FIRST, LAST
```

三层全通过才注入。沙箱隔离：一个插件的 Filter 异常不阻断其他 Filter。

### Filter 异常事件

沙箱内插件 Filter 抛出异常时，框架发布 `PluginFilterErrorEvent`，宿主可监听并响应：

```java
@EventListener
public void onFilterError(PluginFilterErrorEvent e) {
    log.error("插件 [{}] Filter 异常: class={}, position={}, error={}",
            e.getPluginId(), e.getFilterClassName(),
            e.getPosition(), e.getException().getMessage());
    // 熔断、健康检查、告警
}
```

字段：`pluginId`、`filterClassName`、`position`（`PmPluginFilterPosition` 枚举：`FIRST`、`SESSION_RESTORE`、`FORM_LOGIN`、`ANONYMOUS`、`PRE_AUTHORIZE`、`LAST`）、`exception`。宿主典型用法：熔断（连续错误禁用插件）、健康检查、告警。

---

## 异常体系

| 异常 | 含义 | 日志级别 |
|---|---|---|
| `PluginAuthChallenge` | OAuth2/SAML 等跳转（控制流，非错误） | DEBUG |
| `PluginBadCredentialsException` | 凭证无效（API Key、Token、签名等客户端错误） | WARN |
| `PluginAuthServiceException` | 外部认证不可用 | ERROR |

---

## 相关文档

- [REST 端点](REST-Endpoints_CN) — Controller 注册
- [主应用集成](Host-Integration_CN) — Spring Security 配置
- [插件扩展](Plugin-Extensions_CN) — 扩展点模式
