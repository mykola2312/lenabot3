package lenabot3.bot.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lenabot3.bot.Command;
import lenabot3.bot.Service;
import lenabot3.vk.VkRequest;
import lenabot3.vk.object.VkConversation;
import lenabot3.vk.object.VkMessage;

public class ConversationService extends Service {
	private class ChatUser {
		private final int peer_id;
		private final int user_id;
		
		public ChatUser(int peer_id,int user_id) {
			this.peer_id = peer_id;
			this.user_id = user_id;
		}
		
		public int getPeerId() {
			return peer_id;
		}
		
		public int getUserId() {
			return user_id;
		}
	}
	
	public static final String CONVERSATIONSERVICE_SERVICE = "ConversationService";
	
	private ArrayList<Conversation> conversations = new ArrayList<Conversation>();
	private ArrayList<ChatUser> chatUsers = new ArrayList<ChatUser>();
	
	private long nextUpdate = 0;
	
	public ConversationService() {
		super("ConversationService");
	}
	
	public boolean load() {
		DataBase db = getBot().getDataBaseService();
		//Connection conn = db.getSqlConnection();
		
		if (!db.tableExists("conversations")) {
			db.executeSql(
					"CREATE TABLE conversations ("
				+	"peer_id INTEGER PRIMARY KEY,"
				+	"local_id INTEGER,"
				+	"owner_id INTEGER,"
				+	"type TEXT,"
				+	"title TEXT);"
			);
		}
		
		if (!db.tableExists("chat_members")) {
			db.executeSql(
					"CREATE TABLE chat_members ("
				+	"peer_id INTEGER,"
				+	"user_id INTEGER);"
			);
		}
		
		ResultSet rs = db.executeSqlQuery("SELECT * FROM conversations;");
		if (rs != null) {
			try {
				while (rs.next()) {
					conversations.add(new Conversation(
							rs.getInt("peer_id"),rs.getInt("local_id"),
							rs.getString("type"),rs.getInt("owner_id"),
							rs.getString("title")
					));
				}
			} catch (SQLException e) {
				getBot().onException(e);
			}
		}
		
		setUpdateInterval(5000L);
		return true;
	}
	
	public void save() {
		DataBase db = getBot().getDataBaseService();
		
		PreparedStatement pstmt = db.createPreparedStatement(
				"INSERT INTO conversations (peer_id,local_id,owner_id,type,text)"
			+	" VALUAES (?,?,?,?,?);");
		try {
		int count = 0;
			for (Conversation conv : conversations) {
				pstmt.setInt(1, conv.getPeerId());
				pstmt.setInt(2, conv.getLocalId());
				pstmt.setInt(3, conv.getOwnerId());
				pstmt.setString(4, conv.getType());
				pstmt.setString(5, conv.getTitle());
				
				pstmt.addBatch();
				count++;
				
				if (count % 100 == 0 || count == conversations.size()) {
					pstmt.executeBatch();
				}
			}
		} catch (SQLException e) {
			getBot().onException(e);
		}
	}
	
	public boolean start() {
		return true;
	}
	
	public void stop() {}
	
	public void run() {
		if (nextUpdate > System.currentTimeMillis()) {
			return;
		}
		
		DataBase db = getBot().getDataBaseService();
		try {
			for (ChatUser user : chatUsers) {
				PreparedStatement pstmt = db.createPreparedStatement(
						"SELECT * FROM chat_members WHERE peer_id=? AND user_id=?;"
				);
				int count = 0;
				try {
					pstmt.setInt(1, user.getPeerId());
					pstmt.setInt(2, user.getUserId());
					
					ResultSet rs =  pstmt.executeQuery();
					while (rs.next()) {
						count++;
					}
				} catch (SQLException e) {
					getBot().onException(e);
				}
				System.out.println("Service(ConversationService).run: count " + count);
				
				if (count == 0) {
					pstmt = db.createPreparedStatement(
							"INSERT INTO chat_members (peer_id,user_id) VALUES (?,?);"
					);
					
					try {
						pstmt.setInt(1, user.getPeerId());
						pstmt.setInt(2, user.getUserId());
						
						pstmt.executeUpdate();
					} catch (SQLException e) {
						getBot().onException(e);
					}
					
					getBot().sendMessage(getBot().getMainConversation(),
							String.format("Пользователь %d из беседы %d добавлен в базу",
									user.getUserId(),user.getPeerId()));
									
				}
			}
			
			if (!chatUsers.isEmpty()) {
				chatUsers.clear();
			}
		} catch (Exception e) {
			e.printStackTrace(System.err);
		} finally {
			nextUpdate = System.currentTimeMillis() + getUpdateInterval();
		}
	}

	public boolean processCommand(Command command) {
		//This commands only for root admin
		if (!getBot().isAdminConversation(command.getUser())) {
			return false;
		}
		
		if (command.getName().equals("беседы")) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("[ConversationService] беседы\n\n");
			for (Conversation conv : conversations) {
				pw.printf("%s-(owner_id)=%d\n", conv.asFormat(), conv.getOwnerId());
			}
			
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		} else if (command.getName().equals(("участники_беседы"))) {
			if (command.argC() < 1) {
				getBot().sendMessage(command.getPeerId(), "Укажите ID беседы!");
				return false;
			}
			
			int convId = Integer.parseInt(command.arg(0));
			Conversation conv = getConversation(convId);
			if (conv == null || conv.getType() == "chat") {
				getBot().sendMessage(command.getPeerId(), "Беседа не найдена!");
				return false;
			}
			
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("[ConversationService] участники беседы\n\n");
			//We need to cache all user at once
			List<Integer> users = getChatUsers(conv);
			getBot().getUserCache().cacheUsers(users);
			
			for (Integer userId : users) {
				User user = getBot().getUserCache().getUser(userId);
				if (user == null) {
					continue;
				}
				pw.println(user.asMention());
			}
			
			pw.printf("\nВсего пользователей в беседе: %d\n", users.size());
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		} else if (command.getName().equals("отправить")) {
			if (command.argC() < 2) {
				return false;
			}
			int peerId = Integer.parseInt(command.arg(0));
			if (!isConversationExists(peerId)) {
				getBot().sendMessage(command.getPeerId(), "Беседа не найдена!");
				return false;
			}
			getBot().sendMessage(peerId, command.arg(1));
			return true;
		} else if (command.getName().equals("найти_в_беседах")) {
			if (command.argC() < 1) {
				getBot().sendMessage(command.getPeerId(), 
						"Укажите пользователя упоминанием");
				return false;
			}
			
			User user;
			try {
				user = getBot().getUserCache().getUser(command.arg(0));
			} catch (Exception e) {
				getBot().sendMessage(command.getPeerId(),
						"Во время поиска пользователя проиошла ошибка.");
				return true;
			}
			
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("[ConversationService] поиск пользователя в беседах\n\n");
			
			DataBase db = getBot().getDataBaseService();
			PreparedStatement pstmt = db.createPreparedStatement(
					"SELECT * FROM chat_members WHERE user_id=?;"
			);
			
			try {
				pstmt.setInt(1, user.getId());
				
				ResultSet rs = pstmt.executeQuery();
				while(rs.next()) {
					Conversation conv = getConversation(rs.getInt(1));
					if (conv == null) {
						continue;
					}
					pw.println(conv.asFormat());
				}
			} catch (SQLException e) {
				getBot().onException(e);
				return false;
			}
			
			getBot().sendMessage(command.getPeerId(),sw.toString());
			return true;
		}
		
		return false;
	}
	
	public boolean isConversationExists(int peer_id) {
		for (Conversation conv : conversations) {
			if (conv.getPeerId() == peer_id) {
				return true;
			}
		}
		
		return false;
	}
	
	private void addConversation(Conversation conv) {
		conversations.add(conv);
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"INSERT INTO conversations (peer_id,local_id,owner_id,type,title)"
			+	" VALUES (?,?,?,?,?);");
		try {
			pstmt.setInt(1, conv.getPeerId());
			pstmt.setInt(2, conv.getLocalId());
			pstmt.setInt(3, conv.getOwnerId());
			pstmt.setString(4, conv.getType());
			pstmt.setString(5, conv.getTitle());
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
		}
	}
	
	public Conversation addConversation(int peer_id) {
		JsonElement response = getBot().getBotApi().request(
			new VkRequest("messages.getConversationsById")
				.set("peer_ids", peer_id)
		).getAsJsonObject().get("items");
		
		if(response.getAsJsonArray().size() == 0) {
			Conversation conv = new Conversation(peer_id,peer_id-2000000000,"chat",
					-1,String.format("chat-%d", peer_id));
			addConversation(conv);
			return conv;
		}
		
		try {
			VkConversation vkConv = new VkConversation(response.getAsJsonArray().get(0));
			String title = "";
			int owner_id = vkConv.getOwnerId();
			if (vkConv.getType().equals("chat")) {
				title = vkConv.getTitle();
			} else if (vkConv.getType().equals("user")) {
				JsonElement users = getBot().getBotApi().request(
					new VkRequest("users.get")
						.set("user_ids", vkConv.getPeerId())
				);
				
				JsonObject user = users.getAsJsonArray().get(0).getAsJsonObject();
				title = user.get("first_name").getAsString() + " "
					+	user.get("last_name").getAsString();
				owner_id = user.get("id").getAsInt();
			}
			Conversation conv = new Conversation(vkConv.getPeerId(),vkConv.getLocalId(),
					vkConv.getType(),owner_id,title);
			addConversation(conv);
			return conv;
		} catch (Exception e) {
			getBot().onException(e);
			return null;
		}
	}
	
	public Conversation getConversation(int peer_id) {
		for (Conversation conv : conversations) {
			if (conv.getPeerId() == peer_id) {
				return conv;
			}
		}
		return null;
	}
	
	public void checkChatUser(int peer_id,int user_id) {
		Conversation conv = getConversation(peer_id);
		if (conv != null && conv.getType().equals("chat")) {
			chatUsers.add(new ChatUser(peer_id,user_id));
		}
	}
	
	public boolean isUserConversation(int peer_id) {
		Conversation conv = getConversation(peer_id);
		if (conv != null && conv.getType().equals("user")) {
			return true;
		}
		return false;
	}
	
	public List<Integer> getChatUsers(Conversation conv) {
		LinkedList<Integer> users = new LinkedList<Integer>();
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"SELECT * FROM chat_members WHERE peer_id=?;"
		);
		try {
			pstmt.setInt(1, conv.getPeerId());
			
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				users.add(rs.getInt(2));
			}
		} catch (SQLException e) {
			return null;
		}
		
		return users;
	}
	
	public void onMessage(VkMessage message) {
		if (!isConversationExists(message.getPeerId())) {
			Conversation conv = addConversation(message.getPeerId());
			getBot().sendMessage(getBot().getMainConversation(), 
					"Добавлен " + conv.asFormat());
		}
		checkChatUser(message.getPeerId(),message.getFromId());
	}
}
