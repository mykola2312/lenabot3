package lenabot3.vk;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import com.google.gson.JsonElement;

class MultipartData {
	enum Type {
		Text,
		File
	}
	
	private final String name;
	private final String data;
	private final Type type;
	
	private MultipartData(String name,String data,Type type) {
		this.name = name;
		this.data = data;
		this.type = type;
	}
	
	public String getName() {
		return name;
	}
	
	public String getData() {
		return data;
	}
	
	public Type getType() {
		return type;
	}
	
	public static MultipartData text(String name,String data) {
		return new MultipartData(name,data,Type.Text);
	}
	
	public static MultipartData file(String name,String filename) {
		return new MultipartData(name,filename,Type.File);
	}
}

public class VkRequest implements IVkRequest {
	public static final String VK_METHOD_URL = "https://api.vk.com/method/";
	
	private final String method;
	
	private final TreeMap<String,String> params = new TreeMap<String,String>();
	private final TreeMap<String,String> postParams = new TreeMap<String,String>();
	private final LinkedList<MultipartData> multipart = new LinkedList<MultipartData>();
	
	private RequestConfig config;
	private Priority priority = Priority.Standard;
	
	public VkRequest(String method) {
		this.method = method;
	}
	
	public VkRequest(String method, Priority priority) {
		this.method = method;
		this.priority = priority;
	}
	
	public String getMethod() {
		return method;
	}
	
	public Priority getPriority() {
		return priority;
	}
	
	public void setPriority(Priority priority) {
		this.priority = priority;
	}
	
	public RequestType getRequestType() {
		return RequestType.Default;
	}
	
	public void setParam(String name, String value) {
		params.put(name, value);
	}
	
	public VkRequest set(String name, String value) {
		setParam(name, value);
		return this;
	}
	
	public VkRequest set(String name, int value) {
		return set(name, String.valueOf(value));
	}
	
	public VkRequest set(String name, double value) {
		return set(name, String.valueOf(value));
	}
	
	public void setPostParam(String name, String value) {
		postParams.put(name, value);
	}
	
	public VkRequest setPost(String name, String value) {
		setPostParam(name, value);
		return this;
	}
	
	public VkRequest setPost(String name, int value) {
		return set(name, String.valueOf(value));
	}
	
	public VkRequest setPost(String name, double value) {
		return set(name, String.valueOf(value));
	}
	
	public void setConfig(RequestConfig config) {
		this.config = config;
	}
	
	public VkRequest addMultipartText(String name, String value) {
		multipart.add(MultipartData.text(name, value));
		return this;
	}
	
	public VkRequest addMultipartFile(String name, String filename) {
		multipart.add(MultipartData.file(name, filename));
		return this;
	}
	
	public ClassicHttpRequest build() throws URISyntaxException {
		HttpUriRequestBase requestBase;
	
		if (isPost()) {
			requestBase = new HttpPost(getUrl());
		} else {
			requestBase = new HttpGet(getUrl());
		}
		
		if (config != null) {
			requestBase.setConfig(config);
		}
		
		if (!params.isEmpty()) {
			URIBuilder build = new URIBuilder(requestBase.getUri());
			for (Map.Entry<String, String> entry : params.entrySet()) {
				build.addParameter(entry.getKey(), entry.getValue());
			}
			requestBase.setUri(build.build());
		}
		
		if (!postParams.isEmpty()) {
			ArrayList<NameValuePair> pairs = new ArrayList<NameValuePair>();
			for (Map.Entry<String, String> entry : postParams.entrySet()) {
				System.out.println(entry.getKey() + " : " + entry.getValue());
				pairs.add(new BasicNameValuePair(entry.getKey(),entry.getValue()));
			}
			requestBase.setEntity(new UrlEncodedFormEntity(pairs,Charset.forName("UTF-8")));
		}
		else if (!multipart.isEmpty()) {
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			for(MultipartData data : multipart) {
				switch(data.getType()) {
					case Text: 
						builder = builder.addTextBody(data.getName(), data.getData());
						break;
					case File:
						builder = builder.addBinaryBody(data.getName(), 
								new File(data.getData()));
						break;
				}
			}
		}
		
		return requestBase;
	}
	
	protected String getUrl() {
		return VK_METHOD_URL + getMethod();
	}
	
	private boolean isPost() {
		return !postParams.isEmpty() || !multipart.isEmpty();
	}
	
	public boolean doesNeedResultCheck() {
		return true;
	}
	
	public void onComplete(JsonElement response) {}
}
