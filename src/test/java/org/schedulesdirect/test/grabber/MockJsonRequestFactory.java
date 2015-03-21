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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.schedulesdirect.api.ApiResponse;
import org.schedulesdirect.api.json.IJsonRequestFactory;
import org.schedulesdirect.api.json.DefaultJsonRequest;
import org.schedulesdirect.api.json.DefaultJsonRequest.Action;

public final class MockJsonRequestFactory implements IJsonRequestFactory {

	private Queue<DefaultJsonRequest> q = new LinkedBlockingQueue<>();
	
	@Override
	public DefaultJsonRequest get(Action action, String resource, String hash,	String userAgent, String baseUrl) {
		 return get(action, resource);
	}

	@Override
	public DefaultJsonRequest get(Action action, String resource) {
		if(q.isEmpty())
			q.add(mock(DefaultJsonRequest.class));
		return q.size() == 1 ? q.peek() : q.remove();
	}
	
	public DefaultJsonRequest peek() { return q.peek(); }
	
	public void add(DefaultJsonRequest jr) { q.add(jr); }
	
	public DefaultJsonRequest remove() { return q.poll(); }
	
	public void clear() { q.clear(); }
	
	public void addValidTokenResponse() {
		DefaultJsonRequest req = mock(DefaultJsonRequest.class);
		try {
			when(req.submitForJson(any(Object.class))).thenReturn("{\"token\":\"12345abcd\"}");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		add(req);
	}
	
	public void addErrorResponse() {
		addErrorResponse(ApiResponse.NOT_PROVIDED);
	}
	
	public void addErrorResponse(int code) {
		DefaultJsonRequest req = mock(DefaultJsonRequest.class);
		try {
			when(req.submitForJson(any(Object.class))).thenReturn(String.format("{\"code\":%d}", code));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		add(req);
	}

	@Override
	public DefaultJsonRequest get(Action action, URL url) {
		throw new UnsupportedOperationException("Mock does not implement this method!");
	}

}
