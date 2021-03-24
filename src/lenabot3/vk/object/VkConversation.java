package lenabot3.vk.object;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class VkConversation extends VkObject {
	
	private int id;
	private int local_id;
	private String type;
	private int owner_id;
	private String title;
	
	public VkConversation(JsonElement obj) throws Exception {
		super(obj);
	}
	
	protected void parse(JsonElement obj) throws Exception {
		owner_id = 0;
		title = "";
		
		JsonObject peer = obj.getAsJsonObject().get("peer").getAsJsonObject();
		id = peer.get("id").getAsInt();
		local_id = peer.get("local_id").getAsInt();
		type = peer.get("type").getAsString();
		
		if(obj.getAsJsonObject().has("chat_settings")) {
			JsonObject chat_settings = obj.getAsJsonObject()
					.get("chat_settings").getAsJsonObject();
			if (chat_settings.has("owner_id")) {
				owner_id = chat_settings.get("owner_id").getAsInt();
			}
			if (chat_settings.has("title")) {
				title = chat_settings.get("title").getAsString();
			}
		}
	}
	
	public int getPeerId() {
		return id;
	}
	
	public int getLocalId() {
		return local_id;
	}
	
	public String getType() {
		return type;
	}
	
	public int getOwnerId() {
		return owner_id;
	}
	
	public String getTitle() {
		return title;
	}
}
