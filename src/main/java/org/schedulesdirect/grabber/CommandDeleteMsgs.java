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

import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;


/**
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
@Parameters(commandDescription = "Delete messages")
class CommandDeleteMsgs {		
	@Parameter(names = {"--help", "-?", "--?"}, description = "Display help for this command", help = true)
	private boolean help;
	
	@Parameter(names = "--id", description = "Message id to delete; can specify more than one via multiple --id options", required = true)
	private List<String> ids;

	CommandDeleteMsgs() {}

	/**
	 * @return the help
	 */
	public boolean isHelp() {
		return help;
	}

	/**
	 * @return the ids
	 */
	public List<String> getIds() {
		return ids;
	}
}
