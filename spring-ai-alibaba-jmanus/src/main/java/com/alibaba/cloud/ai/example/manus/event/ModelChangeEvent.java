/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.example.manus.event;

import com.alibaba.cloud.ai.example.manus.dynamic.model.entity.DynamicModelEntity;

/**
 * @author dahua
 * @time 2025/7/15
 * @desc jmanus model change event class
 */
public class ModelChangeEvent implements JmanusEvent {

	private DynamicModelEntity dynamicModelEntity;

	private long createTime;

	public ModelChangeEvent(DynamicModelEntity dynamicModelEntity) {
		this.dynamicModelEntity = dynamicModelEntity;
		this.createTime = System.currentTimeMillis();
	}

	public DynamicModelEntity getDynamicModelEntity() {
		return dynamicModelEntity;
	}

	public long getCreateTime() {
		return createTime;
	}

}
