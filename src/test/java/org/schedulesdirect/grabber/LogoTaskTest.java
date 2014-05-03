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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONObject;
import org.junit.Test;
import org.schedulesdirect.test.grabber.TestEpgFile;

public class LogoTaskTest {

	@Test
	public void testRemovalOfStaleLogosFromCache() throws Exception {
		TestEpgFile epg = new TestEpgFile();
		FileSystem vfs = epg.getFileSystem();
		String callsign = epg.getRandomLogoCallsign();
		
		JSONObject req = new JSONObject();
		req.put("callsign", callsign);
		req.put("logo", generateLogo(callsign));
		JSONObject cache = new JSONObject();
		cache.put(epg.getRandomLogoCallsign(), "ABC");
		
		LogoTask task = new LogoTask(req, vfs, cache);
		task.removeStaleLogo();
		assertFalse(cache.has(callsign));
	}

	@Test
	public void testRemovalOfStaleLogosFromZip() throws Exception {
		TestEpgFile epg = new TestEpgFile();
		FileSystem vfs = epg.getFileSystem();
		String callsign = epg.getRandomLogoCallsign();
		
		JSONObject req = new JSONObject();
		req.put("callsign", callsign);
		req.put("logo", generateLogo(callsign));
		JSONObject cache = new JSONObject();
		cache.put(epg.getRandomLogoCallsign(), "ABC");
		
		LogoTask task = new LogoTask(req, vfs, cache);
		Path p = vfs.getPath("logos", String.format("%s.png", callsign));
		assertTrue(Files.exists(p));
		task.removeStaleLogo();
		assertFalse(Files.exists(p));
	}

	protected JSONObject generateLogo(String callsign) {
		JSONObject o = new JSONObject();
		o.put("URL", String.format("http://localhost/%s.png", callsign));
		return o;
	}
}
