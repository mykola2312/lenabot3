package lenabot3.vk.request;

import lenabot3.vk.VkRequest;

public class VkLongPoll extends VkRequest {
	public static final int LONG_POLL_WAIT = 25;
	
	private final String server;
	
	public VkLongPoll(String server,String key,int ts) {
		super("LongPoll");
		this.server = server;
		set("act", "a_check");
		set("key", key);
		set("ts", ts);
		set("wait", LONG_POLL_WAIT);
	}
	
	public RequestType getRequestType() {
		return RequestType.LongPoll;
	}
	
	protected String getUrl() {
		return server;
	}
	
	public boolean doesNeedResultCheck() {
		return false;
	}
}
