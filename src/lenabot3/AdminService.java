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
import java.util.Map;
import java.util.TreeMap;

import lenabot3.bot.Command;
import lenabot3.bot.Service;
import lenabot3.bot.service.Conversation;
import lenabot3.bot.service.ConversationService;
import lenabot3.bot.service.DataBase;
import lenabot3.bot.service.User;
import lenabot3.vk.VkRequest;

public class AdminService extends Service {
	public static final String ADMINSERVICE_SERVICE = "AdminService";
	
	public static final int INVALID_PRIVILEGE = Integer.MAX_VALUE;
	public static final int INVALID_ROLE_ID = Integer.MAX_VALUE;
	
	private TreeMap<Integer,String> privileges = new TreeMap<Integer,String>();
	private ArrayList<Role> roles = new ArrayList<Role>();
	
	private class UserRole {
		private final int userId;
		private final Role role;
		
		public UserRole(int userId,Role role) {
			this.userId = userId;
			this.role = role;
		}
		
		public int getUserId() {
			return userId;
		}
		
		public Role getRole() {
			return role;
		}
	};
	
	private ArrayList<UserRole> userRoles = new ArrayList<UserRole>();
	
	public class PrivilegeLevel {
		public static final int StHelen = -1;
		public static final int Leader = 0;
		public static final int HighAdherent = 1;
		public static final int Adherent = 2;
		public static final int User = 3;
		public static final int Incorrent = 4;
		public static final int Banned = 5;
	}
	
	public AdminService() {
		super(ADMINSERVICE_SERVICE);
	}
	
	public boolean load() {
		registerPrivileges();
		
		DataBase db = getBot().getDataBaseService();
		
		if (!db.tableExists("global_roles")) {
			db.executeSql("CREATE TABLE global_roles ("
				+	"role_id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+	"name TEXT,"
				+	"level INTEGER,"
				+	"description TEXT);"
			);
		}
		
		if (!db.tableExists("roles")) {
			db.executeSql(
					"CREATE TABLE roles ("
				+	"role_id INTEGER PRIMARY KEY AUTOINCREMENT,"
				+	"peer_id INTEGER,"
				+	"name TEXT,"
				+	"level INTEGER,"
				+	"description TEXT);"
			);
		}
		
		if (!db.tableExists("user_roles")) {
			db.executeSql(
					"CREATE TABLE user_roles ("
				+	"user_id INTEGER,"
				+	"peer_id INTEGER,"
				+	"role_id INTEGER);"
			);
		}
		
		ResultSet rs = db.executeSqlQuery("SELECT * FROM global_roles;");
		if (rs != null) {
			try {
				while (rs.next()) {
					roles.add(new Role(
							-1 - rs.getInt("role_id"),
							-1,
							rs.getString("name"),
							rs.getInt("level"),
							rs.getString("description")
					));
				}
			} catch (SQLException e) {
				getBot().onException(e);
			}
		}
		
		rs = db.executeSqlQuery("SELECT * FROM roles;");
		if (rs != null) {
			try {
				while (rs.next()) {
					roles.add(new Role(
							rs.getInt("role_id"),
							rs.getInt("peer_id"),
							rs.getString("name"),
							rs.getInt("level"),
							rs.getString("description")
					));
				}
			} catch (SQLException e) {
				getBot().onException(e);
			}
		}
		
		rs = db.executeSqlQuery("SELECT * FROM user_roles;");
		if (rs != null) {
			try {
				while (rs.next()) {
					int userId = rs.getInt("user_id");
					int peerId = rs.getInt("peer_id");
					int roleId = rs.getInt("role_id");
					
					Role role = getRole(roleId);
					if (role == null) {
						continue;
					}
					
					//Global roles are shared between all conversations
					if (!role.isGlobalRole()) {
						//UserRole and Role must be in one conversation
						if (role.getPeerId() != peerId) {
							continue;
						}
					}
					
					userRoles.add(new UserRole(userId,role));
				}
			} catch (SQLException e) {
				getBot().onException(e);
			}
		}
		return true;
	}
	
	public void save() {}
	
	public boolean start() {
		return true;
	}
	
	public void stop() {}
	public void run() {}
	
	public boolean processCommand(Command command) {
		if (hasPrivilege(command.getPeerId(),command.getUser(),PrivilegeLevel.Leader)) {
			if (command.getName().equals("создать_роль")) {
				if (command.argC() < 3) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: \"имя роли\" \"привилегия\" \"описание роли\"");
					return true;
				}
				
				String name = command.arg(0);
				int privilege = getPrivilege(command.arg(1));
				String description = command.arg(2);
				
				if (!isValidPrivilege(privilege)) {
					getBot().sendMessage(command.getPeerId(),
							"Неверная привилегия!");
					return true;
				}
				
				Role newRole = createRole(command.getPeerId(),name,privilege,description);
				getBot().sendMessage(command.getPeerId(),String.format(
						"Создана новая роль \"%s\" с ID %d",
						newRole.getName(),newRole.getRoleId()));
				return true;
			} else if (command.getName().equals("удалить_роль")) {
				if (command.argC() < 1) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: \"ID роли\"");
					return true;
				}
				Role role = getRole(Integer.parseInt(command.arg(0)));
				if (role == null) {
					getBot().sendMessage(command.getPeerId(),"Роль ненайдена.");
					return true;
				}
				
				if (role.getPeerId() != command.getPeerId()
						|| (!getBot().isAdmin(command.getUser()) && role.isGlobalRole())) {
					getBot().sendMessage(command.getPeerId(),"Доступ запрещен.");
					return true;
				}
				
				deleteRole(role.getRoleId());
				getBot().sendMessage(command.getPeerId(), String.format(
						"Роль роль[%d]-\"%s\" была удалена.",role.getRoleId(),role.getName()));
				return true;
			} else if (command.getName().equals("дать_роль")) {
				if (command.argC() < 2) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: @пользователь \"имя роли\"");
					return true;
				}
				
				User user;
				try {
					user = getBot().getUserCache().getUser(command.arg(0));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(),
							"Во время поиска пользователя проиошла ошибка.");
					return true;
				}
				
				if (user == null) {
					getBot().sendMessage(command.getPeerId(),
							"Пользователь ненайден.");
					return true;
				}
				
				int roleId = findRoleByName(command.getPeerId(),command.arg(1),
						getBot().isAdmin(command.getUser()));
				if (roleId == INVALID_ROLE_ID) {
					getBot().sendMessage(command.getPeerId(),
							"Роль ненайдена.");
					return true;
				}
				
				setUserRoleId(command.getPeerId(),user.getId(),roleId);
				
				getBot().sendMessage(command.getPeerId(),String.format(
						"У пользователя %s теперь роль \"%s\"",
						user.asMention(),getRole(roleId).getName()));
				return true;
			} else if (command.getName().equals("снять_роль")) {
				if (command.argC() < 1) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: @пользователь");
					return true;
				}
				
				User user;
				try {
					user = getBot().getUserCache().getUser(command.arg(0));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(),
							"Во время поиска пользователя проиошла ошибка.");
					return true;
				}
				
				if (user == null) {
					getBot().sendMessage(command.getPeerId(),
							"Пользователь ненайден.");
					return true;
				}
				
				deleteUserRole(command.getPeerId(),user.getId());
				getBot().sendMessage(command.getPeerId(), String.format(
						"С пользователя %s была снята роль.",user.asMention()));
				return true;
			}
		}
		
		if (hasPrivilege(command.getPeerId(),command.getUser(),
				PrivilegeLevel.HighAdherent)) {
			if (command.getName().equals("кик")) {
				if (command.argC() < 1) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: @пользователь");
					return true;
				}
				
				User user;
				try {
					user = getBot().getUserCache().getUser(command.arg(0));
				} catch (Exception e) {
					getBot().sendMessage(command.getPeerId(),
							"Во время поиска пользователя проиошла ошибка.");
					return true;
				}
				
				if (user == null) {
					getBot().sendMessage(command.getPeerId(),
							"Пользователь ненайден.");
					return true;
				}
				
				if (getUserPrivilege(command.getPeerId(),user.getId())
						<= getUserPrivilege(command.getPeerId(),command.getUser())) {
					getBot().sendMessage(command.getPeerId(),
							"Недостаточный уровень доступа (у пользователя выше доступ).");
					return true;
				}
				kick(command.getPeerId(),user.getId());
				
				getBot().sendMessage(command.getPeerId(),
						String.format("Пользователь %s был удалён из беседы.", user.asMention()));
				return true;
			} else if (command.getName().equals("созвать_роль")) {
				if (command.argC() < 1) {
					getBot().sendMessage(command.getPeerId(),
							"Аргументы: \"имя роли\"");
					return true;
				}
				
				int roleId = findRoleByName(command.getPeerId(),command.arg(0),
						getBot().isAdmin(command.getUser()));
				if (roleId == INVALID_ROLE_ID) {
					getBot().sendMessage(command.getPeerId(),
							"Роль ненайдена.");
					return true;
				}
				
				StringBuilder sb = new StringBuilder();
				
				List<Integer> users = getRoleUsers(roleId);
				getBot().getUserCache().cacheUsers(users);
				
				for (Integer userId : users) {
					User user = getBot().getUserCache().getUser(userId);
					if (user == null) {
						continue;
					}
					sb.append(user.asMention("."));
				}
				
				getBot().sendMessage(command.getPeerId(), sb.toString());
				return true;
			} else if (command.getName().equals("созвать_всех")) {
				Conversation conv = getBot().getConversationService()
						.getConversation(command.getPeerId());
				List<Integer> users = getBot().getConversationService()
						.getChatUsers(conv);
				
				StringBuilder sb = new StringBuilder();
				getBot().getUserCache().cacheUsers(users);
				for (Integer userId : users) {
					User user = getBot().getUserCache().getUser(userId);
					if (user == null) {
						continue;
					}
					sb.append(user.asMention("."));
				}
				
				getBot().sendMessage(command.getPeerId(), sb.toString());
				return true;
			}
		}
		
		if (command.getName().equals("статус")) {
			User user;
			try {
				if (command.argC() >= 1) {
					user = getBot().getUserCache().getUser(command.arg(0));
				} else {
					user = getBot().getUserCache().getUser(command.getUser());
				}
			} catch (Exception e) {
				getBot().sendMessage(command.getPeerId(), "Пользователь не найден.");
				return true;
			}
			
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("Пользователь: " + user.asMention());
			pw.println("Имя: " + user.getName());
			pw.println("ID: " + user.getId());
			pw.println("Уровень доступа: " + getPrivilegeName(getUserPrivilege(
					command.getPeerId(),user.getId())));
			Role role = getUserRole(command.getPeerId(),user.getId());
			if (role != null) {
				pw.printf("Роль[%d]: %s\n",role.getRoleId(),role.getName());
			}
			
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		} else if (command.getName().equals("роли")) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			if (hasPrivilege(command.getPeerId(),command.getUser(),
					PrivilegeLevel.HighAdherent)) {
				pw.println("[AdminService] роли\n");
				for (Role role : roles) {
					if (role.getPeerId() == command.getPeerId() && !role.isGlobalRole()) {
						pw.printf("роль[%d]-имя \"%s\"-привилегия[%d]-описание \"%s\"\n",
								role.getRoleId(),role.getName(),
								role.getPrivilegeLevel(),role.getDescription());
					}
				}
			}
			
			if (getBot().isAdmin(command.getUser())) {
				pw.println("\n[AdminService] глобальные роли\n");
				for (Role role : roles) {
					if (role.isGlobalRole()) {
						pw.printf("роль[%d]-имя \"%s\"-привилегия[%d]-описание \"%s\"\n",
								role.getRoleId(),role.getName(),
								role.getPrivilegeLevel(),role.getDescription());
					}
				}
			}
			
			LinkedList<Integer> localUsers = new LinkedList<Integer>();
			LinkedList<Role> localRoles = new LinkedList<Role>();
			
			for (UserRole userRole : userRoles) {
				Role role = userRole.getRole();
				if (role.getPeerId() == command.getPeerId()) {
					localUsers.add(userRole.getUserId());
					localRoles.add(role);
				}
			}
			//Cache user list
			getBot().getUserCache().cacheUsers(localUsers);
			
			pw.printf("\n[AdminService] роли пользователей в этой беседе (chat-%d)\n\n",
					command.getPeerId());
			
			Iterator<Integer> it = localUsers.iterator();
			Iterator<Role> ij = localRoles.iterator();
			while (it.hasNext()) {
				User user = getBot().getUserCache().getUser(it.next());
				Role role = ij.next();
				pw.printf("%s ~ роль[%d]-%s\n", user.asMention(),
						role.getRoleId(),role.getName());
			}
			
			getBot().sendMessage(command.getPeerId(), sw.toString());
			return true;
		}
		return false;
	}
	
	public void kick(int peerId,int memberId) {
		ConversationService cs = getBot().getConversationService();
		Conversation conv = cs.getConversation(peerId);
		if (conv == null) {
			return;
		}
		
		getBot().request(new VkRequest("messages.removeChatUser")
				.set("chat_id",conv.getLocalId())
				.set("member_id",memberId)
		);
	}
	
	protected void registerPrivileges() {
		registerPrivilege(PrivilegeLevel.StHelen,"Святая Елена");
		registerPrivilege(PrivilegeLevel.Leader,"Предводитель");
		registerPrivilege(PrivilegeLevel.HighAdherent,"Высший адепт");
		registerPrivilege(PrivilegeLevel.Adherent,"Адепт");
		registerPrivilege(PrivilegeLevel.User,"Пользователь");
		registerPrivilege(PrivilegeLevel.Incorrent,"Неверный");
		registerPrivilege(PrivilegeLevel.Banned,"Заблокированный");
	}
	
	protected void registerPrivilege(int privilegeLevel,String name) {
		privileges.put(privilegeLevel, name);
	}
	
	public boolean isValidPrivilege(int privilege) {
		return (privilege >= -1);
	}
	
	public int getPrivilege(String name) {
		for (Map.Entry<Integer, String> priv : privileges.entrySet()) {
			if (priv.getValue().equalsIgnoreCase(name)) {
				return priv.getKey();
			}
		}
		return INVALID_PRIVILEGE;
	}
	
	public String getPrivilegeName(int privilege) {
		return privileges.get(privilege);
	}
	
	public Role createRole(int peerId,String name,int level,String description) {
		int roleId = roles.isEmpty() ? 0 : roles.get(roles.size() - 1).getRoleId() + 1;
		Role role = new Role(roleId,peerId,name,level,description);
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"INSERT INTO roles (role_id,peer_id,name,level,description)"
			+	" VALUES (?,?,?,?,?);"
		);
		try {
			pstmt.setInt(1, role.getRoleId());
			pstmt.setInt(2, role.getPeerId());
			pstmt.setString(3, role.getName());
			pstmt.setInt(4, role.getPrivilegeLevel());
			pstmt.setString(5, role.getDescription());
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
		}
		
		roles.add(role);
		return role;
	}
	
	public Role getRole(int roleId) {
		for (Role role : roles) {
			if (role.getRoleId() == roleId) {
				return role;
			}
		}
		return null;
	}
	
	public int findRoleByName(int peerId,String name,boolean canBeGlobal) {
		for (Role role : roles) {
			if (role.getName().equalsIgnoreCase(name)
					&& (role.getPeerId() == peerId
						|| (role.isGlobalRole() && canBeGlobal))) {
				return role.getRoleId();
			}
		}
		return INVALID_ROLE_ID;
	}
	
	public boolean isValidRole(int roleId) {
		if (roleId == INVALID_ROLE_ID) {
			return false;
		}
		return getRole(roleId) != null;
	}
	
	public void deleteRole(int roleId) {
		Role role = getRole(roleId);
		if (role == null) {
			return;
		}
		roles.remove(role);
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"DELETE FROM roles WHERE role_id=?;"
		);
		
		try {
			pstmt.setInt(1, roleId);
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
		}
	}
	
	private int getUserGlobalRoleId(int userId) {
		for (UserRole userRole : userRoles) {
			if (userRole.getUserId() == userId
					&& userRole.getRole().isGlobalRole()) {
				return userRole.getRole().getRoleId();
			}
		}
		return INVALID_ROLE_ID;
	}
	
	public int getUserRoleId(int peerId,int userId) {
		int globalRoleId = getUserGlobalRoleId(userId);
		if (globalRoleId != INVALID_ROLE_ID) {
			return globalRoleId;
		}
		
		for (UserRole userRole : userRoles) {
			Role role = userRole.getRole();
			if (userRole.getUserId() == userId 
					&& role.getPeerId() == peerId) {
				return role.getRoleId();
			}
		}
		return INVALID_ROLE_ID;
	}
	
	public Role getUserRole(int peerId,int userId) {
		int roleId = getUserRoleId(peerId,userId);
		if (roleId != INVALID_ROLE_ID) {
			return getRole(roleId);
		}
		return null;
	}
	
	public void deleteUserRole(int peerId,int userId) {
		Role role = getUserRole(peerId,userId);
		if (role == null) {
			return;
		}
		
		Iterator<UserRole> iter = userRoles.iterator();
		while (iter.hasNext()) {
			UserRole userRole = iter.next();
			if (userRole.getUserId() == userId 
					&& userRole.getRole() == role) {
				iter.remove();
			}
		}
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"DELETE FROM user_roles WHERE user_id=? AND role_id=?");
		try {
			pstmt.setInt(1, userId);
			pstmt.setInt(2, role.getRoleId());
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
		}
	}
	
	public Role setUserRoleId(int peerId,int userId,int roleId) {
		deleteUserRole(peerId,userId);
		
		Role newRole = getRole(roleId);
		if (newRole == null) {
			return null;
		}
		
		//userRoles.put(newRole, userId);
		userRoles.add(new UserRole(userId,newRole));
		
		DataBase db = getBot().getDataBaseService();
		PreparedStatement pstmt = db.createPreparedStatement(
				"INSERT INTO user_roles (peer_id,user_id,role_id) VALUES (?,?,?);"
		);
		try {
			pstmt.setInt(1, peerId);
			pstmt.setInt(2, userId);
			pstmt.setInt(3, newRole.getRoleId());
			
			pstmt.executeUpdate();
		} catch (SQLException e) {
			getBot().onException(e);
		}
		
		return newRole;
	}
	
	public int getUserPrivilege(int peerId,int userId) {
		if (getBot().isAdmin(userId)) {
			return PrivilegeLevel.Leader;
		}
		
		Role role = getUserRole(peerId,userId);
		if (role == null) {
			return PrivilegeLevel.User;
		}
		return role.getPrivilegeLevel();
	}
	
	public boolean hasPrivilege(int peerId,int userId,int privilegeLevel) {
		return (getUserPrivilege(peerId,userId) <= privilegeLevel)
				|| getBot().isAdmin(userId);
	}
	
	public List<Integer> getRoleUsers(int roleId) {
		LinkedList<Integer> users = new LinkedList<Integer>();
		for(UserRole userRole : userRoles) {
			if (userRole.getRole().getRoleId() == roleId) {
				users.add(userRole.getUserId());
			}
		}
		return users;
	}
}
