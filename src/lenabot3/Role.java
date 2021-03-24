package lenabot3;

public class Role {
	private final int roleId;
	private final int peerId;
	private final String name;
	private final int privilegeLevel;
	private final String description;
	
	public Role(int roleId,int peerId, String name,
			int privilegeLevel,String description) {
		this.roleId = roleId;
		this.peerId = peerId;
		this.name = name;
		this.privilegeLevel = privilegeLevel;
		this.description = description;
	}
	
	public int getRoleId() {
		return roleId;
	}
	
	public int getPeerId() {
		return isGlobalRole() ? 0 : peerId;
	}
	
	public String getName() {
		return name;
	}
	
	public int getPrivilegeLevel() {
		return privilegeLevel;
	}
	
	public String getDescription() {
		return description;
	}
	
	public boolean isGlobalRole() {
		return getRoleId() < 0;
	}
}
