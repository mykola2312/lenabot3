package lenabot3.bot;

import java.util.ArrayList;

import lenabot3.vk.object.VkMessage;

public class CommandParser {
	
	private ArrayList<String> interpreterNames = new ArrayList<String>();
	private String localMention;
	
	public CommandParser(int interpreterId, String interpreterScreenName) {
		addName(interpreterScreenName);
		addName("[club" + interpreterId + "|");
		addName("[public" + interpreterId + "|");
		addName("[id" + -interpreterId + "|");
		addName("[id" + interpreterId + "|");
		localMention = "[club" + interpreterId + "|";
	}
	
	public void addName(String name) {
		interpreterNames.add(name);
	}
	
	private boolean isBotMention(String line, boolean isUserConv) {
		if (isUserConv) {
			return true;
		}
		
		for (String name : interpreterNames) {
			if (line.startsWith(name)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean isCommand(String line, boolean isUserConv) {
		if (isUserConv) {
			return true;
		}
		
		for (String name : interpreterNames) {
			if (line.startsWith(name)) {
				if (line.length() > name.length()) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public boolean isPayloadCommand(VkMessage message, boolean isUserConv) {
		if (!message.hasPayload()) {
			return false;
		}
		
		return isBotMention(message.getText(), isUserConv);
	}
	
	public Command parse(Bot bot,VkMessage message, String line, boolean isUserConv) {
		ArrayList<String> tokens = new ArrayList<String>();
		StringBuffer token = new StringBuffer();
		
		//parser state
		boolean inQuotes = false;
		boolean push = false;
		
		boolean inBrackets = false;
		boolean ignore = false;
		
		if (isUserConv) {
			tokens.add(localMention);
		}
		
		for (char c : line.toCharArray()) {
			if (push) {
				tokens.add(token.toString());
				token = new StringBuffer();
				//reset parser state
				inQuotes = false;
				push = false;
				
				inBrackets = false;
				ignore = false;
			}
			
			if (c == '\"') {
				inQuotes = !inQuotes;
			} else if (c == '[' && !inQuotes) {
				inBrackets = true;
			} else if (c == '|' && inBrackets && !inQuotes) {
				ignore = true;
			} else if (c == ']' && !inQuotes) {
				inBrackets = false;
			} else if (c == ' ' && !inQuotes && !inBrackets) {
				//separator
				push = true;
			} else if (!ignore) {
				//write
				token.append(c);
			}
		}
		
		tokens.add(token.toString());
		return new Command(bot,message,tokens);
	}
}
