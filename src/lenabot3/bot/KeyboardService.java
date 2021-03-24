package lenabot3.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;

import lenabot3.vk.VkRequest;
import lenabot3.vk.object.VkMessage;

public class KeyboardService extends Service {
	public static final String KEYBOARDSERVICE_SERVICE = "KeyboardSerivce";
	public static final long KEYBOARD_AUTOREMOVE_INTERVAL = 1000L * 60L; 
	
	private static int keyboardCounter = 0;
	
	private HashMap<Integer,Keyboard> keyboards = new HashMap<Integer,Keyboard>();
	
	public class Action {
		public static final String TYPE_TEXT = "text";
		public static final String TYPE_OPEN_LINK = "open_link";
		public static final String TYPE_LOCATION = "location";
		public static final String TYPE_VK_PAY = "vkpay";
		
		@Expose
		public String type;
		@Expose
		public String link = null;
		@Expose
		public String label = null;
		@Expose
		public String payload = null;
		
		public void asTextButton(String label) {
			this.type = "text";
			this.label = label;
		}
		
		public void asLink(String label,String link) {
			this.type = "link";
			this.label = label;
			this.link = link;
		}
		
		public void setPayload(String payload) {
			this.payload = payload;
		}
	}
	
	private class CommandButton {
		private final String name;
		private final String[] args;
		
		public CommandButton(String name,String... args) {
			this.name = name;
			this.args = args;
		}
		
		public String getName() {
			return name;
		}
		
		public String[] getArgs() {
			return args;
		}
	}
	
	public class Button {
		public static final String COLOR_BLUE = "primary";
		public static final String COLOR_WHITE = "secondary";
		public static final String COLOR_RED = "negative";
		public static final String COLOR_GREEN = "positive";
		
		@Expose
		public final Action action;
		@Expose
		public final String color;
		
		@Expose(serialize = false)
		private CommandButton command = null;
		@Expose(serialize = false)
		private int buttonId;
		
		public Button(Action action,String color) {
			this.action = action;
			this.color = color;
		}
		
		public Button(String label,String color) {
			this.action = new Action();
			action.asTextButton(label);
			this.color = color;
		}
		
		public Button(String label,String link,String color) {
			this.action = new Action();
			action.asLink(label, link);
			this.color = color;
		}
		
		public Button(String label,String color,String name,String... args) {
			this.action = new Action();
			this.color = color;
			action.asTextButton(label);
			command = new CommandButton(name, args);
		}
		
		public CommandButton getCommand() {
			return command;
		}
		
		public void setButtonId(int buttonId) {
			this.buttonId = buttonId;
		}
		
		public int getButtonId() {
			return buttonId;
		}
	}
	
	public class Keyboard {
		@Expose(serialize = false)
		private int peerId;
		@Expose(serialize = false)
		private int userId;
		@Expose(serialize = false)
		private final boolean shared; 
		@Expose(serialize = false)
		private long lastTimeUsed = 0;
		@Expose(serialize = false)
		private int lastUserId = 0;
		
		@Expose
		public boolean one_time = true;
		@Expose
		public boolean inline = false;
		@Expose
		public List<List<Button>> buttons = new ArrayList<List<Button>>();
		
		public Keyboard(boolean oneTime, boolean inline, boolean shared) {
			this.one_time = oneTime;
			this.inline = inline;
			this.shared = shared;
		}
		
		public List<Button> createRow() {
			List<Button> row = new ArrayList<Button>();
			buttons.add(row);
			return row;
		}
		
		public Button createButton(String label,String color,String command,String... args) {
			return  new Button(label,color,command,args);
		}
		
		public boolean isEmpty() {
			return buttons.isEmpty();
		}
		
		public void setPeerId(int peerId) {
			this.peerId = peerId;
		}
		
		public void setUserId(int userId) {
			this.userId = userId;
		}
		
		public int getPeerId() {
			return peerId;
		}
		
		public int getUserId() {
			return userId;
		}
		
		public void setLastTimeUsed(long time) {
			lastTimeUsed = time;
		}
		
		public void update(int userId) {
			lastTimeUsed = System.currentTimeMillis();
			if (shared) {
				lastUserId = userId;
			}
		}
		
		public long getLastActivity() {
			return lastTimeUsed;
		}
		
		public int getLastUserId() {
			return lastUserId;
		}
	}
	
	private class Payload {
		@Expose
		private final int keyboard_id;
		@Expose
		private final int button_id;
		
		public Payload(int keyboardId, int buttonId) {
			this.keyboard_id = keyboardId;
			this.button_id = buttonId;
		}
	}
	
	KeyboardService() {
		super(KEYBOARDSERVICE_SERVICE);
	}
	
	public boolean load() {
		setUpdateInterval(KEYBOARD_AUTOREMOVE_INTERVAL);
		return true;
	}
	
	public void save() {}
	
	public boolean start() {
		return true;
	}
	
	public void stop() {}
	
	public void run() {
		for (Map.Entry<Integer, Keyboard> entry : keyboards.entrySet()) {
			long curtime = System.currentTimeMillis();
			long lastUsed = entry.getValue().getLastActivity();
			if (curtime - lastUsed > KEYBOARD_AUTOREMOVE_INTERVAL 
					&& !entry.getValue().one_time) {
				removeKeyboard(entry.getKey(), true);
			}
		}
	}
	
	public boolean processCommand(Command command) {
		if (command.getName().equals("ks_remove")) {
			for (Map.Entry<Integer, Keyboard> entry : keyboards.entrySet()) {
				Keyboard kb = entry.getValue();
				if (kb.getPeerId() == command.getPeerId()) {
					removeKeyboard(entry.getKey(), true);
				}
			}
			
			return true;
		}
		return false;
	}
	
	public Keyboard createKeyboard(boolean oneTime,boolean inline,boolean shared) {
		return new Keyboard(oneTime, inline, shared);
	}
	
	private void sendKeyboard(Keyboard keyboard) {
		keyboard.setLastTimeUsed(System.currentTimeMillis());
		
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		//serialize keyboard
		VkRequest send = getBot().createSendMessage(keyboard.getPeerId(), 
				keyboard.isEmpty() ? "Успешно" : "Меню", 0, null);
		send.set("keyboard", gson.toJson(keyboard));
		//send keyboard
		getBot().getBotApi().request(send);
	}
	
	public void sendKeyboard(int peerId,int userId,Keyboard keyboard) {
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
		//prepare keyboard
		int keyboardId = keyboardCounter++;
		int buttonId = 0;		
		for(List<Button> row : keyboard.buttons) {
			for (Button button : row) {
				button.setButtonId(buttonId);
				Payload payload = new Payload(keyboardId,buttonId);
				button.action.setPayload(gson.toJson(payload));
				buttonId++;
			}
		}
		keyboard.setPeerId(peerId);
		keyboard.setUserId(userId);
		keyboards.put(keyboardId, keyboard);
		
		sendKeyboard(keyboard);
	}
	
	public void hideKeyboard(int peerId) {
		Keyboard empty = new Keyboard(true,false,true);
		empty.setPeerId(peerId);
		sendKeyboard(empty);
	}
	
	private void removeKeyboard(int keyboardId, boolean alwaysHide) {
		Keyboard keyboard = keyboards.get(keyboardId);
		if (!keyboard.inline && (!keyboard.one_time || alwaysHide)) {
			hideKeyboard(keyboard.getPeerId());
		}
		keyboards.remove(keyboardId);
	}
	
	public boolean processPayloadMessage(VkMessage message) {
		JsonObject payload = message.getPayload().getAsJsonObject();
		if (!payload.has("keyboard_id") || !payload.has("button_id")) {
			return false;
		}
		
		int userId = message.getFromId();
		
		int keyboardId = payload.get("keyboard_id").getAsInt();
		int buttonId = payload.get("button_id").getAsInt();
		
		Keyboard keyboard = keyboards.get(keyboardId);
		if (keyboard == null) {
			return false;
		}
		
		if (keyboard.getPeerId() != message.getPeerId()) {
			//This is strange behavior, but any way..
			return false;
		}
		
		if (userId != keyboard.getUserId() && !keyboard.shared) {
			if (keyboard.one_time) {
				//If this keyboard isn't for this user - send again
				sendKeyboard(keyboard);
			}
			//If this keyboard isn't one_time - just ignore
			return false;
		}
		
		Button button = null;
		for (List<Button> row : keyboard.buttons) {
			for (Button btn : row) {
				if (btn.getButtonId() == buttonId) {
					button = btn;
				}
			}
		}
		
		if (button == null) {
			return false;
		}
		
		//Execute payload's command
		CommandButton command = button.getCommand();
		if (command != null) {
			Bot bot = getBot();
			//Compose command
			ArrayList<String> args = new ArrayList<String>();
			args.add(bot.getBotMention());
			args.add(command.getName());
			String nativeArgs[] = command.getArgs();
			for (int i = 0; i < nativeArgs.length; i++) {
				args.add(nativeArgs[i]);
			}
			//Execute command!
			bot.runCommand(new Command(bot, message, args));
		}
		
		//Remove keyboard if necessary or update
		if (keyboard.one_time) {
			removeKeyboard(keyboardId, false);
		} else {
			//Update interval
			keyboard.update(userId);
		}
		
		return true;
	}
}
