package lenabot3;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import lenabot3.bot.Bot;
import lenabot3.bot.Command;
import lenabot3.bot.KeyboardService.Button;
import lenabot3.bot.KeyboardService.Keyboard;
import lenabot3.bot.Service;
import lenabot3.bot.service.Conversation;
import lenabot3.bot.service.DataBase;
import lenabot3.bot.service.User;
import lenabot3.bot.service.UserCache;

public class ActionService extends Service {
	public static final String ACTIONSERVICE_SERVICE = "ActionService";
	
	private class Action {
		private final int actionId;
		private final String name;
		private final int peerId;
		private final String actionText;
		
		public Action(int actionId,String name,
				int peerId,String actionText) {
			this.actionId = actionId;
			this.name = name;
			this.peerId = peerId;
			this.actionText = actionText;
		}
		
		public int getActionId() {
			return actionId;
		}
		
		public String getName() {
			return name;
		}
		
		public int getPeerId() {
			return peerId;
		}
		
		public String getActionText() {
			return actionText;
		}
		
		public boolean isGlobalAction() {
			return getPeerId() == Bot.GLOBAL_PEER_ID;
		}
	}
	
	private ArrayList<Action> actions = new ArrayList<Action>();
	
	public ActionService() {
		super(ACTIONSERVICE_SERVICE);
	}
	
	public boolean load() {
		DataBase db = getBot().getDataBaseService();
		
		if (!db.tableExists("actions")) {
			db.executeSql("CREATE TABLE actions("
				+	"action_id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+	"name TEXT,"
				+	"peer_id INTEGER,"
				+	"action_text TEXT);"
			);
		}
		
		ResultSet rs = db.executeSqlQuery("SELECT * FROM actions;");
		try {
			while (rs.next()) {
				actions.add(new Action(
						rs.getInt("action_id"),
						rs.getString("name"),
						rs.getInt("peer_id"),
						rs.getString("action_text")
				));
			}
		} catch (SQLException e) {
			getBot().onException(e);
		}
		
		registerGlobalActions();
		return true;
	}
	
	public void save() {}
	public void run() {}

	public boolean start() {
		return true;
	}
	
	public void stop() {}
	
	public boolean processCommand(Command command) {
		if (getBot().isAdminConversation(command.getPeerId())) {
			if (command.getName().equals("применить_действие")) {
				if (command.argC() < 3) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: беседа \"действие\" @пользователь");
					return true;
				}
				
				Conversation conv;
				User commandUser,user;
				try {
					conv = getBot().getConversationService()
							.getConversation(Integer.parseInt(command.arg(0)));
					commandUser = getBot().getUserCache().getUser(command.getUser());
					user = getBot().getUserCache().getUser(command.arg(2));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(), 
							"Произошла ошибка при поиске пользователя");
					return true;
				}
				
				if (user == null || conv == null) {
					getBot().sendMessage(command.getPeerId(),
							"Пользователь не найден! (Используйте упоминания, а не имя!).");
					return true;
				}
				
				getBot().sendMessage(conv.getPeerId(), String.format("%s %s %s",
						commandUser.asMention(),command.arg(1),user.asMention()));
			}
		}
		
		AdminService admin = (AdminService)getBot()
				.getService(AdminService.ADMINSERVICE_SERVICE);
		if (admin.hasPrivilege(command.getPeerId(), command.getUser(), 
				AdminService.PrivilegeLevel.HighAdherent)) {
			if (command.getName().equals("создать_действие")) {
				if (command.argC() < 2) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: имя_действия \"описание действия\"");
					return true;
				}
				
				if (getAction(command.getPeerId(),command.arg(0)) != null) {
					getBot().sendMessage(command.getPeerId(),
							"Такое действие уже существует.");
					return true;
				}
				Action action = createAction(command.arg(0),command.getPeerId(),
						command.arg(1));
				if (action == null) {
					getBot().sendMessage(command.getPeerId(),
							"Невозможно создать действие (неизвестная ошибка).");
					return true;
				}
				
				getBot().sendMessage(command.getPeerId(),String.format(
						"действие[%d]-\"%s\" успешно создано.",
						action.getActionId(),action.getName()));
				return true;
			} else if(command.getName().equals("удалить_действие")) {
				if (command.argC() < 1) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: ID_действия");
					return true;
				}
				
				Action action;
				try {
					action = getAction(Integer.parseInt(command.arg(0)));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(),
							"Невозможно найти действие (неверный ID).");
					return true;
				}
				
				if (action == null) {
					getBot().sendMessage(command.getPeerId(),
							"Действие ненайдено.");
					return true;
				}
				
				if (action.isGlobalAction() || action.getPeerId() != command.getPeerId()) {
					getBot().sendMessage(command.getPeerId(),
							"Доступ запрещен.");
					return true;
				}
				
				boolean ok = deleteAction(action.getActionId());
				getBot().sendMessage(command.getPeerId(),
						ok ? "Действие было удалено." : "Действие не было удалено.");
				return true;
			} else if (command.getName().equals("действие")) {
				if (command.argC() < 2) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: \"действие\" @пользователь");
					return true;
				}
				
				User commandUser,user;
				try {
					commandUser = getBot().getUserCache().getUser(command.getUser());
					user = getBot().getUserCache().getUser(command.arg(1));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(), 
							"Произошла ошибка при поиске пользователя");
					return true;
				}
				
				if (user == null) {
					getBot().sendMessage(command.getPeerId(),
							"Пользователь не найден! (Используйте упоминания, а не имя!).");
					return true;
				}
				
				getBot().sendMessage(command.getPeerId(), String.format("%s %s %s",
						commandUser.asMention(),command.arg(0),user.asMention()));
				return true;
			} else if (command.getName().equals("действие_на_роль")) {
				if (command.argC() < 2) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: \"действие\" <роль>");
					return true;
				}
				
				Action action = getAction(command.getPeerId(),command.arg(0));
				if (action == null) {
					getBot().sendMessage(command.getPeerId(), "Действие не найдено!");
					return true;
				}
				
				AdminService as = (AdminService)getBot()
						.getService(AdminService.ADMINSERVICE_SERVICE);
				int roleId = as.findRoleByName(command.getPeerId(), command.arg(1), false);
				if (roleId == AdminService.INVALID_ROLE_ID) {
					getBot().sendMessage(command.getPeerId(), "Роль не найдена!");
					return true;
				}
				
				User commandUser = getBot().getUserCache().getUser(command.getUser());
				List<Integer> roleUsers = as.getRoleUsers(roleId);
				getBot().getUserCache().cacheUsers(roleUsers);
				
				StringBuilder sb = new StringBuilder();
				sb.append(String.format(
						"%s %s ",
						commandUser.asMention(),
						action.getActionText()
				));
				
				Iterator<Integer> it = roleUsers.iterator();
				while(it.hasNext()) {
					User user = getBot().getUserCache().getUser(it.next());
					sb.append(user.asMention());
					if (it.hasNext()) {
						sb.append(", ");
					}
				}
				
				getBot().sendMessage(command.getPeerId(), sb.toString());
				return true;
			}
		}
		
		if (admin.hasPrivilege(command.getPeerId(), command.getUser(), 
				AdminService.PrivilegeLevel.User)) {
			if (command.getName().equals("действия") && command.argC() == 2) {
				LinkedList<Action> actions = new LinkedList<Action>();
				String[] textCommands = command.arg(0).split(",");
				if (textCommands.length > 9) {
					return false;
				}
				
				boolean shared = true;
				for (String actionName : textCommands) {
					if (actionName.equals("!личное")) {
						shared = false;
						continue;
					}
					
					Action action = getAction(command.getPeerId(), actionName);
					if (action != null) {
						actions.add(action);
					}
				}
				
				User user;
				try {
					user = getBot().getUserCache().getUser(command.arg(1));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(), "Пользователь не найден"); 
					return false;
				}
				
				Keyboard kb = getBot().getKeyboardService().createKeyboard(
						false, false, shared);
				List<Button> row;
				for (Action action : actions) {
					row = kb.createRow();
					row.add(kb.createButton(
							String.format("%s %s", action.getName(), user.getName()), 
							Button.COLOR_WHITE, action.getName(), user.asArgument()));
				}
				
				row = kb.createRow();
				row.add(kb.createButton("Закрыть", Button.COLOR_RED, "ks_remove"));
				
				getBot().getKeyboardService().sendKeyboard(command.getPeerId(),
						command.getUser(), kb);
				return true;
			}
		} else if (command.getName().equals("действия")) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("[ActionService] действия\n");
			for (Action action : actions) {
				if (action.isGlobalAction()) {
					pw.printf("действие[%d]-имя \"%s\"-текст \"%s\"\n",
							action.getActionId(),action.getName(),
							action.getActionText());
				}
			}
			
			pw.printf("\n[ActionService] действия в этой беседе (chat-%d)\n\n",
					command.getPeerId());
			for (Action action : actions) {
				if (action.getPeerId() == command.getPeerId()) {
					pw.printf("действие[%d]-имя \"%s\"-текст \"%s\"\n",
							action.getActionId(),action.getName(),
							action.getActionText());
				}
			}
			
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		}
		
		LinkedList<Action> actions = new LinkedList<Action>();
		for (String actionName : command.getName().split(",")) {
			Action action = getAction(command.getPeerId(), actionName);
			if (action != null) {
				actions.add(action);
			}
		}
		
		if (!actions.isEmpty()) {
			if (command.argC() < 1) {
				getBot().sendMessage(command.getPeerId(),
						"Укажите пользователя, к которому хотите применить действие"
					+	" (только через упоминания! @ - упоминание)");
			}
			
			int count = 1;
			if (command.argC() > 1) {
				try {
					count = Integer.parseInt(command.arg(1));
				} catch (NumberFormatException e) {
					count = 1;
				}
				count = count > 10 ? 10 : count;
			}
			
			UserCache userCache = getBot().getUserCache();
			User commandUser,user;
			try {
				commandUser = userCache.getUser(command.getUser());
				user = userCache.getUser(command.arg(0));
			} catch (Exception e) {
				getBot().sendMessage(command.getPeerId(), 
						"Произошла ошибка при поиске пользователя");
				return true;
			}
			
			if (user == null) {
				getBot().sendMessage(command.getPeerId(),
						"Пользователь не найден! (Используйте упоминания, а не имя!).");
				return true;
			}
			
			if (command.getUser() == 194017277)
			{
				getBot().sendMessage(command.getPeerId(), "Вы не можете заниматься сексом.");
				return true;
			}
			
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			for (int i = 0; i < count; i++) {
				for (Action action : actions) {
					pw.printf("%s %s %s\n", 
							commandUser.asMention(), 
							action.getActionText(), 
							user.asMention()
					);
				}
			}
			
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		}
		return false;
	}
	
	public void registerGlobalAction(String name,String actionText) {
		int actionId = actions.isEmpty() ? 0 : actions.size();
		actions.add(new Action(actionId,name,Bot.GLOBAL_PEER_ID,actionText));
	}
	
	protected void registerGlobalActions() {
		registerGlobalAction("секс","занялся сексом с");
		registerGlobalAction("кусь","прикусил мочку уха");
		registerGlobalAction("шлёп","шлёпнул по упругой попке");
		registerGlobalAction("чиии","пристально смотрит на");
		registerGlobalAction("обнять","обнял");
		registerGlobalAction("нежно-обнять","нежно обнял");
		registerGlobalAction("связать","связал");
		registerGlobalAction("нож","пригрозил ножом");
		registerGlobalAction("поцеловать","поцеловал");
		registerGlobalAction("смущение","мило краснеет в присутствии");
		registerGlobalAction("гитара","играет на гитаре для");
		registerGlobalAction("танцы","танцует с");
	}
	
	public Action createAction(String name,int peerId,String actionText) {
		int actionId = actions.isEmpty() ? 0 : actions.size();
		Action action = new Action(actionId,name,peerId,actionText);
		actions.add(action);
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"INSERT INTO actions (action_id,name,peer_id,action_text) VALUES (?,?,?,?);"
		);
		try {
			pstmt.setInt(1, action.getActionId());
			pstmt.setString(2, action.getName());
			pstmt.setInt(3, action.getPeerId());
			pstmt.setString(4, action.getActionText());
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
			return null;
		}
		
		return action;
	}
	
	public Action getAction(int actionId) {
		for (Action action : actions) {
			if (action.getActionId() == actionId) {
				return action;
			}
		}
		return null;
	}
	
	public Action getAction(int peerId,String name) {
		for (Action action : actions) {
			if (action.getName().equalsIgnoreCase(name)
					&& (action.getPeerId() == peerId || action.isGlobalAction())) {
				return action;
			}
		}
		return null;
	}
	
	public boolean deleteAction(int actionId) {
		Action action = getAction(actionId);
		if (action == null || action.isGlobalAction()) {
			return false;
		}
		
		actions.remove(action);
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"DELETE FROM actions WHERE action_id=?;"
		);
		try {
			pstmt.setInt(1, action.getActionId());
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
			return false;
		}
		
		return true;
	}
}
