/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.server;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.jaxrs.testing.GuavaMultivaluedMap;
import io.trino.client.ProtocolHeaders;
import io.trino.spi.security.Identity;
import io.trino.spi.security.SelectedRole;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import java.util.Optional;

import static io.trino.SystemSessionProperties.HASH_PARTITION_COUNT;
import static io.trino.SystemSessionProperties.JOIN_DISTRIBUTION_TYPE;
import static io.trino.SystemSessionProperties.QUERY_MAX_MEMORY;
import static io.trino.client.ProtocolHeaders.TRINO_HEADERS;
import static io.trino.client.ProtocolHeaders.createProtocolHeaders;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testng.Assert.assertEquals;

public class TestHttpRequestSessionContext
{
    @Test
    public void testSessionContext()
    {
        assertSessionContext(TRINO_HEADERS);
        assertSessionContext(createProtocolHeaders("taco"));
    }

    private static void assertSessionContext(ProtocolHeaders protocolHeaders)
    {
        MultivaluedMap<String, String> headers = new GuavaMultivaluedMap<>(ImmutableListMultimap.<String, String>builder()
                .put(protocolHeaders.requestUser(), "testUser")
                .put(protocolHeaders.requestSource(), "testSource")
                .put(protocolHeaders.requestCatalog(), "testCatalog")
                .put(protocolHeaders.requestSchema(), "testSchema")
                .put(protocolHeaders.requestPath(), "testPath")
                .put(protocolHeaders.requestLanguage(), "zh-TW")
                .put(protocolHeaders.requestTimeZone(), "Asia/Taipei")
                .put(protocolHeaders.requestClientInfo(), "client-info")
                .put(protocolHeaders.requestSession(), QUERY_MAX_MEMORY + "=1GB")
                .put(protocolHeaders.requestSession(), JOIN_DISTRIBUTION_TYPE + "=partitioned," + HASH_PARTITION_COUNT + " = 43")
                .put(protocolHeaders.requestSession(), "some_session_property=some value with %2C comma")
                .put(protocolHeaders.requestPreparedStatement(), "query1=select * from foo,query2=select * from bar")
                .put(protocolHeaders.requestRole(), "foo_connector=ALL")
                .put(protocolHeaders.requestRole(), "bar_connector=NONE")
                .put(protocolHeaders.requestRole(), "foobar_connector=ROLE{role}")
                .put(protocolHeaders.requestExtraCredential(), "test.token.foo=bar")
                .put(protocolHeaders.requestExtraCredential(), "test.token.abc=xyz")
                .build());

        SessionContext context = new HttpRequestSessionContext(headers, Optional.of(protocolHeaders.getProtocolName()), "testRemote", Optional.empty(), ImmutableSet::of);
        assertEquals(context.getSource(), "testSource");
        assertEquals(context.getCatalog(), "testCatalog");
        assertEquals(context.getSchema(), "testSchema");
        assertEquals(context.getPath(), "testPath");
        assertEquals(context.getIdentity(), Identity.ofUser("testUser"));
        assertEquals(context.getClientInfo(), "client-info");
        assertEquals(context.getLanguage(), "zh-TW");
        assertEquals(context.getTimeZoneId(), "Asia/Taipei");
        assertEquals(context.getSystemProperties(), ImmutableMap.of(
                QUERY_MAX_MEMORY, "1GB",
                JOIN_DISTRIBUTION_TYPE, "partitioned",
                HASH_PARTITION_COUNT, "43",
                "some_session_property", "some value with , comma"));
        assertEquals(context.getPreparedStatements(), ImmutableMap.of("query1", "select * from foo", "query2", "select * from bar"));
        assertEquals(context.getIdentity().getRoles(), ImmutableMap.of(
                "foo_connector", new SelectedRole(SelectedRole.Type.ALL, Optional.empty()),
                "bar_connector", new SelectedRole(SelectedRole.Type.NONE, Optional.empty()),
                "foobar_connector", new SelectedRole(SelectedRole.Type.ROLE, Optional.of("role"))));
        assertEquals(context.getIdentity().getExtraCredentials(), ImmutableMap.of("test.token.foo", "bar", "test.token.abc", "xyz"));
        assertEquals(context.getIdentity().getGroups(), ImmutableSet.of("testUser"));
    }

    @Test
    public void testMappedUser()
    {
        assertMappedUser(TRINO_HEADERS);
        assertMappedUser(createProtocolHeaders("taco"));
    }

    private static void assertMappedUser(ProtocolHeaders protocolHeaders)
    {
        MultivaluedMap<String, String> userHeaders = new GuavaMultivaluedMap<>(ImmutableListMultimap.of(protocolHeaders.requestUser(), "testUser"));
        MultivaluedMap<String, String> emptyHeaders = new MultivaluedHashMap<>();

        HttpRequestSessionContext context = new HttpRequestSessionContext(userHeaders, Optional.of(protocolHeaders.getProtocolName()), "testRemote", Optional.empty(), ImmutableSet::of);
        assertEquals(context.getIdentity(), Identity.forUser("testUser").withGroups(ImmutableSet.of("testUser")).build());

        context = new HttpRequestSessionContext(
                emptyHeaders,
                Optional.of(protocolHeaders.getProtocolName()),
                "testRemote",
                Optional.of(Identity.forUser("mappedUser").withGroups(ImmutableSet.of("test")).build()),
                ImmutableSet::of);
        assertEquals(context.getIdentity(), Identity.forUser("mappedUser").withGroups(ImmutableSet.of("test", "mappedUser")).build());

        context = new HttpRequestSessionContext(userHeaders, Optional.of(protocolHeaders.getProtocolName()), "testRemote", Optional.of(Identity.ofUser("mappedUser")), ImmutableSet::of);
        assertEquals(context.getIdentity(), Identity.forUser("testUser").withGroups(ImmutableSet.of("testUser")).build());

        assertThatThrownBy(() -> new HttpRequestSessionContext(emptyHeaders, Optional.of(protocolHeaders.getProtocolName()), "testRemote", Optional.empty(), user -> ImmutableSet.of()))
                .isInstanceOf(WebApplicationException.class)
                .matches(e -> ((WebApplicationException) e).getResponse().getStatus() == 400);
    }

    @Test
    public void testPreparedStatementsHeaderDoesNotParse()
    {
        assertPreparedStatementsHeaderDoesNotParse(TRINO_HEADERS);
        assertPreparedStatementsHeaderDoesNotParse(createProtocolHeaders("taco"));
    }

    private static void assertPreparedStatementsHeaderDoesNotParse(ProtocolHeaders protocolHeaders)
    {
        MultivaluedMap<String, String> headers = new GuavaMultivaluedMap<>(ImmutableListMultimap.<String, String>builder()
                .put(protocolHeaders.requestUser(), "testUser")
                .put(protocolHeaders.requestSource(), "testSource")
                .put(protocolHeaders.requestCatalog(), "testCatalog")
                .put(protocolHeaders.requestSchema(), "testSchema")
                .put(protocolHeaders.requestPath(), "testPath")
                .put(protocolHeaders.requestLanguage(), "zh-TW")
                .put(protocolHeaders.requestTimeZone(), "Asia/Taipei")
                .put(protocolHeaders.requestClientInfo(), "null")
                .put(protocolHeaders.requestPreparedStatement(), "query1=abcdefg")
                .build());

        assertThatThrownBy(() -> new HttpRequestSessionContext(headers, Optional.of(protocolHeaders.getProtocolName()), "testRemote", Optional.empty(), user -> ImmutableSet.of()))
                .isInstanceOf(WebApplicationException.class)
                .hasMessageMatching("Invalid " + protocolHeaders.requestPreparedStatement() + " header: line 1:1: mismatched input 'abcdefg'. Expecting: .*");
    }
}
