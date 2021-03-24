package lenabot3.vk.object;

import com.google.gson.JsonElement;

public abstract class VkObject {
	
	public VkObject(JsonElement obj) throws Exception {
		parse(obj);
	}
	
	protected abstract void parse(JsonElement obj) throws Exception;
	
}
