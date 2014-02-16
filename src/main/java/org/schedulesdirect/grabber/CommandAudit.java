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

import java.io.File;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;


/**
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
@Parameters(commandDescription = "Run validation tests against cached EPG data")
class CommandAudit {
	@Parameter(names = {"--help", "-?", "--?"}, description = "Display help for this command", help = true)
	private boolean help;
	
	@Parameter(names = "--scheds", description = "Audit schedules for gaps and overlaps")
	private boolean auditScheds;
	
	@Parameter(names = "--json", description = "Construct every downloaded object to ensure json validity")
	private boolean auditJson;
	
	@Parameter(names = "--source", description = "Source EPG cache file to audit", required = true)
	private File src;	

	CommandAudit() {}

	/**
	 * @return the help
	 */
	public boolean isHelp() {
		return help;
	}

	/**
	 * @return the auditScheds
	 */
	public boolean isAuditScheds() {
		return auditScheds;
	}

	/**
	 * @return the auditJson
	 */
	public boolean isAuditJson() {
		return auditJson;
	}

	/**
	 * @return the src
	 */
	public File getSrc() {
		return src;
	}
}
