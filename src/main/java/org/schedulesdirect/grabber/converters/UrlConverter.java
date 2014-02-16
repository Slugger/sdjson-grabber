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
package org.schedulesdirect.grabber.converters;

import java.net.MalformedURLException;
import java.net.URL;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

/**
 * Converts a string to a URL
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
public class UrlConverter implements IStringConverter<URL> {
	@Override
	public URL convert(String arg0) {
		try {
			return new URL(arg0);
		} catch (MalformedURLException e) {
			throw new ParameterException(e);
		}
	}

}
