/*
 *      Copyright 2013 Battams, Derek
 *       
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */
package org.schedulesdirect.grabber.validators;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * Validate options for the grab command
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
public class GrabValidator implements IParameterValidator {
	/* (non-Javadoc)
	 * @see com.beust.jcommander.IParameterValidator#validate(java.lang.String, java.lang.String)
	 */
	@Override
	public void validate(String name, String value) throws ParameterException {
		int val = Integer.parseInt(value);
		if(name.contains("sched") && (val < 10 || val > 1000))
			throw new ParameterException("--max-sched-chunk must be between 10 and 1000");
		else if(name.contains("prog") && (val < 100 || val > 50000))
			throw new ParameterException("--max-prog-chunk must be between 100 and 50000");
	}
}
