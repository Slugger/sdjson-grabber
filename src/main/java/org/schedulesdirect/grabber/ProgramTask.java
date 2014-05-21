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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
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
import org.schedulesdirect.api.Program;
import org.schedulesdirect.api.RestNouns;
import org.schedulesdirect.api.ZipEpgClient;
import org.schedulesdirect.api.exception.InvalidJsonObjectException;
import org.schedulesdirect.api.json.IJsonRequestFactory;
import org.schedulesdirect.api.json.JsonRequest;
import org.schedulesdirect.api.utils.JsonResponseUtils;

/**
 * Downloads program data from the SD service in batch; writing the results to the given VFS
 * 
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
class ProgramTask implements Runnable {
	static private final Log LOG = LogFactory.getLog(ProgramTask.class);
	
	private JSONArray req;
	private FileSystem vfs;
	private NetworkEpgClient clnt;
	private IJsonRequestFactory factory;
	private Set<String> seriesIds;
	private String targetDir;
	
	/**
	 * Constructor
	 * @param req The array of program ids to be downloaded
	 * @param vfs The name of the vfs being written to
	 * @param clnt The EpgClient to be used to download the request
	 * @param factory The JsonRequestFactory implementation to use
	 * @param seriesIds The master collection of series info object ids that needs to be collected
	 * @param targetDir The directory where collected programs should be stored 
	 */
	public ProgramTask(Collection<String> progIds, FileSystem vfs, NetworkEpgClient clnt, IJsonRequestFactory factory, Set<String> seriesIds, String targetDir) throws JSONException {
		this.req = new JSONArray();
		for(String id : progIds)
			req.put(id);
		this.vfs = vfs;
		this.clnt = clnt;
		this.factory = factory;
		this.seriesIds = seriesIds;
		this.targetDir = targetDir;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		long start = System.currentTimeMillis();
		JsonRequest req = factory.get(JsonRequest.Action.POST, RestNouns.PROGRAMS, clnt.getHash(), clnt.getUserAgent(), clnt.getBaseUrl());
		JSONObject input = new JSONObject();
		input.put("request", this.req);
		try (InputStream ins = req.submitForInputStream(input)) {
			for(String data : (List<String>)IOUtils.readLines(ins)) {
				JSONObject o = new JSONObject(data);
				String id = o.optString("programID", "<unknown>");
				if(!JsonResponseUtils.isErrorResponse(o)) {
					if(id.startsWith("EP"))
						seriesIds.add(Program.convertToSeriesId(id));
					Path p = vfs.getPath(targetDir, String.format("%s.txt", id));
					Files.write(p, o.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
				} else if(JsonResponseUtils.getErrorCode(o) == ApiResponse.INVALID_PROGID)
					LOG.warn(String.format("Missing program object: %s", id));
				else
					throw new InvalidJsonObjectException("Error received for Program", o.toString(3));
			}
		} catch(IOException e) {
			Grabber.failedTask = true;
			LOG.error("IOError receiving program data; filling in empty program info for non-existent program ids!", e);
			try {
				JSONArray ids = this.req;
				for(int i = 0; i < ids.length(); ++i) {
					String id = ids.getString(i);
					Path p = vfs.getPath(targetDir, String.format("%s.txt", id));
					if(!Files.exists(p))
						Files.write(p, Program.EMPTY_PROGRAM.getBytes(ZipEpgClient.ZIP_CHARSET));
				}
			} catch(Exception x) {
				LOG.error("Unexpected error!", x);
				throw new RuntimeException(x);
			}
		} catch (JSONException e) {
			Grabber.failedTask = true;
			LOG.error("JSONError!", e);
			throw new RuntimeException(e);
		}
		LOG.info(String.format("Completed ProgramTask in %dms [%d programs]", System.currentTimeMillis() - start, this.req.length()));
	}
}
