package pkg

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.walmartlabs.concord.runtime.v2.sdk.Context

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class USPToggle extends Script {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module())

    @Override
    Object run() {
        var input = getExecution().variables()
        var unifiHost = input.getString("unifiHost", 'https://192.168.1.1')
        var plugName = input.assertString("plugName")
        var unifiCreds = MAPPER.convertValue(input.assertMap("unifiCreds"), Credentials.class)
        var relayState = input.assertBoolean("relayState")

        var cookieHandler = auth(unifiHost, unifiCreds)

        var device = getDeviceInfo(unifiHost, cookieHandler).stream()
                .filter {it.model == 'UP1' }
                .filter {it.name == plugName }
                .findFirst()
                .get()

        var payload = new HashMap<String, Object>().tap {
            put('name', device['name'])
            put('outlet_overrides', [device['outlet_overrides'][0]])
            put('led_override', device['led_override'])
            put('led_override_color_brightness', device['led_override_color_brightness'])
            put('led_override_color', device['led_override_color'])
            put('config_network', device['config_network'])
            put('mgmt_network_id', device['mgmt_network_id'])
        }

        payload['outlet_overrides'][0]['relay_state'] = relayState

        updateDeviceInfo(unifiHost, payload, device["_id"].toString(), cookieHandler)

        return null
    }

    private static void updateDeviceInfo(String unifiHost, Map<String, Object> payload, String deviceId, AuthInfo auth) {
        var req = HttpRequest.newBuilder()
                .uri(new URI("${unifiHost}/proxy/network/api/s/default/rest/device/${deviceId}"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-csrf-token", auth.token)
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build()

        HttpResponse<InputStream> resp = getHttpClient(auth.cookieHandler)
                .send(req, HttpResponse.BodyHandlers.ofInputStream())

        if (resp.statusCode() != 200) {
            throw new IllegalStateException("invalid response code: ${resp.statusCode()}")
        }
    }

    private static List<Map<String, Object>> getDeviceInfo(String unifiHost, AuthInfo a) {
        var req = HttpRequest.newBuilder()
                .uri(new URI("${unifiHost}/proxy/network/api/s/default/stat/device"))
                .header("Accept", "application/json")
                .header("x-csrf-token", a.token)
                .GET()
                .build()

        HttpResponse<InputStream> resp = getHttpClient(a.cookieHandler)
            .send(req, HttpResponse.BodyHandlers.ofInputStream())

        if (resp.statusCode() != 200) {
            throw new IllegalStateException("invalid response code: ${resp.statusCode()}")
        }

        return resp.body().withCloseable {
            JavaType t = MAPPER.typeFactory.constructMapType(HashMap.class, String.class, Object.class)
            return (List<Map<String, Object>>) MAPPER.readValue(it, t)['data']
        }
    }

    private static HttpClient getHttpClient(CookieHandler cookieHandler) {
        return HttpClient.newBuilder()
                .cookieHandler(cookieHandler)
                .sslContext(SSLContext.getInstance("TLS").tap {
                    init(null, (TrustManager[]) [NOOP_TRUST_MANAGER ].toArray(), new SecureRandom())
                })
                .build()
    }

    private static class AuthInfo {
        private final String token
        private final CookieHandler cookieHandler

        AuthInfo(String token, CookieHandler cookieHandler) {
            this.token = token
            this.cookieHandler = cookieHandler
        }
    }

    private static AuthInfo auth(String unifiHost, Credentials creds) {
        var cookieHandler = new CookieManager()
        var HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookieHandler)
                .sslContext(SSLContext.getInstance("TLS").tap {
                    init(null, (TrustManager[]) [NOOP_TRUST_MANAGER ].toArray(), new SecureRandom())
                })
                .build()

        var req = HttpRequest.newBuilder()
            .uri(new URI("${unifiHost}/api/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(authBody(creds), StandardCharsets.UTF_8))
            .build()

        HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding())
        if (resp.statusCode() == 200) {
            var token = resp.headers().firstValue("x-csrf-token").get()

            return new AuthInfo(token, cookieHandler)
        }

        throw new IllegalStateException("no csrf token found")
    }

    static String authBody(Credentials creds) {
        return MAPPER.writeValueAsString(creds)
    }

    // TODO import self-signed cert for actual trust
    private static final TrustManager NOOP_TRUST_MANAGER = new X509ExtendedTrustManager() {
        @Override
        X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0]
        }

        @Override
        void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // empty method
        }

        @Override
        void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

        }

        @Override
        void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

        }

        @Override
        void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

        }

        @Override
        void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

        }
    }

    private static class Credentials {
        private String username
        private String password

        Credentials(@JsonProperty("username") String username, @JsonProperty("password") String password) {
            this.username = username
            this.password = password
        }

        String getUsername() {
            return username
        }

        String getPassword() {
            return password
        }
    }

    private Context getExecution() {
        return Optional.of(getBinding().getVariable("execution"))
            .filter { it instanceof Context }
            .map { (Context) it }
            .orElseThrow(() -> new IllegalStateException("Cannot find valid Context instance"))
    }
}
