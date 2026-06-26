package net.villagerzock.backend.security;

import java.util.LinkedHashMap;
import java.util.Map;

public enum NodePermission {
    PROXY_PAGE(),
    SERVERS_PAGE(),
    TEMPLATES_PAGE(),
    USERS_PAGE(),
    MATCHMAKING_PAGE(),
    MAINTENANCE_PAGE(),
    BANNED_PLAYERS_PAGE(),
    PROXY_STATUS(),
    SERVER_CONSOLE(),
    SERVER_STATUS(),
    TEMPLATES_FILE_READ_DIR(),
    TEMPLATES_FILE_WRITE(),
    TEMPLATES_FILE_DOWNLOAD(),
    TEMPLATES_FILE_CREATE(),
    USERS_ADD(), // Allow this user to add a new User to the Node, cannot give that user the same role or higher roles if user is not node owner
    ROLES_ADD(), // Allows you to create new Roles, cannot put role above you own if the user is not node owner, also cannot give any permission the user doesnt have without being node owner. Also allows user to edit existing roles
    ROLES_MOVE(), // Allows the user to change the order of the roles, has to follow same principle with not being able to move it above his own and not bein able to move his own role or any higher roles
    TEMPLATES_CREATE(),
    // More to come


    ;
    NodePermission(){
    }

    public int getFlag(){
        return 1 << ordinal();
    }

    public int getBit(){
        return getFlag();
    }



    public static final int ALL = 0xFFFFFFFF;

    public static final int DEFAULT_USER = PROXY_PAGE.getFlag() | SERVERS_PAGE.getFlag();

    public static boolean has(int permissions, int permission) {
        return (permissions & permission) == permission;
    }

    public static Map<String, Boolean> options(int permissions) {
        Map<String, Boolean> options = new LinkedHashMap<>();
        for (NodePermission permission : values()){
            options.put(permission.name(), has(permissions, permission.getFlag()));
        }
        return options;
    }

    public static int valueOfName(String name) {
        return valueOf(name).getFlag();
    }

    public static Map<String, Integer> valuesByName() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (NodePermission permission : values()) {
            values.put(permission.name(), permission.getFlag());
        }
        return values;
    }
}
