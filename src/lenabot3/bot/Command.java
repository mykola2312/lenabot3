package lenabot3.bot;

import java.util.ArrayList;

import lenabot3.vk.object.VkMessage;

public class Command {
	
	private final Bot bot;
	private final VkMessage message;
	private final ArrayList<String> args;
	
	public Command(Bot bot,VkMessage message, ArrayList<String> args) {
		this.bot = bot;
		this.message = message;
		this.args = args;
	}
	
	public Bot getBot() {
		return bot;
	}
	
	protected String get(int i) {
		if (i < 0 || i >= args.size()) {
			return "";
		}
		
		return args.get(i);
	}
	
	public String getInterpreterName() {
		return get(0);
	}
	
	public String getName() {
		return get(1);
	}
	
	public String arg(int i) {
		return get(i+2);
	}
	
	public int argC() {
		int size = args.size() - 2;
		return size >= 0 ? size : 0;
	}
	
	public String argS(int from) {
		StringBuffer line = new StringBuffer("");
		
		for (int i = from; i < argC(); i++) {
			line.append(arg(i) + (i == argC()-1 ? "" : " "));
		}
		
		return line.toString();
	}
	
	public String argS() {
		return argS(0);
	}
	
	public VkMessage getMessage() {
		return message;
	}
	
	public int getUser() {
		return message.getFromId();
	}
	
	public int getPeerId() {
		return message.getPeerId();
	}
}
