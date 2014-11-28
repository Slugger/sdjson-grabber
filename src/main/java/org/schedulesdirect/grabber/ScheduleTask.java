/*
 *      Copyright 2012-2014 Battams, Derek
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
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schedulesdirect.api.ApiResponse;
import org.schedulesdirect.api.NetworkEpgClient;
import org.schedulesdirect.api.RestNouns;
import org.schedulesdirect.api.ZipEpgClient;
import org.schedulesdirect.api.exception.InvalidJsonObjectException;
import org.schedulesdirect.api.json.IJsonRequestFactory;
import org.schedulesdirect.api.json.JsonRequest;
import org.schedulesdirect.api.utils.AiringUtils;
import org.schedulesdirect.api.utils.JsonResponseUtils;

/**
 * Download schedules in bulk
 * 
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
class ScheduleTask implements Runnable {
	static private final Log LOG = LogFactory.getLog(ScheduleTask.class);
	
	static private boolean isScheduleStale(JSONObject src, Path cache) {
		boolean rc = false;
		try(Reader r = Files.newBufferedReader(cache, ZipEpgClient.ZIP_CHARSET)) {
			JSONObject o = new JSONObject(IOUtils.toString(r));
			rc = !src.getString("md5").equals(o.getJSONObject("metadata").getString("md5"));
		} catch(IOException e) {
			LOG.error("IOError reading schedule cache!", e);
			rc = true;
		}
		return rc;
	}
	
	private JSONArray req;
	private FileSystem vfs;
	private NetworkEpgClient clnt;
	private ProgramCache cache;
	private IJsonRequestFactory factory;

	/**
	 * Constructor
	 * @param req The array of station ids to be sent to the server for download
	 * @param vfs The name of the vfs used for storing EPG data
	 * @param clnt The EpgClient to be used to download the request
	 * @param cache The global cache of processed Program ids
	 * @param factory The JsonRequest factory implemenation to use
	 */
	public ScheduleTask(JSONArray req, FileSystem vfs, NetworkEpgClient clnt, ProgramCache cache, IJsonRequestFactory factory) throws JSONException {
		this.req = req;
		this.vfs = vfs;
		this.clnt = clnt;
		this.cache = cache;
		this.factory = factory;
	}

	@Override
	public void run() {
		long start = System.currentTimeMillis();
		Collection<String> staleIds = getStaleStationIds();
		fetchStations(staleIds);
		LOG.info(String.format("ScheduleTask completed in %dms [TOTAL: %d, FETCH: %d, CACHE: %d]", System.currentTimeMillis() - start, this.req.length(), staleIds.size(), this.req.length() - staleIds.size()));
	}

	@SuppressWarnings("unchecked")
	protected void fetchStations(Collection<String> ids) {
		if(ids == null || ids.size() == 0) {
			LOG.info("No stale schedules identified; skipping schedule download!");
			return;
		}
		JsonRequest req = factory.get(JsonRequest.Action.POST, RestNouns.SCHEDULES, clnt.getHash(), clnt.getUserAgent(), clnt.getBaseUrl());
		JSONArray data = new JSONArray();
		Iterator<String> itr = ids.iterator();
		while(itr.hasNext()) {
			JSONObject o = new JSONObject();
			o.put("stationID", itr.next());
			o.put("days", 13);
			data.put(o);
		}
		try(InputStream ins = req.submitForInputStream(data)) {
			for(String input : (List<String>)IOUtils.readLines(ins)) {
				JSONObject o = new JSONObject(input);
				if(!JsonResponseUtils.isErrorResponse(o)) {
					JSONArray sched = o.getJSONArray("programs");
					Date expiry = new Date(System.currentTimeMillis() - Grabber.MAX_AIRING_AGE);
					for(int j = 0; j < sched.length(); ++j) {
						try {
							JSONObject airing = sched.getJSONObject(j);
							Date end = AiringUtils.getEndDate(airing);
							String progId = airing.getString("programID");
							if(!end.before(expiry)) {
								String md5 = airing.getString("md5");
								cache.markIfDirty(progId, md5);
							} else
								LOG.debug(String.format("Expired airing discovered and ignored! [%s; %s; %s]", progId, o.getString("stationID"), end));
						} catch(JSONException e) {
							LOG.warn(String.format("JSONException [%s]", o.optString("stationID", "unknown")), e);
						}
					}

					Path p = vfs.getPath("schedules", String.format("%s.txt", o.getString("stationID")));
					Files.write(p, o.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
				} else if(JsonResponseUtils.getErrorCode(o) == ApiResponse.SCHEDULE_QUEUED)
					LOG.warn(String.format("StationID %s is queued server side and will be downloaded on next EPG update!", o.getString("stationID")));
				else
					throw new InvalidJsonObjectException("Error received for schedule", o.toString(3));
			}
		} catch(JSONException e) {
			Grabber.failedTask = true;
			LOG.fatal("Fatal JSON error!", e);
			throw new RuntimeException(e);
		} catch(IOException e) {
			Grabber.failedTask = true;
			LOG.error("IOError receiving schedule data! Filling cache with empty schedules!", e);
			try {
				JSONArray schedIds = this.req;
				for(int i = 0; i < schedIds.length(); ++i) {
					String id = schedIds.getString(i);
					Path p = vfs.getPath("schedules", String.format("%s.txt", id));
					if(!Files.exists(p)) {
						JSONObject emptySched = new JSONObject();
						emptySched.put("stationID", id);
						emptySched.put("programs", new JSONArray());
						Files.write(p, emptySched.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET));
					}
				}
			} catch(Exception x) {
				LOG.error("Unexpected error!", x);
				throw new RuntimeException(x);
			}
		}
	}
	
	protected Set<String> getStaleStationIds() {
		Set<String> staleIds = new HashSet<>();
		JsonRequest req = factory.get(JsonRequest.Action.POST, RestNouns.SCHEDULE_MD5S, clnt.getHash(), clnt.getUserAgent(), clnt.getBaseUrl());
		JSONArray data = new JSONArray();
		for(int i = 0; i < this.req.length(); ++i) {
			JSONObject o = new JSONObject();
			o.put("stationID", this.req.getString(i));
			o.put("days", 13);
			data.put(o);
		}
		try {
			JSONObject result = new JSONObject(req.submitForJson(data, true));
			if(!JsonResponseUtils.isErrorResponse(result)) {
				Iterator<?> itr = result.keys();
				while(itr.hasNext()) {
					String k = itr.next().toString();
					Path p = vfs.getPath("schedules", String.format("%s.txt", k));
					if(!Files.exists(p) || isScheduleStale(result.getJSONArray(k).getJSONObject(0), p)) {
						staleIds.add(k);
						if(LOG.isDebugEnabled())
							LOG.debug(String.format("Station %s queued for refresh!", k));
					} else if(LOG.isDebugEnabled())
						LOG.debug(String.format("Station %s is unchanged on the server; skipping it!", k));
				}
			}
		} catch(Throwable t) {
			Grabber.failedTask = true;
			LOG.error("Error processing cache; returning partial stale list!", t);
		}
		return staleIds;
	}
}
