/*
 *      Copyright 2014 Battams, Derek
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

/**
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
public interface GrabberReturnCodes {
	static public final int OK = 0;
	static public final int ARGS_PARSE_ERR = 1;
	static public final int NO_CMD_ERR = 2;
	static public final int SERVICE_OFFLINE_ERR = 3;
	static public final int CMD_FAILED_ERR = 4;
}
