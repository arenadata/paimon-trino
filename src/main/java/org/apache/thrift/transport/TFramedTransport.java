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

package org.apache.thrift.transport;

/**
 * Compatibility shim for Hive 3.1.x clients compiled against libthrift 0.9.3.
 *
 * <p>THRIFT-5237 moved this class to {@code org.apache.thrift.transport.layered} in 0.14.0. Hive
 * Metastore still instantiates {@code org.apache.thrift.transport.TFramedTransport}.
 *
 * @see <a href="https://issues.apache.org/jira/browse/THRIFT-5237">THRIFT-5237</a>
 */
public class TFramedTransport extends org.apache.thrift.transport.layered.TFramedTransport {

    public TFramedTransport(TTransport transport) throws TTransportException {
        super(transport);
    }

    public TFramedTransport(TTransport transport, int maxLength) throws TTransportException {
        super(transport, maxLength);
    }

    public static void encodeFrameSize(int frameSize, byte[] buf) {
        org.apache.thrift.transport.layered.TFramedTransport.encodeFrameSize(frameSize, buf);
    }

    public static int decodeFrameSize(byte[] buf) {
        return org.apache.thrift.transport.layered.TFramedTransport.decodeFrameSize(buf);
    }

    /** Factory matching the 0.9.3 inner-class name {@code TFramedTransport$Factory}. */
    public static class Factory
            extends org.apache.thrift.transport.layered.TFramedTransport.Factory {
        public Factory() {}

        public Factory(int maxLength) {
            super(maxLength);
        }
    }
}
