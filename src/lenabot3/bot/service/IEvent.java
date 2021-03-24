package lenabot3.bot.service;

import lenabot3.bot.Bot;

public interface IEvent {
	public String getName();
	public long getInterval();
	public void fire(Bot bot);
}
