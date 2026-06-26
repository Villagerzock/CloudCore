import {useEffect, useMemo, useState} from "react";
import type {FormEvent} from "react";
import {useParams} from "react-router";
import {getRole, updateRole, usePersistentNavigate, type Role} from "../lib/api.ts";
import styles from "./ConfigRoute.module.css";
import {useToast} from "../components/ToastProvider.tsx";
import Button from "../components/Button.tsx";
import {useI18n} from "../lib/i18n.ts";
import PermissionGroups from "../components/PermissionGroups.tsx";

function RoleConfigRoute() {
    const {id} = useParams();
    const navigate = usePersistentNavigate();
    const {showToast} = useToast();
    const {t} = useI18n();
    const roleId = Number(id);
    const [role, setRole] = useState<Role | null>(null);
    const [name, setName] = useState("");
    const [permissions, setPermissions] = useState<Record<string, boolean>>({});
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!Number.isSafeInteger(roleId) || roleId <= 0) {
            setError("Invalid role");
            return;
        }

        getRole(roleId)
            .then(loadedRole => {
                setRole(loadedRole);
                setName(loadedRole.name);
                setPermissions(loadedRole.permissionOptions);
            })
            .catch(reason => setError(reason instanceof Error ? reason.message : "Failed to load role"));
    }, [roleId]);

    const permissionChanges = useMemo(() => {
        if (role === null) return {};
        return Object.fromEntries(
            Object.entries(permissions)
                .filter(([permission, enabled]) => role.permissionOptions[permission] !== enabled)
        );
    }, [permissions, role]);

    const nameChanged = role !== null && name.trim() !== role.name;
    const permissionsChanged = Object.keys(permissionChanges).length > 0;

    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (role === null || saving) return;

        try {
            setSaving(true);
            if (nameChanged || permissionsChanged) {
                await updateRole(role.id, {
                    ...(nameChanged ? {name: name.trim()} : {}),
                    ...(permissionsChanged ? {permissions: permissionChanges} : {})
                });
            }
            setError(null);
            showToast(nameChanged || permissionsChanged ? "Role saved" : "No changes to save");
            navigate("/users");
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : "Failed to save role");
        } finally {
            setSaving(false);
        }
    }

    function cancel() {
        navigate("/users");
    }

    if (role === null) {
        return <div className={styles.page}>{error && <p className={styles.error}>{error}</p>}</div>;
    }

    return (
        <main className={styles.page}>
            <div className={styles.header}>
                <h1>{role.name}</h1>
                <p>{t("role.config")}</p>
            </div>
            <form className={styles.form} onSubmit={save}>
                <div className={styles.field}>
                    <label htmlFor="role-name">{t("field.name")}</label>
                    <input id="role-name" value={name} onChange={event => setName(event.target.value)}/>
                </div>
                <PermissionGroups
                    idPrefix="permission"
                    permissions={permissions}
                    onChange={setPermissions}
                />
                {error && <p className={styles.error}>{error}</p>}
                <div className={styles.actions}>
                    <Button type="secondary" onClick={cancel}>{t("action.cancel")}</Button>
                    <Button type="primary" buttonType="submit" disabled={saving}>{t("action.save")}</Button>
                </div>
            </form>
        </main>
    );
}

export default RoleConfigRoute;
