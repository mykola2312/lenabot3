package lenabot3.vk;

import lenabot3.bot.ITask;

public class VkApiAsync extends VkApi implements ITask {

	private final VkApiThread[] vkApiThreads = 
			new VkApiThread[IVkRequest.Priority.Last.ordinal()];
	
	public VkApiAsync(String token,boolean isGroup) {
		super(token,isGroup);
	}
	
	public boolean start() {
		for(int i = 0; i < vkApiThreads.length; i++) {
			vkApiThreads[i] = new VkApiThread(this);
			vkApiThreads[i].start();
		}
		return true;
	}
	
	public void stop() {
		for(int i = 0; i < vkApiThreads.length; i++) {
			vkApiThreads[i].stopApi();
		}
	}
	
	public void requestAsync(IVkRequest vkRequest) {
		vkApiThreads[vkRequest.getPriority().ordinal()].addRequest(vkRequest);
	}

	public void run() {}
}
