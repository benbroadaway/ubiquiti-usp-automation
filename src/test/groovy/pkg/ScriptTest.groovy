package pkg

import com.walmartlabs.concord.runtime.v2.sdk.Context
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables
import org.junit.jupiter.api.Test

import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class ScriptTest {

    @Test
    void testScript() {
        var execution = mock(Context.class)

        var creds = [
                "username": assertEnv("TEST_UNIFI_USERNAME"),
                "password": assertEnv("TEST_UNIFI_PASSWORD")
        ]

        var input = new HashMap<String, Object>().tap {
            put("unifiHost", assertEnv("TEST_UNIFI_HOST"))
            put("plugName", assertEnv("TEST_UNIFI_DEVICE"))
            put("relayState", true)
            put("unifiCreds", creds)
        }

        when(execution.variables()).thenReturn(new MapBackedVariables(input))

        var script = new USPToggle().tap {
            getBinding().setVariable("execution", execution)
        }

        script.run()
    }

    private static String assertEnv(String name) {
        return Optional.ofNullable(System.getenv(name))
            .orElseThrow { new IllegalStateException("Required test env var '${name}' not found or null")}
    }
}
