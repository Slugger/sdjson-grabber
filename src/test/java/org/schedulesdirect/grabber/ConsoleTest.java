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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class ConsoleTest {
	static {
		Path cwd = Paths.get(System.getProperty("user.dir"));
		Path stdout = Paths.get(cwd.toString(), String.format("%s.stdout", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())));
		Path stderr = Paths.get(cwd.toString(), String.format("%s.stderr", new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())));
		System.out.println("stdout redirected to: " + stdout);
		System.out.println("stderr redirected to: " + stderr);
		try {
			System.setOut(new PrintStream(Files.newOutputStream(stdout)));
			System.setErr(new PrintStream(Files.newOutputStream(stderr)));
		} catch (IOException e) {
			System.out.println("Failed to redirect streams; probably going to spam your console instead!");
		}
	}
}
