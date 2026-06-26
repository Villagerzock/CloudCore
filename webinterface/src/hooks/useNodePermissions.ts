import {useEffect, useMemo, useState} from "react";
import {getMe, getRoles, type Role, type User} from "../lib/api.ts";

export type NodePermissionName =
    | "PROXY_PAGE"
    | "SERVERS_PAGE"
    | "TEMPLATES_PAGE"
    | "USERS_PAGE"
    | "MATCHMAKING_PAGE"
    | "MAINTENANCE_PAGE"
    | "BANNED_PLAYERS_PAGE"
    | "PROXY_STATUS"
    | "SERVER_CONSOLE"
    | "SERVER_STATUS"
    | "TEMPLATES_FILE_READ_DIR"
    | "TEMPLATES_FILE_WRITE"
    | "TEMPLATES_FILE_DOWNLOAD"
    | "TEMPLATES_FILE_CREATE"
    | "TEMPLATES_CREATE"
    | "USERS_ADD"
    | "ROLES_ADD"
    | "ROLES_MOVE";

export function useNodePermissions() {
    const [currentUser, setCurrentUser] = useState<User | null>(null);
    const [roles, setRoles] = useState<Role[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function load() {
            try {
                setLoading(true);
                const [loadedUser, loadedRoles] = await Promise.all([getMe(), getRoles()]);
                if (cancelled) return;
                setCurrentUser(loadedUser);
                setRoles(loadedRoles);
                setError(null);
            } catch (reason) {
                if (!cancelled) {
                    setError(reason instanceof Error ? reason.message : "Failed to load permissions");
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        load();
        return () => {
            cancelled = true;
        };
    }, []);

    const permissionOptions = useMemo(() => {
        if (currentUser?.hasAsterix) return null;
        return roles.find(role => role.id === currentUser?.roleId)?.permissionOptions ?? {};
    }, [currentUser, roles]);

    function has(permission: NodePermissionName): boolean {
        if (currentUser?.hasAsterix) return true;
        return permissionOptions?.[permission] === true;
    }

    return {
        currentUser,
        roles,
        loading,
        error,
        has
    };
}
