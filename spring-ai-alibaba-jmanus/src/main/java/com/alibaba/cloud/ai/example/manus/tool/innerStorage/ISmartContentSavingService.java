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
package com.alibaba.cloud.ai.example.manus.tool.innerStorage;

import com.alibaba.cloud.ai.example.manus.config.ManusProperties;

/**
 * Smart content saving service interface for storing intermediate data in MapReduce
 * processes
 */
public interface ISmartContentSavingService {

	/**
	 * Get Manus properties
	 * @return Manus properties
	 */
	ManusProperties getManusProperties();

	/**
	 * Process content, automatically store if content is too long
	 * @param planId Plan ID
	 * @param content Content
	 * @param callingMethod Calling method name
	 * @return Processing result containing filename and summary
	 */
	SmartContentSavingService.SmartProcessResult processContent(String planId, String content, String callingMethod);

}
