package lenabot3.vk.object;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class VkEvent extends VkObject {

	private String type;
	private int group_id;
	private JsonObject object;
	private String event_id;
	
	public VkEvent(JsonElement obj) throws Exception {
		super(obj);
	}

	protected void parse(JsonElement obj) throws Exception {
		type = obj.getAsJsonObject().get("type").getAsString();
		group_id = obj.getAsJsonObject().get("group_id").getAsInt();
		object = obj.getAsJsonObject().get("object").getAsJsonObject();
		event_id = obj.getAsJsonObject().get("event_id").getAsString();
	}
	
	public String getType() {
		return type;
	}
	
	public int getGroupId() {
		return group_id;
	}
	
	public JsonObject getObject() {
		return object;
	}
	
	public String getEventId() {
		return event_id;
	}
}
