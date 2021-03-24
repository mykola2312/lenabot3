package lenabot3.vk;

import java.io.IOException;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import lenabot3.vk.exception.VkApiException;
import lenabot3.vk.exception.VkException;
import lenabot3.vk.exception.VkHttpException;
import lenabot3.vk.exception.VkParserException;

public class VkApi implements IVkApi {
	static final long BOT_REQUEST_INTERVAL = 50L;
	static final long USER_REQUEST_INTERVAL = 334L;
	public static final String API_VERSION = "5.92";
	
	private static HttpClientBuilder builder = HttpClients.custom();
	
	private final String token;
	private final boolean isGroup;
	
	private RequestConfig config;
	private IVkApiHandler apiHandler;
	
	private long nextTimeRequest = 0;
	
	public VkApi(String token, boolean isGroup) {
		this.token = token;
		this.isGroup = isGroup;
	}
	
	public void setProxy(String host,int port) {
		HttpHost proxy = new HttpHost(host,port);
		
		RequestConfig.Builder configBuilder = RequestConfig.custom();
		configBuilder = configBuilder.setProxy(proxy);
		config = configBuilder.build();
	}
	
	public void setApiHandler(IVkApiHandler apiHandler) {
		this.apiHandler = apiHandler;
	}
	
	private synchronized void doRequestInterval(IVkRequest vkRequest) {
		if (nextTimeRequest != 0) {
			long interval = nextTimeRequest - System.currentTimeMillis();
			if (interval > 0) {
				try {
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					throw new VkException("Interval interrupted",vkRequest,e);
				}
			}
			nextTimeRequest = System.currentTimeMillis()
					+ (isGroup ? BOT_REQUEST_INTERVAL : USER_REQUEST_INTERVAL);
		}
	}
	
	public JsonElement request(IVkRequest vkRequest) {
		System.out.println("exec " + vkRequest.getMethod());
		
		if (vkRequest.getRequestType() == IVkRequest.RequestType.Default) {
			vkRequest.setParam("access_token", token);
			vkRequest.setParam("v", API_VERSION);
			vkRequest.setParam("lang", "ru");
		}
		
		//API request interval
		doRequestInterval(vkRequest);
		
		//HTTP request
		String body;
		try (CloseableHttpClient client = builder.build()) {
			if (config != null) {
				vkRequest.setConfig(config);
			}
			ClassicHttpRequest req = vkRequest.build();
			
			try (CloseableHttpResponse response = client.execute(req)) {
				HttpEntity entity = response.getEntity();
				if(entity == null) {
					throw new VkException("No HTTP Entity!",vkRequest);
				}
				
				try {
					body = EntityUtils.toString(entity);
				} catch (ParseException e) {
					throw new VkException("Bad HTTP Entity",vkRequest,e);
				}
				
				if(response.getCode() != 200) {
					throw new VkHttpException(response.getCode(),body,vkRequest);
				}
			}
		} catch (IOException ioe) {
			throw new VkException("IOException",vkRequest,ioe);
		} catch (Exception e) {
			throw new VkException(e.getLocalizedMessage(),vkRequest,e);
		}
		
		//Parse JSON
		JsonElement json;
		try {
			JsonParser parser = new JsonParser();
			json = parser.parse(body);
		} catch (JsonSyntaxException jse) {
			throw new VkParserException(body,vkRequest,jse);
		} catch (Exception e) {
			throw new VkException(e.getLocalizedMessage(),vkRequest,e);
		}
		
		//Check API response
		if (vkRequest.doesNeedResultCheck()) {
			JsonElement response = json.getAsJsonObject().get("response");
			if (response == null) {
				JsonElement error = json.getAsJsonObject().get("error");
				if (error == null) {
					throw new VkParserException(body,vkRequest);
				}
				throw new VkApiException(
					error.getAsJsonObject().get("error_code").getAsInt(),
					error.getAsJsonObject().get("error_msg").getAsString(),
					vkRequest);
			}
			
			onVkRequestComplete(vkRequest, response);
			return response;
		}
		
		onVkRequestComplete(vkRequest, json);
		return json;
	}
	
	public void requestAsync(IVkRequest vkRequest) {
		throw new UnsupportedOperationException(
				"VkApi doesn't support requestAsync. Use VkApiAsync or request method");
	}
	
	public synchronized void onVkException(IVkRequest vkRequest, VkException e) {
		if (apiHandler != null) {
			apiHandler.onVkException(vkRequest, e);
		}
	}
	
	public void onVkRequestComplete(IVkRequest vkRequest, JsonElement response) {
		vkRequest.onComplete(response);
		if (apiHandler != null) {
			apiHandler.onVkRequestComplete(vkRequest, response);
		}
	}
}
