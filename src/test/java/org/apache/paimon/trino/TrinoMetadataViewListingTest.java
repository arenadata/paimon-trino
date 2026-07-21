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
import org.apache.paimon.options.Options;
import org.apache.paimon.table.Table;
import org.apache.paimon.trino.catalog.TrinoCatalog;

import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Tests that {@link TrinoMetadata} surfaces views in relation listings. */
public class TrinoMetadataViewListingTest {

    private static final String SCHEMA = "db";

    /** Stubbed catalog for relation listing behavior. */
    private static class StubCatalog extends TrinoCatalog {

        private final List<String> tables;
        private final List<String> views;

        StubCatalog(List<String> tables, List<String> views) {
            super(new Options(), null, null);
            this.tables = tables;
            this.views = views;
        }

        @Override
        public void initSession(ConnectorSession connectorSession) {}

        @Override
        public List<String> listDatabases() {
            return List.of(SCHEMA);
        }

        @Override
        public List<String> listTables(String database) throws DatabaseNotExistException {
            return tables;
        }

        @Override
        public List<String> listViews(String database) throws DatabaseNotExistException {
            return views;
        }

        @Override
        public Table getTable(Identifier identifier) throws TableNotExistException {
            if (!tables.contains(identifier.getObjectName())) {
                throw new TableNotExistException(identifier);
            }
            throw new UnsupportedOperationException("stub does not build real tables");
        }
    }

    private static TrinoMetadata metadata(List<String> tables, List<String> views) {
        return new TrinoMetadata(new StubCatalog(tables, views), TESTING_TYPE_MANAGER);
    }

    @Test
    public void testListTablesIncludesViews() {
        List<SchemaTableName> relations =
                metadata(List.of("t1"), List.of("v1")).listTables(null, Optional.of(SCHEMA));

        assertThat(relations)
                .containsExactlyInAnyOrder(
                        new SchemaTableName(SCHEMA, "t1"), new SchemaTableName(SCHEMA, "v1"));
    }

    @Test
    public void testListTablesAcrossAllSchemasIncludesViews() {
        List<SchemaTableName> relations =
                metadata(List.of("t1"), List.of("v1")).listTables(null, Optional.empty());

        assertThat(relations).contains(new SchemaTableName(SCHEMA, "v1"));
    }

    @Test
    public void testListTablesDeduplicatesNameCollisions() {
        List<SchemaTableName> relations =
                metadata(List.of("x"), List.of("x")).listTables(null, Optional.of(SCHEMA));

        assertThat(relations).containsExactly(new SchemaTableName(SCHEMA, "x"));
    }

    @Test
    public void testListTablesSurvivesSchemaDroppedWhileListing() {
        TrinoMetadata metadata =
                new TrinoMetadata(
                        new StubCatalog(List.of("t1"), List.of("v1")) {
                            @Override
                            public List<String> listTables(String database)
                                    throws DatabaseNotExistException {
                                throw new DatabaseNotExistException(database);
                            }

                            @Override
                            public List<String> listViews(String database)
                                    throws DatabaseNotExistException {
                                throw new DatabaseNotExistException(database);
                            }
                        },
                        TESTING_TYPE_MANAGER);

        assertThat(metadata.listTables(null, Optional.empty())).isEmpty();
    }

    @Test
    public void testListTableColumnsSkipsViewNamedByPrefix() {
        SchemaTablePrefix prefix = new SchemaTablePrefix(SCHEMA, "v1");

        assertThatCode(
                        () ->
                                assertThat(
                                                metadata(List.of("t1"), List.of("v1"))
                                                        .listTableColumns(null, prefix))
                                        .isEmpty())
                .doesNotThrowAnyException();
    }
}
