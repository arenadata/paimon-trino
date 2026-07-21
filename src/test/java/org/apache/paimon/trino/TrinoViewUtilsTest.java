/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.trino;

import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.IntType;
import org.apache.paimon.view.View;
import org.apache.paimon.view.ViewImpl;

import io.trino.spi.connector.ConnectorViewDefinition;
import io.trino.spi.type.IntegerType;
import io.trino.spi.type.TypeManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Tests for {@link TrinoViewUtils}. */
public class TrinoViewUtilsTest {

    private static final TypeManager TYPE_MANAGER = TESTING_TYPE_MANAGER;
    private static final Identifier IDENTIFIER = Identifier.create("db", "v");
    private static final String QUERY = "SELECT a FROM t1";

    private static ConnectorViewDefinition definition(
            Optional<String> catalog, Optional<String> schema) {
        return new ConnectorViewDefinition(
                QUERY,
                catalog,
                schema,
                List.of(
                        new ConnectorViewDefinition.ViewColumn(
                                "a", IntegerType.INTEGER.getTypeId(), Optional.empty())),
                Optional.of("a comment"),
                Optional.empty(),
                true,
                List.of());
    }

    private static View paimonView(Map<String, String> options) {
        return new ViewImpl(
                IDENTIFIER,
                List.of(new DataField(0, "a", new IntType())),
                QUERY,
                Map.of("trino", QUERY),
                null,
                options);
    }

    @Test
    public void testCatalogAndSchemaSurviveRoundTrip() {
        ConnectorViewDefinition original = definition(Optional.of("paimon"), Optional.of("db"));

        View view = TrinoViewUtils.toPaimonView(IDENTIFIER, original, TYPE_MANAGER);
        ConnectorViewDefinition restored =
                TrinoViewUtils.toConnectorViewDefinition(view, TYPE_MANAGER);

        assertThat(restored.getCatalog()).contains("paimon");
        assertThat(restored.getSchema()).contains("db");
        assertThat(restored.getOriginalSql()).isEqualTo(QUERY);
        assertThat(restored.getColumns()).hasSize(1);
        assertThat(restored.getColumns().get(0).getName()).isEqualTo("a");
    }

    @Test
    public void testCatalogAndSchemaArePersistedAsOptions() {
        View view =
                TrinoViewUtils.toPaimonView(
                        IDENTIFIER,
                        definition(Optional.of("paimon"), Optional.of("db")),
                        TYPE_MANAGER);

        assertThat(view.options())
                .containsEntry("trino.view.catalog", "paimon")
                .containsEntry("trino.view.schema", "db");
    }

    @Test
    public void testViewWithoutCatalogOrSchemaOptionsRoundTripsToEmpty() {
        ConnectorViewDefinition restored =
                TrinoViewUtils.toConnectorViewDefinition(paimonView(new HashMap<>()), TYPE_MANAGER);

        assertThat(restored.getCatalog()).isEmpty();
        assertThat(restored.getSchema()).isEmpty();
    }

    @Test
    public void testSchemaWithoutCatalogIsDropped() {
        Map<String, String> options = new HashMap<>();
        options.put("trino.view.schema", "db");

        assertThatCode(
                        () ->
                                TrinoViewUtils.toConnectorViewDefinition(
                                        paimonView(options), TYPE_MANAGER))
                .doesNotThrowAnyException();

        assertThat(
                        TrinoViewUtils.toConnectorViewDefinition(paimonView(options), TYPE_MANAGER)
                                .getSchema())
                .isEmpty();
    }

    @Test
    public void testCatalogWithoutSchemaIsKept() {
        Map<String, String> options = new HashMap<>();
        options.put("trino.view.catalog", "paimon");

        ConnectorViewDefinition restored =
                TrinoViewUtils.toConnectorViewDefinition(paimonView(options), TYPE_MANAGER);

        assertThat(restored.getCatalog()).contains("paimon");
        assertThat(restored.getSchema()).isEmpty();
    }
}
