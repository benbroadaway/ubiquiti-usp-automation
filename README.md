# Ubiquiti Unifi USP-Plug Automation

Power scheduling for wifi plug from Ubiquiti.

## Project Setup

The project requires JDK 17 and Gradle for development.

Install some gradle version (e.g. on macos)

```text
$ brew install gradle
```

Initialize wrapper, customize url and version as necessary

```text
$ gradle wrapper --gradle-distribution-url https://services.gradle.org/distributions/gradle-8.3-all.zip
$ ./gradlew --version                                                                                                                                                                                  ─╯
------------------------------------------------------------
Gradle 8.3
------------------------------------------------------------
```

## Concord Workflow

Included in this repo is a [Concord workflow](https://github.com/walmartlabs/concord)
definition to automate scheduling on and off states for a given USP plug.

It can be run as a normal project, or as a payload with `run.sh`.

```text
# enable power
$ SERVER_URL=https://my-concord-server ORG=myOrg PROJECT=myProject ACTIVE_PROFILES=state_on ./run.sh

# disable power
$ SERVER_URL=https://my-concord-server ORG=myOrg PROJECT=myProject ACTIVE_PROFILES=state_off ./run.sh
```

See the [main Concord docs](https://concord.walmartlabs.com/docs/getting-started/index.html)
for more info on running with Concord.

## General Ways to Interact With The API

### Authenticate and get a cookie for subsequent calls

```text
$ read UDM_USER
$ read -s UDM_PASS

$ curl -kv -X POST \
    --data "{\"username\": \"${UDM_USER}\", \"password\": \"${UDM_PASS}\"}" \
    --header 'Content-Type: application/json' \
    -D headers.txt \
    -c cookie.txt \
    https://192.168.1.1:443/api/auth/login
```

### List Device Info

```text
$ curl -ks -X GET \
    -b cookie.txt \
    -o devices.json \
    https://192.168.1.1:443/proxy/network/api/s/default/stat/device
```

### Update USP Settings

The current info for the device can be modified to send back with different
settings. This requires the CSRF header returned from a previous call.

- The end of the url path (`{deviceId}` below) must be the device ID to update
- The `relay_state` attribute in the payload controls the power outlet.

```text
curl -sk -X PUT \
    -b cookie.txt \
    --header "x-csrf-token: $csrfHeader" \
    --data '
    {
        "name": "My-Plug-Name",
        "outlet_overrides": [
            {
                "index": 1,
                "has_relay": true,
                "has_metering": false,
                "relay_state": true,
                "cycle_enabled": false,
                "name": "Outlet 1"
            }
        ],
        "led_override": "on",
        "led_override_color_brightness": 100,
        "led_override_color": "#0000FF",
        "config_network": {
            "type": "dhcp",
            "bonding_enabled": false
        },
        "mgmt_network_id": "123456789012345678901234"
    }
    '
    -o response.json \
    -w "%{http_code}" \
    https://192.168.1.1:443/proxy/network/api/s/default/rest/device/{deviceId}
```
