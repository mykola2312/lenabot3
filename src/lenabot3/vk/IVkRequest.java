package lenabot3.vk;

import java.net.URISyntaxException;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;

import com.google.gson.JsonElement;

public interface IVkRequest {
	enum Priority {
		Standard,
		LongPoll,
		Other,
		Last
	}
	
	enum RequestType {
		Default,
		LongPoll,
		Upload,
		Download
	}
	
	public String getMethod();
	public Priority getPriority();
	public void setPriority(Priority priority);
	public RequestType getRequestType();
	public void setParam(String name,String value);
	public void setPostParam(String name,String value);
	
	public void setConfig(RequestConfig config);
	public ClassicHttpRequest build() throws URISyntaxException;
	
	public boolean doesNeedResultCheck();
	
	public void onComplete(JsonElement response);
}
