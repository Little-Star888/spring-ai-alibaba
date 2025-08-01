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
package com.alibaba.cloud.ai.example.manus.exception;

/**
 * @author dahua
 * @time 2025/7/30
 * @desc jmanus execution event exception runtime
 */
public class PlanException extends RuntimeException {

	public PlanException() {
		super();
	}

	public PlanException(Throwable cause) {
		super(cause);
	}

	public PlanException(String message) {
		super(message);
	}

	public PlanException(String message, Throwable cause) {
		super(message, cause);
	}

	protected PlanException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
