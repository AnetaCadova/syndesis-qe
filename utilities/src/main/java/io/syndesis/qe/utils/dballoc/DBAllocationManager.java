package io.syndesis.qe.utils.dballoc;

import io.syndesis.qe.TestConfiguration;
import io.syndesis.qe.endpoint.client.EndpointClient;

import org.assertj.core.api.Assertions;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DBAllocationManager {

    private static final String URL_GUI = TestConfiguration.getDbAllocatorUrl() + "/Allocator/AllocatorServlet";

    private static final String REQUESTEE = "syndesis-qe";

    private static final int EXPIRE = 60;

    private static final Client CLIENT = EndpointClient.getInsecureClient();

    public static DBAllocation allocate(String dbLabel) {
        return allocate(dbLabel, REQUESTEE, EXPIRE);
    }

    private static DBAllocation allocate(String label, String requestee, int expire) {

        DBAllocation alloc = new DBAllocation(label);
        Invocation.Builder invocation = createAllocateInvocation(label, requestee, expire);

        final Response response = invocation.get();

        final String text = response.readEntity(String.class);

        try {
            alloc.setAllocationMap(loadProps(text));
            alloc.setUuid(alloc.getAllocationMap().get("uuid"));
            return alloc;
        } catch (IOException e) {
            Assertions.fail(e.getMessage());
            return null;
        }
    }

    public static void free(String uuid) {
        Invocation.Builder invocation = createFreeInvocation(uuid);
        invocation.get();
    }

    private static Map<String, String> loadProps(String propsText) throws IOException {
        Properties prop = new Properties();
        Reader reader = new StringReader(propsText);
        prop.load(reader);

        log.info("uuid: *{}*", prop.get("uuid"));

        Map<String, String> map = (Map) prop;
        return map;
    }

    private static Invocation.Builder createAllocateInvocation(String dbLabel, String requestee, int expire) {
        return CLIENT.target(createAllocationEndpointUrl(dbLabel, requestee, expire)).request(MediaType.TEXT_PLAIN);
    }

    private static String createAllocationEndpointUrl(String dbLabel, String requestee, int expire) {
        StringBuilder url = new StringBuilder(URL_GUI);
        url.append("?")
            .append("operation=allocate&")
            .append("expression=").append(dbLabel).append("&")
            .append("expiry=").append(expire).append("&")
            .append("requestee=").append(requestee);
        return url.toString();
    }

    private static Invocation.Builder createFreeInvocation(String uuid) {
        return CLIENT.target(createFreeEdndpointUrl(uuid)).request(MediaType.TEXT_PLAIN);
    }

    private static String createFreeEdndpointUrl(String uuid) {
        StringBuilder url = new StringBuilder(URL_GUI);
        url.append("?");
        url.append("operation=dealloc&");
        url.append("uuid=").append(uuid);
        return url.toString();
    }
}
