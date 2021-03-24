package lenabot3.bot;

import lenabot3.vk.object.VkEvent;
import lenabot3.vk.object.VkMessage;

public abstract class Service implements IService {
	
	private String name;
	private long updateInterval = -1L;
	private Bot bot;

	public Service(String name) {
		this.name = name;
	}
	
	protected void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	protected void setUpdateInterval(long updateInterval) {
		this.updateInterval = updateInterval;
	}
	
	public long getUpdateInterval() {
		return updateInterval;
	}
	
	public void setBot(Bot bot) {
		this.bot = bot;
	}
	
	public Bot getBot() {
		return bot;
	}
	
	public boolean processEvent(VkEvent event) {
		return false;
	}

	
	public boolean processMessage(VkMessage message) {
		return false;
	}
	
	public boolean processCommand(Command command) {
		return false;
	}

}
