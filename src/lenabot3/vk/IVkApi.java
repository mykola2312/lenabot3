package lenabot3.vk;

import com.google.gson.JsonElement;

public interface IVkApi {
	public JsonElement request(IVkRequest vkRequest);
	public void requestAsync(IVkRequest vkRequest);
}
