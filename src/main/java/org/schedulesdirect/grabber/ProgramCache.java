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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.schedulesdirect.api.ZipEpgClient;

/**
 * An in-memory cache of Program objects obtained by the grabber from the SD service; should only be used by the grabber app!
 * @author Derek Battams &lt;derek@battams.ca&gt;
 *
 */
final class ProgramCache {
	static private final Map<String, ProgramCache> CACHE = new HashMap<String, ProgramCache>();
	/**
	 * Get the ProgramCache for the given File
	 * @param src The File to get the cache for
	 * @return The cache for the given File
	 * @throws IOException On any IO error
	 */
	synchronized static public ProgramCache get(FileSystem src) throws IOException {
		Iterator<Path> itr = src.getRootDirectories().iterator();
		Path root = null;
		if(itr.hasNext()) {
			Path p = itr.next();
			if(itr.hasNext())
				throw new IOException("Cannot create ProgramCache for unrecognized file system type!");
			root = p;
		}
		ProgramCache cache = CACHE.get(root.toString());
		if(cache == null) {
			cache = new ProgramCache(src);
			CACHE.put(root.toString(), cache);
		}
		return cache;
	}
	
	private FileSystem vfs;
	private Set<String> dirtyIds;
	
	private ProgramCache(FileSystem vfs) throws IOException {
		this.vfs = vfs;
		dirtyIds = Collections.synchronizedSet(new HashSet<String>());
	}
	
	/**
	 * Is the given program id with the given md5 in the cache?
	 * @param progId The program id to search for
	 * @param md5 The md5 hash to search for
	 * @return True if progId with the given md5 is in the cache or false otherwise
	 */
	public boolean inCache(String progId, String md5) {
		Path prog = vfs.getPath("programs", String.format("%s.txt", progId));
		if(Files.isReadable(prog)) {
			try(InputStream ins = Files.newInputStream(prog)) {
				JSONObject o = new JSONObject(IOUtils.toString(ins, ZipEpgClient.ZIP_CHARSET.toString()));
				return o.getString("md5").equals(md5);
			} catch (JSONException | IOException e) {
				throw new RuntimeException(e);
			}
		} else
			return false;
	}
	
	/**
	 * Search for the given progId and md5 in the cache; if it's not in the cache, mark the cache as dirty for that object so it will update it
	 * @param progId
	 * @param md5
	 */
	public void markIfDirty(String progId, String md5) {
		if(!dirtyIds.contains(progId) && !inCache(progId, md5))
			dirtyIds.add(progId);
	}

	/**
	 * Get an array of dirty program ids in the cache
	 * @return The array of dirty program ids
	 */
	public String[] getDirtyIds() {
		return Arrays.copyOf(dirtyIds.toArray(new String[0]), dirtyIds.size());
	}
	
	/**
	 * Mark the given program id as clean in the cache
	 * @param progId The program id to mark clean
	 */
	public void markClean(String progId) {
		dirtyIds.remove(progId);
	}
	
	/**
	 * Mark all program ids in the cache as clean
	 */
	public void markAllClean() {
		dirtyIds.clear();
	}	
}
