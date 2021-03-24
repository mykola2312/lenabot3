package lenabot3.vk;

import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.JsonElement;

import lenabot3.vk.exception.VkException;

public class VkApiThread extends Thread {
	private final VkApi vkApi;
	private LinkedBlockingQueue<IVkRequest> queue = new LinkedBlockingQueue<IVkRequest>();
	
	public VkApiThread(VkApi vkApi) {
		super("VkApiThread");
		this.vkApi = vkApi;
	}
	
	public void stopApi() {
		interrupt();
	}
	
	public void run() {
		runApi();
	}
	
	//Default VkApiThread schedule
	protected void runApi() {
		try {
		while (!Thread.currentThread().isInterrupted()) {
			IVkRequest vkRequest = queue.take();
			
			try {
				JsonElement response = request(vkRequest);
				onVkRequestComplete(vkRequest,response);
			} catch (VkException e) {
				onVkException(vkRequest, e);
			} catch (Exception e) {
				System.err.println(String.format("[VkApiThread] Exception - %s",
						e.getLocalizedMessage()));
			}
		}
		} catch (InterruptedException ie) {
			//Allow to exit
		}
	}
	
	protected JsonElement request(IVkRequest vkRequest) {
		return vkApi.request(vkRequest);
	}
	
	protected void onVkRequestComplete(IVkRequest vkRequest, JsonElement response) {}
	
	protected void onVkException(IVkRequest vkRequest, VkException e) {
		vkApi.onVkException(vkRequest, e);
	}
	
	public void addRequest(IVkRequest vkRequest) {
		queue.add(vkRequest);
	}
}
