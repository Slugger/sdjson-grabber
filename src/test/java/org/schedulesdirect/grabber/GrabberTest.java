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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.schedulesdirect.api.ApiResponse;
import org.schedulesdirect.api.json.IJsonRequestFactory;
import org.schedulesdirect.api.json.DefaultJsonRequest;
import org.schedulesdirect.test.grabber.MockJsonRequestFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ DefaultJsonRequest.class })
public class GrabberTest extends ConsoleTestSuite {
	
	@Test
	public void testInvalidGlobalArgs() throws Exception {
		final String[] ARGS = {"--invalid-arg", "foobar"};
		int rc = new Grabber(mock(IJsonRequestFactory.class)).execute(ARGS);
		assertEquals(GrabberReturnCodes.ARGS_PARSE_ERR, rc);
	}
	
	@Test
	public void testNoCommandArg() throws Exception {
		final String[] ARGS = {};
		int rc = new Grabber(mock(IJsonRequestFactory.class)).execute(ARGS);
		assertEquals(GrabberReturnCodes.NO_CMD_ERR, rc);		
	}
	
	@Test
	public void testHandlesServiceOffline() throws Exception {
		final String[] ARGS = {"--username", "foo", "--password", "bar", "info"};
		MockJsonRequestFactory factory = new MockJsonRequestFactory();
		factory.addErrorResponse(ApiResponse.SERVICE_OFFLINE);
		int rc = new Grabber(factory).execute(ARGS);
		assertEquals(GrabberReturnCodes.SERVICE_OFFLINE_ERR, rc);
	}
}
