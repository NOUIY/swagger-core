package io.swagger.v3.core.deserialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.matchers.SerializationMatchers;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.ResourceUtils;
import io.swagger.v3.core.util.TestUtils;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.EncodingProperty;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class JsonDeserializationTest {
    private final ObjectMapper m = Json.mapper();

    @Test(description = "it should deserialize the petstore")
    public void testPetstore() throws IOException {
        final String json = ResourceUtils.loadClassResource(getClass(), "specFiles/petstore-3.0.json");
        final Object swagger = m.readValue(json, OpenAPI.class);
        assertTrue(swagger instanceof OpenAPI);
    }

    @Test(description = "it should deserialize the composition test")
    public void testCompositionTest() throws IOException {
        final String json = ResourceUtils.loadClassResource(getClass(), "specFiles/compositionTest-3.0.json");
        final Object deserialized = m.readValue(json, OpenAPI.class);
        assertTrue(deserialized instanceof OpenAPI);
        OpenAPI openAPI = (OpenAPI) deserialized;
        Schema lizardSchema = openAPI.getComponents().getSchemas().get("Lizard");
        assertTrue(lizardSchema instanceof ComposedSchema);
        assertEquals(((ComposedSchema) lizardSchema).getAllOf().size(), 2);

        Schema petSchema = openAPI.getComponents().getSchemas().get("Pet");
        assertEquals(petSchema.getDiscriminator().getPropertyName(), "pet_type");
        assertEquals(petSchema.getDiscriminator().getMapping().get("cachorro"), "#/components/schemas/Dog");

    }

    @Test(description = "it should deserialize a simple ObjectProperty")
    public void testObjectProperty() throws IOException {
        final String json = "{\n" +
                "   \"type\":\"object\",\n" +
                "   \"title\":\"objectProperty\",\n" +
                "   \"description\":\"top level object\",\n" +
                "   \"properties\":{\n" +
                "      \"property1\":{\n" +
                "         \"type\":\"string\",\n" +
                "         \"description\":\"First property\"\n" +
                "      },\n" +
                "      \"property2\":{\n" +
                "         \"type\":\"string\",\n" +
                "         \"description\":\"Second property\"\n" +
                "      },\n" +
                "      \"property3\":{\n" +
                "         \"type\":\"string\",\n" +
                "         \"description\":\"Third property\"\n" +
                "      }\n" +
                "   }\n" +
                "}";
        final Schema result = m.readValue(json, Schema.class);
        assertEquals(3, result.getProperties().size());
        assertEquals("objectProperty", result.getTitle());
    }

    @Test(description = "it should deserialize nested ObjectProperty(s)")
    public void testNestedObjectProperty() throws IOException {
        final String json = "{\n" +
                "   \"type\":\"object\",\n" +
                "   \"description\":\"top level object\",\n" +
                "   \"properties\":{\n" +
                "      \"property1\":{\n" +
                "         \"type\":\"string\",\n" +
                "         \"description\":\"First property\"\n" +
                "      },\n" +
                "      \"property2\":{\n" +
                "         \"type\":\"string\",\n" +
                "         \"description\":\"Second property\"\n" +
                "      },\n" +
                "      \"property3\":{\n" +
                "         \"type\":\"object\",\n" +
                "         \"description\":\"Third property\",\n" +
                "         \"properties\":{\n" +
                "            \"property1\":{\n" +
                "               \"type\":\"string\",\n" +
                "               \"description\":\"First nested property\"\n" +
                "            }\n" +
                "         }\n" +
                "      }\n" +
                "   }\n" +
                "}";
        final Schema result = m.readValue(json, Schema.class);
        final Map<String, Schema> firstLevelProperties = result.getProperties();
        assertEquals(firstLevelProperties.size(), 3);

        final Schema property3 = firstLevelProperties.get("property3");

        final Map<String, Schema> secondLevelProperties = property3.getProperties();
        assertEquals(secondLevelProperties.size(), 1);
    }

    @Test
    public void testDeserializePetStoreFile() throws Exception {
        TestUtils.deserializeJsonFileFromClasspath("specFiles/petstore.json", OpenAPI.class);
    }

    @Test
    public void testDeserializeCompositionTest() throws Exception {
        TestUtils.deserializeJsonFileFromClasspath("specFiles/compositionTest.json", OpenAPI.class);
    }

    @Test
    public void testDeserializeAPathRef() throws Exception {
        final OpenAPI oas = TestUtils.deserializeJsonFileFromClasspath("specFiles/pathRef.json", OpenAPI.class);

        final PathItem petPath = oas.getPaths().get("/pet");
        assertNotNull(petPath.get$ref());
        assertEquals(petPath.get$ref(), "http://my.company.com/paths/health.json");
        assertTrue(oas.getPaths().get("/user") instanceof PathItem);
    }

    @Test
    public void testDeserializeAResponseRef() throws Exception {
        final OpenAPI oas = TestUtils.deserializeJsonFileFromClasspath("specFiles/responseRef.json", OpenAPI.class);

        final ApiResponses responseMap = oas.getPaths().get("/pet").getPut().getResponses();

        // TODO: missing response ref
        assertIsRefResponse(responseMap.get("405"), "http://my.company.com/responses/errors.json#/method-not-allowed");
        assertIsRefResponse(responseMap.get("404"), "http://my.company.com/responses/errors.json#/not-found");
        assertTrue(responseMap.get("400") instanceof ApiResponse);
    }

    private void assertIsRefResponse(Object response, String expectedRef) {
        assertTrue(response instanceof ApiResponse);

        ApiResponse refResponse = (ApiResponse) response;
        assertEquals(refResponse.get$ref(), expectedRef);
    }

    @Test
    public void testDeserializeSecurity() throws Exception {
        final OpenAPI swagger = TestUtils.deserializeJsonFileFromClasspath("specFiles/securityDefinitions.json", OpenAPI.class);

        final List<SecurityRequirement> security = swagger.getSecurity();
        assertNotNull(security);
        assertEquals(security.size(), 3);

        final Map<String, SecurityScheme> securitySchemes = swagger.getComponents().getSecuritySchemes();
        assertNotNull(securitySchemes);
        assertEquals(securitySchemes.size(), 4);

        {
            final SecurityScheme scheme = securitySchemes.get("petstore_auth");
            assertNotNull(scheme);
            assertEquals(scheme.getType().toString(), "oauth2");
            assertEquals(scheme.getFlows().getImplicit().getAuthorizationUrl(), "http://petstore.swagger.io/oauth/dialog");
            assertEquals(scheme.getFlows().getImplicit().getScopes().get("write:pets"), "modify pets in your account");
            assertEquals(scheme.getFlows().getImplicit().getScopes().get("read:pets"), "read your pets");
        }

        {
            final SecurityScheme scheme = securitySchemes.get("api_key");
            assertNotNull(scheme);
            assertEquals(scheme.getType().toString(), "apiKey");
            assertEquals(scheme.getIn().toString(), "header");
            assertEquals(scheme.getName(), "api_key");
        }

        {
            final SecurityScheme scheme = securitySchemes.get("http");
            assertNotNull(scheme);
            assertEquals(scheme.getType().toString(), "http");
            assertEquals(scheme.getScheme(), "basic");
        }

        {
            final SecurityScheme scheme = securitySchemes.get("open_id_connect");
            assertNotNull(scheme);
            assertEquals(scheme.getType().toString(), "openIdConnect");
            assertEquals(scheme.getOpenIdConnectUrl(), "http://petstore.swagger.io/openid");
        }

        {
            final SecurityRequirement securityRequirement = security.get(0);
            final List<String> scopes = securityRequirement.get("petstore_auth");
            assertNotNull(scopes);
            assertEquals(scopes.size(), 2);
            assertTrue(scopes.contains("write:pets"));
            assertTrue(scopes.contains("read:pets"));

        }

        {
            final SecurityRequirement securityRequirement = security.get(1);
            final List<String> scopes = securityRequirement.get("api_key");
            assertNotNull(scopes);
            assertTrue(scopes.isEmpty());

        }

        {
            final SecurityRequirement securityRequirement = security.get(2);
            final List<String> scopes = securityRequirement.get("http");
            assertNotNull(scopes);
            assertTrue(scopes.isEmpty());

        }
    }

    @Test(description = "it should deserialize a Header with style")
    public void deserializeHeaderWithStyle() throws IOException {
        final String json = "{\"description\":\"aaaa\",\"style\":\"simple\"}";
        final Header p = m.readValue(json, Header.class);
        SerializationMatchers.assertEqualsToJson(p, json);
    }

    @Test(description = "it should deserialize an Encoding with style")
    public void deserializeEncodingWithStyle() throws IOException {
        final String json = "{\"style\":\"spaceDelimited\"}";
        final Encoding p = m.readValue(json, Encoding.class);
        SerializationMatchers.assertEqualsToJson(p, json);
    }

    @Test(description = "it should deserialize an EncodingProperty with style")
    public void deserializeEncodingPropertyWithStyle() throws IOException {
        final String json = "{\"style\":\"spaceDelimited\"}";
        final EncodingProperty p = m.readValue(json, EncodingProperty.class);
        SerializationMatchers.assertEqualsToJson(p, json);
    }

    @Test(description = "it should desserialize Long schema correctly")
    public void deserializeLongSchema() throws IOException {

        final String jsonString = ResourceUtils.loadClassResource(getClass(), "specFiles/oas3_2.yaml");
        final OpenAPI swagger = Yaml.mapper().readValue(jsonString, OpenAPI.class);
        assertNotNull(swagger);
        Schema s = swagger.getPaths().get("/withIntegerEnum/{stage}").getGet().getParameters().get(0).getSchema();
        assertEquals(s.getEnum().get(0), 2147483647);
        assertEquals(s.getEnum().get(1), 3147483647L);
        assertEquals(s.getEnum().get(2), 31474836475505055L);
        assertEquals(s.getEnum().get(3), -9223372036854775808L);
    }

    @Test
    public void deserializeDateExample() throws IOException {

        final String jsonString = ResourceUtils.loadClassResource(getClass(), "specFiles/swos-126.yaml");
        final OpenAPI swagger = Yaml.mapper().readValue(jsonString, OpenAPI.class);
        assertNotNull(swagger);
        Map<String, Schema> props = swagger.getComponents().getSchemas().get("MyModel").getProperties();
        assertTrue(Yaml.pretty().writeValueAsString(props.get("date")).contains("example: 2019-08-05"));
        assertTrue(Yaml.pretty().writeValueAsString(props.get("dateTime")).contains("example: 2019-08-05T12:34:56Z"));

    }

    @Test(description = "Deserialize ref callback")
    public void testDeserializeRefCallback() throws Exception {
        String yaml = "openapi: 3.0.1\n" +
                "info:\n" +
                "  description: info\n" +
                "paths:\n" +
                "  /simplecallback:\n" +
                "    get:\n" +
                "      summary: Simple get operation\n" +
                "      operationId: getWithNoParameters\n" +
                "      responses:\n" +
                "        \"200\":\n" +
                "          description: voila!\n" +
                "      callbacks:\n" +
                "        testCallback1:\n" +
                "          $ref: \"#/components/callbacks/Callback\"\n" +
                "      callbacks:\n" +
                "        testCallback1:\n" +
                "          $ref: \"#/components/callbacks/Callback\"\n" +
                "components:\n" +
                "  callbacks:\n" +
                "    Callback:\n" +
                "      /post:\n" +
                "        description: Post Path Item\n";

        OpenAPI oas = Yaml.mapper().readValue(yaml, OpenAPI.class);
        assertEquals(oas.getPaths().get("/simplecallback").getGet().getCallbacks().get("testCallback1").get$ref(), "#/components/callbacks/Callback");
    }

    @Test(description = "Deserialize null enum item")
    public void testNullEnumItem() throws Exception {
        String yaml = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      tags:\n" +
                "      - MyTag\n" +
                "      summary: Operation Summary\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "      parameters:\n" +
                "      - name: subscriptionId\n" +
                "        in: query\n" +
                "        schema:\n" +
                "          type: string\n" +
                "      responses:\n" +
                "        default:\n" +
                "          description: default response\n" +
                "          content:\n" +
                "            '*/*': {}\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: integer\n" +
                "      description: some int values with null\n" +
                "      format: int32\n" +
                "      enum:\n" +
                "      - 1\n" +
                "      - 2\n" +
                "      - null\n";

        OpenAPI oas = Yaml.mapper().readValue(yaml, OpenAPI.class);

        assertEquals(oas.getComponents().getSchemas().get("UserStatus").getEnum(), Arrays.asList(1, 2, null));

        yaml = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      tags:\n" +
                "      - MyTag\n" +
                "      summary: Operation Summary\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "      parameters:\n" +
                "      - name: subscriptionId\n" +
                "        in: query\n" +
                "        schema:\n" +
                "          type: string\n" +
                "      responses:\n" +
                "        default:\n" +
                "          description: default response\n" +
                "          content:\n" +
                "            '*/*': {}\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: string\n" +
                "      description: some int values with null\n" +
                "      enum:\n" +
                "      - 1\n" +
                "      - 2\n" +
                "      - null\n";

        oas = Yaml.mapper().readValue(yaml, OpenAPI.class);

        assertEquals(oas.getComponents().getSchemas().get("UserStatus").getEnum(), Arrays.asList("1", "2", null));
    }

    @Test
    public void testNullExampleDeserialization() throws Exception {
        String yamlNull = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: string\n" +
                "      example: null\n";

        String yamlMissing = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: string\n";

        OpenAPI oas = Yaml.mapper().readValue(yamlNull, OpenAPI.class);
        Yaml.prettyPrint(oas);
        assertNull(oas.getComponents().getSchemas().get("UserStatus").getExample());
        assertTrue(oas.getComponents().getSchemas().get("UserStatus").getExampleSetFlag());

        oas = Yaml.mapper().readValue(yamlMissing, OpenAPI.class);
        Yaml.prettyPrint(oas);
        assertNull(oas.getComponents().getSchemas().get("UserStatus").getExample());
        assertFalse(oas.getComponents().getSchemas().get("UserStatus").getExampleSetFlag());
        Yaml.prettyPrint(oas);
    }

    @Test
    public void testNullExampleAndValues() throws Exception {
        String yamlNull = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: object\n" +
                "      example: null\n";

        String yamlMissing = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: object\n";

        String yamlNotNull = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  schemas:\n" +
                "    UserStatus:\n" +
                "      type: object\n" +
                "      example:\n" +
                "        value: bar\n";

        String yamlValueNull = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  examples:\n" +
                "    UserStatus:\n" +
                "      summary: string\n" +
                "      value: null\n";

        String yamlValueMissing = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  examples:\n" +
                "    UserStatus:\n" +
                "      summary: string\n";

        String yamlValueNotNull = "openapi: 3.0.1\n" +
                "paths:\n" +
                "  /:\n" +
                "    get:\n" +
                "      description: Operation Description\n" +
                "      operationId: operationId\n" +
                "components:\n" +
                "  examples:\n" +
                "    UserStatus:\n" +
                "      summary: string\n" +
                "      value: bar\n";

        OpenAPI oas = Yaml.mapper().readValue(yamlNull, OpenAPI.class);
        Yaml.prettyPrint(oas);

        assertNull(oas.getComponents().getSchemas().get("UserStatus").getExample());
        assertTrue(oas.getComponents().getSchemas().get("UserStatus").getExampleSetFlag());
        assertEquals(Yaml.pretty(oas), yamlNull);

        oas = Yaml.mapper().readValue(yamlMissing, OpenAPI.class);
        Yaml.prettyPrint(oas);
        assertNull(oas.getComponents().getSchemas().get("UserStatus").getExample());
        assertFalse(oas.getComponents().getSchemas().get("UserStatus").getExampleSetFlag());
        assertEquals(Yaml.pretty(oas), yamlMissing);

        oas = Yaml.mapper().readValue(yamlNotNull, OpenAPI.class);
        Yaml.prettyPrint(oas);
        assertNotNull(oas.getComponents().getSchemas().get("UserStatus").getExample());
        assertTrue(oas.getComponents().getSchemas().get("UserStatus").getExampleSetFlag());
        assertEquals(Yaml.pretty(oas), yamlNotNull);

        oas = Yaml.mapper().readValue(yamlValueNull, OpenAPI.class);
        Yaml.prettyPrint(oas);
        Example ex = oas.getComponents().getExamples().get("UserStatus");
        assertNull(ex.getValue());
        assertTrue(ex.getValueSetFlag());
        assertEquals(Yaml.pretty(oas), yamlValueNull);

        oas = Yaml.mapper().readValue(yamlValueMissing, OpenAPI.class);
        Yaml.prettyPrint(oas);
        ex = oas.getComponents().getExamples().get("UserStatus");
        assertNull(ex.getValue());
        assertFalse(ex.getValueSetFlag());
        assertEquals(Yaml.pretty(oas), yamlValueMissing);

        oas = Yaml.mapper().readValue(yamlValueNotNull, OpenAPI.class);
        Yaml.prettyPrint(oas);
        ex = oas.getComponents().getExamples().get("UserStatus");
        assertNotNull(ex.getValue());
        assertTrue(ex.getValueSetFlag());
        assertEquals(Yaml.pretty(oas), yamlValueNotNull);
    }

    @Test
    public void testExampleDeserializationOnMediaType() throws Exception {
        String content = FileUtils.readFileToString(new File("src/test/resources/specFiles/media-type-null-example.yaml"), "UTF-8");
        OpenAPI openAPI = Yaml.mapper().readValue(content, OpenAPI.class);

        assertNull(openAPI.getPaths().get("/pets/{petId}").getGet().getResponses().get("200").getContent().get("application/json").getExample());
        assertTrue(openAPI.getPaths().get("/pets/{petId}").getGet().getResponses().get("200").getContent().get("application/json").getExampleSetFlag());

        assertNull(openAPI.getPaths().get("/pet").getPost().getResponses().get("200").getContent().get("application/json").getExample());
        assertFalse(openAPI.getPaths().get("/pet").getPost().getResponses().get("200").getContent().get("application/json").getExampleSetFlag());

        assertNotNull(openAPI.getPaths().get("/pet").getPost().getRequestBody().getContent().get("application/json").getExample());

        assertTrue(openAPI.getPaths().get("/pet").getPost().getRequestBody().getContent().get("application/json").getExampleSetFlag());
    }

    @Test
    public void testDateSchemaSerialization() throws Exception {
        String content = FileUtils.readFileToString(new File("src/test/resources/dateSchema.yaml"), "UTF-8");
        OpenAPI openAPI = Yaml.mapper().readValue(content, OpenAPI.class);
        Yaml.prettyPrint(openAPI);
        SerializationMatchers.assertEqualsToYaml(openAPI, "openapi: 3.0.3\n" +
                "info:\n" +
                "  title: Simple Inventory API\n" +
                "  version: 1.0.0\n" +
                "paths:\n" +
                "  /inventory:\n" +
                "    get:\n" +
                "      operationId: searchInventory\n" +
                "      parameters:\n" +
                "      - name: test\n" +
                "        in: header\n" +
                "        schema:\n" +
                "          type: string\n" +
                "          format: date\n" +
                "          enum:\n" +
                "          - 2023-12-12\n" +
                "          default: 2023-12-12\n" +
                "      responses:\n" +
                "        \"200\":\n" +
                "          description: search results matching criteria\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                type: array\n" +
                "                items:\n" +
                "                  $ref: \"#/components/schemas/InventoryItem\"\n" +
                "components:\n" +
                "  schemas:\n" +
                "    InventoryItem:\n" +
                "      type: object\n" +
                "      properties:\n" +
                "        releaseDate:\n" +
                "          type: string\n" +
                "          format: date-time\n" +
                "          example: 2016-08-29T09:12:33.001Z");
    }

}
