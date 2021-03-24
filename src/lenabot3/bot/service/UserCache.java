package lenabot3.bot.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lenabot3.bot.Command;
import lenabot3.bot.Service;
import lenabot3.vk.VkRequest;

public class UserCache extends Service {
	public static final String USERCACHE_SERVICE = "UserCache";
	public static final long CACHE_CLEAN_INTERVAL = 1000L * 60L * 60L * 6L; //every 6 hours
	
	private LinkedList<User> userCache = new LinkedList<User>();
	private long nextCacheClean = 0L;
	
	public UserCache() {
		super(USERCACHE_SERVICE);
	}
	
	public boolean load() {
		nextCacheClean = System.currentTimeMillis() + CACHE_CLEAN_INTERVAL;
		setUpdateInterval(1000L * 60L);
		return true;
	}
	
	public void save() {}
	
	public boolean start() {
		return true;
	}
	
	public void stop() {}
	public void run() {
		if (System.currentTimeMillis() >= nextCacheClean) {
			userCache.clear();
			
			nextCacheClean = System.currentTimeMillis() + CACHE_CLEAN_INTERVAL;
		}
	}
	
	public boolean processCommand(Command command) {
		if (!getBot().isAdminConversation(command.getPeerId()) ) {
			return false;
		}
		
		if (command.getName().equals("user_cache")) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("[UserCache] кэшированные пользователи\n");
			for (User user : userCache) {
				pw.println(user.asMention());
			}
			
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		}
		
		return false;
	}
	
	private void cacheUser(int id) {
		try {
			User cacheUser;
			if (id > 0) {
				JsonElement response = getBot().getBotApi().request(new VkRequest("users.get")
						.set("user_ids", id)
						.set("fields","screen_name")
				);
				JsonObject user = response.getAsJsonArray().get(0).getAsJsonObject();
				cacheUser = new User(user.get("id").getAsInt(),String.format(
						"%s %s",
								user.get("first_name").getAsString(),
								user.get("last_name").getAsString()),
						user.get("screen_name").getAsString()
				);
			} else if (id < 0) {
				JsonElement response = getBot().getBotApi().request(
						new VkRequest("groups.getById")
								.set("group_ids", -id)
				);
				JsonObject group = response.getAsJsonArray().get(0).getAsJsonObject();
				cacheUser = new User(-group.get("id").getAsInt(),
						group.get("name").getAsString(),
						group.get("screen_name").getAsString()
				);
			} else {
				return;
			}
			
			userCache.add(cacheUser);
		} catch (Exception e) {
			getBot().onException(e);
		}
	}
	
	private static String joinUserIds(List<Integer> idList) {
		StringBuilder sb = new StringBuilder();
		Iterator<Integer> it = idList.iterator();
		while(it.hasNext()) {
			sb.append(it.next().toString());
			if (it.hasNext()) {
				sb.append(',');
			}
		}
		return sb.toString();
	}
	
	public void cacheUsers(List<Integer> idList) {
		LinkedList<Integer> userIds = new LinkedList<Integer>();
		LinkedList<Integer> groupIds = new LinkedList<Integer>();
		
		for (Integer userId : idList) {
			if (getCacheUser(userId) == null) {
				if (userId >= 0) {
					userIds.add(userId);
				} else {
					groupIds.add(-userId);
				}
			}
		}
		
		try {
			if (!userIds.isEmpty()) {
				JsonElement resp = getBot().getBotApi().request(new VkRequest("users.get")
						.set("user_ids", joinUserIds(userIds))
						.set("fields", "screen_name")
				);
				
				for (JsonElement item : resp.getAsJsonArray()) {
					JsonObject user = item.getAsJsonObject();
					int userId = user.get("id").getAsInt();
					String name = user.get("first_name").getAsString() 
							+ " " +	user.get("last_name").getAsString();
					String screenName;
					if (user.has("screen_name")) {
						screenName = user.get("screen_name").getAsString();
					} else {
						screenName = "id" + userId;
					}
					
					userCache.add(new User(userId,name,screenName));
				}
			}
			
			if (!groupIds.isEmpty()) {
				JsonElement resp = getBot().getBotApi().request(
						new VkRequest("groups.getById")
								.set("group_ids", joinUserIds(groupIds))
				);
				
				for (JsonElement item : resp.getAsJsonArray()) {
					JsonObject group = item.getAsJsonObject();
					int groupId = group.get("id").getAsInt();
					String name;
					String screenName;
					
					if (group.has("name")) {
						name = group.get("name").getAsString();
					} else {
						name = "club" + groupId;
					}
					
					if (group.has("screen_name")) {
						screenName = group.get("screen_name").getAsString();
					} else {
						screenName = "club" + groupId;
					}
					
					userCache.add(new User(-groupId,name,screenName));
				}
			}
		} catch (Exception e) {
			getBot().onException(e);
		}
	}
	
	private User getCacheUser(int id) {
		for (User user : userCache) {
			if (user.getId() == id) {
				return user;
			}
		}
		return null;
	}
	
	public User getUser(int id) {
		if (id == 0) {
			id = -getBot().getBotId();
		}
		
		User user = getCacheUser(id);
		if (user == null) {
			cacheUser(id);
			user = getCacheUser(id);
		}
		return user;
	}
	
	public User getUser(String mentionId) throws Exception {
		int id;
		if (mentionId.startsWith("id")) {
			id = Integer.parseInt(mentionId.substring(2));
		} else if (mentionId.startsWith("club")) {
			id = -Integer.parseInt(mentionId.substring(4));
		} else {
			throw new Exception(String.format("UserCache.getUser(mentionId = %s) failed",
					mentionId));
		}
		return getUser(id);
	}
}
