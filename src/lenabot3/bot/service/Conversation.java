package lenabot3.bot.service;

public class Conversation {
	private final int id;
	private final int local_id;
	private final String type;
	private final int owner_id;
	private final String title;
	
	public Conversation(int id,int local_id,String type,int owner_id,String title) {
		this.id = id;
		this.local_id = local_id;
		this.type = type;
		this.owner_id = owner_id;
		this.title = title;
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
	
	public String asFormat() {
		return String.format("%s-%d-\"%s\"", getType(),getPeerId(),getTitle());
	}
}
