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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.SimpleLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schedulesdirect.api.Config;
import org.schedulesdirect.api.EpgClient;
import org.schedulesdirect.api.JsonRequest;
import org.schedulesdirect.api.Lineup;
import org.schedulesdirect.api.Message;
import org.schedulesdirect.api.NetworkEpgClient;
import org.schedulesdirect.api.RestNouns;
import org.schedulesdirect.api.UserStatus;
import org.schedulesdirect.api.ZipEpgClient;
import org.schedulesdirect.api.exception.InvalidCredentialsException;
import org.schedulesdirect.api.exception.ServiceOfflineException;
import org.schedulesdirect.api.utils.AiringUtils;
import org.schedulesdirect.api.utils.JsonResponseUtils;
import org.schedulesdirect.grabber.utils.PathUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * An application that will download a Schedules Direct user's lineup data from the servers and generates a zip file in response
 * 
 * <p>This application can be used to generate a local cache file of a user's lineup data.  The generated cache file is suitable as a source for
 * the ZipEpgClient class.  That is, you can download a local file of your data and then use that file in your applications instead of always
 * having to contact the Schedules Direct servers for data.</p>
 * 
 * <p>This app can be used as a simple grabber tool if you just need to download the raw JSON data for your account.</p>
 * 
 * <p>To run this app, be sure to download the standalone jar from the project site then execute:</p>
 * 
 * <code>java -jar sdjson.jar</code>
 * 
 * <p>That command alone will dump all of the command line arguments required for operation.</p>
 * 
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
public final class Grabber {
	static private Log LOG = null;
	
	/**
	 * Location of the saved command line args; saves typing on the command line
	 */
	static public final File OPTS_FILE = new File(new File(System.getProperty("user.home")), ".sd4j.opts");
	/**
	 * Name of the file holding user data in the zip
	 */
	static public final String USER_DATA = "user.txt";
	/**
	 * Max age of an airing before it's considered expired
	 */
	static public final long MAX_AIRING_AGE = 3L * 86400000L; // Ignore airings older than this
	/**
	 * Name of the "clean" logger used to display app output without level context
	 */
	static public final String LOGGER_APP_DISPLAY = "AppDisplay";
	
	/**
	 * Has a download task failed?
	 */
	static volatile boolean failedTask = false;

	/**
	 * Supported actions for this app
	 * @author Derek Battams &lt;derek@battams.ca&gt;
	 *
	 */
	static public enum Action {
		GRAB,
		LIST,
		ADD,
		DELETE,
		INFO,
		SEARCH,
		AUDIT,
		LISTMSGS,
		DELMSG
	}
	
	static public final Logger getDisplay() { return Logger.getLogger(LOGGER_APP_DISPLAY); }
	
	private Collection<String> ignoreList = new ArrayList<String>();
	private Set<String> activeProgIds = new HashSet<String>();
	private JCommander parser;
	private GlobalOptions globalOpts;
	private CommandGrab grabOpts;
	private CommandList listOpts;
	private CommandAdd addOpts;
	private CommandDelete delOpts;
	private CommandSearch searchOpts;
	private CommandInfo infoOpts;
	private CommandAudit auditOpts;
	private CommandListMsgs listMsgsOpts;
	private CommandDeleteMsgs delMsgsOpts;
	private boolean freshZip;
	private long start;
	private boolean logosWarned = false;
	private Action action;
	
	private ThreadPoolExecutor pool;

	private void parseArgs(String[] args) {
		List<String> finalArgs = new ArrayList<String>();
		finalArgs.addAll(Arrays.asList(args));
		try {
			String savedUser = null;
			String savedPwd = null;
			if(OPTS_FILE.exists()) {
				Properties props = new Properties();
				Reader r = new FileReader(OPTS_FILE);
				props.load(r);
				r.close();
				savedUser = props.getProperty("user");
				savedPwd = props.getProperty("password");
			}
			globalOpts = new GlobalOptions(savedUser, savedPwd);
			parser = new JCommander(globalOpts);
			parser.setProgramName("sdjson");
			grabOpts = new CommandGrab();
			parser.addCommand("grab", grabOpts);
			listOpts = new CommandList();
			parser.addCommand("list", listOpts);
			addOpts = new CommandAdd();
			parser.addCommand("add", addOpts);
			delOpts = new CommandDelete();
			parser.addCommand("delete", delOpts);
			searchOpts = new CommandSearch();
			parser.addCommand("search", searchOpts);
			infoOpts = new CommandInfo();
			parser.addCommand("info", infoOpts);
			auditOpts = new CommandAudit();
			parser.addCommand("audit", auditOpts);
			listMsgsOpts = new CommandListMsgs();
			parser.addCommand("listmsgs", listMsgsOpts);
			delMsgsOpts = new CommandDeleteMsgs();
			parser.addCommand("delmsg", delMsgsOpts);
			parser.parse(finalArgs.toArray(new String[finalArgs.size()]));
			if(globalOpts.isHelp()) {
				parser.usage();
				System.exit(1);
			}
			if(LOG == null) {
				Appender a = null;
				File logFile = globalOpts.getLogFile();
				if(logFile != null)
					a = new FileAppender(new SimpleLayout(), logFile.getAbsolutePath(), false);
				else
					a = new ConsoleAppender(new SimpleLayout());
				Logger.getRootLogger().addAppender(a);
				Logger.getRootLogger().setLevel(globalOpts.getGrabberLogLvl());
				Logger.getLogger("org.apache.http").setLevel(globalOpts.getHttpLogLvl());
				LOG = LogFactory.getLog(Grabber.class);
				Logger l = Logger.getLogger(LOGGER_APP_DISPLAY);
				l.setAdditivity(false);
				l.setLevel(Level.ALL);
				Layout layout = new PatternLayout("%m");
				File consoleFile = globalOpts.getConsole();
				if(consoleFile != null)
					a = new FileAppender(layout, consoleFile.getAbsolutePath(), false);
				else
					a = new ConsoleAppender(layout);
				l.addAppender(a);
			}
		} catch(ParameterException e) {
			System.out.println(e.getMessage());
			String cmd = parser.getParsedCommand();
			if(cmd != null && !globalOpts.isHelp())
				parser.usage(cmd);
			else
				parser.usage();
			System.exit(1);			
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private int deleteMessages(EpgClient clnt, Message[] msgs) throws IOException {
		int deleted = 0;
		for(String id : delMsgsOpts.getIds()) {
			for(Message m : msgs) {
				if(m.getId().equals(id)) {
					clnt.deleteMessage(m);
					++deleted;
					if(LOG.isInfoEnabled())
						LOG.info("Deleted message with id " + m.getId());
					break;
				}
			}
		}
		return deleted;
	}
	
	private void listAllMessages(EpgClient clnt) throws IOException {
		UserStatus status = clnt.getUserStatus();
		listMessages(clnt, status.getSystemMessages(), "SYSTEM MESSAGES\n===============");
		listMessages(clnt, status.getUserMessages(), "USER MESSAGES\n=============");
	}

	private void listMessages(EpgClient clnt, Message[] msgs, String header) throws IOException {
		Logger l = getDisplay();
		if(msgs != null && msgs.length > 0) {
			l.info(header + "\n");
			SimpleDateFormat fmt = Config.get().getDateTimeFormat();
			for(Message m : msgs) {
				l.info(String.format("%-20s ID: %s%n", fmt.format(m.getDate()), m.getId()));
				l.info(String.format("\t%s%n", WordUtils.wrap(m.getContent(), 78, String.format("%n\t"), globalOpts.getConsole() != null)));
			}
		}
	}
	
	private void listHeadends(EpgClient clnt) throws IOException {
		Logger display = getDisplay();
		display.info(String.format("Available lineups for user '%s'%n", globalOpts.getUsername()));
		display.info(String.format("%-20s Description%n==============================================================================%n", "Lineup ID"));
		for(Lineup l : clnt.getLineups())
			display.info(String.format("%-20s %s %s%n", l.getUri().substring(l.getUri().lastIndexOf('/') + 1), l.getName(), l.getLocation()));
	}
	
	private void listHeadendsForZip(EpgClient clnt) throws IOException {
		Logger display = getDisplay();
		display.info(String.format("Available headends for zip '%s'%n", searchOpts.getPostalCode()));
		display.info(String.format("%-20s Description%n==============================================================================%n", "Headend ID"));
		for(Lineup l : clnt.getLineups(searchOpts.getIsoCountry(), searchOpts.getPostalCode()))
			display.info(String.format("%-20s %s %s%n", l.getUri().substring(l.getUri().lastIndexOf('/') + 1), l.getName(), l.getLocation()));
	}

	private void addLineup(NetworkEpgClient clnt) {
		boolean oneFailure = false;
		for(String lineup : addOpts.getIds()) {
			try {
				clnt.registerLineup(clnt.getLineupByUriPath(lineup));
			} catch (IOException e) {
				oneFailure = true;
				LOG.error(String.format("Register lineup command failed for '%s' [msg=%s]", lineup, e.getMessage()));
			}
		}
		if(oneFailure)
			System.exit(1);
		LOG.info("Headend(s) added successfully!");
	}

	private void removeHeadend(NetworkEpgClient clnt) {
		boolean oneFailure = false;
		for(String lineup : delOpts.getIds()) {
			try {
				clnt.unregisterLineup(clnt.getLineupByUriPath(lineup));
			} catch (IOException e) {
				oneFailure = true;
				LOG.error(String.format("Unreigster lineup command failed for '%s' [msg=%s]", lineup, e.getMessage()));
			}
		}
		if(oneFailure)
			System.exit(1);
		LOG.info("Headend(s) deleted successfully!");
	}

	@SuppressWarnings("unchecked")
	private void buildIgnoreList() {
		File ignoreFile = grabOpts.getIgnoreFile();
		if(ignoreFile != null && ignoreFile.canRead()) {
			try {
				ignoreList = (List<String>)FileUtils.readLines(ignoreFile, ZipEpgClient.ZIP_CHARSET.toString());
			} catch (IOException e) {
				LOG.error("IOError", e);
				ignoreList.clear();
			}
		}
	}

	private ThreadPoolExecutor createThreadPoolExecutor() {
		return new ThreadPoolExecutor(0, globalOpts.getMaxThreads(), 10, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	private void updateZip(NetworkEpgClient clnt) throws IOException, JSONException {
		Set<String> completedListings = new HashSet<String>();
		LOG.debug(String.format("Using %d worker threads", globalOpts.getMaxThreads()));
		pool = createThreadPoolExecutor(); 
		start = System.currentTimeMillis();
		File dest = grabOpts.getTarget();
		boolean rmDest = false;
		if(dest.exists()) {
			ZipEpgClient zipClnt = null;
			try {
				zipClnt = new ZipEpgClient(dest);
				if(!zipClnt.getUserStatus().getLastServerRefresh().before(clnt.getUserStatus().getLastServerRefresh())) {
					LOG.info("Current cache file contains latest data from Schedules Direct server; use --force-download to force a new download from server.");
					boolean force = grabOpts.isForce();
					if(!force && !Config.get().isDebugOverrideActive())
						return;
					else if(!force)
						LOG.warn("Ignoring timestamp from Schedules Direct due to debug flag; proceeding with grab!");
					else
						LOG.warn("Forcing an update of data with the server due to user request!");
				}
			} catch(Exception e) {
				if(grabOpts.isKeep()) {
					LOG.error("Existing cache is invalid, keeping by user request!", e);
					return;
				} else {
					LOG.warn("Existing cache is invalid, deleting it; use --keep-bad-cache to keep existing cache!", e);
					rmDest = true;
				}
			} finally {
				if(zipClnt != null) try { zipClnt.close(); } catch(IOException e) {}
				if(rmDest && !dest.delete())
					throw new IOException("Unable to delete " + dest);
			}
		}

		freshZip = !dest.exists();
		try(FileSystem vfs = FileSystems.newFileSystem(new URI(String.format("jar:%s", dest.toURI())), Collections.singletonMap("create", "true"))) {
			if(freshZip) {
				Path target = vfs.getPath(ZipEpgClient.ZIP_VER_FILE);
				Files.write(target, Integer.toString(ZipEpgClient.ZIP_VER).getBytes(ZipEpgClient.ZIP_CHARSET));
			}
			ProgramCache progCache = ProgramCache.get(vfs);
			Path lineups = vfs.getPath("lineups.txt");
			Files.deleteIfExists(lineups);
			Path scheds = vfs.getPath("/schedules/");
			PathUtils.removeDirectory(scheds);
			Files.createDirectory(scheds);
			Path maps = vfs.getPath("/maps/");
			PathUtils.removeDirectory(maps);
			Files.createDirectory(maps);
			Path progs = vfs.getPath("/programs/");
			if(!Files.isDirectory(progs))
				Files.createDirectory(progs);
			Path logos = vfs.getPath("/logos/");
			if(!Files.isDirectory(logos))
				Files.createDirectory(logos);
			JSONObject resp = new JsonRequest(JsonRequest.Action.GET, RestNouns.LINEUPS, clnt.getHash(), clnt.getUserAgent(), globalOpts.getUrl().toString()).submitForJson(null);
			if(!JsonResponseUtils.isErrorResponse(resp))
				Files.write(lineups, resp.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET));
			else
				LOG.error("Received error response when requesting lineup data!");
			
			for(Lineup l : clnt.getLineups()) {
				buildIgnoreList();
				JSONObject o = new JsonRequest(JsonRequest.Action.GET, l.getUri(), clnt.getHash(), clnt.getUserAgent(), globalOpts.getUrl().toString()).submitForJson(null);
				Files.write(vfs.getPath("/maps", ZipEpgClient.scrubFileName(String.format("%s.txt", l.getId()))), o.toString(3).getBytes(ZipEpgClient.ZIP_CHARSET));
				JSONArray stations = o.getJSONArray("stations");
				JSONArray ids = new JSONArray();
				for(int i = 0; i < stations.length(); ++i) {
					JSONObject obj = stations.getJSONObject(i);
					String sid = obj.getString("stationID");
					if(ignoreList.contains(sid))
						LOG.debug(String.format("Skipped %s; found in ignore list", sid));
					else if(completedListings.add(sid)) {
						ids.put(sid);
						if(!grabOpts.isNoLogos()) {
							if(logoCacheInvalid(obj, vfs))
								pool.execute(new LogoTask(obj, vfs));
							else
								LOG.debug(String.format("Skipped logo for %s; already cached!", obj.optString("callsign", null)));
						} else if(!logosWarned) {
							logosWarned = true;
							LOG.warn("Logo downloads disabled by user request!");
						}
					} else
						LOG.debug(String.format("Skipped %s; already downloaded.", sid));
					if(ids.length() == grabOpts.getMaxSchedChunk()) {
						pool.execute(new ScheduleTask(ids, vfs, clnt, progCache));
						ids = new JSONArray();
					}
				}
				if(ids.length() > 0)
					pool.execute(new ScheduleTask(ids, vfs, clnt, progCache));
			}
			pool.shutdown();
			try {
				pool.awaitTermination(15, TimeUnit.MINUTES);
			} catch (InterruptedException e) {}

			pool = createThreadPoolExecutor();
			String[] dirtyPrograms = progCache.getDirtyIds();
			progCache.markAllClean();
			progCache = null;
			LOG.info(String.format("Identified %d program ids requiring an update!", dirtyPrograms.length));
			Collection<String> progIds = new ArrayList<String>();
			for(String progId : dirtyPrograms) {
				progIds.add(progId);
				if(progIds.size() == grabOpts.getMaxProgChunk()) {
					pool.execute(new ProgramTask(progIds, vfs, clnt));
					progIds.clear();
				}
			}
			if(progIds.size() > 0)
				pool.execute(new ProgramTask(progIds, vfs, clnt));
			pool.shutdown();
			try {
				pool.awaitTermination(15, TimeUnit.MINUTES);
			} catch (InterruptedException e) {}

			String userData = clnt.getUserStatus().toJson();
			if(failedTask) {
				LOG.error("One or more tasks failed!  Resetting last data refresh timestamp to zero.");
				SimpleDateFormat fmt = Config.get().getDateTimeFormat();
				String exp = fmt.format(new Date(0L));
				JSONObject o = new JSONObject(userData);
				o.put("lastDataUpdate", exp);
				userData = o.toString(2);
			}
			Path p = vfs.getPath(USER_DATA);
			Files.write(p, userData.getBytes(ZipEpgClient.ZIP_CHARSET), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			removeIgnoredStations(vfs);
		} catch (URISyntaxException e1) {
			throw new RuntimeException(e1);
		}
	}
	
	private boolean logoCacheInvalid(JSONObject station, FileSystem vfs) throws JSONException, IOException {
		JSONObject logo = station.optJSONObject("logo");
		//TMSBUG: The modified field shouldn't be optional
		if(logo != null && logo.has("modified")) {
			String callsign = station.getString("callsign");
			String ext = logo.getString("URL");
			ext = ext.substring(ext.lastIndexOf('.') + 1);
			Path p = vfs.getPath("logos", String.format("%s.%s", callsign, ext));
			Date cached = new Date(Files.getLastModifiedTime(p).toMillis());
			if(cached != null) {
				Date lastMod;
				try {
					lastMod = Config.get().getDateTimeFormat().parse(logo.getString("modified"));
				} catch (ParseException e) {
					throw new IOException(e);
				}
				return cached.before(lastMod);
			}
		}
		return true;
	}
	
	private void removeExpiredSchedules(FileSystem vfs) throws IOException, JSONException {
		final int[] i = new int[] {0};
		final Path root = vfs.getPath("schedules");
		Files.walkFileTree(root, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return !Files.isSameFile(dir, root) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				try(InputStream ins = Files.newInputStream(file)) {
					JSONArray sched = new JSONObject(IOUtils.toString(ins, ZipEpgClient.ZIP_CHARSET.toString())).getJSONArray("programs");
					if(isScheduleExpired(sched)) {
						Files.delete(file);
						++i[0];
					}
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc)
					throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				return FileVisitResult.CONTINUE;
			}
			
		});
		LOG.info(String.format("Removed %d expired schedule(s).", i[0]));
	}

	private boolean isScheduleExpired(JSONArray sched) throws JSONException {
		boolean rc = true;
		Date expiry = new Date(System.currentTimeMillis() - (3L * 86400000L));
		for(int i = 0; i < sched.length(); ++i) {
			JSONObject air = sched.getJSONObject(i);
			if(!AiringUtils.getEndDate(air).before(expiry)) {
				rc = false;
				activeProgIds.add(air.getString("programID"));
			}
		}
		return rc;
	}

	private void removeUnusedPrograms(FileSystem vfs) throws IOException {
		final int[] i = new int[] {0};
		final Path root = vfs.getPath("programs");
		Files.walkFileTree(root, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return !Files.isSameFile(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String id = file.getName(file.getNameCount() - 1).toString();
				id = id.substring(0, id.indexOf('.'));
				if(!activeProgIds.contains(id)) {
					if(LOG.isDebugEnabled())
						LOG.debug(String.format("CacheCleaner: Unused '%s'", id));
					Files.delete(file);
					++i[0];
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				return FileVisitResult.CONTINUE;
			}
			
		});
		LOG.info(String.format("Removed %d unused program(s).", i[0]));
	}

	private void removeIgnoredStations(FileSystem vfs) throws IOException {
		final int[] i = new int[] {0};
		final Path root = vfs.getPath("schedules");
		Files.walkFileTree(root, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				return !Files.isSameFile(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String id = file.getName(file.getNameCount() - 1).toString();
				id = id.substring(0, id.indexOf('.'));
				if(ignoreList.contains(id)) {
					if(LOG.isDebugEnabled())
						LOG.debug(String.format("CacheCleaner: Remove '%s'", id));
					Files.delete(file);
					++i[0];
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				return FileVisitResult.CONTINUE;
			}
			
		});
		LOG.info(String.format("Removed %d ignored station(s).", i[0]));
	}

	/**
	 * Execute the grabber app
	 * @param args The command line args
	 * @throws IOException Thrown on any unexpected IO error
	 * @throws InvalidCredentialsException Thrown if the login attempt to Schedules Direct failed
	 */
	public void run(String[] args) throws IOException, InvalidCredentialsException {
		parseArgs(args);
		if(parser.getParsedCommand() == null) {
			parser.usage();
			System.exit(1);
		}
		NetworkEpgClient clnt = null;
		try {
			if(!parser.getParsedCommand().equals("audit")) {
				try {
					clnt = new NetworkEpgClient(globalOpts.getUsername(), globalOpts.getPassword(), globalOpts.getUserAgent(), globalOpts.getUrl().toString(), true);
					LOG.debug(String.format("Client details: %s", clnt.getUserAgent()));
				} catch(ServiceOfflineException e) {
					LOG.error("Web service is offline!");
					if(!Config.get().isDebugOverrideActive()) {
						LOG.error("Please try again later.");
						System.exit(1);
					} else
						LOG.warn("Ignoring web service status due to debug mode override!");
				}
			}
			action = Action.valueOf(parser.getParsedCommand().toUpperCase());
			switch(action) {
				case LIST:
					if(!listOpts.isHelp())
						listHeadends(clnt);
					else
						parser.usage(action.toString().toLowerCase());
					break;
				case GRAB:
					if(!grabOpts.isHelp())
						updateZip(clnt);
					else
						parser.usage(action.toString().toLowerCase());
					break;
				case ADD:
					throw new UnsupportedOperationException("Not implemented");
//					if(!addOpts.isHelp())
//						addHeadend(clnt);
//					else
//						parser.usage(action.toString().toLowerCase());
//					break;
				case DELETE: 
					if(!delOpts.isHelp())
						removeHeadend(clnt);
					else
						parser.usage(action.toString().toLowerCase());
					break;
				case INFO: 
					if(!infoOpts.isHelp())
						dumpAccountInfo(clnt);
					else
						parser.usage(action.toString().toLowerCase());
					break;
				case SEARCH:
					if(!searchOpts.isHelp())
						listHeadendsForZip(clnt);
					else
						parser.usage(action.toString().toLowerCase());
					break;
				case AUDIT:
					throw new UnsupportedOperationException("Not implemented");
//					if(!auditOpts.isHelp()) {
//						Auditor a = new Auditor(auditOpts);
//						a.run();
//						if(a.isFailed()) {
//							LOG.error("Auditor failed!");
//							System.exit(1);
//						}
//					} else
//						parser.usage(action.toString().toLowerCase());
//					break;
				case LISTMSGS:
					if(!listMsgsOpts.isHelp())
						listAllMessages(clnt);
					else
						parser.usage(action.toString().toLowerCase());
					break;
				case DELMSG:
					if(!delMsgsOpts.isHelp()) {
						if(deleteMessages(clnt, clnt.getUserStatus().getSystemMessages()) < delMsgsOpts.getIds().size())
							deleteMessages(clnt, clnt.getUserStatus().getUserMessages());
					} else
						parser.usage(action.toString().toLowerCase());
					break;
			}
		} catch(ParameterException e) {
			System.out.println(e.getMessage());
			parser.usage();
			System.exit(1);
		} catch(JSONException e) {
			throw new RuntimeException(e);
		} finally {
			if(clnt != null)
				clnt.close();
			if(grabOpts.getTarget().exists() && action == Action.GRAB) {
				FileSystem target;
				try {
					target = FileSystems.newFileSystem(new URI(String.format("jar:%s", grabOpts.getTarget().toURI())), Collections.<String, Object>emptyMap());
				} catch (URISyntaxException e1) {
					throw new RuntimeException(e1);
				}
				if(action == Action.GRAB && !grabOpts.isHelp() && grabOpts.isPurge() && !freshZip) {
					LOG.warn("Performing a cache cleanup, this will take a few minutes!");
					try {
						removeExpiredSchedules(target);
					} catch(JSONException e) {
						throw new IOException(e);
					}
					removeUnusedPrograms(target);
				}
				if(action == Action.GRAB && !grabOpts.isHelp())
					LOG.info(String.format("Created '%s' successfully! [%dms]", target, System.currentTimeMillis() - start));
			}
			if(globalOpts.isSaveCreds()) {
				String user = globalOpts.getUsername();
				String pwd = globalOpts.getPassword();
				if(user != null && user.length() > 0 && pwd != null && pwd.length() > 0) {
					Properties props = new Properties();
					props.setProperty("user", globalOpts.getUsername());
					props.setProperty("password", globalOpts.getPassword());
					Writer w = new FileWriter(OPTS_FILE);
					props.store(w, "Generated by sd4j");
					w.close();
					LOG.info(String.format("Credentials and headends saved for future use in %s!", OPTS_FILE.getAbsoluteFile()));
				}
			}
		}
	}

	private void dumpAccountInfo(NetworkEpgClient clnt) throws IOException {
		System.out.println(clnt.getUserStatus());
	}
	
	public static void main(String[] args) throws Exception {
		new Grabber().run(args);
	}
}