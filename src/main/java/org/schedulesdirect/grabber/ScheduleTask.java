/*
 *      Copyright 2012-2015 Battams, Derek
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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schedulesdirect.api.ApiResponse;
import org.schedulesdirect.api.Config;
import org.schedulesdirect.api.NetworkEpgClient;
import org.schedulesdirect.api.RestNouns;
import org.schedulesdirect.api.ZipEpgClient;
import org.schedulesdirect.api.exception.InvalidJsonObjectException;
import org.schedulesdirect.api.json.IJsonRequestFactory;
import org.schedulesdirect.api.json.DefaultJsonRequest;
import org.schedulesdirect.api.utils.AiringUtils;
import org.schedulesdirect.api.utils.JsonResponseUtils;

import com.fasterxml.jackson.core.JsonParseException;

/**
 * Download schedules in bulk
 * 
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
class ScheduleTask implements Runnable {
	static private final Log LOG = LogFactory.getLog(ScheduleTask.class);
	
	static final Map<String, List<JSONObject>> FULL_SCHEDS = new HashMap<>();
	
	static void commit(FileSystem vfs) throws IOException {
		Iterator<String> itr = FULL_SCHEDS.keySet().iterator();
		while(itr.hasNext()) {
			String id = itr.next();
			JSONObject sched = new JSONObject();
			List<JSONObject> airs = FULL_SCHEDS.get(id);
			Collections.sort(airs, new Comparator<JSONObject>() {
				@Override
				public int compare(JSONObject arg0, JSONObject arg1) {
					return arg0.getString("airDateTime").compareTo(arg1.getString("airDateTime"));
				}	
			});
			sched.put("programs", airs);
			Path p = vfs.getPath("schedules", String.format("%s.txt", id));
			Files.write(p, sched.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);		
		}
		FULL_SCHEDS.clear();
	}
	
	static private boolean isScheduleStale(JSONObject src, JSONObject cache) {
		boolean rc = true;
		if(cache != null)
			rc = !cache.getString("md5").equals(src.getString("md5"));
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
		Map<String, Collection<String>> staleIds = getStaleStationIds();
		fetchStations(staleIds);
		LOG.info(String.format("ScheduleTask completed in %dms [TOTAL: %d, FETCH: %d, CACHE: %d]", System.currentTimeMillis() - start, this.req.length(), staleIds.size(), this.req.length() - staleIds.size()));
	}

	protected void fetchStations(Map<String, Collection<String>> ids) {
		if(ids == null || ids.size() == 0) {
			LOG.info("No stale schedules identified; skipping schedule download!");
			return;
		}
		DefaultJsonRequest req = factory.get(DefaultJsonRequest.Action.POST, RestNouns.SCHEDULES, clnt.getHash(), clnt.getUserAgent(), clnt.getBaseUrl());
		JSONArray data = new JSONArray();
		Iterator<String> idItr = ids.keySet().iterator();
		while(idItr.hasNext()) {
			String id = idItr.next();
			JSONObject o = new JSONObject();
			o.put("stationID", id);
			Collection<String> dates = new ArrayList<>();
			for(String date : ids.get(id))
				dates.add(date);
			o.put("date", dates);
			data.put(o);
		}
		try {
			JSONArray resp = Config.get().getObjectMapper().readValue(req.submitForJson(data), JSONArray.class);
			for(int i = 0; i < resp.length(); ++i) {
				JSONObject o = resp.getJSONObject(i);
				if(!JsonResponseUtils.isErrorResponse(o)) {
					JSONArray sched = o.getJSONArray("programs");
					String schedId = o.getString("stationID");
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
							synchronized(ScheduleTask.class) {
								List<JSONObject> objs = FULL_SCHEDS.get(schedId);
								if(objs == null) {
									objs = new ArrayList<JSONObject>();
									FULL_SCHEDS.put(schedId, objs);
								}
								objs.add(airing);
							}
						} catch(JSONException e) {
							LOG.warn(String.format("JSONException [%s]", o.optString("stationID", "unknown")), e);
						}
					}
				} else if(JsonResponseUtils.getErrorCode(o) == ApiResponse.SCHEDULE_QUEUED)
					LOG.warn(String.format("StationID %s is queued server side and will be downloaded on next EPG update!", o.getString("stationID")));
				else
					throw new InvalidJsonObjectException("Error received for schedule", o.toString(3));
			}
		} catch(JSONException|JsonParseException e) {
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
	
	protected Map<String, Collection<String>> getStaleStationIds() {
		Map<String, Collection<String>> staleIds = new HashMap<>();
		DefaultJsonRequest req = factory.get(DefaultJsonRequest.Action.POST, RestNouns.SCHEDULE_MD5S, clnt.getHash(), clnt.getUserAgent(), clnt.getBaseUrl());
		JSONArray data = new JSONArray();
		for(int i = 0; i < this.req.length(); ++i) {
			JSONObject o = new JSONObject();
			o.put("stationID", this.req.getString(i));
			data.put(o);
		}
		try {
			JSONObject result = Config.get().getObjectMapper().readValue(req.submitForJson(data), JSONObject.class);
			if(!JsonResponseUtils.isErrorResponse(result)) {
				Iterator<?> idItr = result.keys();
				while(idItr.hasNext()) {
					String stationId = idItr.next().toString();
					boolean schedFileExists = Files.exists(vfs.getPath("schedules", String.format("%s.txt", stationId)));
					Path cachedMd5File = vfs.getPath("md5s", String.format("%s.txt", stationId));
					JSONObject cachedMd5s = Files.exists(cachedMd5File) ? Config.get().getObjectMapper().readValue(new String(Files.readAllBytes(cachedMd5File), ZipEpgClient.ZIP_CHARSET.toString()), JSONObject.class) : new JSONObject(); 
					JSONObject stationInfo = result.getJSONObject(stationId);
					Iterator<?> dateItr = stationInfo.keys();
					while(dateItr.hasNext()) {
						String date = dateItr.next().toString();
						JSONObject dateInfo = stationInfo.getJSONObject(date);
						if(!schedFileExists || isScheduleStale(dateInfo, cachedMd5s.optJSONObject(date))) {
							Collection<String> dates = staleIds.get(stationId);
							if(dates == null) {
								dates = new ArrayList<String>();
								staleIds.put(stationId, dates);
							}
							dates.add(date);
							if(LOG.isDebugEnabled())
								LOG.debug(String.format("Station %s/%s queued for refresh!", stationId, date));
						} else if(LOG.isDebugEnabled())
							LOG.debug(String.format("Station %s is unchanged on the server; skipping it!", stationId));
					}
					Files.write(cachedMd5File, stationInfo.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
				}
			}
		} catch(Throwable t) {
			Grabber.failedTask = true;
			LOG.error("Error processing cache; returning partial stale list!", t);
		}
		return staleIds;
	}
}
