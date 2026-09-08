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

import org.apache.hadoop.hive.metastore.security.TFilterTransport;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests libthrift 0.14+ compatibility shims used by Hive Metastore clients. */
public class ThriftTransportShimTest {

    @Test
    public void framedTransportIsLoadableAtLegacyPackage() throws Exception {
        Class<?> framed = Class.forName("org.apache.thrift.transport.TFramedTransport");
        assertThat(framed.getConstructor(TTransport.class)).isNotNull();
        assertThat(Class.forName("org.apache.thrift.transport.TFramedTransport$Factory"))
                .isNotNull();

        TTransport inner = new TMemoryBuffer(32);
        assertThat(new TFramedTransport(inner)).isInstanceOf(TTransport.class);
        assertThat(new TFramedTransport.Factory()).isNotNull();
    }

    @Test
    public void filterTransportImplementsThrift014Abstracts() throws TTransportException {
        TTransport inner = new TMemoryBuffer(32);
        TFilterTransport filter = new TFilterTransport(inner);
        assertThat(filter.getConfiguration()).isNotNull();
        filter.updateKnownMessageSize(0);
        filter.checkReadBytesAvailable(0);
    }
}
