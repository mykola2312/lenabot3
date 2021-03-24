package lenabot3;

import java.util.ArrayList;
import java.util.Random;

import lenabot3.bot.Bot;
import lenabot3.bot.Command;
import lenabot3.bot.Service;
import lenabot3.vk.VkRequest;
import lenabot3.vk.object.VkMessage;

public class LenaBot extends Bot {

	public LenaBot(String configFile) {
		super(configFile);
	}
	
	protected void registerServices() {
		super.registerServices();
		
		registerService(new AdminService());
		registerService(new ActionService());
		
		registerService(new Service("LenaTihonova_StressTest") {
			static final int LENATIHONOVA_ID = 412569479;
			static final int BOT_CONV_PEER_ID = 2000000132;
			
			private ArrayList<String> lenaMessages = new ArrayList<String>();
			
			public boolean load() {
				return true;
			}
			
			public void save() {}
			
			public boolean start() {
				return true;
			}
			
			public void stop() {}
			
			public void run() {}
			
			public boolean processMessage(VkMessage message) {
				if (message.getFromId() == LENATIHONOVA_ID) {
					lenaMessages.add(message.getText());
					Random random = new Random();
					//getBot().sendMessage(message.getPeerId(),lenaMessages.get(
					//		random.nextInt(lenaMessages.size())));
					String randMessage = lenaMessages.get(
							random.nextInt(lenaMessages.size())); 
					getBot().getUserApi().request(new VkRequest("messages.send")
							.set("peer_id", BOT_CONV_PEER_ID)
							.set("random_id", String.valueOf(random.nextLong()))
							.setPost("message", randMessage));
					return true;
				}
				return false;
			}
		});
		
		registerService(new Service("ThirdTemple") {
			public boolean load() {
				return true;
			}
			
			public void save() {}
			
			public boolean start() {
				return true;
			}
			
			public void stop() {}
			
			public void run() {}
			
			public boolean processCommand(Command cmd) {
				if (getBot().isAdmin(cmd.getUser())) {
					if (cmd.getName().equals("сказать")) {
						getBot().sendMessage(cmd.getPeerId(), cmd.argS());
						return true;
					}
				}
				
				return false;
			}
		});
	}
	
	public boolean start() {
		if (!super.start()) {
			return false;
		}
		
		addInterpreterName("Лена");
		addInterpreterName("ЛенаБот");
		addInterpreterName("Ленабот");
		addInterpreterName("ленабот");
		addInterpreterName("lenabot");
		addInterpreterName("lenabot3");
		addInterpreterName("Бот");
		addInterpreterName("БЛ");
		
		return true;
	}
}
