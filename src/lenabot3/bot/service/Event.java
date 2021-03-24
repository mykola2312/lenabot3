package lenabot3.bot.service;

public abstract class Event implements IEvent {
	
	private final String name;
	private long interval;
	
	public Event(String name, long interval) {
		this.name = name;
		this.interval = interval;
	}
	
	public String getName() {
		return name;
	}
	
	public long getInterval() {
		return interval;
	}
}
