/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.versioning;

import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class ConcurrentDocumentOperationIT extends OpenSearchIntegTestCase {
    public void testConcurrentOperationOnSameDoc() throws Exception {
        logger.info("--> create an index with 1 shard and max replicas based on nodes");
        assertAcked(prepareCreate("test").setSettings(Settings.builder().put(indexSettings()).put("index.number_of_shards", 1)));

        logger.info("execute concurrent updates on the same doc");
        int numberOfUpdates = 100;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(numberOfUpdates);
        for (int i = 0; i < numberOfUpdates; i++) {
            client().prepareIndex("test", "type1", "1").setSource("field1", i).execute(new ActionListener<IndexResponse>() {
                @Override
                public void onResponse(IndexResponse response) {
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    logger.error("Unexpected exception while indexing", e);
                    failure.set(e);
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertThat(failure.get(), nullValue());

        client().admin().indices().prepareRefresh().execute().actionGet();

        logger.info("done indexing, check all have the same field value");
        Map masterSource = client().prepareGet("test", "1").execute().actionGet().getSourceAsMap();
        for (int i = 0; i < (cluster().size() * 5); i++) {
            assertThat(client().prepareGet("test", "1").execute().actionGet().getSourceAsMap(), equalTo(masterSource));
        }
    }
}
