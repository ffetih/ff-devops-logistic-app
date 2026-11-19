package com.siose.ff.logistics;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.storage.ReferenceOnlyStorageAdapter;
import com.siose.ff.base.storage.StorageObjectDescriptor;
import com.siose.ff.logistics.geo.GeoRequests;
import com.siose.ff.logistics.tms.TmsRequests;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Upload and download links for a stop's proof of delivery and a shipment's e-CMR, against an
 * in-memory store that signs pretend links and holds what the test says was uploaded.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentLinksApiTest {

    /** Signs links to http://store.test; a document counts as stored once {@link #upload} names its key. */
    static class InMemoryStore extends ReferenceOnlyStorageAdapter {

        private final Set<String> stored = ConcurrentHashMap.newKeySet();

        InMemoryStore() {
            super("ff-documents");
        }

        void upload(String putUrl) {
            stored.add(putUrl.substring("http://store.test/put/".length()));
        }

        @Override
        public boolean exists(StorageObjectDescriptor document) {
            return stored.contains(document.objectKey());
        }

        @Override
        public URI uploadUrl(StorageObjectDescriptor document, Duration validFor) {
            return URI.create("http://store.test/put/" + document.objectKey());
        }

        @Override
        public URI downloadUrl(StorageObjectDescriptor document, Duration validFor) {
            return URI.create("http://store.test/get/" + document.objectKey());
        }
    }

    @TestConfiguration
    static class Store {

        @Bean
        ObjectStoragePort objectStoragePort() {
            return new InMemoryStore();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectStoragePort storage;

    private final String tenant = newTenant();

    @Test
    void aStopsSignatureIsUploadedOnceAndReadBack() throws Exception {
        mvc.perform(GeoRequests.json("/api/v1/geo/territories", GeoRequests.territory("IST-EU", GeoRequests.HUB_GLN))
                .with(as(tenant, "DISPATCHER"))).andExpect(status().isCreated());
        String route = mvc.perform(GeoRequests.json("/api/v1/geo/routes", GeoRequests.route("IST-EU", GeoRequests.fourStops()))
                        .with(as(tenant, "DISPATCHER")))
                .andReturn().getResponse().getContentAsString();
        String routeNumber = JsonPath.read(route, "$.routeNumber");
        String epod = "/api/v1/geo/routes/" + JsonPath.read(route, "$.id") + "/stops/"
                + JsonPath.read(route, "$.stops[0].id") + "/epod";

        mvc.perform(get(epod + "/download-link").with(as(tenant, "DISPATCHER")))
                .andExpect(status().isNotFound());

        String link = mvc.perform(post(epod + "/upload-link").with(as(tenant, "DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.url").value("http://store.test/put/" + tenant + "/epod/"
                        + routeNumber.toLowerCase() + "-ist-esn/signature.png"))
                .andExpect(jsonPath("$.validForSeconds").value(900))
                .andExpect(jsonPath("$.headers.Content-Type").value("image/png"))
                .andExpect(jsonPath("$.headers.If-None-Match").value("*"))
                .andReturn().getResponse().getContentAsString();
        // Asked again before the upload: the same location.
        mvc.perform(post(epod + "/upload-link").with(as(tenant, "DISPATCHER")))
                .andExpect(jsonPath("$.url").value(JsonPath.<String>read(link, "$.url")));

        ((InMemoryStore) storage).upload(JsonPath.read(link, "$.url"));

        mvc.perform(post(epod + "/upload-link").with(as(tenant, "DISPATCHER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:document-already-stored"));
        mvc.perform(get(epod + "/download-link").with(as(tenant, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.url").value(Matchers.startsWith("http://store.test/get/" + tenant + "/epod/")));
        mvc.perform(get(epod + "/download-link").with(as(newTenant(), "DISPATCHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aShipmentsEcmrIsUploadedOnceAndReadBack() throws Exception {
        String shipment = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.shipment("CSQU3054383", "106141411234567897", "Cologne Eifeltor"))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(shipment, "$.shipmentNumber");
        String ecmr = "/api/v1/tms/shipments/" + JsonPath.read(shipment, "$.id") + "/ecmr";

        mvc.perform(get(ecmr + "/download-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isNotFound());
        String link = mvc.perform(post(ecmr + "/upload-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://store.test/put/" + tenant + "/ecmr/"
                        + number.toLowerCase() + "/ecmr.pdf"))
                .andExpect(jsonPath("$.headers.Content-Type").value("application/pdf"))
                .andReturn().getResponse().getContentAsString();

        ((InMemoryStore) storage).upload(JsonPath.read(link, "$.url"));

        mvc.perform(post(ecmr + "/upload-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isConflict());
        String download = mvc.perform(get(ecmr + "/download-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(download, "$.url")).endsWith("/ecmr.pdf");
    }
}
