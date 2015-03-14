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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schedulesdirect.api.Config;
import org.schedulesdirect.api.EpgClient;
import org.schedulesdirect.api.Lineup;
import org.schedulesdirect.api.Program;
import org.schedulesdirect.api.Station;
import org.schedulesdirect.api.ZipEpgClient;

import com.beust.jcommander.ParameterException;

/**
 * A debugging tool used to parse and inspect the raw data feeds and dump reports; mainly used for API development and debugging
 * 
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
public final class Auditor {
	static private final Logger LOG = Logger.getRootLogger();
	
	private CommandAudit opts;
	private boolean failed = false;
	private FileSystem vfs;
	
	Auditor(CommandAudit opts) throws IOException {
		this.opts = opts;
		try {
			vfs = FileSystems.newFileSystem(new URI(String.format("jar:%s", opts.getSrc().toURI())), Collections.<String, Object>emptyMap());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void run() {
		LOG.info(String.format("Auditing file '%s'", opts.getSrc().getAbsolutePath()));
		try {
			if(!opts.isAuditJson() && !opts.isAuditScheds())
				throw new ParameterException("Must specify at least one of --scheds or --json");
			if(opts.isAuditJson())
				auditJson();
			if(opts.isAuditScheds())
				auditScheds();
		} catch(Exception e) {
			LOG.error("Auditor failed!", e);
			failed = true;
		} finally {
			if(vfs != null)
				try {
					vfs.close();
				} catch (IOException e) {
					LOG.warn("IOError", e);
				}
		}
	}
		
	private void auditScheds() throws IOException, JSONException, ParseException {
		final Map<String, JSONObject> stations = getStationMap();
		final SimpleDateFormat FMT = Config.get().getDateTimeFormat();
		final Path scheds = vfs.getPath("schedules");
		if(Files.isDirectory(scheds)) {
			Files.walkFileTree(scheds, new FileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return dir.equals(scheds) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					boolean failed = false;
					String id = getStationIdFromFileName(file.getFileName().toString());
					JSONObject station = stations.get(id);
					StringBuilder msg = new StringBuilder(String.format("Inspecting %s (%s)... ", station != null ? station.getString("callsign") : String.format("[UNKNOWN: %s]", id), id));
					String input;
					try(InputStream ins = Files.newInputStream(file)) {
						input = IOUtils.toString(ins, ZipEpgClient.ZIP_CHARSET.toString());
					}
					JSONArray jarr = new JSONArray(new JSONObject(input).getJSONArray("programs").toString());
					for(int i = 1; i < jarr.length(); ++i) {
						long start, prevStart;
						JSONObject prev;
						try {
							start = FMT.parse(jarr.getJSONObject(i).getString("airDateTime")).getTime();
							prev = jarr.getJSONObject(i - 1);
							prevStart = FMT.parse(prev.getString("airDateTime")).getTime() + 1000L * prev.getLong("duration");
						} catch (ParseException e) {
							throw new RuntimeException(e);
						}
						if(prevStart != start) {
							msg.append(String.format("FAILED! [%s]", prev.getString("airDateTime")));
							LOG.error(msg);
							failed = true;
							Auditor.this.failed = true;
							break;
						}
					}
					if(!failed) {
						msg.append("PASSED!");
						LOG.info(msg);
					}					
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					LOG.error(String.format("Unable to process schedule file '%s'", file), exc);
					Auditor.this.failed = true;
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir,	IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
				
			});
		}
	}
	
	private String getStationIdFromFileName(String f) {
		return f.substring(f.indexOf('/') + 1, f.lastIndexOf('.'));
	}
	
	private Map<String, JSONObject> getStationMap() throws IOException, JSONException {
		final Map<String, JSONObject> map = new HashMap<>();		
		final Path maps = vfs.getPath("maps");
		if(Files.isDirectory(maps)) {
			Files.walkFileTree(maps, new FileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return dir.equals(maps) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String input;
					try(InputStream ins = Files.newInputStream(file)) {
						input = IOUtils.toString(ins, ZipEpgClient.ZIP_CHARSET.toString());
					}
					JSONArray jarr = new JSONArray(new JSONObject(input).getJSONArray("stations").toString());
					for(int i = 0; i < jarr.length(); ++i) {
						JSONObject jobj = jarr.getJSONObject(i);
						String id = jobj.getString("stationID");
						if(!map.containsKey(id))
							map.put(id, jobj);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					LOG.error(String.format("Unable to process map file '%s'", file), exc);
					Auditor.this.failed = true;
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir,	IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
				
			});
		}
		return map;
	}
	
	private void auditJson() throws Exception {
		EpgClient clnt = new ZipEpgClient(opts.getSrc());
		int i = 0;
		long start = System.currentTimeMillis();
		for(Lineup l : clnt.getLineups())
			for(Station s : l.getStations())
				for(Program p : s.getPrograms()) {
					if(++i % 1000 == 0 && LOG.isInfoEnabled())
						LOG.info(String.format("Scanned %d objects!", i));
					try {
						String id = p.getId();
						if(LOG.isDebugEnabled())
							LOG.debug(String.format("Loaded object '%s'", id));
					} catch(Exception e) {
						LOG.error("Exception caught!", e);
						failed = true;
					}
				}
		LOG.warn(String.format("JSON validity audit of %d objects completed in %.1f seconds!", i, (1.0D * System.currentTimeMillis() - start) / 1000));
	}

	/**
	 * @return the failed
	 */
	public boolean isFailed() {
		return failed;
	}
}
