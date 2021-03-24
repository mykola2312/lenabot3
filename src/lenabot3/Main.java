package lenabot3;

public class Main {
	public static LenaBot bot = new LenaBot("config.json");
	
	public static void main(String[] args) {
		if (!bot.start()) {
			System.err.println("Failed to start bot.");
			return;
		}
		bot.run();
	}

}
