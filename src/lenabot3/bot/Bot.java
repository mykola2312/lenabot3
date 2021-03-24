package lenabot3.bot;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lenabot3.bot.KeyboardService.Button;
import lenabot3.bot.KeyboardService.Keyboard;
import lenabot3.bot.service.ConversationService;
import lenabot3.bot.service.DataBase;
import lenabot3.bot.service.EventSystem;
import lenabot3.bot.service.IEvent;
import lenabot3.bot.service.UserCache;
import lenabot3.util.JsonData;
import lenabot3.vk.IVkApiHandler;
import lenabot3.vk.IVkRequest;
import lenabot3.vk.VkApi;
import lenabot3.vk.VkApiAsync;
import lenabot3.vk.VkRequest;
import lenabot3.vk.exception.VkException;
import lenabot3.vk.object.VkEvent;
import lenabot3.vk.object.VkMessage;
import lenabot3.vk.request.VkLongPoll;

public class Bot implements ITask, IVkApiHandler {
	private static final Gson gson = new Gson();
	
	private final String configFile;
	
	public static final int GLOBAL_PEER_ID = -1;
	
	private VkApiAsync botApi;
	private VkApi userApi = null;
	
	private CommandParser commandParser;
	private LinkedList<IService> services = new LinkedList<IService>();
	private Command currentCommand;
	
	//Default services
	private DataBase db;
	
	private EventSystem eventSystem;
	private ConversationService convService;
	private UserCache userCache;
	private KeyboardService keyboardService;
	
	private String longPollServer;
	private String longPollKey;
	
	private boolean running = false;
	
	private long messageCounter = 0;
	
	private long serviceUpdateInterval = Long.MAX_VALUE;
	
	private int vk_maingroup;
	private int vk_mainconv;
	ArrayList<Integer> vk_admins = new ArrayList<Integer>();
	private String vk_screen_name;
	
	public Bot(String configFile) {
		this.configFile = configFile;
	}
	
	protected void registerServices() {
		eventSystem = new EventSystem();
		convService = new ConversationService();
		userCache = new UserCache();
		keyboardService = new KeyboardService();
		
		registerService(eventSystem);
		registerService(convService);
		registerService(userCache);
		registerService(keyboardService);
	}
	
	protected void registerEvents() {}
	
	public boolean start() {
		JsonObject config = JsonData.loadJsonFile(configFile);
		if (config == null) {
			System.err.println("[Bot] Failed to load config! Terminating.");
			return false;
		}
		
		String bot_token = null,user_token = null;
		String proxyHost = null;
		int proxyPort = 0;
		try {
			bot_token = config.get("bot_token").getAsString();
			vk_maingroup = config.get("maingroup").getAsInt();
			vk_mainconv = config.get("mainconv").getAsInt();
			
			if (config.has("admins")) {
				for(JsonElement u : config.get("admins").getAsJsonArray()) {
					vk_admins.add(u.getAsInt());
				}
			}
			
			if (config.has("proxy")) {
				JsonElement proxy = config.get("proxy");
				proxyHost = proxy.getAsJsonObject().get("host").getAsString();
				proxyPort = proxy.getAsJsonObject().get("port").getAsInt();
			}
			
			if (config.has("user_token")) {
				user_token = config.get("user_token").getAsString();
			}
		} catch (NullPointerException e) {
			System.err.println("[Bot] Bad config!");
			return false;
		}
		
		botApi = new VkApiAsync(bot_token,true);
		botApi.setApiHandler(this);
		if (proxyHost != null && proxyPort != 0) {
			botApi.setProxy(proxyHost, proxyPort);
		}
		
		if (user_token != null) {
			userApi = new VkApi(user_token,false);
			if (proxyHost != null && proxyPort != 0) {
				userApi.setProxy(proxyHost, proxyPort);
			}
		}
		
		botApi.start();
		
		running = true;
		
		getGroupInformation();
		setLongPollSettings();
		
		commandParser = new CommandParser(vk_maingroup,vk_screen_name);
		
		//DataBase first
		db = new DataBase();
		if (!db.load()) {
			System.err.println("Failed to load DataBase service! Terminating.");
			return false;
		}
		registerService(db);
		
		//Start up services
		registerServices();
		for (IService service : services) {
			service.load();
			service.start();
		}
		
		//Start up events (built-in service)
		registerEvents();
		
		return prepareLongPoll();
	}
	
	public DataBase getDataBaseService() {
		return db;
	}
	
	public Connection getSqlConnection() {
		return db.getSqlConnection();
	}
	
	public EventSystem getEventSystem() {
		return eventSystem;
	}
	
	public ConversationService getConversationService() {
		return convService;
	}
	
	public UserCache getUserCache() {
		return userCache;
	}
	
	public KeyboardService getKeyboardService() {
		return keyboardService;
	}
	
	protected void addInterpreterName(String name) {
		System.out.println("Added interpreter name: " + name);
		commandParser.addName(name);
	}
	
	protected void registerService(IService service) {
		service.setBot(this);
		services.add(service);
	}
	
	protected void registerEvent(IEvent event) {
		EventSystem es = (EventSystem)getService(EventSystem.EVENTSYSTEM_SERVICE);
		if (es != null) {
			es.registerEvent(event);
		}
	}
	
	public IService getService(String name) {
		for (IService service : services) {
			if (service.getName().equals(name)) {
				return service;
			}
		}
		
		return null;
	}
	
	public void stop() { 
		for (IService service : services) {
			service.save();
			service.stop();
		}
		
		running = false;
		botApi.stop();
	}
	
	public void run() {
		try {
			while (running) {
				serviceUpdateInterval = Long.MAX_VALUE;
				
				for (IService service : services) {
					try {
						service.run();
					} catch (Exception e) {
						onException(e);
					}
					
					long serviceInterval = service.getUpdateInterval();
					if (serviceInterval > 0 && serviceInterval < this.serviceUpdateInterval) {
						this.serviceUpdateInterval = serviceInterval;
					}
				}
				
				
				if (serviceUpdateInterval != Long.MAX_VALUE
						&& serviceUpdateInterval > 0) {
					Thread.sleep(serviceUpdateInterval);
				} else {
					Thread.sleep(1000L);
				}
			}
		} catch (InterruptedException e) {
			//Stop
			stop();
		}
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public VkApi getBotApi() {
		return botApi;
	}
	
	public VkApi getUserApi() {
		return userApi;
	}
	
	public void request(IVkRequest vkRequest) {
		if(!isRunning()) {
			return;
		}
		
		botApi.requestAsync(vkRequest);
	}
	
	private void getGroupInformation() {
		try {
			JsonElement response = botApi.request(new VkRequest("groups.getById")
					.set("group_id", vk_maingroup));
			vk_screen_name = response.getAsJsonArray().get(0).getAsJsonObject()
					.get("screen_name").getAsString();
		} catch (Exception e) {
			System.err.println(String.format("[getGroupInformation] Exception - %s",
					e.getLocalizedMessage()));
			vk_screen_name = "club" + String.valueOf(vk_maingroup);
		}
		System.out.printf("Group:\n\tID: %d\n\tscreen_name: %s\n",
				vk_maingroup, vk_screen_name);
	}
	
	private void setLongPollSettings() {
		botApi.request(new VkRequest("groups.setLongPollSettings")
				.set("group_id", vk_maingroup)
				.set("enabled", 1)
				.set("api_version", VkApi.API_VERSION)
				.set("message_new", 1)
		);
	}
	
	private boolean prepareLongPoll() {
		int ts;
		try {
			JsonObject lpData = botApi.request(new VkRequest("groups.getLongPollServer")
					.set("group_id", vk_maingroup)).getAsJsonObject();
			longPollServer = lpData.get("server").getAsString();
			longPollKey = lpData.get("key").getAsString();
			ts = lpData.get("ts").getAsInt();
		} catch (VkException e) {
			System.err.println(String.format("[prepareLongPoll] Method [%s] - %s",
					e.getRequest().getMethod(),e.getMessage()));
			return false;
		} catch (Exception e) {
			System.err.println(String.format("[prepareLongPoll] Exception - %s",
					e.getLocalizedMessage()));
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Long Poll:\n\tServer: %s\n\tKey: %s\n",longPollServer,longPollKey);
		request(new VkLongPoll(longPollServer,longPollKey,ts));
		return true;
	}
	
	public boolean isAdmin(int user) {
		return vk_admins.contains(user);
	}
	
	public boolean isAdminConversation(int peer_id) {
		if (!getConversationService().isUserConversation(peer_id)) {
			return false;
		}
		return isAdmin(peer_id);
	}
	
	public int getMainConversation() {
		return vk_mainconv;
	}
	
	public String getScreenName() {
		return vk_screen_name;
	}
	
	public int getBotId() {
		return vk_maingroup;
	}
	
	public String getBotMention() {
		return "[club" + getBotId() + "|" + getScreenName() + "]";
	}
	
	public VkRequest createSendMessage(int peer_id,String message,int reply_to,String attachment) {
		VkRequest send = new VkRequest("messages.send");
		send.set("peer_id",peer_id);
		send.set("random_id", String.valueOf(System.currentTimeMillis() + messageCounter++));
		if (message != null && message.length() > 0) {
			send.setPost("message",message);
		}
		if (reply_to != 0) {
			send.set("reply_to", reply_to);
		}
		
		if (attachment != null) {
			send.set("attachment", attachment);
		}
		
		return send;
	}
	
	private void doSendMessage(int peer_id,String message,int reply_to,String attachment) {
		VkRequest send = createSendMessage(peer_id,message,reply_to,attachment);
		
		getBotApi().request(send);
	}
	
	public void sendMessage(int peer_id,String message,int reply_to,String attachment) {
		final int BLOCK_LENGTH = 4096;
		int i = 0, len = message.length();
		if (len > 0) {
			while (len > 0) {
				boolean isLast = len < BLOCK_LENGTH;
				doSendMessage(peer_id,
						message.substring(i,i+Math.min(len, BLOCK_LENGTH)),
						isLast ? reply_to : 0,
						isLast ? attachment : null
				);
				i += BLOCK_LENGTH;
				len -= BLOCK_LENGTH;
			}
		} else {
			doSendMessage(peer_id,message,reply_to,attachment);
		}
	}
	
	public void sendMessage(int peer_id,String message,int reply_to) {
		sendMessage(peer_id,message,reply_to,null);
	}
	
	public void sendMessage(int peer_id,String message, String attachment) {
		sendMessage(peer_id,message,0,attachment);
	}
	
	public void sendMessage(int peer_id,String message) {
		sendMessage(peer_id,message,0,null);
	}
	
	public void onVkException(IVkRequest vkRequest, VkException e) {
		if (vkRequest.getRequestType() == IVkRequest.RequestType.LongPoll) {
			//Restart long poll
			while(!prepareLongPoll()) {
				System.out.println("Restarting long poll..");
			}
		} else {
			String err = String.format("[Bot.onVkException] Method [%s] - %s",
					vkRequest == null ? null : vkRequest.getMethod(),
					e.getLocalizedMessage());
			System.err.println(err);
			sendExceptionReport(vk_mainconv,e,err + "\n\n");
		}
	}
	
	public void onException(Exception e) {
		if(e instanceof VkException) {
			onVkException(null, (VkException)e);
		} else {
			String err = String.format("[Bot.onException] %s",
					e.getLocalizedMessage());
			System.err.println(err);
			e.printStackTrace(System.err);
			sendExceptionReport(vk_mainconv,e,err + "\n\n");
		}
	}
	
	protected void sendExceptionReport(int peer_id,Exception e,String header) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		
		if (header != null) {
			pw.write(header);
		}
		pw.printf("\n - произошло исключение: %s\n\n", e.getLocalizedMessage());
		e.printStackTrace(pw);
		
		//Send report via synchronized bot api
		VkRequest send = createSendMessage(peer_id, sw.toString(), 0, null);
		getBotApi().request(send);
	}

	public void onVkRequestComplete(IVkRequest vkRequest, JsonElement response) {
		if (vkRequest.getRequestType() == IVkRequest.RequestType.LongPoll) {
			processLongPoll(response.getAsJsonObject());
		} else {
			vkRequest.onComplete(response);
		}
	}

	private void processLongPoll(JsonObject response) {
		JsonElement failed = response.get("failed");
		if (failed != null) {
			int failedCode = failed.getAsInt();
			System.out.println(String.format("[processLongPoll] Error %d", failedCode));
			//Restart long poll until it will work
			while(!prepareLongPoll()) {}
			return;
		}
		
		JsonElement updates = response.get("updates");
		if(updates != null) {
			for(JsonElement update : updates.getAsJsonArray()) {
				processUpdate(update.getAsJsonObject());
			}
		}
		
		request(new VkLongPoll(longPollServer,longPollKey,
				response.get("ts").getAsInt()));
	}
	
	protected void processUpdate(JsonObject update) {
		try {
			VkEvent event = new VkEvent(update);
			processEvent(event);
		} catch (VkException e) {
			System.err.println(String.format("[processUpdate] VkException [%s] - %s",
					e.getRequest().getMethod(),e.getMessage()));
			onVkException(e.getRequest(), e);
		} catch (Exception e) {
			System.err.println(String.format("[processUpdate] Exception - %s",
					e.getLocalizedMessage()));
		}
	}
	
	protected void processEvent(VkEvent event) throws Exception {
		System.out.println(gson.toJson(event.getObject()));
		switch(event.getType()) {
		case "message_new":
			VkMessage message = new VkMessage(event.getObject());
			if (!processMessage(message)) {
				for (IService service : services) {
					try {
						if (service.processMessage(message)) {
							break;
						}
					} catch (Exception e) {
						sendExceptionReport(vk_mainconv,e,String.format(
								"Service(%s).processMessage - Exception",service.getName()));
					}
				}
			}
			System.out.printf("VkMessage [%s]: %s\n",event.getType(),
					gson.toJson(event.getObject()));
			break;
		default:
			System.out.printf("VkEvent [%s]: %s\n",event.getType(),
					gson.toJson(event.getObject()));
			for (IService service : services) {
				try {
					if (service.processEvent(event)) {
						break;
					}
				} catch (Exception e) {
					sendExceptionReport(vk_mainconv,e,String.format(
							"Service(%s).processEvent - Exception",service.getName()));
				}
			}
		}
	}
	
	public Command getCurrentCommand() {
		return currentCommand;
	}
	
	protected boolean processMessage(VkMessage message) {
		//Check if conversation registered or not
		getConversationService().onMessage(message);
		boolean isUserConv = getConversationService().isUserConversation(message.getPeerId());
		if (commandParser.isPayloadCommand(message, isUserConv)) {
			try {
				return keyboardService.processPayloadMessage(message);
			} catch (Exception e) {
				String err = String.format(
						"[processMessage] Failed to process payload \"%s\" %s\n",
						message.getText(),JsonData.jsonToString(message.getPayload()));
				
				System.err.print(err);
				sendMessage(vk_mainconv,err);
				onException(e);
				return false;
			}
		} else if (commandParser.isCommand(message.getText(), isUserConv)) {
			Command command;
			try {
				command = commandParser.parse(this, message, 
						message.getText(), isUserConv);
			} catch (Exception e) {
				System.err.printf("[processMessage] Failed to parse command \"%s\"\n",
						message.getText());
				onException(e);
				return false;
			}
			
			return runCommand(command);
		}
		return false;
	}
	
	public boolean runCommand(Command command) {
		currentCommand = command;
		
		try {
			if (!processCommand(currentCommand)) {
				for (IService service : services) {
					try {
						if (service.processCommand(currentCommand)) {
							break;
						}
					} catch (Exception e) {
						sendExceptionReport(vk_mainconv,e,String.format(
								"Service(%s).processCommand(%s) - Exception",
								service.getName(),currentCommand.getName()));
					}
				}
			}
		} catch (Exception e) {
			System.err.printf("[processCommand] Command %s from user %d - %s\n",
					currentCommand.getName(),currentCommand.getUser(),
					e.getLocalizedMessage());
			//Send exception report
			sendExceptionReport(currentCommand.getPeerId(),e,String.format(
					"При выполнение команды \"%s\" для пользователя [id%d|@id%d]\n",
					currentCommand.getName(),currentCommand.getUser(),
					currentCommand.getUser()));
			return false;
		}
		
		return true;
	}
	
	protected boolean processCommand(Command command) {
		if (command.getName().equals("парсер")) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.printf("Пользователь: %d\n",command.getUser());
			pw.printf("Чат: %d\n\n",command.getPeerId());
			
			pw.printf("Интерпретатор: \"%s\"\n",command.getInterpreterName());
			pw.printf("Команда: \"%s\"\n",command.getName());
			pw.printf("Количество аргументов: %d\n",command.argC());
			pw.printf("- Аргументы: \"%s\"\n",command.argS());
			for (int i = 0; i < command.argC(); i++) {
				pw.printf("%d аргумент: \"%s\"\n",i,command.arg(i));
			}
			
			sendMessage(command.getPeerId(),sw.toString());
			return true;
		} else if (command.getName().equals("инфо")) {
			JsonElement response = botApi.request(new VkRequest("users.get")
					.set("user_ids", command.getUser())
			);
			
			sendMessage(command.getPeerId(),gson.toJson(response));
			return true;
		} else if (command.getName().equals("сервисы") && isAdmin(command.getUser())) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.printf("[Bot] сервисы\nИнтервал обновления: %d мс\n",serviceUpdateInterval);
			for (IService service : services) {
				pw.printf("\"%s\" - интервал обновления %d мс\n",
						service.getName(),service.getUpdateInterval());
			}
			
			sendMessage(command.getPeerId(),sw.toString());
			return true;
		} else if (command.getName().equals("меню")) {
			Keyboard kb = keyboardService.createKeyboard(false, false, true);
			List<Button> row = kb.createRow();
			
			row.add(kb.createButton("статус Тихонова", Button.COLOR_WHITE, 
					"статус", "id518441499"));
			row.add(kb.createButton("статус Калашникова", Button.COLOR_WHITE, 
					"статус", "id390496859"));
			
			row = kb.createRow();
			row.add(kb.createButton("Закрыть меню", Button.COLOR_RED, "ks_remove"));
			
			keyboardService.sendKeyboard(command.getPeerId(), command.getUser(), kb);
			return true;
		} else if (command.getName().equals("keyboard_ctrl")) {
			if (command.arg(0).equals("first")) {
				sendMessage(command.getPeerId(),"[keyboard_ctrl] первая команда");
			} else if (command.arg(0).equals("second")) {
				sendMessage(command.getPeerId(),"[keyboard_ctrl] вторая команда");
			}
		}
		return false;
	}
}
