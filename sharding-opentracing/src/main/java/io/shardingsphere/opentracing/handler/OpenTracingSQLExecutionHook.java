/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
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
 * </p>
 */

package io.shardingsphere.opentracing.handler;

import com.google.common.base.Joiner;
import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.shardingsphere.core.executor.sql.execute.threadlocal.ExecutorDataMap;
import io.shardingsphere.core.metadata.datasource.DataSourceMetaData;
import io.shardingsphere.core.spi.executor.SQLExecutionHook;
import io.shardingsphere.opentracing.ShardingTracer;
import io.shardingsphere.opentracing.constant.ShardingTags;

import java.util.List;

/**
 * Open tracing SQL execution hook.
 *
 * @author zhangliang
 */
public final class OpenTracingSQLExecutionHook implements SQLExecutionHook {
    
    private static final String OPERATION_NAME = "/" + ShardingTags.COMPONENT_NAME + "/executeSQL/";
    
    private final ThreadLocal<Boolean> isTrunkThread = new ThreadLocal<>();
    
    private Span span;
    
    @Override
    public void start(final String dataSourceName, final String sql, final List<Object> parameters, final DataSourceMetaData dataSourceMetaData) {
        isTrunkThread.set(OpenTracingRootInvokeHandler.isTrunkThread());
        if (ExecutorDataMap.getDataMap().containsKey(OpenTracingRootInvokeHandler.ROOT_SPAN_CONTINUATION) && !isTrunkThread.get()) {
            OpenTracingRootInvokeHandler.getActiveSpan().set(((ActiveSpan.Continuation) ExecutorDataMap.getDataMap().get(OpenTracingRootInvokeHandler.ROOT_SPAN_CONTINUATION)).activate());
        }
        span = ShardingTracer.get().buildSpan(OPERATION_NAME)
                .withTag(Tags.COMPONENT.getKey(), ShardingTags.COMPONENT_NAME)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(Tags.PEER_HOSTNAME.getKey(), dataSourceMetaData.getHostName())
                .withTag(Tags.PEER_PORT.getKey(), dataSourceMetaData.getPort())
                .withTag(Tags.DB_TYPE.getKey(), "sql")
                .withTag(Tags.DB_INSTANCE.getKey(), dataSourceName)
                .withTag(Tags.DB_STATEMENT.getKey(), sql)
                .withTag(ShardingTags.DB_BIND_VARIABLES.getKey(), parameters.isEmpty() ? "" : Joiner.on(",").join(parameters)).startManual();
        
    }
    
    @Override
    public void finishSuccess() {
        span.finish();
        deactivateSpan();
    }
    
    @Override
    public void finishFailure(final Exception cause) {
        ShardingErrorSpan.setError(span, cause);
        span.finish();
        deactivateSpan();
    }
    
    private void deactivateSpan() {
        if (!isTrunkThread.get()) {
            OpenTracingRootInvokeHandler.getActiveSpan().get().deactivate();
            OpenTracingRootInvokeHandler.getActiveSpan().remove();
        }
    }
}