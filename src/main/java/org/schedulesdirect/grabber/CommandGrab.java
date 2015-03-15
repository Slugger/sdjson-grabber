/*
 *      Copyright 2013-2014 Battams, Derek
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

import org.schedulesdirect.grabber.validators.GrabValidator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
@Parameters(commandDescription = "Grab the EPG data")
class CommandGrab {
	@Parameter(names = "--max-sched-chunk", description = "Max number of schedules to download at once; change only if told to", validateWith = GrabValidator.class)
	private int maxSchedChunk = 250;
	
	@Parameter(names = "--max-prog-chunk", description = "Max number of programs to download at once; change only if told to", validateWith = GrabValidator.class)
	private int maxProgChunk = 5000;
	
	@Parameter(names = "--no-logos", description = "Do not download/update logo files")
	private boolean noLogos;
	
	@Parameter(names = "--purge-cache", description = "Cleanup stale entries in the cache")
	private boolean purge;
	
	@Parameter(names = "--stations", description = "File containing list of only station ids to download")
	private File stationFile = null;
	
	@Parameter(names = "--target", description = "File name to write the EPG data to")
	private File target = new File("sdjson.epg");
	
	@Parameter(names = "--force-download", description = "Force a download of EPG even if service says current cache is latest")
	private boolean force;
	
	@Parameter(names = "--keep-bad-cache", description = "Do not delete cache marked as corrupted")
	private boolean keep;
	
	@Parameter(names = {"--help", "-?", "--?"}, description = "Display help for this command", help = true)
	private boolean help;
	
	/**
	 * 
	 */
	CommandGrab() {}

	/**
	 * @return the maxSchedChunk
	 */
	public int getMaxSchedChunk() {
		return maxSchedChunk;
	}

	/**
	 * @return the maxProgChunk
	 */
	public int getMaxProgChunk() {
		return maxProgChunk;
	}

	/**
	 * @return the noLogos
	 */
	public boolean isNoLogos() {
		return noLogos;
	}

	/**
	 * @return the purge
	 */
	public boolean isPurge() {
		return purge;
	}

	/**
	 * @return the ignoreFile
	 */
	public File getStationFile() {
		return stationFile;
	}

	/**
	 * @return the target
	 */
	public File getTarget() {
		return target;
	}

	/**
	 * @return the force
	 */
	public boolean isForce() {
		return force;
	}

	/**
	 * @return the keep
	 */
	public boolean isKeep() {
		return keep;
	}

	/**
	 * @return the help
	 */
	public boolean isHelp() {
		return help;
	}
}
