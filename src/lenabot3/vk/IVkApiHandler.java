package lenabot3.vk;

import com.google.gson.JsonElement;
import lenabot3.vk.exception.VkException;

public interface IVkApiHandler {
	public void onVkException(IVkRequest vkRequest, VkException e);
	public void onVkRequestComplete(IVkRequest vkRequest, JsonElement response);
}
