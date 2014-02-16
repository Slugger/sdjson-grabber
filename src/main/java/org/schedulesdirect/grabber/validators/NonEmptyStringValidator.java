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
 * Validates a string parameter read from command line
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
public class NonEmptyStringValidator implements IParameterValidator {
	/* (non-Javadoc)
	 * @see com.beust.jcommander.IParameterValidator#validate(java.lang.String, java.lang.String)
	 */
	@Override
	public void validate(String name, String value) throws ParameterException {
		if(value == null || value.length() == 0)
			throw new ParameterException(String.format("Option %s must have a value!"));
	}

}
