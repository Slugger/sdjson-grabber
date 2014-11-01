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
package org.schedulesdirect.test.grabber;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TestEpgFile {

	private Random rng;
	private FileSystem vfs;
	private List<String> logos;
	
	public TestEpgFile() throws IOException {
		rng = new Random();
		logos = new ArrayList<String>();
		Path p = Files.createTempFile("sdjson_epg_", ".epg");
		p.toFile().deleteOnExit();
		Files.delete(p);
		try {
			vfs = FileSystems.newFileSystem(new URI(String.format("jar:%s", p.toFile().toURI())), Collections.singletonMap("create", "true"));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		loadLogos();
	}
	
	public FileSystem getFileSystem() { return vfs; }
	
	protected void loadLogos() throws IOException {
		Path d = vfs.getPath("logos");
		Files.createDirectories(d);
		Path p = vfs.getPath("logos", "TSN.png");
		Files.createFile(p);
		logos.add("TSN");
	}
	
	public String getRandomLogoCallsign() {
		return logos.get(rng.nextInt(logos.size()));
	}
	
	public void close() throws IOException {
		if(vfs != null) {
			vfs.close();
			vfs = null;
		}
	}
	
	@Override
	protected void finalize() throws IOException { close(); }
}
