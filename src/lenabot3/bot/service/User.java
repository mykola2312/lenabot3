package lenabot3.bot.service;

public class User {
	private final int id;
	private final String name;
	private final String screenName;
	
	public User(int id,String name,String screenName) {
		this.id = id;
		this.name = name;
		this.screenName = screenName;
	}
	
	public int getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public String getScreenName() {
		return screenName;
	}
	
	public String asArgument() {
		if (id >= 0) {
			return "id" + getId();
		} else {
			return "club" + (-getId());
		}
	}
	
	public String asMention(String asName) {
		if (id >= 0) {
			return String.format("[%s|%s]", asArgument(), asName);
		} else {
			return String.format("[%s|%s]", asArgument(), asName);
		}
	}
	
	public String asMention() {
		return asMention(getName());
	}
}
