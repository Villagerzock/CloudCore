import styles from "./UserListRoute.module.css"
import UserCard from "../components/UserCard.tsx";
import {useEffect, useState} from "react";
import type {FormEvent} from "react";
import {
    createRole,
    createUser,
    getMe,
    getRoles,
    getUsers,
    moveRole,
    updateRole,
    updateUserRole,
    type Role,
    type User
} from "../lib/api.ts";
import {closestCenter, DndContext, type DragEndEvent, PointerSensor, useSensor, useSensors} from "@dnd-kit/core";
import {arrayMove, SortableContext, useSortable, verticalListSortingStrategy} from "@dnd-kit/sortable";
import {restrictToParentElement, restrictToVerticalAxis} from "@dnd-kit/modifiers";
import {CSS} from "@dnd-kit/utilities";
import RoleCard from "../components/RoleCard.tsx";
import type {CSSProperties, ReactNode} from "react";
import Dropdown from "../components/Dropdown.tsx";
import {FaPlus, FaTimes} from "react-icons/fa";
import {useToast} from "../components/ToastProvider.tsx";
import Button from "../components/Button.tsx";
import {useI18n} from "../lib/i18n.ts";
import PermissionGroups from "../components/PermissionGroups.tsx";


type SortableRoleProps = {
    role: Role;
    children: ReactNode;
}

const roleWrapperStyle: CSSProperties = {
    maxWidth: "50em",
    width: "100%"
};

function emptyPermissions(roles: Role[]): Record<string, boolean> {
    return Object.fromEntries(
        Object.keys(roles[0]?.permissionOptions ?? {})
            .map(permission => [permission, false])
    );
}

function permissionMask(permissions: Record<string, boolean>, permissionValues: Record<string, number>): number {
    return Object.entries(permissions).reduce((result, [permission, enabled]) => {
        return enabled ? result | (permissionValues[permission] ?? 0) : result;
    }, 0);
}

function SortableRole({role, children}: SortableRoleProps) {
    const {attributes, listeners, setNodeRef, transform, transition, isDragging} = useSortable({
        id: role.id
    });
    const style: CSSProperties = {
        ...roleWrapperStyle,
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.6 : undefined
    };

    return (
        <div
            ref={setNodeRef}
            style={style}
            {...attributes}
            {...listeners}
        >
            {children}
        </div>
    );
}

function UserListRoute(){
    const {showToast} = useToast();
    const {t} = useI18n();
    const [users, setUsers] = useState<User[] | null>(null);
    const [roles, setRoles] = useState<Role[] | null>(null);
    const [currentUser, setCurrentUser] = useState<User | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [addingUser, setAddingUser] = useState(false);
    const [addingRole, setAddingRole] = useState(false);
    const [newUserEmail, setNewUserEmail] = useState("");
    const [newUserRoleId, setNewUserRoleId] = useState<number | null>(null);
    const [newRoleName, setNewRoleName] = useState("");
    const [newRolePermissions, setNewRolePermissions] = useState<Record<string, boolean>>({});
    const [savingUser, setSavingUser] = useState(false);
    const [savingRole, setSavingRole] = useState(false);
    const [expandedUserId, setExpandedUserId] = useState<number | null>(null);
    const [expandedRoleId, setExpandedRoleId] = useState<number | null>(null);
    const [editedUserRoleId, setEditedUserRoleId] = useState<number | null>(null);
    const [editedRoleName, setEditedRoleName] = useState("");
    const [editedRolePermissions, setEditedRolePermissions] = useState<Record<string, boolean>>({});
    const [savingExpandedUser, setSavingExpandedUser] = useState(false);
    const [savingExpandedRole, setSavingExpandedRole] = useState(false);
    const sensors = useSensors(
        useSensor(PointerSensor, {
            activationConstraint: {
                distance: 8
            }
        })
    );

    useEffect(() => {
        async function load(){
            const [loadedUsers, loadedRoles, loadedCurrentUser] = await Promise.all([
                getUsers(),
                getRoles(),
                getMe()
            ]);
            setUsers(loadedUsers);
            setRoles(loadedRoles);
            setCurrentUser(loadedCurrentUser);
            setNewUserRoleId(loadedRoles.find(role => role.name.toLowerCase() === "user")?.id ?? loadedRoles[0]?.id ?? null);
            setNewRolePermissions(emptyPermissions(loadedRoles));
        }

        load().catch(reason => setError(reason instanceof Error ? reason.message : "Failed to load users"));
    }, []);

    if (!users || !roles || !currentUser){
        return <></>
    }

    const loadedUsers = users;
    const loadedRoles = roles;
    const loadedCurrentUser = currentUser;
    const currentUserRoleIndex = loadedRoles.findIndex(role => role.id === loadedCurrentUser.roleId);
    const currentUserPermissions = loadedRoles.find(role => role.id === loadedCurrentUser.roleId)?.permissionOptions ?? {};

    function hasPermission(permission: string): boolean {
        return loadedCurrentUser.hasAsterix || currentUserPermissions[permission];
    }

    const canAddUsers = hasPermission("USERS_ADD");
    const canEditRoles = hasPermission("ROLES_ADD");
    const canMoveRoles = hasPermission("ROLES_MOVE");

    function isRoleLocked(role: Role): boolean {
        if (!canMoveRoles) return true;
        if (loadedCurrentUser.hasAsterix) return false;
        if (currentUserRoleIndex < 0) return false;
        return loadedRoles.findIndex(candidate => candidate.id === role.id) <= currentUserRoleIndex;
    }

    const unlockedRoleIds = loadedRoles
        .filter(role => !isRoleLocked(role))
        .map(role => role.id);

    function toggleUser(user: User) {
        if (!canAddUsers) return;
        setAddingUser(false);
        setError(null);
        if (expandedUserId === user.id) {
            setExpandedUserId(null);
            return;
        }
        setExpandedRoleId(null);
        setExpandedUserId(user.id);
        setEditedUserRoleId(user.roleId);
    }

    function toggleRole(role: Role) {
        if (!canEditRoles) return;
        setAddingRole(false);
        setError(null);
        if (expandedRoleId === role.id) {
            setExpandedRoleId(null);
            return;
        }
        setExpandedUserId(null);
        setExpandedRoleId(role.id);
        setEditedRoleName(role.name);
        setEditedRolePermissions(role.permissionOptions);
    }

    async function handleUpdateUserRole(event: FormEvent<HTMLFormElement>, user: User) {
        event.preventDefault();
        if (editedUserRoleId === null || savingExpandedUser) return;

        try {
            setSavingExpandedUser(true);
            const updatedUser = user.roleId === editedUserRoleId
                ? user
                : await updateUserRole(user.id, editedUserRoleId);
            setUsers(loadedUsers.map(candidate => candidate.id === user.id ? updatedUser : candidate));
            setExpandedUserId(null);
            setError(null);
            showToast(user.roleId === editedUserRoleId ? "No changes to save" : "User saved");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Failed to save user");
        } finally {
            setSavingExpandedUser(false);
        }
    }

    async function handleUpdateRole(event: FormEvent<HTMLFormElement>, role: Role) {
        event.preventDefault();
        if (savingExpandedRole) return;

        const trimmedName = editedRoleName.trim();
        const nameChanged = trimmedName !== role.name;
        const permissionChanges = Object.fromEntries(
            Object.entries(editedRolePermissions)
                .filter(([permission, enabled]) => role.permissionOptions[permission] !== enabled)
        );
        const permissionsChanged = Object.keys(permissionChanges).length > 0;

        try {
            setSavingExpandedRole(true);
            const updatedRole = nameChanged || permissionsChanged
                ? await updateRole(role.id, {
                    ...(nameChanged ? {name: trimmedName} : {}),
                    ...(permissionsChanged ? {permissions: permissionChanges} : {})
                })
                : role;
            setRoles(loadedRoles.map(candidate => candidate.id === role.id ? updatedRole : candidate));
            if (nameChanged) {
                setUsers(loadedUsers.map(user => user.roleId === role.id ? {...user, role: updatedRole.name} : user));
            }
            setExpandedRoleId(null);
            setError(null);
            showToast(nameChanged || permissionsChanged ? "Role saved" : "No changes to save");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Failed to save role");
        } finally {
            setSavingExpandedRole(false);
        }
    }

    async function handleCreateUser(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (newUserRoleId === null || savingUser) return;

        try {
            setSavingUser(true);
            const createdUser = await createUser(newUserEmail, newUserRoleId);
            setUsers([...loadedUsers, createdUser].sort((left, right) => left.username.localeCompare(right.username)));
            setNewUserEmail("");
            setAddingUser(false);
            setError(null);
            showToast("User added");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Failed to add user");
        } finally {
            setSavingUser(false);
        }
    }

    async function handleCreateRole(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (savingRole) return;

        try {
            setSavingRole(true);
            const createdRole = await createRole(newRoleName, permissionMask(
                newRolePermissions,
                loadedRoles[0]?.permissionValues ?? {}
            ));
            setRoles([...loadedRoles, createdRole]);
            setNewRoleName("");
            setNewRolePermissions(emptyPermissions([...loadedRoles, createdRole]));
            setAddingRole(false);
            setError(null);
            showToast("Role added");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Failed to add role");
        } finally {
            setSavingRole(false);
        }
    }

    async function handleRoleDragEnd(event: DragEndEvent) {
        const {active, over} = event;
        if (!over || active.id === over.id) return;

        const activeRoleId = Number(active.id);
        const overRoleId = Number(over.id);
        const oldIndex = loadedRoles.findIndex(role => role.id === activeRoleId);
        const newIndex = loadedRoles.findIndex(role => role.id === overRoleId);
        if (oldIndex < 0 || newIndex < 0) return;
        if (isRoleLocked(loadedRoles[oldIndex]) || isRoleLocked(loadedRoles[newIndex])) return;

        const nextRoles = arrayMove(loadedRoles, oldIndex, newIndex);
        const nextIndex = nextRoles.findIndex(role => role.id === activeRoleId);
        const afterRoleId = nextIndex === 0 ? 0 : nextRoles[nextIndex - 1].id;
        setRoles(nextRoles);
        setError(null);

        try {
            setRoles(await moveRole(activeRoleId, afterRoleId));
        } catch (reason) {
            setRoles(loadedRoles);
            setError(reason instanceof Error ? reason.message : "Failed to move role");
        }
    }

    return (
        <>
            <div className={styles.scrollContainer}>
                {error && <p className={styles.error} role="alert">{error}</p>}
                <div className={styles.list}>
                    <div className={styles.listHeader}>
                        <h2>{t("page.users")}</h2>
                        {canAddUsers && (
                            <Button
                                type={addingUser ? "secondary" : "primary"}
                                onClick={() => {
                                    setExpandedUserId(null);
                                    setExpandedRoleId(null);
                                    setAddingUser(current => !current);
                                }}
                            >
                                {addingUser ? <FaTimes aria-hidden/> : <FaPlus aria-hidden/>}
                                <span>{addingUser ? t("action.cancel") : t("action.add_user")}</span>
                            </Button>
                        )}
                    </div>
                    {addingUser && (
                        <form className={styles.addForm} onSubmit={handleCreateUser}>
                            <label className={styles.field}>
                                <span>{t("field.email")}</span>
                                <input
                                    type="email"
                                    value={newUserEmail}
                                    onChange={event => setNewUserEmail(event.target.value)}
                                    required
                                />
                            </label>
                            <label className={styles.field}>
                                <span>{t("field.role")}</span>
                                <Dropdown
                                    id="new-user-role"
                                    value={newUserRoleId}
                                    options={loadedRoles.map(role => ({value: role.id, label: role.name}))}
                                    onChange={setNewUserRoleId}
                                />
                            </label>
                            <Button type="primary" buttonType="submit" disabled={savingUser || newUserRoleId === null}>{t("action.add")}</Button>
                        </form>
                    )}
                    {loadedUsers.map(user => (
                        <div className={styles.itemShell} key={user.id}>
                            <UserCard
                                id={user.id}
                                role={user.role}
                                title={user.username}
                                email={user.email}
                                expanded={expandedUserId === user.id}
                                onClick={canAddUsers ? () => toggleUser(user) : undefined}
                            />
                            {expandedUserId === user.id && (
                                <form className={styles.editForm} onSubmit={event => handleUpdateUserRole(event, user)}>
                                    <label className={styles.field}>
                                        <span>{t("field.role")}</span>
                                        <Dropdown
                                            id={`user-${user.id}-role`}
                                            value={editedUserRoleId}
                                            options={loadedRoles.map(role => ({value: role.id, label: role.name}))}
                                            onChange={setEditedUserRoleId}
                                        />
                                    </label>
                                    <div className={styles.formActions}>
                                        <Button type="secondary" onClick={() => setExpandedUserId(null)}>{t("action.cancel")}</Button>
                                        <Button
                                            type="primary"
                                            buttonType="submit"
                                            disabled={savingExpandedUser || editedUserRoleId === null}
                                        >
                                            {t("action.save")}
                                        </Button>
                                    </div>
                                </form>
                            )}
                        </div>
                    ))}
                </div>
                <div className={styles.list}>
                    <div className={styles.listHeader}>
                        <h2>{t("page.roles")}</h2>
                        {canEditRoles && (
                            <Button
                                type={addingRole ? "secondary" : "primary"}
                                onClick={() => {
                                    setExpandedUserId(null);
                                    setExpandedRoleId(null);
                                    setAddingRole(current => !current);
                                }}
                            >
                                {addingRole ? <FaTimes aria-hidden/> : <FaPlus aria-hidden/>}
                                <span>{addingRole ? t("action.cancel") : t("action.add_role")}</span>
                            </Button>
                        )}
                    </div>
                    {addingRole && (
                        <form className={styles.addForm} onSubmit={handleCreateRole}>
                            <label className={styles.field}>
                                <span>{t("field.name")}</span>
                                <input
                                    value={newRoleName}
                                    onChange={event => setNewRoleName(event.target.value)}
                                    maxLength={50}
                                    required
                                />
                            </label>
                            <PermissionGroups
                                idPrefix="new-role-permission"
                                permissions={newRolePermissions}
                                onChange={setNewRolePermissions}
                            />
                            <Button type="primary" buttonType="submit" disabled={savingRole}>{t("action.add")}</Button>
                        </form>
                    )}
                    <div className={styles.roleDndBoundary}>
                        <DndContext sensors={sensors} collisionDetection={closestCenter} autoScroll={false} modifiers={[restrictToVerticalAxis, restrictToParentElement]} onDragEnd={handleRoleDragEnd}>
                            <SortableContext items={unlockedRoleIds} strategy={verticalListSortingStrategy}>
                                {loadedRoles.map(role => {
                                    const locked = isRoleLocked(role);
                                    const card = (
                                        <div className={styles.itemShell}>
                                            <RoleCard
                                                title={role.name}
                                                id={role.id}
                                                locked={locked}
                                                expanded={expandedRoleId === role.id}
                                                onClick={canEditRoles ? () => toggleRole(role) : undefined}
                                            />
                                            {expandedRoleId === role.id && (
                                                <form className={styles.editForm} onSubmit={event => handleUpdateRole(event, role)} onPointerDown={event => event.stopPropagation()}>
                                                    <label className={styles.field}>
                                                        <span>{t("field.name")}</span>
                                                        <input
                                                            value={editedRoleName}
                                                            onChange={event => setEditedRoleName(event.target.value)}
                                                            maxLength={50}
                                                            required
                                                        />
                                                    </label>
                                                    <PermissionGroups
                                                        idPrefix={`role-${role.id}-permission`}
                                                        permissions={editedRolePermissions}
                                                        onChange={setEditedRolePermissions}
                                                    />
                                                    <div className={styles.formActions}>
                                                        <Button type="secondary" onClick={() => setExpandedRoleId(null)}>{t("action.cancel")}</Button>
                                                        <Button type="primary" buttonType="submit" disabled={savingExpandedRole}>{t("action.save")}</Button>
                                                    </div>
                                                </form>
                                            )}
                                        </div>
                                    );

                                    if (locked) {
                                        return <div key={role.id} style={roleWrapperStyle}>{card}</div>;
                                    }

                                    return <SortableRole key={role.id} role={role}>{card}</SortableRole>;
                                })}
                            </SortableContext>
                        </DndContext>
                    </div>
                </div>
            </div>
        </>
    );
}

export default UserListRoute;
