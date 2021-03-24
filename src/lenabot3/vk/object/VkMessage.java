package lenabot3.vk.object;

import java.util.ArrayList;
import java.util.Date;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class VkMessage extends VkObject {

	private Date date;
	private int from_id;
	private int id;
	private boolean out;
	private int peer_id;
	private String text;
	private int conversation_message_id;
	private ArrayList<VkMessage> fwd_messages;
	private VkMessage reply_message;
	private JsonObject payload;
	
	public VkMessage(JsonElement obj) throws Exception {
		super(obj);
	}
	
	protected void parse(JsonElement obj) {
		id = 0;
		conversation_message_id = 0;
		
		JsonObject object = obj.getAsJsonObject();
		
		date = new Date(object.get("date").getAsLong()*1000);
		from_id = object.get("from_id").getAsInt();
		id = object.get("id").getAsInt();
		if (object.has("out")) {
			out = object.get("out").getAsInt() == 1 ? true : false;
		}
		if (object.has("peer_id")) {
			peer_id = object.get("peer_id").getAsInt();
		}
		if (object.has("text")) {
			text = object.get("text").getAsString();
		} else {
			text = "";
		}
		
		if (object.has("conversation_message_id")) {
			conversation_message_id = object.get("conversation_message_id").getAsInt();
		}
		
		if (object.has("fwd_messages")) {
			fwd_messages = new ArrayList<VkMessage>();
			try {
				for(JsonElement msg : object.get("fwd_messages").getAsJsonArray()) {
					fwd_messages.add(new VkMessage(msg));
				}
			} catch (Exception e) {
				System.err.printf(
						"[VkMessage.parse] Error when parsing fwd_messages in %d: %s\n",
						this.conversation_message_id,e.getLocalizedMessage());
				fwd_messages = null;
			}
		}
		
		if (object.has("reply_message")) {
			try {
				reply_message = new VkMessage(object.get("reply_message"));
			} catch (Exception e) {
				System.err.printf(
						"[VkMessage.parse] Error when parsing reply_message in %d: %s\n",
						this.conversation_message_id,e.getLocalizedMessage());
			}
		}
		
		payload = null;
		if (object.has("payload")) {
			String payloadText = object.get("payload").getAsString();
			JsonParser parser = new JsonParser();
			try {
				payload = parser.parse(payloadText).getAsJsonObject();
			} catch (Exception e) {
				payload = null;
			}
		}
	}
	
	public Date getDate() {
		return date;
	}
	
	public int getFromId() {
		return from_id;
	}
	
	public int getPeerId() {
		return peer_id;
	}
	
	public int getMessageId() {
		return id;
	}
	
	public int getConversationId() {
		return conversation_message_id;
	}
	
	public int getAnyMessageId() {
		return id != 0 ? id : conversation_message_id;
	}
	
	public boolean isOutcomingMessage() {
		return out;
	}
	
	public boolean isIncomingMessage() {
		return !out;
	}
	
	public boolean hasAnyForwardOrReplyMessage() {
		return (fwd_messages != null) || (reply_message != null); 
	}
	
	public boolean hasForwardMessages() {
		return fwd_messages != null;
	}
	
	public VkMessage getFirstForwardMessage() {
		if (fwd_messages == null) return null;
		
		return fwd_messages.get(0);
	}
	
	public ArrayList<VkMessage> getForwardMessages() {
		return fwd_messages;
	}
	
	public VkMessage getReplyMessage() {
		return reply_message;
	}
	
	public String getText() {
		return text;
	}
	
	public boolean hasPayload() {
		return payload != null;
	}
	
	public JsonObject getPayload() {
		return payload;
	}
}
