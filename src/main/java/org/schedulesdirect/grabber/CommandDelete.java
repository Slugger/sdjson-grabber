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
package org.schedulesdirect.grabber;

import java.util.ArrayList;
import java.util.List;

import org.schedulesdirect.grabber.validators.NonEmptyStringValidator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;


/**
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
@Parameters(commandDescription = "Delete headend(s) from your Schedules Direct JSON account")
class CommandDelete {
	@Parameter(names = "--id", description = "Headend id to remove from account", required = true, validateWith = NonEmptyStringValidator.class)
	private List<String> ids = new ArrayList<String>();
	
	@Parameter(names = {"--help", "-?", "--?"}, description = "Display help for this command", help = true)
	private boolean help;

	CommandDelete() {}

	/**
	 * @return the ids
	 */
	public List<String> getIds() {
		return ids;
	}

	/**
	 * @return the help
	 */
	public boolean isHelp() {
		return help;
	}
}
