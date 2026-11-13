package com.hackhu.hp.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * 요청 횟수 제한과 로그에 쓰는 클라이언트 IP (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-7, CWE-348).
 *
 * <ol>
 *   <li>연결 상대가 믿는 프록시가 아니면 연결 상대의 주소를 쓰고 {@code X-Forwarded-For}는 보지 않는다.
 *   <li>믿는 프록시에서 온 연결이면 {@code X-Forwarded-For} 목록의 오른쪽에서 {@code trustedHops}번째 값을 쓴다. 왼쪽 값은 클라이언트가
 *       보낸 그대로라 믿지 않는다. 로드밸런서는 자기에게 연결한 주소를 맨 오른쪽에 덧붙인다.
 *   <li>헤더가 없거나, 항목이 모자라거나, IP 형식이 아니면 연결 상대의 주소를 쓰고 경고 로그를 남긴다.
 * </ol>
 *
 * <p>Spring의 {@code ForwardedHeaderFilter}는 어떤 프록시를 믿을지 검사하지 않아 쓰지 않는다. {@code Forwarded}, {@code
 * X-Real-IP} 헤더도 보지 않는다.
 */
@Slf4j
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";
    // IPv6 표기의 최대 길이
    private static final int MAX_LENGTH = 45;

    // 각 칸 0~255, 앞에 0을 붙이지 않은 IPv4.
    private static final Pattern IPV4 =
            Pattern.compile(
                    "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    // IPv6 표기에 쓰는 문자만. 콜론이 있으면 InetAddress가 DNS를 찾지 않고 리터럴로만 해석한다. %(zone)는 받지 않는다.
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final List<IpAddressMatcher> trustedProxies;
    private final int trustedHops;

    public ClientIpResolver(ClientIpProperties properties) {
        // 잘못된 대역은 서버가 시작할 때 IllegalArgumentException으로 드러난다.
        this.trustedProxies =
                properties.trustedProxies().stream().map(IpAddressMatcher::new).toList();
        this.trustedHops = properties.trustedHops();
        // 홉 수가 한 칸 틀리면 값은 멀쩡한 IP라 요청 로그로는 드러나지 않는다. 적으면 모든 사용자가 프록시 IP 하나로 묶이고,
        // 많으면 클라이언트가 보낸 값을 고른다. 배포한 뒤 이 로그로 실효 설정을 확인한다 (6-11 CI4).
        // 대역과 홉 수는 비밀값이 아니다.
        log.info(
                "클라이언트 IP 판별: trustedProxies={}, trustedHops={}",
                properties.trustedProxies(),
                trustedHops);
    }

    /**
     * 클라이언트 IP. 요청 횟수 제한의 키와 로그에 그대로 쓸 수 있는 값만 돌려준다.
     *
     * <p>연결 상대의 주소가 IP 형식이 아닌 경우(예: 유닉스 소켓 경로)도 IP 표기에 쓰는 문자만 남기고 길이를 묶는다. 이 값은 로그에 남으므로 줄바꿈이나 제어
     * 문자로 로그 줄을 위조하지 못하게 한다 (CWE-117).
     */
    public String resolve(HttpServletRequest request) {
        return forKeyAndLog(resolveRaw(request));
    }

    private String resolveRaw(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String remoteIp = parseIp(remote).orElse(remote);
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }

        List<String> entries = forwardedFor(request);
        if (entries.size() < trustedHops) {
            log.warn(
                    "믿는 프록시에서 온 요청의 X-Forwarded-For 항목이 모자람: entries={}, hops={}",
                    entries.size(),
                    trustedHops);
            return remoteIp;
        }
        Optional<String> client = parseIp(entries.get(entries.size() - trustedHops));
        if (client.isEmpty()) {
            // 헤더 값은 클라이언트가 보낸 것일 수 있어 로그에 남기지 않는다 (CWE-117).
            log.warn("믿는 프록시에서 온 요청의 X-Forwarded-For 값이 IP 형식이 아님");
            return remoteIp;
        }
        return client.get();
    }

    // 출력 가능한 ASCII만 남긴 뒤 IP 표기에 쓰는 문자(16진수, 콜론, 점) 밖은 _로 바꾸고 길이를 묶는다.
    // IP 형식의 값은 바뀌지 않는다.
    static String forKeyAndLog(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String printable = value.replaceAll("[^\\x20-\\x7E]", "_");
        String ipChars = printable.replaceAll("[^0-9A-Fa-f:.]", "_");
        return ipChars.length() > MAX_LENGTH ? ipChars.substring(0, MAX_LENGTH) : ipChars;
    }

    private boolean isTrustedProxy(String ip) {
        if (parseIp(ip).isEmpty()) {
            return false;
        }
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(ip));
    }

    // 헤더가 여러 줄로 와도 순서대로 이어 붙인 하나의 목록으로 본다 (RFC 7230 3.2.2).
    private static List<String> forwardedFor(HttpServletRequest request) {
        List<String> entries = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(X_FORWARDED_FOR))) {
            for (String entry : header.split(",", -1)) {
                entries.add(entry.strip());
            }
        }
        return entries;
    }

    /** IP 리터럴이면 표준 표기로 바꿔 돌려준다. 호스트 이름이나 다른 값은 받지 않는다. */
    static Optional<String> parseIp(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (IPV4.matcher(value).matches()) {
            return Optional.of(value);
        }
        if (value.indexOf(':') >= 0 && IPV6_CHARS.matcher(value).matches()) {
            try {
                return Optional.of(InetAddress.getByName(value).getHostAddress());
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
