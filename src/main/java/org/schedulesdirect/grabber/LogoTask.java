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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Download a station logo
 * 
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
class LogoTask implements Runnable {
	static private final Log LOG = LogFactory.getLog(LogoTask.class);
	
	private JSONObject req;
	private FileSystem vfs;
	private URL url;
	private String callsign;
	private String ext;
	private String md5;
	private JSONObject cache;

	/**
	 * Constructor
	 * @param req A station object containing the details for the logo
	 * @param vfs The name of the vfs used for storing the logo image
	 * @param cache The internal cache index of the logos
	 */
	public LogoTask(JSONObject req, FileSystem vfs, JSONObject cache) throws JSONException {
		this.vfs = vfs;
		callsign = req.getString("callsign");
		this.req = req.optJSONObject("logo");
		this.cache = cache;
		if(this.req != null) {
			this.md5 = this.req.optString("md5", null);
			String urlStr = this.req.optString("URL", null);
			try {
				url = urlStr != null && urlStr.length() > 0 ? new URL(urlStr) : null;
				if(url != null)
					ext = urlStr.substring(urlStr.lastIndexOf(".") + 1);
			} catch (MalformedURLException e) {
				LOG.warn("Invalid URL", e);
				url = null;
			}
		}
	}

	@Override
	public void run() {
		if(req != null && url != null) {
			long start = System.currentTimeMillis();
			try {
				HttpResponse resp = Request.Get(url.toURI()).execute().returnResponse();
				if(resp.getStatusLine().getStatusCode() == 200) {
					try(InputStream ins = resp.getEntity().getContent()) {
						Path p = vfs.getPath("logos", String.format("%s.%s", callsign, ext));
						Files.copy(ins, p, StandardCopyOption.REPLACE_EXISTING);
						if(md5 != null)
							synchronized(cache) {
								cache.put(callsign, md5);
							}
						LOG.info(String.format("LogoTask COMPLETE for %s [%dms]", callsign, System.currentTimeMillis() - start));
					}
				} else
					LOG.warn(String.format("Received error response for logo '%s': %s", callsign, resp.getStatusLine()));
			} catch(IOException | URISyntaxException e) {
				LOG.error(String.format("IOError grabbing logo for %s", callsign), e);
			}
		} else if(LOG.isDebugEnabled())
			LOG.debug(String.format("No logo info for %s", callsign));
	}
}
