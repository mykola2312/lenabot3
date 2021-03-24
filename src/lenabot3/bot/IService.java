package lenabot3.bot;

import lenabot3.vk.object.VkEvent;
import lenabot3.vk.object.VkMessage;

public interface IService extends ITask {
	public String getName();
	public long getUpdateInterval();
	public boolean load();
	public void save();
	public void setBot(Bot bot);
	public boolean processEvent(VkEvent event);
	public boolean processMessage(VkMessage message);
	public boolean processCommand(Command command);
}
