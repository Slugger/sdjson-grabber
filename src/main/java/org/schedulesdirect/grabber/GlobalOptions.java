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
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.log4j.Level;
import org.schedulesdirect.grabber.converters.LevelConverter;
import org.schedulesdirect.grabber.converters.UrlConverter;
import org.schedulesdirect.grabber.validators.MinMaxThreadsValidator;
import org.schedulesdirect.grabber.validators.NonEmptyStringValidator;

import com.beust.jcommander.Parameter;

/**
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
class GlobalOptions {
	static private final URL DEFAULT_URL;
	static {
		try {
			DEFAULT_URL = new URL("https://data2.schedulesdirect.org");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Parameter(names = "--username", description = "Schedules Direct id", validateWith = NonEmptyStringValidator.class)
	private String username;
	private String savedUsername;

	@Parameter(names = "--password", description = "Schedules Direct password", validateWith = NonEmptyStringValidator.class)
	private String password;
	private String savedPassword;
	
	@Parameter(names = "--url", description = "Web service URL", converter = UrlConverter.class)
	private URL url = DEFAULT_URL;
	
	@Parameter(names = "--grabber-log-level", description = "Log level for grabber", converter = LevelConverter.class)
	private Level grabberLogLvl = Level.INFO;
	
	@Parameter(names = "--http-log-level", description = "Log level for http traffic", converter = LevelConverter.class)
	private Level httpLogLvl = Level.WARN;
	
	@Parameter(names = "--log-file", description = "Log to file instead of console")
	private File logFile = null;
	
	@Parameter(names = "--save-creds", description = "Save Schedules Direct credentials for future use (SAVED IN PLAIN TEXT!)")
	private boolean saveCreds;
	
	@Parameter(names = "--max-threads", description = "Max number of worker threads; do not modify unless told to", validateWith = MinMaxThreadsValidator.class)
	private int maxThreads = 200;
	
	@Parameter(names = "--user-agent", description = "User agent to send on all web requests")
	private String userAgent = null;
	
	@Parameter(names = {"--help", "-?", "--?"}, description = "Print user help and exit immediately", help = true)
	private boolean help = false;
	
	@Parameter(names = "--console-file", description = "File to log app output to instead of stdout")
	private File console = null;
	
	/**
	 * 
	 */
	GlobalOptions(String savedUser, String savedPassword) {
		savedUsername = savedUser;
		this.savedPassword = savedPassword;
	}


	/**
	 * @return the username
	 */
	public String getUsername() {
		return username != null ? username : savedUsername;
	}


	/**
	 * @return the password
	 */
	public String getPassword() {
		return password != null ? password : savedPassword;
	}


	/**
	 * @return the url
	 */
	public URL getUrl() {
		return url;
	}


	/**
	 * @return the grabberLogLvl
	 */
	public Level getGrabberLogLvl() {
		return grabberLogLvl;
	}


	/**
	 * @return the httpLogLvl
	 */
	public Level getHttpLogLvl() {
		return httpLogLvl;
	}


	/**
	 * @return the logFile
	 */
	public File getLogFile() {
		return logFile;
	}


	/**
	 * @return the saveCreds
	 */
	public boolean isSaveCreds() {
		return saveCreds;
	}


	/**
	 * @return the maxThreads
	 */
	public int getMaxThreads() {
		return maxThreads;
	}


	/**
	 * @return the userAgent
	 */
	public String getUserAgent() {
		return userAgent;
	}


	/**
	 * @return the help
	 */
	public boolean isHelp() {
		return help;
	}


	/**
	 * @return the console
	 */
	public File getConsole() {
		return console;
	}	
}
