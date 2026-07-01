import styles from "./PermissionGroups.module.css";
import {useI18n} from "../lib/i18n.ts";

const permissionCategories: Record<string, string[]> = {
    "category.permission.pages": [
        "PROXY_PAGE",
        "SERVERS_PAGE",
        "TEMPLATES_PAGE",
        "USERS_PAGE",
        "MATCHMAKING_PAGE",
        "MAINTENANCE_PAGE",
        "BANNED_PLAYERS_PAGE"
    ],
    "category.permission.status": [
        "PROXY_STATUS",
        "SERVER_STATUS"
    ],
    "category.permission.console": [
        "SERVER_CONSOLE"
    ],
    "category.permission.templates": [
        "TEMPLATES_CREATE"
    ],
    "category.permission.template_files": [
        "TEMPLATES_FILE_READ_DIR",
        "TEMPLATES_FILE_WRITE",
        "TEMPLATES_FILE_DOWNLOAD",
        "TEMPLATES_FILE_CREATE"
    ],
    "category.permission.users_roles": [
        "USERS_ADD",
        "ROLES_ADD",
        "ROLES_MOVE"
    ],
    "category.permission.bans": [
        "BANNED_PLAYERS_ADD",
        "BANNED_PLAYERS_EDIT"
    ]
};

type PermissionGroupsProps = {
    permissions: Record<string, boolean>;
    onChange: (permissions: Record<string, boolean>) => void;
    idPrefix: string;
}

function labelFor(permission: string, translate: (key: string) => string): string {
    return translate(`role.permission.${permission.toLowerCase()}`);
}

function groupedPermissions(permissions: Record<string, boolean>): Array<[string, string[]]> {
    const available = new Set(Object.keys(permissions));
    const groups: Array<[string, string[]]> = Object.entries(permissionCategories)
        .map(([category, permissionNames]) => [
            category,
            permissionNames.filter(permission => available.delete(permission))
        ] as [string, string[]])
        .filter(([, permissionNames]) => permissionNames.length > 0);

    if (available.size > 0) {
        groups.push(["category.permission.unknown", [...available].sort()]);
    }

    return groups;
}

function PermissionGroups({permissions, onChange, idPrefix}: PermissionGroupsProps) {
    const {t} = useI18n();

    return (
        <div className={styles.groups}>
            {groupedPermissions(permissions).map(([category, permissionNames]) => (
                <fieldset className={styles.group} key={category}>
                    <legend>{t(category)}</legend>
                    <div className={styles.permissionList}>
                        {permissionNames.map(permission => (
                            <label className={styles.permissionRow} key={permission}>
                                <input
                                    id={`${idPrefix}-${permission}`}
                                    type="checkbox"
                                    checked={permissions[permission]}
                                    onChange={event => onChange({
                                        ...permissions,
                                        [permission]: event.target.checked
                                    })}
                                />
                                <span>{labelFor(permission, t)}</span>
                            </label>
                        ))}
                    </div>
                </fieldset>
            ))}
        </div>
    );
}

export default PermissionGroups;
