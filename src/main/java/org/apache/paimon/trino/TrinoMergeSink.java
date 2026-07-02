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

import org.apache.paimon.types.RowKind;

import io.airlift.slice.Slice;
import io.trino.spi.Page;
import io.trino.spi.connector.ConnectorMergeSink;
import io.trino.spi.connector.ConnectorPageSink;
import io.trino.spi.connector.MergePage;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** Trino {@link ConnectorMergeSink}. */
public class TrinoMergeSink implements ConnectorMergeSink {

    private final TrinoPageSink pageSink;
    private final int dataColumnCount;

    public TrinoMergeSink(ConnectorPageSink pageSink, int dataColumnCount) {
        this.pageSink = (TrinoPageSink) pageSink;
        this.dataColumnCount = dataColumnCount;
    }

    @Override
    public void storeMergedRows(Page page) {
        MergePage mergePage = MergePage.createDeleteAndInsertPages(page, dataColumnCount);
        mergePage
                .getDeletionsPage()
                .ifPresent(deletePage -> pageSink.writePage(deletePage, RowKind.DELETE));
        mergePage
                .getInsertionsPage()
                .ifPresent(insertPage -> pageSink.writePage(insertPage, RowKind.INSERT));
    }

    @Override
    public CompletableFuture<Collection<Slice>> finish() {
        return pageSink.finish();
    }
}
